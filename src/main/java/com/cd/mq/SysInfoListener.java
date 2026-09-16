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
            host.setHostname(firstText(
                    text(root, "hostName"),
                    text(root, "hostname"),
                    text(root, "主机名")
            ));
            host.setIpv4(firstText(
                    text(root, "ipv4"),
                    text(root, "本机IPv4地址"),
                    text(root, "本机IPv4"),
                    nestedText(root, "system", "ipv4")
            ));
            host.setMacAddress(firstText(
                    text(root, "macAddress"),
                    text(root, "mac"),
                    text(root, "MAC地址"),
                    nestedText(root, "system", "macAddress")
            ));

            host.setOsName(firstText(
                    text(root, "osName"),
                    nestedText(root, "os", "name"),
                    nestedText(root, "system", "osName"),
                    nestedText(root, "操作系统信息", "系统名称")
            ));
            host.setOsVersion(firstText(
                    text(root, "osVersion"),
                    nestedText(root, "os", "version"),
                    nestedText(root, "system", "osVersion"),
                    nestedText(root, "操作系统信息", "系统版本")
            ));
            host.setOsArch(firstText(
                    text(root, "osArch"),
                    nestedText(root, "os", "arch"),
                    nestedText(root, "system", "osArch"),
                    nestedText(root, "操作系统信息", "系统架构")
            ));
            host.setOsRelease(firstText(
                    text(root, "osRelease"),
                    nestedText(root, "os", "release"),
                    nestedText(root, "system", "osRelease"),
                    nestedText(root, "操作系统信息", "具体版本")
            ));

            host.setCpuModel(firstText(
                    text(root, "cpuModel"),
                    nestedText(root, "cpu", "model"),
                    nestedText(root, "CPU信息", "CPU型号")
            ));
            host.setCpuPhysicalCores(firstInteger(
                    integer(root, "cpuPhysicalCores"),
                    nestedInteger(root, "cpu", "physicalCores"),
                    nestedInteger(root, "CPU信息", "物理核心数")
            ));
            host.setCpuLogicalCores(firstInteger(
                    integer(root, "cpuLogicalCores"),
                    nestedInteger(root, "cpu", "logicalCores"),
                    nestedInteger(root, "CPU信息", "逻辑核心数")
            ));

            host.setMemTotal(firstText(
                    text(root, "memTotal"),
                    nestedText(root, "memory", "total"),
                    nestedText(root, "内存信息", "总内存")
            ));
            host.setMemUsed(firstText(
                    text(root, "memUsed"),
                    nestedText(root, "memory", "used"),
                    nestedText(root, "内存信息", "已用内存")
            ));
            host.setMemAvailable(firstText(
                    text(root, "memAvailable"),
                    nestedText(root, "memory", "available"),
                    nestedText(root, "内存信息", "可用内存")
            ));
            host.setMemUsage(firstText(
                    text(root, "memUsage"),
                    nestedText(root, "memory", "usage"),
                    nestedText(root, "内存信息", "使用率")
            ));

            if (!StringUtils.hasText(host.getMacAddress())) {
                log.warn("收到的主机信息缺少 MAC 地址，跳过入库: {}", message);
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

    private void routeToAgentQueue(String rawMac, String message) {
        String normalizedMac = normalizeMac(rawMac);
        if (!StringUtils.hasText(normalizedMac)) {
            log.warn("MAC 标准化后为空，跳过转发: {}", rawMac);
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
        log.info("sysinfo 注册确认已转发到客户端专属队列: queue={}, routingKey={}", queueName, rawMac);
    }

    private String normalizeMac(String mac) {
        if (mac == null) {
            return null;
        }
        return mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    private String text(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String value = child.isValueNode() ? child.asText() : child.toString();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nestedText(JsonNode node, String objectField, String valueField) {
        JsonNode nested = node.path(objectField);
        return nested.isObject() ? text(nested, valueField) : null;
    }

    private Integer integer(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        if (child.canConvertToInt()) {
            return child.asInt();
        }
        try {
            return Integer.parseInt(child.asText().trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer nestedInteger(JsonNode node, String objectField, String valueField) {
        JsonNode nested = node.path(objectField);
        return nested.isObject() ? integer(nested, valueField) : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer firstInteger(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
