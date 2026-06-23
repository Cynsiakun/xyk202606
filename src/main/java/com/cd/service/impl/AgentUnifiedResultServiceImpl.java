package com.cd.service.impl;

import com.cd.entity.AccountEntity;
import com.cd.entity.AgentResultOperationEntity;
import com.cd.entity.AppEntity;
import com.cd.entity.BaselineCheckDataEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.MqErrorLogEntity;
import com.cd.entity.PortScanResultEntity;
import com.cd.entity.ProcessEntity;
import com.cd.entity.ServiceEntity;
import com.cd.mapper.AccountMapper;
import com.cd.mapper.AgentResultOperationMapper;
import com.cd.mapper.AppMapper;
import com.cd.mapper.BaselineCheckDataMapper;
import com.cd.mapper.BaselineTaskMapper;
import com.cd.mapper.HostMapper;
import com.cd.mapper.MqErrorLogMapper;
import com.cd.mapper.PortScanResultMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.AgentUnifiedResultService;
import com.cd.service.BaselineRuleEngine;
import com.cd.service.PatchScanService;
import com.cd.service.PortFingerprintService;
import com.cd.service.VulnRuleEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentUnifiedResultServiceImpl implements AgentUnifiedResultService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_CLIENT = "CLIENT";

    private final AgentResultOperationMapper agentResultOperationMapper;
    private final MqErrorLogMapper mqErrorLogMapper;
    private final HostMapper hostMapper;
    private final AccountMapper accountMapper;
    private final ServiceMapper serviceMapper;
    private final ProcessMapper processMapper;
    private final AppMapper appMapper;
    private final PortScanResultMapper portScanResultMapper;
    private final BaselineCheckDataMapper baselineCheckDataMapper;
    private final BaselineTaskMapper baselineTaskMapper;
    private final PatchScanService patchScanService;
    private final PortFingerprintService portFingerprintService;
    private final VulnRuleEngine vulnRuleEngine;
    private final BaselineRuleEngine baselineRuleEngine;

    @Override
    @Transactional
    public void processAgentResult(String queueName, String message) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(message);
        } catch (Exception e) {
            saveError(queueName, message, "Invalid JSON: " + e.getMessage(), 0L);
            return;
        }

        String operationId = text(root, "operationId");
        String type = text(root, "type");
        String hostName = text(root, "hostName");
        String macAddress = text(root, "macAddress");
        String status = text(root, "status");
        JsonNode requestNode = root.path("request");
        JsonNode resultNode = root.path("result");

        if (!StringUtils.hasText(operationId)) {
            saveError(queueName, message, "Missing operationId", 0L);
            return;
        }
        if (!StringUtils.hasText(type)) {
            saveError(queueName, message, "Missing type", 0L);
            return;
        }
        if (!StringUtils.hasText(macAddress)) {
            saveError(queueName, message, "Missing macAddress", 0L);
            return;
        }
        if (resultNode.isMissingNode() || resultNode.isNull()) {
            saveError(queueName, message, "Missing result", 0L);
            return;
        }

        HostEntity host = hostMapper.selectByNormalizedMac(normalizeMac(macAddress));
        if (host == null && !"host_info".equals(type)) {
            saveError(queueName, message, "Host not found for mac=" + macAddress, 0L);
            return;
        }

        Long tenantId = host == null || host.getTenantId() == null ? 0L : host.getTenantId();
        AgentResultOperationEntity operation = buildOperation(root, operationId, type, hostName, macAddress, status);
        operation.setTenantId(tenantId);
        if (agentResultOperationMapper.insertIgnore(operation) == 0) {
            log.info("Duplicate agent result ignored: operationId={}, type={}", operationId, type);
            return;
        }

        if ("ERROR".equalsIgnoreCase(status)) {
            log.warn("Agent result recorded with ERROR status: operationId={}, type={}", operationId, type);
            return;
        }

        try {
            switch (type) {
                case "host_info" -> handleHostInfo(macAddress, hostName, resultNode);
                case "patch_scan" -> patchScanService.processPatchScanMessage(
                        queueName,
                        buildPatchPayload(root, resultNode, macAddress)
                );
                case "asset_scan" -> handleAssetScan(operationId, tenantId, hostName, macAddress, resultNode);
                case "port_scan" -> handlePortScan(operationId, tenantId, hostName, macAddress, requestNode, resultNode);
                case "baseline_scan" -> handleBaselineScan(queueName, tenantId, host, root, resultNode);
                default -> saveError(queueName, message, "Unsupported type: " + type, tenantId);
            }
        } catch (Exception e) {
            log.error("Agent unified result dispatch failed: operationId={}, type={}", operationId, type, e);
            saveError(queueName, message, "Dispatch failed: " + e.getMessage(), tenantId);
        }
    }

    private AgentResultOperationEntity buildOperation(JsonNode root,
                                                      String operationId,
                                                      String type,
                                                      String hostName,
                                                      String macAddress,
                                                      String status) {
        AgentResultOperationEntity entity = new AgentResultOperationEntity();
        entity.setOperationId(operationId);
        entity.setType(type);
        entity.setHostName(hostName);
        entity.setMacAddress(macAddress);
        entity.setStatus(status);
        entity.setRequestJson(toJson(root.path("request")));
        entity.setResultJson(toJson(root.path("result")));
        entity.setRawMessage(toJson(root));
        entity.setCreatedAt(parseDateTime(text(root, "createdAt")));
        entity.setFinishedAt(parseDateTime(text(root, "finishedAt")));
        entity.setReceivedAt(LocalDateTime.now());
        return entity;
    }

    private void handleHostInfo(String macAddress, String hostName, JsonNode resultNode) {
        HostEntity host = new HostEntity();
        host.setMacAddress(firstNonBlank(
                macAddress,
                nestedText(resultNode, "MAC地址", "MAC地址"),
                text(resultNode, "macAddress"),
                text(resultNode, "mac")
        ));
        host.setHostname(firstNonBlank(
                hostName,
                nestedText(resultNode, "主机名", "主机名"),
                text(resultNode, "hostname"),
                text(resultNode, "hostName"),
                nestedText(resultNode, "system", "hostname"),
                nestedText(resultNode, "computer", "name")
        ));
        host.setIpv4(firstNonBlank(
                nestedText(resultNode, "本机IPv4地址", "本机IPv4"),
                text(resultNode, "ipv4"),
                text(resultNode, "ip"),
                nestedText(resultNode, "network", "ipv4")
        ));
        host.setOsName(firstNonBlank(
                nestedText(resultNode, "操作系统信息", "系统名称"),
                text(resultNode, "osName"),
                nestedText(resultNode, "os", "name"),
                nestedText(resultNode, "system", "osName")
        ));
        host.setOsVersion(firstNonBlank(
                nestedText(resultNode, "操作系统信息", "系统版本"),
                text(resultNode, "osVersion"),
                nestedText(resultNode, "os", "version"),
                nestedText(resultNode, "system", "osVersion")
        ));
        host.setOsArch(firstNonBlank(
                nestedText(resultNode, "操作系统信息", "系统架构"),
                text(resultNode, "osArch"),
                nestedText(resultNode, "os", "arch"),
                nestedText(resultNode, "system", "osArch")
        ));
        host.setOsRelease(firstNonBlank(
                nestedText(resultNode, "操作系统信息", "具体版本"),
                text(resultNode, "osRelease"),
                nestedText(resultNode, "os", "release"),
                nestedText(resultNode, "system", "osRelease")
        ));
        host.setCpuModel(firstNonBlank(
                nestedText(resultNode, "CPU信息", "CPU型号"),
                text(resultNode, "cpuModel"),
                nestedText(resultNode, "cpu", "model")
        ));
        host.setCpuPhysicalCores(firstInteger(
                nestedInteger(resultNode, "CPU信息", "物理核心数"),
                integer(resultNode, "cpuPhysicalCores"),
                integer(resultNode.path("cpu"), "physicalCores")
        ));
        host.setCpuLogicalCores(firstInteger(
                nestedInteger(resultNode, "CPU信息", "逻辑核心数"),
                integer(resultNode, "cpuLogicalCores"),
                integer(resultNode.path("cpu"), "logicalCores")
        ));
        host.setMemTotal(firstNonBlank(
                nestedText(resultNode, "内存信息", "总内存"),
                text(resultNode, "memTotal"),
                nestedText(resultNode, "memory", "total")
        ));
        host.setMemUsed(firstNonBlank(
                nestedText(resultNode, "内存信息", "已使用内存"),
                text(resultNode, "memUsed"),
                nestedText(resultNode, "memory", "used")
        ));
        host.setMemAvailable(firstNonBlank(
                nestedText(resultNode, "内存信息", "可用内存"),
                text(resultNode, "memAvailable"),
                nestedText(resultNode, "memory", "available")
        ));
        host.setMemUsage(firstNonBlank(
                nestedText(resultNode, "内存信息", "使用率"),
                text(resultNode, "memUsage"),
                nestedText(resultNode, "memory", "usage")
        ));
        host.setStatus(1);
        host.setLastScanTime(LocalDateTime.now());
        hostMapper.upsertByMac(host);
    }

    private void handleAssetScan(String operationId,
                                 Long tenantId,
                                 String hostName,
                                 String macAddress,
                                 JsonNode resultNode) {
        insertAssetRecord(operationId, tenantId, hostName, macAddress, resultNode, "accounts");
        insertAssetRecord(operationId, tenantId, hostName, macAddress, resultNode, "services");
        insertAssetRecord(operationId, tenantId, hostName, macAddress, resultNode, "processes");
        insertAssetRecord(operationId, tenantId, hostName, macAddress, resultNode, "apps");
        hostMapper.updateLastScanTimeByMac(macAddress, LocalDateTime.now());
        triggerStaticVulnMatch(macAddress, tenantId);
    }

    private void insertAssetRecord(String operationId,
                                   Long tenantId,
                                   String hostName,
                                   String macAddress,
                                   JsonNode resultNode,
                                   String fieldName) {
        JsonNode arrayNode = resultNode.path(fieldName);
        if (!arrayNode.isArray()) {
            return;
        }
        int count = arrayNode.size();
        String json = toJson(arrayNode);

        switch (fieldName) {
            case "accounts" -> {
                AccountEntity entity = new AccountEntity();
                entity.setTenantId(tenantId);
                entity.setTaskId(operationId);
                entity.setHostName(hostName);
                entity.setMacAddress(macAddress);
                entity.setSource(SOURCE_CLIENT);
                entity.setAssetCount(count);
                entity.setAssetJson(json);
                accountMapper.insert(entity);
            }
            case "services" -> {
                ServiceEntity entity = new ServiceEntity();
                entity.setTenantId(tenantId);
                entity.setTaskId(operationId);
                entity.setHostName(hostName);
                entity.setMacAddress(macAddress);
                entity.setSource(SOURCE_CLIENT);
                entity.setAssetCount(count);
                entity.setAssetJson(json);
                serviceMapper.insert(entity);
            }
            case "processes" -> {
                ProcessEntity entity = new ProcessEntity();
                entity.setTenantId(tenantId);
                entity.setTaskId(operationId);
                entity.setHostName(hostName);
                entity.setMacAddress(macAddress);
                entity.setSource(SOURCE_CLIENT);
                entity.setAssetCount(count);
                entity.setAssetJson(json);
                processMapper.insert(entity);
            }
            case "apps" -> {
                AppEntity entity = new AppEntity();
                entity.setTenantId(tenantId);
                entity.setTaskId(operationId);
                entity.setHostName(hostName);
                entity.setMacAddress(macAddress);
                entity.setSource(SOURCE_CLIENT);
                entity.setAssetCount(count);
                entity.setAssetJson(json);
                appMapper.insert(entity);
            }
            default -> {
            }
        }
    }

    private void handlePortScan(String operationId,
                                Long tenantId,
                                String hostName,
                                String macAddress,
                                JsonNode requestNode,
                                JsonNode resultNode) {
        JsonNode openPortsNode = resultNode.path("openPorts");
        if (!openPortsNode.isArray()) {
            throw new IllegalArgumentException("port_scan result.openPorts must be an array");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "port_scan");
        payload.put("taskId", operationId);
        payload.put("hostName", hostName);
        payload.put("macAddress", macAddress);
        payload.put("openPorts", OBJECT_MAPPER.convertValue(openPortsNode, Object.class));
        payload.put("openPortCount", openPortsNode.size());
        copyIfPresent(requestNode, payload, "scanRange");
        copyIfPresent(requestNode, payload, "customPorts");
        copyIfPresent(requestNode, payload, "grabBanner");
        copyIfPresent(resultNode, payload, "scannedPortCount");
        copyIfPresent(resultNode, payload, "endTime");

        PortScanResultEntity entity = new PortScanResultEntity();
        entity.setTenantId(tenantId);
        entity.setTaskId(operationId);
        entity.setHostName(hostName);
        entity.setMacAddress(macAddress);
        entity.setSource(SOURCE_CLIENT);
        entity.setPortCount(openPortsNode.size());
        entity.setPortJson(toJson(payload));
        portScanResultMapper.insert(entity);

        portFingerprintService.processPortScanResult(entity.getId());
        hostMapper.updateLastScanTimeByMac(macAddress, parseDateTime(text(resultNode, "endTime")));
        triggerStaticVulnMatch(macAddress, tenantId);
    }

    private void handleBaselineScan(String queueName,
                                    Long tenantId,
                                    HostEntity host,
                                    JsonNode root,
                                    JsonNode resultNode) {
        Long taskId = firstLong(
                longValue(resultNode.path("taskId")),
                longValue(root.path("taskId")),
                longValue(root.path("request").path("taskId"))
        );
        if (taskId == null) {
            saveError(queueName, toJson(root), "baseline_scan missing taskId", tenantId);
            return;
        }
        if (host == null || host.getId() == null) {
            saveError(queueName, toJson(root), "baseline_scan host not found", tenantId);
            return;
        }
        if (baselineTaskMapper.selectTaskHostByTaskAndHost(taskId, host.getId()) == null) {
            saveError(queueName, toJson(root), "baseline_scan task_host not found", tenantId);
            return;
        }

        JsonNode resultsNode = resultNode.path("results");
        if (!resultsNode.isArray()) {
            resultsNode = OBJECT_MAPPER.createArrayNode();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "baseline_scan_result");
        payload.put("taskId", taskId);
        payload.put("hostId", host.getId());
        copyIfPresent(resultNode, payload, "taskHostId");
        payload.put("scanTime", firstNonBlank(
                text(root, "finishedAt"),
                text(resultNode, "scanTime"),
                text(resultNode, "finishedAt")
        ));
        payload.put("results", OBJECT_MAPPER.convertValue(resultsNode, Object.class));

        BaselineCheckDataEntity entity = new BaselineCheckDataEntity();
        entity.setTenantId(tenantId);
        entity.setTaskId(taskId);
        entity.setHostId(host.getId());
        entity.setCheckData(toJson(payload));
        entity.setCreateTime(LocalDateTime.now());
        baselineCheckDataMapper.insert(entity);
        baselineRuleEngine.evaluate(entity);
    }

    private String buildPatchPayload(JsonNode root, JsonNode resultNode, String macAddress) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "patch_scan");

        Map<String, Object> hostPatch = new LinkedHashMap<>();
        JsonNode hostPatchNode = resultNode.path("hostPatch");
        if (hostPatchNode.isObject()) {
            hostPatch.putAll(OBJECT_MAPPER.convertValue(hostPatchNode, Map.class));
        }
        hostPatch.putIfAbsent("macAddress", macAddress);
        payload.put("hostPatch", hostPatch);

        JsonNode installedPatchesNode = resultNode.path("installedPatches");
        payload.put(
                "installedPatches",
                installedPatchesNode.isArray()
                        ? OBJECT_MAPPER.convertValue(installedPatchesNode, Object.class)
                        : OBJECT_MAPPER.convertValue(OBJECT_MAPPER.createArrayNode(), Object.class)
        );

        if (root.has("finishedAt")) {
            payload.put("scanTime", root.get("finishedAt").asText());
        }
        return toJson(payload);
    }

    private void triggerStaticVulnMatch(String macAddress, Long tenantId) {
        try {
            Long resolvedTenantId = tenantId == null ? 0L : tenantId;
            HostEntity host = hostMapper.selectByNormalizedMacAndTenant(normalizeMac(macAddress), resolvedTenantId);
            if (host == null || host.getId() == null) {
                return;
            }
            vulnRuleEngine.evaluateHostForTenant(host.getId(), resolvedTenantId);
        } catch (Exception e) {
            log.warn("Post-ingest vuln rematch failed for mac={}", macAddress, e);
        }
    }

    private void saveError(String queueName, String rawMessage, String errorReason, Long tenantId) {
        try {
            MqErrorLogEntity errorLog = new MqErrorLogEntity();
            errorLog.setTenantId(tenantId == null ? 0L : tenantId);
            errorLog.setQueueName(queueName);
            errorLog.setRawMessage(rawMessage);
            errorLog.setErrorReason(errorReason);
            mqErrorLogMapper.insert(errorLog);
            log.warn("Agent result error recorded: queue={}, reason={}", queueName, errorReason);
        } catch (Exception e) {
            log.error("Failed to write mq_error_logs: queue={}, reason={}", queueName, errorReason, e);
        }
    }

    private void copyIfPresent(JsonNode source, Map<String, Object> target, String fieldName) {
        JsonNode node = source.path(fieldName);
        if (!node.isMissingNode() && !node.isNull()) {
            target.put(fieldName, OBJECT_MAPPER.convertValue(node, Object.class));
        }
    }

    private String nestedText(JsonNode node, String objectField, String valueField) {
        JsonNode nested = node.path(objectField);
        return nested.isObject() ? text(nested, valueField) : null;
    }

    private Integer nestedInteger(JsonNode node, String objectField, String valueField) {
        JsonNode nested = node.path(objectField);
        return nested.isObject() ? integer(nested, valueField) : null;
    }

    private String text(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String value = child.isValueNode() ? child.asText() : child.toString();
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private Long longValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value.trim()).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

    private String normalizeMac(String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            return null;
        }
        return macAddress.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-f]", "");
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    private String firstNonBlank(String... values) {
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

    private Long firstLong(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
