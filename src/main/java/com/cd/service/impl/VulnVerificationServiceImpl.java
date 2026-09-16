package com.cd.service.impl;

import com.cd.common.config.RabbitMQConfig;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.SecurityUtils;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.VulnVerificationRuleDTO;
import com.cd.dto.VulnVerificationTaskResponseDTO;
import com.cd.entity.HostEntity;
import com.cd.entity.HostVulnTaskEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.mapper.HostVulnTaskMapper;
import com.cd.service.VulnVerificationService;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VulnVerificationServiceImpl implements VulnVerificationService {

    private static final String TASK_TYPE = "VULN";
    private static final String SCAN_MODE = "REALTIME";
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final String VERIFYING = "VERIFYING";

    private final HostMapper hostMapper;
    private final HostVulnResultMapper hostVulnResultMapper;
    private final HostVulnTaskMapper hostVulnTaskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmqpAdmin amqpAdmin;

    @Override
    @Transactional
    public VulnVerificationTaskResponseDTO verifyHost(Long hostId) {
        HostEntity host = requireHost(hostId);
        Long tenantId = currentTenantId();
        List<VulnVerificationRuleDTO> rules = hostVulnResultMapper.selectPendingVerificationRulesByTenant(hostId, tenantId);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("该主机没有可下发的待验证漏洞规则");
        }
        return createAndSendTask(host, rules);
    }

    @Override
    @Transactional
    public VulnVerificationTaskResponseDTO verifyResult(Long hostId, Long resultId) {
        HostEntity host = requireHost(hostId);
        Long tenantId = currentTenantId();
        List<Long> groupedIds = hostVulnResultMapper.selectGroupedResultIdsByResultIdsAndTenant(List.of(resultId), tenantId);
        if (groupedIds.isEmpty()) {
            throw new IllegalArgumentException("该漏洞结果不存在或不可下发验证");
        }
        List<VulnVerificationRuleDTO> rules = hostVulnResultMapper.selectVerificationRulesByResultIdsAndTenant(groupedIds, tenantId)
                .stream()
                .filter(rule -> hostId.equals(rule.getHostId()))
                .toList();
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("该漏洞结果不存在或不可下发验证");
        }
        return createAndSendTask(host, rules);
    }

    @Override
    @Transactional
    public Map<Long, Long> verifyResults(List<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = resultIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        Long tenantId = currentTenantId();
        List<Long> expandedIds = hostVulnResultMapper.selectGroupedResultIdsByResultIdsAndTenant(distinctIds, tenantId);
        List<VulnVerificationRuleDTO> rules = expandedIds.isEmpty()
                ? List.of()
                : hostVulnResultMapper.selectVerificationRulesByResultIdsAndTenant(expandedIds, tenantId);
        Map<Long, List<VulnVerificationRuleDTO>> rulesByHost = rules.stream()
                .filter(rule -> rule.getHostId() != null)
                .collect(Collectors.groupingBy(VulnVerificationRuleDTO::getHostId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long resultId : distinctIds) {
            result.put(resultId, null);
        }
        for (Map.Entry<Long, List<VulnVerificationRuleDTO>> entry : rulesByHost.entrySet()) {
            try {
                HostEntity host = requireHost(entry.getKey());
                VulnVerificationTaskResponseDTO task = createAndSendTask(host, entry.getValue());
                for (VulnVerificationRuleDTO rule : entry.getValue()) {
                    result.put(rule.getResultId(), task.getTaskId());
                }
            } catch (Exception e) {
                log.warn("按漏洞结果下发验证任务失败: hostId={}, reason={}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    @Override
    public Map<Long, Long> batchVerify(List<Long> hostIds) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long hostId : hostIds) {
            try {
                VulnVerificationTaskResponseDTO task = verifyHost(hostId);
                result.put(hostId, task.getTaskId());
            } catch (Exception e) {
                log.warn("批量下发漏洞验证任务失败: hostId={}, reason={}", hostId, e.getMessage());
                result.put(hostId, null);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public VulnVerificationTaskResponseDTO retry(Long taskId) {
        Long tenantId = currentTenantId();
        HostVulnTaskEntity task = hostVulnTaskMapper.selectByIdAndTenant(taskId, tenantId);
        if (task == null) {
            throw new ResourceNotFoundException("漏洞验证任务不存在: " + taskId);
        }
        if (task.getStatus() != null && task.getStatus() == STATUS_RUNNING) {
            return response(task.getId(), task.getRuleCount(), true, STATUS_RUNNING, "任务已处于执行中");
        }

        JsonNode summary = readSummary(task.getSummaryJson());
        JsonNode messageNode = summary.path("agentMessage");
        if (messageNode.isMissingNode() || messageNode.isNull()) {
            throw new IllegalArgumentException("任务缺少可重试的下发消息");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> agentMessage = objectMapper.convertValue(messageNode, Map.class);
        boolean sent = sendToAgent(task.getMacAddress(), agentMessage);
        if (sent) {
            hostVulnTaskMapper.updateStatusByTenant(task.getId(), STATUS_RUNNING, task.getSummaryJson(), tenantId);
            List<Long> summaryResultIds = readResultIds(summary.path("resultIds"));
            if (!summaryResultIds.isEmpty()) {
                hostVulnResultMapper.updateVerifyStatusByIdsAndTenant(summaryResultIds, VERIFYING, tenantId);
            }
        }
        return response(task.getId(), task.getRuleCount(), sent, sent ? STATUS_RUNNING : STATUS_PENDING,
                sent ? "验证任务已重新下发" : "重试下发失败，任务仍为待执行");
    }

    private VulnVerificationTaskResponseDTO createAndSendTask(HostEntity host, List<VulnVerificationRuleDTO> rules) {
        HostVulnTaskEntity task = createTask(host, rules.size());
        Map<String, Object> agentMessage = buildAgentMessage(task.getId(), host.getMacAddress(), rules);
        List<Long> resultIds = rules.stream().map(VulnVerificationRuleDTO::getResultId).toList();
        String summaryJson = buildSummaryJson(agentMessage, resultIds, null);
        Long tenantId = currentTenantId();
        hostVulnResultMapper.updateTaskIdByIdsAndTenant(resultIds, task.getId(), tenantId);
        hostVulnTaskMapper.updateStatusByTenant(task.getId(), STATUS_PENDING, summaryJson, tenantId);

        boolean sent = sendToAgent(host.getMacAddress(), agentMessage);
        if (sent) {
            hostVulnTaskMapper.updateStatusByTenant(task.getId(), STATUS_RUNNING, summaryJson, tenantId);
            hostVulnResultMapper.updateVerifyStatusByIdsAndTenant(resultIds, VERIFYING, tenantId);
        }
        return response(task.getId(), rules.size(), sent, sent ? STATUS_RUNNING : STATUS_PENDING,
                sent ? "验证任务已下发" : "验证任务已创建，但消息下发失败，可稍后重试");
    }

    private HostEntity requireHost(Long hostId) {
        HostEntity host = hostMapper.selectByIdAndTenant(hostId, currentTenantId());
        if (host == null) {
            throw new ResourceNotFoundException("主机不存在: " + hostId);
        }
        if (!StringUtils.hasText(host.getMacAddress())) {
            throw new IllegalArgumentException("主机缺少MAC地址: " + hostId);
        }
        return host;
    }

    private HostVulnTaskEntity createTask(HostEntity host, int ruleCount) {
        LocalDateTime now = LocalDateTime.now();
        HostVulnTaskEntity task = new HostVulnTaskEntity();
        task.setTaskName("VULN_VERIFY_HOST_" + host.getId() + "_" + System.currentTimeMillis());
        task.setTaskType(TASK_TYPE);
        task.setHostId(host.getId());
        task.setMacAddress(host.getMacAddress());
        task.setScanMode(SCAN_MODE);
        task.setRuleCount(ruleCount);
        task.setStatus(STATUS_PENDING);
        task.setTenantId(currentTenantId());
        task.setTriggeredBy(currentUsername());
        task.setStartedAt(now);
        task.setCreatedAt(now);
        hostVulnTaskMapper.insert(task);
        return task;
    }

    private Map<String, Object> buildAgentMessage(Long taskId, String macAddress, List<VulnVerificationRuleDTO> rules) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("taskId", String.valueOf(taskId));
        message.put("type", "vuln_verify");
        message.put("macAddress", macAddress);
        message.put("rules", rules.stream().map(rule -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ruleId", rule.getRuleId());
            item.put("productType", rule.getProductType());
            item.put("productName", rule.getProductName());
            item.put("matchType", rule.getMatchType());
            item.put("versionExpression", rule.getVersionExpression());
            item.put("verifyType", rule.getVerifyType());
            item.put("verifyRule", rule.getVerifyRule());
            return item;
        }).toList());
        message.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return message;
    }

    private boolean sendToAgent(String macAddress, Map<String, Object> agentMessage) {
        try {
            String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX
                    + normalizeMac(macAddress)
                    + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
            Properties queueProperties = amqpAdmin.getQueueProperties(queueName);
            if (queueProperties == null) {
                log.warn("漏洞验证任务跳过：客户端队列不存在 queueName={}, routingKey={}", queueName, macAddress);
                return false;
            }
            amqpAdmin.declareExchange(new DirectExchange(RabbitMQConfig.AGENT_EXCHANGE, true, false));
            String payload = objectMapper.writeValueAsString(agentMessage);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AGENT_EXCHANGE,
                    macAddress,
                    payload,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setContentType("application/json");
                        return message;
                    });
            log.info("漏洞验证任务已下发: routingKey={}, payload={}", macAddress, payload);
            return true;
        } catch (Exception e) {
            log.error("漏洞验证任务下发失败: routingKey={}", macAddress, e);
            return false;
        }
    }

    private String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    private String buildSummaryJson(Map<String, Object> agentMessage, List<Long> resultIds, String errorMessage) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("agentMessage", agentMessage);
        summary.put("resultIds", resultIds);
        if (StringUtils.hasText(errorMessage)) {
            summary.put("error", errorMessage);
        }
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JsonNode readSummary(String summaryJson) {
        try {
            return objectMapper.readTree(StringUtils.hasText(summaryJson) ? summaryJson : "{}");
        } catch (Exception e) {
            throw new IllegalArgumentException("任务下发内容解析失败");
        }
    }

    private List<Long> readResultIds(JsonNode resultIdsNode) {
        if (!resultIdsNode.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(resultIdsNode.spliterator(), false)
                .filter(JsonNode::canConvertToLong)
                .map(JsonNode::asLong)
                .toList();
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(username) ? username : "system";
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private VulnVerificationTaskResponseDTO response(Long taskId,
                                                     Integer ruleCount,
                                                     boolean sent,
                                                     Integer status,
                                                     String message) {
        VulnVerificationTaskResponseDTO response = new VulnVerificationTaskResponseDTO();
        response.setTaskId(taskId);
        response.setRuleCount(ruleCount);
        response.setSent(sent);
        response.setStatus(status);
        response.setMessage(message);
        return response;
    }
}
