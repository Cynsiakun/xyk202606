package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.service.HostService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 监听 {@code status_queue}，处理采集端上报的主机心跳。
 *
 * <p>消息格式为 {@code {"mac_address":"00:1A:2B:3C:4D:5E"}}。服务端以「消费到消息的时刻」
 * 作为该主机的最后活跃时间，不信任客户端自带的时间戳。</p>
 *
 * <p>采用手动 ACK：入库成功后才 ACK；任何异常（解析失败、数据库异常等）打印日志后 NACK
 * 并重新入队，避免消息丢失。容器工厂见 {@code manualAckContainerFactory}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostStatusListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HostService hostService;

    @RabbitListener(queues = RabbitMQConfig.STATUS_QUEUE, containerFactory = "manualAckContainerFactory")
    public void onHeartbeat(String message, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(message);
            JsonNode macNode = root.path("mac_address");
            String macAddress = macNode.isMissingNode() || macNode.isNull() ? null : macNode.asText();

            if (!StringUtils.hasText(macAddress)) {
                throw new IllegalArgumentException("心跳消息缺少 mac_address");
            }

            // 以消费时刻为最后活跃时间：status=1，updated_at=NOW()（DB 时间）
            hostService.heartbeat(macAddress);
            channel.basicAck(deliveryTag, false);
            log.debug("心跳已处理: mac={}", macAddress);
        } catch (Exception e) {
            log.error("处理心跳消息失败，重新入队: {}", message, e);
            // requeue=true：重新入队，避免丢失
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
