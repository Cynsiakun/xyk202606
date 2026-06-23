package com.cd.service.impl;

import com.cd.common.config.RabbitMQConfig;
import com.cd.mapper.HostMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCommandCleanupService {

    private static final String PORT_SCAN_TYPE = "port_scan";

    private final HostMapper hostMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmqpAdmin amqpAdmin;

    public int clearPendingPortScanCommandsForAllHosts() {
        List<String> macAddresses = hostMapper.selectAllMacAddresses();
        int removedTotal = 0;
        for (String macAddress : macAddresses) {
            removedTotal += clearPendingPortScanCommands(macAddress);
        }
        if (removedTotal > 0) {
            log.info("已清理残留端口扫描指令: removedCount={}", removedTotal);
        }
        return removedTotal;
    }

    public int clearPendingPortScanCommands(String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            return 0;
        }

        String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX + normalizeMac(macAddress) + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
        Properties queueProperties = amqpAdmin.getQueueProperties(queueName);
        if (queueProperties == null) {
            return 0;
        }

        List<Message> retainedMessages = new ArrayList<>();
        int removedCount = 0;
        while (true) {
            Message message = rabbitTemplate.receive(queueName, 100);
            if (message == null) {
                break;
            }
            if (isPortScanMessage(message)) {
                removedCount++;
                continue;
            }
            retainedMessages.add(message);
        }

        for (Message message : retainedMessages) {
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            if (!StringUtils.hasText(routingKey)) {
                routingKey = macAddress;
            }
            rabbitTemplate.send(RabbitMQConfig.AGENT_EXCHANGE, routingKey, message);
        }

        if (removedCount > 0) {
            log.info("客户端队列残留端口扫描指令已清理: queue={}, mac={}, removedCount={}, retainedCount={}",
                    queueName, macAddress, removedCount, retainedMessages.size());
        }
        return removedCount;
    }

    private boolean isPortScanMessage(Message message) {
        try {
            JsonNode root = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            return PORT_SCAN_TYPE.equals(root.path("type").asText());
        } catch (Exception e) {
            log.debug("识别客户端队列消息类型失败，按非端口扫描消息保留: {}", e.getMessage());
            return false;
        }
    }

    private String normalizeMac(String mac) {
        return mac == null ? "" : mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }
}
