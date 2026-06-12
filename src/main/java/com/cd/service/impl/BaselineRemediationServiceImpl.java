package com.cd.service.impl;

import com.cd.common.config.RabbitMQConfig;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.entity.BaselineRemediationEntity;
import com.cd.entity.BaselineResultEntity;
import com.cd.entity.BaselineRuleEntity;
import com.cd.entity.HostEntity;
import com.cd.mapper.BaselineRemediationMapper;
import com.cd.mapper.BaselineResultMapper;
import com.cd.mapper.BaselineRuleMapper;
import com.cd.mapper.HostMapper;
import com.cd.service.BaselineRemediationService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineRemediationServiceImpl implements BaselineRemediationService {

    private static final String TYPE_REMEDIATION = "baseline_remediation";
    private static final String TYPE_ROLLBACK = "baseline_rollback";
    private static final String RESULT_STATUS_PENDING = "PENDING";
    private static final String RESULT_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String RESULT_STATUS_COMPLETED = "COMPLETED";
    private static final String RESULT_STATUS_FAILED = "FAILED";
    private static final String RESULT_STATUS_ROLLBACK = "ROLLBACK";
    private static final String REMEDIATION_STATUS_PENDING = "PENDING";
    private static final String REMEDIATION_STATUS_SUCCESS = "SUCCESS";
    private static final String REMEDIATION_STATUS_FAILED = "FAILED";
    private static final List<String> AUTO_FIX_TYPES = List.of("AUTO", "SEMI");

    private final BaselineResultMapper baselineResultMapper;
    private final BaselineRemediationMapper baselineRemediationMapper;
    private final BaselineRuleMapper baselineRuleMapper;
    private final HostMapper hostMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmqpAdmin amqpAdmin;

    @Override
    @Transactional
    public BaselineActionResponseDTO remediate(List<Long> resultIds) {
        List<Long> distinctIds = distinctPositiveIds(resultIds);
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("修复目标不能为空");
        }
        List<BaselineResultEntity> results = baselineResultMapper.selectByIds(distinctIds);
        Map<Long, BaselineRuleEntity> ruleById = loadRules(results);

        int success = 0;
        int failed = 0;
        for (BaselineResultEntity result : results) {
            if (dispatchRemediation(result, ruleById.get(result.getRuleId()))) {
                success++;
            } else {
                failed++;
            }
        }
        failed += distinctIds.size() - results.size();
        return response(distinctIds.size(), success, failed, "已下发自动修复");
    }

    @Override
    @Transactional
    public BaselineActionResponseDTO rollback(List<Long> resultIds) {
        List<Long> distinctIds = distinctPositiveIds(resultIds);
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("回滚目标不能为空");
        }
        List<BaselineResultEntity> results = baselineResultMapper.selectByIds(distinctIds);

        int success = 0;
        int failed = 0;
        for (BaselineResultEntity result : results) {
            BaselineRemediationEntity remediation =
                    baselineRemediationMapper.selectLatestSuccessfulByResultId(result.getId());
            if (dispatchRollback(result, remediation)) {
                success++;
            } else {
                failed++;
            }
        }
        failed += distinctIds.size() - results.size();
        return response(distinctIds.size(), success, failed, "已下发回滚");
    }

    private boolean dispatchRemediation(BaselineResultEntity result, BaselineRuleEntity rule) {
        if (rule == null) {
            log.warn("修复跳过：规则不存在 resultId={}, ruleId={}", result.getId(), result.getRuleId());
            return false;
        }
        String remediationType = upper(rule.getRemediationType());
        if (!AUTO_FIX_TYPES.contains(remediationType)) {
            log.warn("修复跳过：规则非自动/半自动 resultId={}, remediationType={}", result.getId(), remediationType);
            return false;
        }
        if (!StringUtils.hasText(rule.getRemediationScript())) {
            log.warn("修复跳过：规则缺少修复脚本 resultId={}, ruleId={}", result.getId(), rule.getId());
            return false;
        }
        HostEntity host = hostMapper.selectById(result.getHostId());
        if (!isDispatchable(host)) {
            log.warn("修复跳过：主机不存在或缺少 MAC resultId={}, hostId={}", result.getId(), result.getHostId());
            return false;
        }

        BaselineRemediationEntity remediation = createRemediationRecord(result, rule);
        Map<String, Object> message = buildRemediationMessage(result, rule, host, remediation);
        if (!sendToAgent(host, message, "基线修复")) {
            baselineRemediationMapper.markFailed(remediation.getId());
            return false;
        }
        baselineRemediationMapper.updateStarted(remediation.getId());
        baselineResultMapper.updateRemediationStatus(result.getId(), RESULT_STATUS_IN_PROGRESS);
        return true;
    }

    private boolean dispatchRollback(BaselineResultEntity result, BaselineRemediationEntity remediation) {
        if (remediation == null) {
            log.warn("回滚跳过：找不到成功修复记录 resultId={}", result.getId());
            return false;
        }
        if (!StringUtils.hasText(remediation.getBackupData())) {
            log.warn("回滚跳过：修复记录缺少备份数据 resultId={}, remediationId={}",
                    result.getId(), remediation.getId());
            return false;
        }
        HostEntity host = hostMapper.selectById(result.getHostId());
        if (!isDispatchable(host)) {
            log.warn("回滚跳过：主机不存在或缺少 MAC resultId={}, hostId={}", result.getId(), result.getHostId());
            return false;
        }
        Map<String, Object> message = buildRollbackMessage(result, host, remediation);
        if (!sendToAgent(host, message, "基线回滚")) {
            return false;
        }
        baselineResultMapper.updateRemediationStatus(result.getId(), RESULT_STATUS_IN_PROGRESS);
        return true;
    }

    private BaselineRemediationEntity createRemediationRecord(BaselineResultEntity result, BaselineRuleEntity rule) {
        BaselineRemediationEntity remediation = new BaselineRemediationEntity();
        remediation.setResultId(result.getId());
        remediation.setHostId(result.getHostId());
        remediation.setRuleId(result.getRuleId());
        remediation.setRemediationType(rule.getRemediationType());
        remediation.setOldValue(result.getActualValue());
        remediation.setNewValue(result.getExpectedValue());
        remediation.setExecuteScript(BaselineWindowsPathNormalizer.normalizeScript(rule.getRemediationScript()));
        remediation.setStatus(REMEDIATION_STATUS_PENDING);
        remediation.setStartTime(LocalDateTime.now());
        remediation.setCreateTime(LocalDateTime.now());
        baselineRemediationMapper.insert(remediation);
        return remediation;
    }

    private Map<String, Object> buildRemediationMessage(BaselineResultEntity result,
                                                        BaselineRuleEntity rule,
                                                        HostEntity host,
                                                        BaselineRemediationEntity remediation) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", TYPE_REMEDIATION);
        message.put("remediationId", remediation.getId());
        message.put("resultId", result.getId());
        message.put("taskId", result.getTaskId());
        message.put("hostId", host.getId());
        message.put("macAddress", host.getMacAddress());
        message.put("ruleId", rule.getId());
        message.put("ruleCode", rule.getRuleCode());
        message.put("checkMethod", rule.getCheckMethod());
        message.put("checkKey", BaselineWindowsPathNormalizer.normalizeCheckKey(result.getCheckKey()));
        message.put("remediationType", rule.getRemediationType());
        message.put("executionMode", "SCRIPT");
        message.put("oldValue", result.getActualValue());
        message.put("actualValue", result.getActualValue());
        message.put("expectedValue", result.getExpectedValue());
        message.put("backupRequired", true);
        message.put("remediationScript", remediation.getExecuteScript());
        message.put("createdAt", nowText());
        return message;
    }

    private Map<String, Object> buildRollbackMessage(BaselineResultEntity result,
                                                     HostEntity host,
                                                     BaselineRemediationEntity remediation) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", TYPE_ROLLBACK);
        message.put("remediationId", remediation.getId());
        message.put("resultId", result.getId());
        message.put("hostId", host.getId());
        message.put("macAddress", host.getMacAddress());
        message.put("ruleId", remediation.getRuleId());
        BaselineRuleEntity rule = loadRule(remediation.getRuleId());
        if (rule != null) {
            message.put("ruleCode", rule.getRuleCode());
            message.put("checkMethod", rule.getCheckMethod());
        }
        message.put("checkKey", BaselineWindowsPathNormalizer.normalizeCheckKey(result.getCheckKey()));
        message.put("rollbackMode", "BACKUP_DATA");
        message.put("oldValue", remediation.getOldValue());
        message.put("newValue", remediation.getNewValue());
        message.put("backupData", remediation.getBackupData());
        message.put("executeScript", remediation.getExecuteScript());
        message.put("createdAt", nowText());
        return message;
    }

    @Override
    @Transactional
    public void handleResult(Long remediationId, Long resultId, boolean success,
                             String oldValue, String newValue, String backupData) {
        Long resolvedRemediationId = resolveRemediationId(remediationId, resultId);
        boolean completed = success && resolvedRemediationId != null && StringUtils.hasText(backupData);
        if (success && !StringUtils.hasText(backupData)) {
            log.warn("基线修复成功回传缺少 backupData，按失败处理以避免不可回滚: remediationId={}, resultId={}",
                    resolvedRemediationId, resultId);
        }
        if (success && resolvedRemediationId == null) {
            log.warn("基线修复成功回传找不到 remediation 记录，按失败处理以避免闭环断裂: resultId={}", resultId);
        }
        String resultStatus = completed ? RESULT_STATUS_COMPLETED : RESULT_STATUS_FAILED;
        String remediationStatus = completed ? REMEDIATION_STATUS_SUCCESS : REMEDIATION_STATUS_FAILED;
        if (resolvedRemediationId != null) {
            baselineRemediationMapper.updateResult(resolvedRemediationId, remediationStatus, oldValue, newValue, backupData);
        }
        int updated = baselineResultMapper.updateRemediationStatus(resultId, resultStatus);
        log.info("基线修复结果回填: remediationId={}, resultId={}, status={}, updated={}",
                resolvedRemediationId, resultId, resultStatus, updated);
    }

    @Override
    @Transactional
    public void handleRollbackResult(Long remediationId, Long resultId, boolean success) {
        Long resolvedRemediationId = resolveRollbackRemediationId(remediationId, resultId);
        if (success && resolvedRemediationId != null) {
            baselineRemediationMapper.markRollback(resolvedRemediationId);
        }
        String resultStatus = success ? RESULT_STATUS_ROLLBACK : RESULT_STATUS_COMPLETED;
        int updated = baselineResultMapper.updateRemediationStatus(resultId, resultStatus);
        log.info("基线回滚结果回填: remediationId={}, resultId={}, success={}, updated={}",
                resolvedRemediationId, resultId, success, updated);
    }

    private boolean sendToAgent(HostEntity host, Map<String, Object> payload, String actionName) {
        try {
            String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX
                    + normalizeMac(host.getMacAddress())
                    + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
            if (amqpAdmin.getQueueProperties(queueName) == null) {
                log.warn("{}跳过：客户端队列不存在 {}", actionName, queueName);
                return false;
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
            log.info("{}已下发: hostId={}, routingKey={}, payload={}", actionName, host.getId(), host.getMacAddress(), message);
            return true;
        } catch (Exception e) {
            log.error("{}下发失败: hostId={}", actionName, host.getId(), e);
            return false;
        }
    }

    private boolean isDispatchable(HostEntity host) {
        return host != null && StringUtils.hasText(host.getMacAddress());
    }

    private Map<Long, BaselineRuleEntity> loadRules(List<BaselineResultEntity> results) {
        List<Long> ruleIds = results.stream()
                .map(BaselineResultEntity::getRuleId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        return baselineRuleMapper.selectByIds(ruleIds).stream()
                .collect(Collectors.toMap(BaselineRuleEntity::getId, Function.identity(), (a, b) -> a));
    }

    private BaselineRuleEntity loadRule(Long ruleId) {
        if (ruleId == null) {
            return null;
        }
        List<BaselineRuleEntity> rules = baselineRuleMapper.selectByIds(List.of(ruleId));
        return rules.isEmpty() ? null : rules.get(0);
    }

    private Long resolveRemediationId(Long remediationId, Long resultId) {
        if (remediationId != null) {
            return remediationId;
        }
        if (resultId == null) {
            return null;
        }
        BaselineRemediationEntity pending = baselineRemediationMapper.selectLatestPendingByResultId(resultId);
        return pending == null ? null : pending.getId();
    }

    private Long resolveRollbackRemediationId(Long remediationId, Long resultId) {
        if (remediationId != null) {
            return remediationId;
        }
        if (resultId == null) {
            return null;
        }
        BaselineRemediationEntity remediation = baselineRemediationMapper.selectLatestSuccessfulByResultId(resultId);
        return remediation == null ? null : remediation.getId();
    }

    private BaselineActionResponseDTO response(int total, int success, int failed, String successPrefix) {
        BaselineActionResponseDTO response = new BaselineActionResponseDTO();
        response.setTotal(total);
        response.setSuccess(success);
        response.setFailed(failed);
        response.setMessage(failed == 0
                ? successPrefix + " " + success + " 项"
                : "成功 " + success + " 项，失败 " + failed + " 项");
        return response;
    }

    private List<Long> distinctPositiveIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    private String nowText() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
