package com.cd.service.impl;

import com.cd.entity.AccountEntity;
import com.cd.entity.AppEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.MqErrorLogEntity;
import com.cd.entity.ProcessEntity;
import com.cd.entity.ServiceEntity;
import com.cd.mapper.AccountMapper;
import com.cd.mapper.AppMapper;
import com.cd.mapper.HostMapper;
import com.cd.mapper.MqErrorLogMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.AssetDataService;
import com.cd.service.VulnRuleEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 资产探测结果处理实现。
 *
 * <p>校验规则：JSON 合法、必要字段（type / hostName / macAddress / 资产数组 / 统计数）齐全、
 * 字段类型正确、type 与队列匹配。通过则写入对应业务表，失败则写入 {@code mq_error_logs}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetDataServiceImpl implements AssetDataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AccountMapper accountMapper;
    private final ServiceMapper serviceMapper;
    private final ProcessMapper processMapper;
    private final AppMapper appMapper;
    private final MqErrorLogMapper mqErrorLogMapper;
    private final HostMapper hostMapper;
    private final VulnRuleEngine vulnRuleEngine;

    @Override
    public void processAssetMessage(String queueName, String message) {
        try {
            String expectedType = queueToType(queueName);
            if (expectedType == null) {
                saveError(queueName, message, "未知队列: " + queueName);
                return;
            }

            JsonNode root;
            try {
                root = OBJECT_MAPPER.readTree(message);
            } catch (Exception e) {
                saveError(queueName, message, "JSON 格式非法: " + e.getMessage());
                return;
            }

            // 校验 type 字段
            JsonNode typeNode = root.path("type");
            if (typeNode.isMissingNode() || typeNode.isNull() || !typeNode.isTextual()) {
                saveError(queueName, message, "缺少必要字段 type 或其类型不正确");
                return;
            }
            String type = typeNode.asText();
            if (!expectedType.equals(type)) {
                saveError(queueName, message, "type=" + type + " 与队列 " + queueName + " 不匹配，期望 " + expectedType);
                return;
            }

            // 校验 hostName
            JsonNode hostNameNode = root.path("hostName");
            if (hostNameNode.isMissingNode() || hostNameNode.isNull() || !hostNameNode.isTextual()) {
                saveError(queueName, message, "缺少必要字段 hostName 或其类型不正确");
                return;
            }
            String hostName = hostNameNode.asText();

            // 校验 macAddress
            JsonNode macNode = root.path("macAddress");
            if (macNode.isMissingNode() || macNode.isNull() || !macNode.isTextual()) {
                saveError(queueName, message, "缺少必要字段 macAddress 或其类型不正确");
                return;
            }
            String macAddress = macNode.asText();
            HostEntity host = hostMapper.selectByNormalizedMac(normalizeMac(macAddress));
            if (host == null) {
                saveError(queueName, message, "未找到匹配主机: mac=" + macAddress);
                log.warn("资产探测消息未关联到主机: mac={}", macAddress);
                return;
            }
            Long tenantId = host.getTenantId() == null ? 0L : host.getTenantId();

            // 按类型确定资产数组字段与统计字段
            String arrayField;
            String countField;
            switch (type) {
                case "account" -> { arrayField = "accounts"; countField = "account_count"; }
                case "service" -> { arrayField = "services"; countField = "service_count"; }
                case "process" -> { arrayField = "processes"; countField = "process_count"; }
                case "app" -> { arrayField = "apps"; countField = "app_count"; }
                default -> {
                    saveError(queueName, message, "未知的 type 值: " + type);
                    return;
                }
            }

            // 校验资产数组
            JsonNode arrayNode = root.path(arrayField);
            if (arrayNode.isMissingNode() || arrayNode.isNull() || !arrayNode.isArray()) {
                saveError(queueName, message, "缺少必要字段 " + arrayField + " 或其类型不是数组");
                return;
            }
            String assetJson = arrayNode.toString();

            // 校验统计数
            JsonNode countNode = root.path(countField);
            if (countNode.isMissingNode() || countNode.isNull() || !countNode.isInt()) {
                saveError(queueName, message, "缺少必要字段 " + countField + " 或其类型不是数字");
                return;
            }
            int assetCount = countNode.asInt();

            // taskId 透传，可为 null
            String taskId = root.has("taskId") && !root.get("taskId").isNull()
                    ? root.get("taskId").asText() : null;

            // 写入对应业务表
            switch (type) {
                case "account" -> {
                    AccountEntity entity = new AccountEntity();
                    entity.setTenantId(tenantId);
                    entity.setTaskId(taskId);
                    entity.setHostName(hostName);
                    entity.setMacAddress(macAddress);
                    entity.setAssetCount(assetCount);
                    entity.setAssetJson(assetJson);
                    accountMapper.insert(entity);
                }
                case "service" -> {
                    ServiceEntity entity = new ServiceEntity();
                    entity.setTenantId(tenantId);
                    entity.setTaskId(taskId);
                    entity.setHostName(hostName);
                    entity.setMacAddress(macAddress);
                    entity.setAssetCount(assetCount);
                    entity.setAssetJson(assetJson);
                    serviceMapper.insert(entity);
                }
                case "process" -> {
                    ProcessEntity entity = new ProcessEntity();
                    entity.setTenantId(tenantId);
                    entity.setTaskId(taskId);
                    entity.setHostName(hostName);
                    entity.setMacAddress(macAddress);
                    entity.setAssetCount(assetCount);
                    entity.setAssetJson(assetJson);
                    processMapper.insert(entity);
                }
                case "app" -> {
                    AppEntity entity = new AppEntity();
                    entity.setTenantId(tenantId);
                    entity.setTaskId(taskId);
                    entity.setHostName(hostName);
                    entity.setMacAddress(macAddress);
                    entity.setAssetCount(assetCount);
                    entity.setAssetJson(assetJson);
                    appMapper.insert(entity);
                }
            }
            hostMapper.updateLastScanTimeByMac(macAddress, LocalDateTime.now());
            triggerStaticVulnMatch(macAddress, tenantId);
            log.info("资产探测结果已入库: queue={}, type={}, host={}, count={}", queueName, type, hostName, assetCount);

        } catch (Exception e) {
            log.error("处理资产探测消息异常: queue={}", queueName, e);
            saveError(queueName, message, "服务端异常: " + e.getMessage());
        }
    }

    private void triggerStaticVulnMatch(String macAddress, Long tenantId) {
        try {
            Long resolvedTenantId = tenantId == null ? 0L : tenantId;
            var host = hostMapper.selectByNormalizedMacAndTenant(normalizeMac(macAddress), resolvedTenantId);
            if (host == null || host.getId() == null) {
                log.warn("资产入库后未找到主机，跳过静态漏洞匹配: mac={}", macAddress);
                return;
            }
            vulnRuleEngine.evaluateHostForTenant(host.getId(), resolvedTenantId);
        } catch (Exception e) {
            log.warn("资产入库后静态漏洞匹配失败: mac={}", macAddress, e);
        }
    }

    /** 队列名 → type 值映射。 */
    private String queueToType(String queueName) {
        return switch (queueName) {
            case "account_queue" -> "account";
            case "service_queue" -> "service";
            case "process_queue" -> "process";
            case "app_queue" -> "app";
            default -> null;
        };
    }

    private String normalizeMac(String mac) {
        return mac == null ? "" : mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    private void saveError(String queueName, String rawMessage, String errorReason) {
        try {
            MqErrorLogEntity errorLog = new MqErrorLogEntity();
            errorLog.setQueueName(queueName);
            errorLog.setRawMessage(rawMessage);
            errorLog.setErrorReason(errorReason);
            mqErrorLogMapper.insert(errorLog);
            log.warn("MQ 消息格式异常已记录: queue={}, reason={}", queueName, errorReason);
        } catch (Exception e) {
            log.error("写入 mq_error_logs 失败 (已尝试入库并原消息已 ACK): queue={}, reason={}", queueName, errorReason, e);
        }
    }
}
