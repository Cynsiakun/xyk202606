package com.cd.service.impl;

import com.cd.entity.HostEntity;
import com.cd.entity.MqErrorLogEntity;
import com.cd.entity.PortScanResultEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.mapper.HostVulnTaskMapper;
import com.cd.mapper.MqErrorLogMapper;
import com.cd.mapper.PortScanResultMapper;
import com.cd.service.PortFingerprintService;
import com.cd.service.PortScanResultService;
import com.cd.service.VulnRuleEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortScanResultServiceImpl implements PortScanResultService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_PLATFORM = "PLATFORM";
    private static final String VERIFYING = "VERIFYING";
    private static final int TASK_STATUS_RUNNING = 1;

    private final HostMapper hostMapper;
    private final PortScanResultMapper portScanResultMapper;
    private final MqErrorLogMapper mqErrorLogMapper;
    private final HostVulnResultMapper hostVulnResultMapper;
    private final HostVulnTaskMapper hostVulnTaskMapper;
    private final PortFingerprintService portFingerprintService;
    private final VulnRuleEngine vulnRuleEngine;

    @Override
    public void processPortScanMessage(String queueName, String message) {
        try {
            log.info("收到端口采集消息: queue={}", queueName);

            JsonNode root;
            try {
                root = OBJECT_MAPPER.readTree(message);
            } catch (Exception e) {
                saveError(queueName, message, "JSON 格式非法: " + e.getMessage());
                return;
            }

            String type = text(root, "type");
            if (!"port_scan".equals(type)) {
                saveError(queueName, message, "type 不匹配，期望 port_scan，实际为: " + type);
                return;
            }

            String hostName = text(root, "hostName");
            if (!StringUtils.hasText(hostName)) {
                saveError(queueName, message, "缺少必要字段 hostName 或其类型不正确");
                return;
            }

            String macAddress = text(root, "macAddress");
            String normalizedMac = normalizeMac(macAddress);
            if (!StringUtils.hasText(normalizedMac)) {
                saveError(queueName, message, "缺少必要字段 macAddress 或其类型不正确");
                return;
            }

            HostEntity host = hostMapper.selectByNormalizedMac(normalizedMac);
            if (host == null) {
                saveError(queueName, message, "未找到匹配主机: mac=" + macAddress);
                log.warn("端口采集消息未关联到主机: mac={}", macAddress);
                return;
            }

            JsonNode openPortsNode = root.path("openPorts");
            if (openPortsNode.isMissingNode() || openPortsNode.isNull() || !openPortsNode.isArray()) {
                saveError(queueName, message, "缺少必要字段 openPorts 或其类型不是数组");
                return;
            }

            JsonNode openPortCountNode = root.path("openPortCount");
            if (openPortCountNode.isMissingNode() || openPortCountNode.isNull() || !openPortCountNode.canConvertToInt()) {
                saveError(queueName, message, "缺少必要字段 openPortCount 或其类型不是数字");
                return;
            }

            int openPortCount = openPortCountNode.asInt();
            String taskId = text(root, "taskId");
            Long tenantId = host.getTenantId() == null ? 0L : host.getTenantId();

            PortScanResultEntity entity = new PortScanResultEntity();
            entity.setTenantId(tenantId);
            entity.setTaskId(taskId);
            entity.setHostName(hostName);
            entity.setMacAddress(macAddress);
            entity.setSource(SOURCE_PLATFORM);
            entity.setPortCount(openPortCount);
            entity.setPortJson(root.toString());
            portScanResultMapper.insert(entity);
            portFingerprintService.processPortScanResult(entity.getId());

            LocalDateTime scanFinishedAt = parseBestEffortDateTime(text(root, "endTime"));
            hostMapper.updateLastScanTimeByMac(macAddress, scanFinishedAt == null ? LocalDateTime.now() : scanFinishedAt);
            triggerStaticVulnMatch(macAddress, tenantId);

            log.info("端口采集结果已入库: queue={}, host={}, mac={}, openPortCount={}, scannedPortCount={}, scanRange={}, taskId={}",
                    queueName,
                    hostName,
                    macAddress,
                    openPortCount,
                    integerValue(root, "scannedPortCount"),
                    text(root, "scanRange"),
                    taskId);
            if (log.isDebugEnabled()) {
                log.debug("端口采集原始消息: {}", message);
            }
        } catch (Exception e) {
            log.error("处理端口采集消息异常: queue={}", queueName, e);
            saveError(queueName, message, "服务端异常: " + e.getMessage());
        }
    }

    private void triggerStaticVulnMatch(String macAddress, Long tenantId) {
        try {
            Long resolvedTenantId = tenantId == null ? 0L : tenantId;
            HostEntity host = hostMapper.selectByNormalizedMacAndTenant(normalizeMac(macAddress), resolvedTenantId);
            if (host == null || host.getId() == null) {
                log.warn("端口采集入库后未找到主机，跳过静态漏洞匹配: mac={}", macAddress);
                return;
            }
            if (hasOngoingVerification(host.getId(), resolvedTenantId)) {
                log.info("host {} has ongoing vuln verification, skip port-triggered rematch", host.getId());
                return;
            }
            vulnRuleEngine.evaluateHostForTenant(host.getId(), resolvedTenantId);
        } catch (Exception e) {
            log.warn("端口采集入库后静态漏洞匹配失败: mac={}", macAddress, e);
        }
    }

    private boolean hasOngoingVerification(Long hostId, Long tenantId) {
        if (hostVulnResultMapper.countActiveByHostAndVerifyStatusAndTenant(hostId, VERIFYING, tenantId) > 0) {
            return true;
        }
        return hostVulnTaskMapper.countByHostAndStatusAndTenant(hostId, TASK_STATUS_RUNNING, tenantId) > 0;
    }

    private Integer integerValue(JsonNode root, String key) {
        JsonNode value = root.path(key);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToInt()) {
            return null;
        }
        return value.asInt();
    }

    private LocalDateTime parseBestEffortDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return java.time.OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return java.time.LocalDateTime.parse(trimmed);
        } catch (Exception ignored) {
        }
        return null;
    }

    private String text(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull() || !child.isValueNode()) {
            return null;
        }
        String value = child.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }
        String normalized = mac.toLowerCase().replaceAll("[^0-9a-f]", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private void saveError(String queueName, String rawMessage, String errorReason) {
        try {
            MqErrorLogEntity errorLog = new MqErrorLogEntity();
            errorLog.setQueueName(queueName);
            errorLog.setRawMessage(rawMessage);
            errorLog.setErrorReason(errorReason);
            mqErrorLogMapper.insert(errorLog);
            log.warn("端口采集消息异常已记录: queue={}, reason={}", queueName, errorReason);
        } catch (Exception e) {
            log.error("写入 mq_error_logs 失败: queue={}, reason={}", queueName, errorReason, e);
        }
    }
}
