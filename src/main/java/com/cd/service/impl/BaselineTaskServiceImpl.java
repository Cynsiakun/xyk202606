package com.cd.service.impl;

import com.cd.common.config.RabbitMQConfig;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.SecurityUtils;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.BaselineHostDispatchDTO;
import com.cd.dto.BaselineTaskCreateRequestDTO;
import com.cd.dto.BaselineTaskDispatchResponseDTO;
import com.cd.entity.BaselineProtectionLevelEntity;
import com.cd.entity.BaselineRuleEntity;
import com.cd.entity.BaselineRuleItemEntity;
import com.cd.entity.BaselineTaskEntity;
import com.cd.entity.BaselineTaskHostEntity;
import com.cd.entity.HostEntity;
import com.cd.mapper.BaselineRuleMapper;
import com.cd.mapper.BaselineTaskMapper;
import com.cd.mapper.HostMapper;
import com.cd.service.BaselineTaskService;
import com.cd.util.BaselineWindowsPathNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineTaskServiceImpl implements BaselineTaskService {

    private static final String TYPE_BASELINE_SCAN = "baseline_scan";
    private static final String EXECUTE_TYPE_MANUAL = "MANUAL";
    private static final String EXECUTE_TYPE_SCHEDULED = "SCHEDULED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_PROTECTION_LEVEL_CODE = "L3";

    private final BaselineRuleMapper baselineRuleMapper;
    private final BaselineTaskMapper baselineTaskMapper;
    private final HostMapper hostMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmqpAdmin amqpAdmin;

    @Override
    @Transactional
    public BaselineTaskDispatchResponseDTO createAndDispatch(BaselineTaskCreateRequestDTO request) {
        List<Long> hostIds = distinctPositiveIds(request.getHostIds(), "主机范围不能为空");
        List<Long> ruleIds = distinctPositiveIds(request.getRuleIds(), "规则范围不能为空");
        BaselineTaskContext taskContext = resolveTaskContext(request);
        Long tenantId = resolveTenantId(hostIds);
        List<HostEntity> hosts = loadHosts(hostIds, tenantId);
        List<BaselineRuleEntity> rules = loadRules(ruleIds, taskContext.assetTypeCodes());

        BaselineTaskEntity task = createTask(request, hostIds, rules, tenantId, taskContext);
        List<BaselineHostDispatchDTO> dispatchResults = new ArrayList<>();
        int sentCount = 0;
        int failedCount = 0;

        for (HostEntity host : hosts) {
            BaselineTaskHostEntity taskHost = createTaskHost(task.getId(), host.getId(), tenantId);
            BaselineHostDispatchDTO result = dispatchHost(task, taskHost, host, rules, taskContext.protectionLevelId(), tenantId);
            dispatchResults.add(result);
            if (Boolean.TRUE.equals(result.getSent())) {
                sentCount++;
            } else {
                failedCount++;
            }
        }

        String taskStatus = sentCount > 0 ? STATUS_RUNNING : STATUS_FAILED;
        baselineTaskMapper.updateTaskStatusByTenant(task.getId(), taskStatus, sentCount, failedCount, tenantId);

        BaselineTaskDispatchResponseDTO response = new BaselineTaskDispatchResponseDTO();
        response.setTaskId(task.getId());
        response.setStatus(taskStatus);
        response.setTotalHostCount(hosts.size());
        response.setSentCount(sentCount);
        response.setFailedCount(failedCount);
        response.setHosts(dispatchResults);
        response.setMessage(sentCount > 0 ? "基线任务已创建并下发" : "基线任务已创建，但所有主机下发失败");
        return response;
    }

    @Override
    @Transactional
    public BaselineTaskDispatchResponseDTO createAndDispatchForTenant(BaselineTaskCreateRequestDTO request, Long tenantId) {
        List<Long> hostIds = distinctPositiveIds(request.getHostIds(), "host scope cannot be empty");
        List<Long> ruleIds = distinctPositiveIds(request.getRuleIds(), "rule scope cannot be empty");
        BaselineTaskContext taskContext = resolveTaskContext(request);
        Long resolvedTenantId = tenantId == null ? 0L : tenantId;
        List<HostEntity> hosts = loadHosts(hostIds, resolvedTenantId);
        List<BaselineRuleEntity> rules = loadRules(ruleIds, taskContext.assetTypeCodes());

        BaselineTaskEntity task = createTask(request, hostIds, rules, resolvedTenantId, taskContext);
        List<BaselineHostDispatchDTO> dispatchResults = new ArrayList<>();
        int sentCount = 0;
        int failedCount = 0;

        for (HostEntity host : hosts) {
            BaselineTaskHostEntity taskHost = createTaskHost(task.getId(), host.getId(), resolvedTenantId);
            BaselineHostDispatchDTO result = dispatchHost(task, taskHost, host, rules, taskContext.protectionLevelId(), resolvedTenantId);
            dispatchResults.add(result);
            if (Boolean.TRUE.equals(result.getSent())) {
                sentCount++;
            } else {
                failedCount++;
            }
        }

        String taskStatus = sentCount > 0 ? STATUS_RUNNING : STATUS_FAILED;
        baselineTaskMapper.updateTaskStatusByTenant(task.getId(), taskStatus, sentCount, failedCount, resolvedTenantId);

        BaselineTaskDispatchResponseDTO response = new BaselineTaskDispatchResponseDTO();
        response.setTaskId(task.getId());
        response.setStatus(taskStatus);
        response.setTotalHostCount(hosts.size());
        response.setSentCount(sentCount);
        response.setFailedCount(failedCount);
        response.setHosts(dispatchResults);
        response.setMessage(sentCount > 0 ? "baseline task created and dispatched" : "baseline task created but dispatch failed");
        return response;
    }

    private BaselineTaskEntity createTask(BaselineTaskCreateRequestDTO request,
                                          List<Long> hostIds,
                                          List<BaselineRuleEntity> rules,
                                          Long tenantId,
                                          BaselineTaskContext taskContext) {
        BaselineTaskEntity task = new BaselineTaskEntity();
        task.setTenantId(tenantId);
        task.setTaskName(request.getTaskName().trim());
        task.setExecuteType(normalizeExecuteType(request.getExecuteType()));
        task.setCronExpr(emptyToNull(request.getCronExpr()));
        task.setProtectionLevelId(taskContext.protectionLevelId());
        task.setTargetLevel(taskContext.targetLevel());
        task.setAssetTypeFilter(taskContext.assetTypeFilter());
        task.setRuleScope(writeJson(buildRuleScope(rules, taskContext)));
        task.setRuleSnapshotJson(writeJson(rules.stream()
                .map(rule -> buildRuleSnapshot(rule, taskContext))
                .toList()));
        task.setStatus(STATUS_PENDING);
        task.setTotalHostCount(hostIds.size());
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setCreator(currentUsername());
        task.setStartTime(LocalDateTime.now());
        baselineTaskMapper.insertTask(task);
        return task;
    }

    private BaselineTaskHostEntity createTaskHost(Long taskId, Long hostId, Long tenantId) {
        BaselineTaskHostEntity taskHost = new BaselineTaskHostEntity();
        taskHost.setTenantId(tenantId);
        taskHost.setTaskId(taskId);
        taskHost.setHostId(hostId);
        taskHost.setStatus(STATUS_PENDING);
        baselineTaskMapper.insertTaskHost(taskHost);
        return taskHost;
    }

    private BaselineHostDispatchDTO dispatchHost(BaselineTaskEntity task,
                                                 BaselineTaskHostEntity taskHost,
                                                 HostEntity host,
                                                 List<BaselineRuleEntity> candidateRules,
                                                 Long protectionLevelId,
                                                 Long tenantId) {
        BaselineHostDispatchDTO result = new BaselineHostDispatchDTO();
        result.setHostId(host.getId());
        result.setTaskHostId(taskHost.getId());
        result.setMacAddress(host.getMacAddress());

        if (!StringUtils.hasText(host.getMacAddress())) {
            return markDispatchFailed(taskHost.getId(), result, "主机缺少 MAC 地址，无法下发", tenantId);
        }

        List<BaselineRuleEntity> hostRules = filterRulesForHost(candidateRules, host);
        if (hostRules.isEmpty()) {
            return markDispatchFailed(taskHost.getId(), result,
                    "主机未匹配到适用的基线规则，os=" + host.getOsName(), tenantId);
        }
        Map<Long, List<BaselineRuleItemEntity>> itemsByRuleId = loadRuleItems(
                hostRules.stream().map(BaselineRuleEntity::getId).toList(), protectionLevelId);
        Map<String, Object> payload = buildAgentMessage(task, taskHost, host, hostRules, itemsByRuleId);
        try {
            String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX
                    + normalizeMac(host.getMacAddress())
                    + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
            if (amqpAdmin.getQueueProperties(queueName) == null) {
                return markDispatchFailed(taskHost.getId(), result, "客户端队列不存在，无法下发: " + queueName, tenantId);
            }

            amqpAdmin.declareExchange(new DirectExchange(RabbitMQConfig.AGENT_EXCHANGE, true, false));
            String message = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AGENT_EXCHANGE,
                    host.getMacAddress(),
                    message,
                    mqMessage -> {
                        mqMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        mqMessage.getMessageProperties().setContentType("application/json");
                        return mqMessage;
                    });
            baselineTaskMapper.updateTaskHostStatusByTenant(taskHost.getId(), STATUS_RUNNING,
                    writeJson(Map.of("dispatchAt", nowText())), tenantId);
            result.setSent(true);
            result.setMessage("下发成功");
            log.info("基线检测任务已下发: taskId={}, taskHostId={}, hostId={}, routingKey={}, payload={}",
                    task.getId(), taskHost.getId(), host.getId(), host.getMacAddress(), message);
            return result;
        } catch (Exception e) {
            log.error("基线检测任务下发失败: taskId={}, taskHostId={}, hostId={}",
                    task.getId(), taskHost.getId(), host.getId(), e);
            return markDispatchFailed(taskHost.getId(), result, "下发失败: " + e.getMessage(), tenantId);
        }
    }

    private Map<String, Object> buildAgentMessage(BaselineTaskEntity task,
                                                  BaselineTaskHostEntity taskHost,
                                                  HostEntity host,
                                                  List<BaselineRuleEntity> rules,
                                                  Map<Long, List<BaselineRuleItemEntity>> itemsByRuleId) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", TYPE_BASELINE_SCAN);
        message.put("taskId", task.getId());
        message.put("taskHostId", taskHost.getId());
        message.put("hostId", host.getId());
        message.put("macAddress", host.getMacAddress());
        message.put("createdAt", nowText());
        message.put("checks", rules.stream().map(rule -> {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("ruleId", rule.getId());
            check.put("ruleCode", rule.getRuleCode());
            check.put("ruleVersion", defaultVersion(rule));
            check.put("checkMethod", rule.getCheckMethod());
            check.put("checkScript", BaselineWindowsPathNormalizer.normalizeScript(rule.getCheckScript()));
            check.put("items", itemsByRuleId.getOrDefault(rule.getId(), List.of()).stream()
                    .map(item -> buildCheckItem(rule, item))
                    .toList());
            return check;
        }).toList());
        return message;
    }

    private List<BaselineRuleEntity> filterRulesForHost(List<BaselineRuleEntity> candidateRules, HostEntity host) {
        String hostAssetTypeCode = resolveHostAssetTypeCode(host);
        if (!StringUtils.hasText(hostAssetTypeCode)) {
            return List.of();
        }
        return candidateRules.stream()
                .filter(rule -> ruleMatchesAssetType(rule, hostAssetTypeCode))
                .toList();
    }

    private boolean ruleMatchesAssetType(BaselineRuleEntity rule, String hostAssetTypeCode) {
        if (!StringUtils.hasText(hostAssetTypeCode)) {
            return false;
        }
        if (!isOsScopedRule(rule)) {
            return true;
        }
        if (StringUtils.hasText(rule.getAssetTypeCode())) {
            Set<String> assetTypeCodes = splitUpperCsv(rule.getAssetTypeCode());
            if (!assetTypeCodes.isEmpty()) {
                return assetTypeCodes.contains(hostAssetTypeCode);
            }
        }
        String legacyAssetType = normalizeLegacyAssetType(rule);
        return hostAssetTypeCode.equals(legacyAssetType);
    }

    private boolean isOsScopedRule(BaselineRuleEntity rule) {
        if (rule == null) {
            return false;
        }
        Set<String> assetTypeCodes = splitUpperCsv(rule.getAssetTypeCode());
        if (!assetTypeCodes.isEmpty()) {
            return assetTypeCodes.stream().allMatch(code -> code.startsWith("OS_"));
        }
        String legacyAssetType = normalizeLegacyAssetType(rule);
        return "OS_WINDOWS".equals(legacyAssetType) || "OS_LINUX".equals(legacyAssetType);
    }

    private String resolveHostAssetTypeCode(HostEntity host) {
        if (host == null || !StringUtils.hasText(host.getOsName())) {
            return null;
        }
        String osName = host.getOsName().trim().toLowerCase();
        if (osName.contains("windows")) {
            return "OS_WINDOWS";
        }
        if (osName.contains("linux")
                || osName.contains("centos")
                || osName.contains("ubuntu")
                || osName.contains("debian")
                || osName.contains("red hat")
                || osName.contains("rhel")
                || osName.contains("rocky")
                || osName.contains("alma")
                || osName.contains("suse")
                || osName.contains("kylin")
                || osName.contains("uos")) {
            return "OS_LINUX";
        }
        return null;
    }

    private String normalizeLegacyAssetType(BaselineRuleEntity rule) {
        if (rule == null) {
            return null;
        }
        String osType = normalizeUpper(rule.getOsType());
        if ("WINDOWS".equals(osType)) {
            return "OS_WINDOWS";
        }
        if ("LINUX".equals(osType)) {
            return "OS_LINUX";
        }
        return normalizeUpper(rule.getAssetType());
    }

    private Map<String, Object> buildCheckItem(BaselineRuleEntity rule, BaselineRuleItemEntity item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("itemId", item.getId());
        value.put("checkKey", BaselineWindowsPathNormalizer.normalizeCheckKey(item.getCheckKey()));
        value.put("operator", item.getOperator());
        value.put("expectedValue", item.getExpectedValue());
        value.put("matchType", StringUtils.hasText(item.getMatchType()) ? item.getMatchType() : "EXACT");
        value.put("valueType", inferValueType(rule, item));
        return value;
    }

    private BaselineHostDispatchDTO markDispatchFailed(Long taskHostId,
                                                       BaselineHostDispatchDTO result,
                                                       String message,
                                                       Long tenantId) {
        baselineTaskMapper.updateTaskHostStatusByTenant(taskHostId, STATUS_FAILED,
                writeJson(Map.of("dispatchAt", nowText(), "error", message)), tenantId);
        result.setSent(false);
        result.setMessage(message);
        return result;
    }

    private List<HostEntity> loadHosts(List<Long> hostIds, Long tenantId) {
        List<HostEntity> hosts = new ArrayList<>();
        for (Long hostId : hostIds) {
            HostEntity host = hostMapper.selectByIdAndTenant(hostId, tenantId);
            if (host == null) {
                throw new ResourceNotFoundException("主机不存在: " + hostId);
            }
            hosts.add(host);
        }
        return hosts;
    }

    private List<BaselineRuleEntity> loadRules(List<Long> ruleIds, List<String> assetTypeCodes) {
        List<BaselineRuleEntity> rules = assetTypeCodes.isEmpty()
                ? baselineRuleMapper.selectPublishedByIds(ruleIds)
                : baselineRuleMapper.selectPublishedByIdsAndAssetTypes(ruleIds, assetTypeCodes);
        Set<Long> foundIds = rules.stream().map(BaselineRuleEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> missingIds = ruleIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missingIds.isEmpty()) {
            String reason = assetTypeCodes.isEmpty() ? "" : "，或不适用于资产类型 " + assetTypeCodes;
            throw new ResourceNotFoundException("规则不存在、未启用、未发布" + reason + ": " + missingIds);
        }
        return rules;
    }

    private Map<Long, List<BaselineRuleItemEntity>> loadRuleItems(List<Long> ruleIds, Long protectionLevelId) {
        List<BaselineRuleItemEntity> items = baselineRuleMapper.selectItemsByRuleIdsAndProtectionLevel(ruleIds, protectionLevelId);
        Map<Long, List<BaselineRuleItemEntity>> itemsByRuleId = items.stream()
                .collect(Collectors.groupingBy(BaselineRuleItemEntity::getRuleId, LinkedHashMap::new, Collectors.toList()));
        List<Long> emptyRuleIds = ruleIds.stream()
                .filter(ruleId -> itemsByRuleId.getOrDefault(ruleId, List.of()).isEmpty())
                .toList();
        if (!emptyRuleIds.isEmpty()) {
            throw new IllegalArgumentException("规则缺少检查项，无法下发: " + emptyRuleIds);
        }
        return itemsByRuleId;
    }

    private BaselineTaskContext resolveTaskContext(BaselineTaskCreateRequestDTO request) {
        BaselineProtectionLevelEntity level = resolveProtectionLevel(request);
        List<String> assetTypeCodes = normalizeAssetTypeCodes(request.getAssetTypeCodes());
        return new BaselineTaskContext(
                level.getId(),
                level.getLevelCode(),
                level.getLevelName(),
                levelOrderToTargetLevel(level),
                assetTypeCodes,
                assetTypeCodes.isEmpty() ? null : String.join(",", assetTypeCodes)
        );
    }

    private BaselineProtectionLevelEntity resolveProtectionLevel(BaselineTaskCreateRequestDTO request) {
        BaselineProtectionLevelEntity level = null;
        if (request.getProtectionLevelId() != null && request.getProtectionLevelId() > 0) {
            level = baselineRuleMapper.selectProtectionLevelById(request.getProtectionLevelId());
            if (level == null) {
                throw new ResourceNotFoundException("等保等级不存在或未启用: id=" + request.getProtectionLevelId());
            }
            return level;
        }
        String levelCode = StringUtils.hasText(request.getProtectionLevelCode())
                ? request.getProtectionLevelCode().trim()
                : DEFAULT_PROTECTION_LEVEL_CODE;
        level = baselineRuleMapper.selectProtectionLevelByCode(levelCode);
        if (level == null) {
            throw new ResourceNotFoundException("等保等级不存在或未启用: code=" + levelCode);
        }
        return level;
    }

    private List<String> normalizeAssetTypeCodes(List<String> assetTypeCodes) {
        if (assetTypeCodes == null) {
            return List.of();
        }
        return assetTypeCodes.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase())
                .distinct()
                .toList();
    }

    private Set<String> splitUpperCsv(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptySet();
        }
        return List.of(text.split(",")).stream()
                .map(this::normalizeUpper)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Integer levelOrderToTargetLevel(BaselineProtectionLevelEntity level) {
        if (level == null || !StringUtils.hasText(level.getLevelCode())) {
            return 3;
        }
        String code = level.getLevelCode().trim().toUpperCase();
        if (code.matches("L[1-5]")) {
            return Integer.parseInt(code.substring(1));
        }
        Integer order = level.getLevelOrder();
        if (order == null || order < 1) {
            return 3;
        }
        return Math.min(order, 5);
    }

    private Map<String, Object> buildRuleScope(List<BaselineRuleEntity> rules, BaselineTaskContext taskContext) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("ruleIds", rules.stream().map(BaselineRuleEntity::getId).toList());
        scope.put("protectionLevelId", taskContext.protectionLevelId());
        scope.put("protectionLevelCode", taskContext.protectionLevelCode());
        scope.put("assetTypes", taskContext.assetTypeCodes());
        return scope;
    }

    private Map<String, Object> buildRuleSnapshot(BaselineRuleEntity rule, BaselineTaskContext taskContext) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ruleId", rule.getId());
        snapshot.put("version", defaultVersion(rule));
        snapshot.put("ruleCode", rule.getRuleCode());
        snapshot.put("assetType", rule.getAssetType());
        snapshot.put("protectionLevelId", taskContext.protectionLevelId());
        snapshot.put("protectionLevelCode", taskContext.protectionLevelCode());
        return snapshot;
    }

    private List<Long> distinctPositiveIds(List<Long> ids, String emptyMessage) {
        if (ids == null) {
            throw new IllegalArgumentException(emptyMessage);
        }
        List<Long> result = ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return result;
    }

    private String normalizeExecuteType(String executeType) {
        if (!StringUtils.hasText(executeType)) {
            return EXECUTE_TYPE_MANUAL;
        }
        String normalized = executeType.trim().toUpperCase();
        if (!EXECUTE_TYPE_MANUAL.equals(normalized) && !EXECUTE_TYPE_SCHEDULED.equals(normalized)) {
            throw new IllegalArgumentException("executeType 仅支持 MANUAL/SCHEDULED");
        }
        return normalized;
    }

    private String inferValueType(BaselineRuleEntity rule, BaselineRuleItemEntity item) {
        String expectedValue = item.getExpectedValue();
        if ("true".equalsIgnoreCase(expectedValue) || "false".equalsIgnoreCase(expectedValue)) {
            return "BOOLEAN";
        }
        if (isNumericLiteral(expectedValue)) {
            return "NUMBER";
        }
        String operator = item.getOperator();
        if (List.of(">", ">=", "<", "<=").contains(operator)) {
            return "NUMBER";
        }
        if ("WMI".equalsIgnoreCase(rule.getCheckMethod())) {
            return "STRING";
        }
        return "STRING";
    }

    private boolean isNumericLiteral(String value) {
        return StringUtils.hasText(value) && value.trim().matches("-?\\d+(\\.\\d+)?");
    }

    private Integer defaultVersion(BaselineRuleEntity rule) {
        return rule.getVersion() == null ? 1 : rule.getVersion();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(username) ? username : "system";
    }

    private String nowText() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private Long resolveTenantId(List<Long> hostIds) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        if (hostIds != null && !hostIds.isEmpty()) {
            HostEntity host = hostMapper.selectById(hostIds.get(0));
            if (host != null && host.getTenantId() != null) {
                return host.getTenantId();
            }
        }
        return 0L;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private record BaselineTaskContext(Long protectionLevelId,
                                       String protectionLevelCode,
                                       String protectionLevelName,
                                       Integer targetLevel,
                                       List<String> assetTypeCodes,
                                       String assetTypeFilter) {
    }
}
