package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.entity.HostEntity;
import com.cd.service.HostService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 监听 {@code sysinfo_queue}，将采集端上报的主机系统信息入库。
 *
 * <p>消息为中文键的嵌套 JSON，使用 {@link JsonNode} 逐层取值最稳健。解析或入库失败时
 * 仅记录日志而不抛异常，避免消息被无限重新投递。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysInfoListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HostService hostService;
    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.SYSINFO_QUEUE)
    public void onMessage(String message) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(message);

            HostEntity host = new HostEntity();
            host.setHostname(text(root, "主机名", "主机名"));
            host.setIpv4(text(root, "本机IPv4地址", "本机IPv4"));
            host.setMacAddress(text(root, "MAC地址", "MAC地址"));

            JsonNode os = root.path("操作系统信息");
            host.setOsName(text(os, "系统名称"));
            host.setOsVersion(text(os, "系统版本"));
            host.setOsArch(text(os, "系统架构"));
            host.setOsRelease(text(os, "具体版本"));

            JsonNode cpu = root.path("CPU信息");
            host.setCpuModel(text(cpu, "CPU型号"));
            host.setCpuPhysicalCores(integer(cpu, "物理核心数"));
            host.setCpuLogicalCores(integer(cpu, "逻辑核心数"));

            JsonNode mem = root.path("内存信息");
            host.setMemTotal(text(mem, "总内存"));
            host.setMemUsed(text(mem, "已使用内存"));
            host.setMemAvailable(text(mem, "可用内存"));
            host.setMemUsage(text(mem, "使用率"));

            if (!StringUtils.hasText(host.getMacAddress())) {
                log.warn("收到的主机信息缺少 MAC 地址，已跳过入库: {}", message);
                return;
            }

            host.setStatus(1);
            hostService.saveOrUpdateFromMessage(host);
            log.info("主机信息已入库: mac={}, hostname={}", host.getMacAddress(), host.getHostname());

            routeToAgentQueue(host.getMacAddress(), message);
        } catch (Exception e) {
            log.error("处理主机信息消息失败，已丢弃该消息: {}", message, e);
        }
    }

    /**
     * 为指定 MAC 客户端声明专属队列并将消息转发到 {@code agent_exchange}。
     *
     * <p>交换机、队列、绑定的声明均为幂等操作，已存在时不会报错。声明完成后将原始消息
     * 以 MAC 地址为 routingKey 重新发布，使其进入对应的 {@code agent_<mac>_queue}。</p>
     */
    private void routeToAgentQueue(String rawMac, String message) {
        String normalizedMac = normalizeMac(rawMac);
        if (!StringUtils.hasText(normalizedMac)) {
            log.warn("MAC 地址标准化后为空，跳过转发: {}", rawMac);
            return;
        }

        String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX + normalizedMac + RabbitMQConfig.AGENT_QUEUE_SUFFIX;

        DirectExchange exchange = new DirectExchange(RabbitMQConfig.AGENT_EXCHANGE, true, false);
        amqpAdmin.declareExchange(exchange);

        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-expires", RabbitMQConfig.AGENT_QUEUE_EXPIRES)
                .withArgument("x-message-ttl", RabbitMQConfig.AGENT_MESSAGE_TTL)
                .build();
        amqpAdmin.declareQueue(queue);

        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(rawMac));

        rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, rawMac, message);
        log.info("消息已转发至客户端专属队列: queue={}, routingKey={}", queueName, rawMac);
    }

    /** 标准化 MAC：转小写并去除冒号、横杠、空格等分隔符。 */
    private String normalizeMac(String mac) {
        if (mac == null) {
            return null;
        }
        return mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    private String text(JsonNode parent, String... path) {
        JsonNode node = parent;
        for (String key : path) {
            node = node.path(key);
        }
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private Integer integer(JsonNode parent, String key) {
        JsonNode node = parent.path(key);
        return node.isMissingNode() || node.isNull() || !node.canConvertToInt() ? null : node.asInt();
    }
}
