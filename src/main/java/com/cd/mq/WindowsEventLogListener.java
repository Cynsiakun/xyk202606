package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.entity.AccountChangeLogEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.LoginSecurityLogEntity;
import com.cd.entity.MqErrorLogEntity;
import com.cd.entity.WindowsEventLogEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.MqErrorLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 监听 {@code log_queue}，消费 Windows 事件日志。
 *
 * <p>流程：基本校验 → 按 {@code mac_address} 映射 {@code host_id} → 映射为
 * {@link WindowsEventLogEntity} 放入 {@link WindowsEventLogBatchWriter} 的缓冲队列。
 * 真正的入库与 ACK/NACK 由批量写入器在事务中统一完成，本监听器不在这里 ACK 成功入队的消息。</p>
 *
 * <p>broker 上 {@code log_queue} 由采集端预建且无死信参数，本服务只消费不声明。因没有死信
 * 队列，所有「坏消息」统一落 {@code mq_error_logs} 表后正常 ACK，既不丢失也不无限重投。</p>
 *
 * <p>异常处理：</p>
 * <ul>
 *   <li>格式非法（JSON 解析失败 / 缺少关键字段）：写入 {@code mq_error_logs} 后 ACK，不重试。</li>
 *   <li>主机未注册（mac 查不到 host_id）：记录 WARN，按消息头 {@code x-retry-count} 计数，
 *       republish 到本队列重试；超过 {@code MAX_RETRY} 次后落 {@code mq_error_logs} 并 ACK。</li>
 *   <li>入队被中断等系统异常：{@code nack(requeue=true)} 重新投递。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WindowsEventLogListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_RETRY = 3;

    /**
     * 规范化 mac → host_id 的本地缓存，避免每条消息都查库（{@code selectByNormalizedMac}
     * 因 SQL 内做 REPLACE/LOWER 无法走索引）。仅缓存命中结果；未命中不缓存，使新注册主机能在
     * 下一条消息被解析。写后 5 分钟过期，兜底主机 mac 变更等场景。
     */
    private final Cache<String, Long> hostIdCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    private final HostMapper hostMapper;
    private final MqErrorLogMapper mqErrorLogMapper;
    private final WindowsEventLogBatchWriter batchWriter;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.LOG_QUEUE, containerFactory = "windowsLogContainerFactory")
    public void onMessage(@Payload String message,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = RabbitMQConfig.HEADER_RETRY_COUNT, required = false) Integer retryCount)
            throws IOException {

        WindowsEventLogEntity entity;
        String macAddress;
        int eventId;
        LocalDateTime eventTime;
        String rawXml;
        String msgUsername;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(message);
            macAddress = text(root, "mac_address");
            if (!StringUtils.hasText(macAddress)) {
                throw new IllegalArgumentException("缺少 mac_address");
            }
            Long recordNumber = longValue(root, "record_number");
            String logType = text(root, "log_type");
            Integer eventIdBox = integer(root, "event_id");
            eventTime = parseTime(text(root, "event_time"));
            // event_id、event_time 在表中为 NOT NULL，缺失视为格式非法
            if (recordNumber == null || !StringUtils.hasText(logType)
                    || eventIdBox == null || eventTime == null) {
                throw new IllegalArgumentException("缺少 record_number / log_type / event_id / event_time");
            }
            eventId = eventIdBox;
            rawXml = text(root, "raw_xml");
            msgUsername = text(root, "username");
            entity = mapToEntity(root, logType, recordNumber, eventId, eventTime);
        } catch (Exception parseEx) {
            // 格式非法：落 mq_error_logs 后正常 ACK，绝不无限重试，也不丢失
            log.warn("log_queue 消息格式非法，落 mq_error_logs 后丢弃: {}", message, parseEx);
            recordError(message, "格式非法: " + parseEx.getMessage());
            channel.basicAck(deliveryTag, false);
            return;
        }

        // 按 mac 映射 host_id（命中缓存则免查库）
        Long hostId = resolveHostId(macAddress);
        if (hostId == null) {
            handleHostNotFound(message, macAddress, channel, deliveryTag, retryCount);
            return;
        }
        entity.setHostId(hostId);

        // 分流：登录 / 账户变更事件解析为子记录，随主记录在同一事务内入库（source_log_id 稍后回填）
        LoginSecurityLogEntity login = WindowsSecurityEventParser.isLoginEvent(eventId)
                ? WindowsSecurityEventParser.parseLogin(eventId, eventTime, hostId, msgUsername, rawXml)
                : null;
        AccountChangeLogEntity account = WindowsSecurityEventParser.isAccountEvent(eventId)
                ? WindowsSecurityEventParser.parseAccount(eventId, eventTime, hostId, rawXml)
                : null;

        // 放入批量缓冲；入库与 ACK 由 WindowsEventLogBatchWriter 统一完成
        try {
            batchWriter.enqueue(new EventLogMessage(entity, login, account, channel, deliveryTag));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("入队被中断，NACK 重新投递: mac={}, record={}", macAddress, entity.getRecordNumber(), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 解析 mac 对应的 host_id：先查本地缓存，未命中再查库，命中库则回填缓存。
     * 主机不存在返回 {@code null}（不缓存，便于主机注册后及时映射）。
     */
    private Long resolveHostId(String macAddress) {
        String nmac = normalizeMac(macAddress);
        Long cached = hostIdCache.getIfPresent(nmac);
        if (cached != null) {
            return cached;
        }
        HostEntity host = hostMapper.selectByNormalizedMac(nmac);
        if (host != null && host.getId() != null) {
            hostIdCache.put(nmac, host.getId());
            return host.getId();
        }
        return null;
    }

    private void handleHostNotFound(String message, String macAddress, Channel channel,
                                    long deliveryTag, Integer retryCount) throws IOException {
        int attempts = retryCount == null ? 0 : retryCount;
        if (attempts >= MAX_RETRY) {
            log.warn("主机未注册且重试已达上限({})，落 mq_error_logs 后丢弃: mac={}", MAX_RETRY, macAddress);
            recordError(message, "主机未注册，mac=" + macAddress + "，重试" + attempts + "次仍未找到");
            channel.basicAck(deliveryTag, false);
            return;
        }
        log.warn("主机未注册，第 {} 次重试: mac={}", attempts + 1, macAddress);
        republishWithRetry(message, attempts + 1);
        // 原消息已重新投递为新消息，确认掉当前这条
        channel.basicAck(deliveryTag, false);
    }

    private void republishWithRetry(String message, int nextRetryCount) {
        rabbitTemplate.convertAndSend("", RabbitMQConfig.LOG_QUEUE, message, m -> {
            MessageProperties props = m.getMessageProperties();
            props.setHeader(RabbitMQConfig.HEADER_RETRY_COUNT, nextRetryCount);
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return m;
        });
    }

    private WindowsEventLogEntity mapToEntity(JsonNode root, String logType, Long recordNumber,
                                              Integer eventId, LocalDateTime eventTime) {
        WindowsEventLogEntity entity = new WindowsEventLogEntity();
        entity.setLogType(truncate(logType, 20));
        entity.setRecordNumber(recordNumber);
        entity.setEventId(eventId);
        entity.setEventTime(eventTime);
        entity.setUsername(truncate(text(root, "username"), 255));
        entity.setLevel(truncate(text(root, "level"), 20));
        // message 列 varchar(1000)，超长截断；完整内容保留在 raw_json
        entity.setMessage(truncate(text(root, "message"), 1000));
        entity.setRawJson(text(root, "raw_xml"));
        return entity;
    }

    private void recordError(String message, String reason) {
        try {
            MqErrorLogEntity errorLog = new MqErrorLogEntity();
            errorLog.setQueueName(RabbitMQConfig.LOG_QUEUE);
            errorLog.setRawMessage(message);
            errorLog.setErrorReason(reason);
            mqErrorLogMapper.insert(errorLog);
        } catch (Exception e) {
            log.error("写入 mq_error_logs 失败: reason={}", reason, e);
        }
    }

    private static String normalizeMac(String mac) {
        return mac.replace(":", "").replace("-", "").replace(" ", "").toLowerCase();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private static LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), EVENT_TIME_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || !v.canConvertToInt() ? null : v.asInt();
    }

    private static Long longValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || !v.canConvertToLong() ? null : v.asLong();
    }
}
