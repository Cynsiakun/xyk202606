package com.cd.service.impl;

import com.cd.common.config.RabbitMQConfig;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.SecurityUtils;
import com.cd.dto.BaselineHostDispatchDTO;
import com.cd.dto.BaselineTaskCreateRequestDTO;
import com.cd.dto.BaselineTaskDispatchResponseDTO;
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
        List<HostEntity> hosts = loadHosts(hostIds);
        List<BaselineRuleEntity> rules = loadRules(ruleIds);
        Map<Long, List<BaselineRuleItemEntity>> itemsByRuleId = loadRuleItems(ruleIds);

        BaselineTaskEntity task = createTask(request, hostIds, rules);
        List<BaselineHostDispatchDTO> dispatchResults = new ArrayList<>();
        int sentCount = 0;
        int failedCount = 0;

        for (HostEntity host : hosts) {
            BaselineTaskHostEntity taskHost = createTaskHost(task.getId(), host.getId());
            BaselineHostDispatchDTO result = dispatchHost(task, taskHost, host, rules, itemsByRuleId);
            dispatchResults.add(result);
            if (Boolean.TRUE.equals(result.getSent())) {
                sentCount++;
            } else {
                failedCount++;
            }
        }

        String taskStatus = sentCount > 0 ? STATUS_RUNNING : STATUS_FAILED;
        baselineTaskMapper.updateTaskStatus(task.getId(), taskStatus, sentCount, failedCount);

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

    private BaselineTaskEntity createTask(BaselineTaskCreateRequestDTO request,
                                          List<Long> hostIds,
                                          List<BaselineRuleEntity> rules) {
        BaselineTaskEntity task = new BaselineTaskEntity();
        task.setTaskName(request.getTaskName().trim());
        task.setExecuteType(normalizeExecuteType(request.getExecuteType()));
        task.setCronExpr(emptyToNull(request.getCronExpr()));
        task.setRuleScope(writeJson(Map.of("ruleIds", rules.stream().map(BaselineRuleEntity::getId).toList())));
        task.setRuleSnapshotJson(writeJson(rules.stream()
                .map(rule -> Map.of("ruleId", rule.getId(), "version", defaultVersion(rule)))
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

    private BaselineTaskHostEntity createTaskHost(Long taskId, Long hostId) {
        BaselineTaskHostEntity taskHost = new BaselineTaskHostEntity();
        taskHost.setTaskId(taskId);
        taskHost.setHostId(hostId);
        taskHost.setStatus(STATUS_PENDING);
        baselineTaskMapper.insertTaskHost(taskHost);
        return taskHost;
    }

    private BaselineHostDispatchDTO dispatchHost(BaselineTaskEntity task,
                                                 BaselineTaskHostEntity taskHost,
                                                 HostEntity host,
                                                 List<BaselineRuleEntity> rules,
                                                 Map<Long, List<BaselineRuleItemEntity>> itemsByRuleId) {
        BaselineHostDispatchDTO result = new BaselineHostDispatchDTO();
        result.setHostId(host.getId());
        result.setTaskHostId(taskHost.getId());
        result.setMacAddress(host.getMacAddress());

        if (!StringUtils.hasText(host.getMacAddress())) {
            return markDispatchFailed(taskHost.getId(), result, "主机缺少 MAC 地址，无法下发");
        }

        Map<String, Object> payload = buildAgentMessage(task, taskHost, host, rules, itemsByRuleId);
        try {
            String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX
                    + normalizeMac(host.getMacAddress())
                    + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
            if (amqpAdmin.getQueueProperties(queueName) == null) {
                return markDispatchFailed(taskHost.getId(), result, "客户端队列不存在，无法下发: " + queueName);
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
            baselineTaskMapper.updateTaskHostStatus(taskHost.getId(), STATUS_RUNNING,
                    writeJson(Map.of("dispatchAt", nowText())));
            result.setSent(true);
            result.setMessage("下发成功");
            log.info("基线检测任务已下发: taskId={}, taskHostId={}, hostId={}, routingKey={}, payload={}",
                    task.getId(), taskHost.getId(), host.getId(), host.getMacAddress(), message);
            return result;
        } catch (Exception e) {
            log.error("基线检测任务下发失败: taskId={}, taskHostId={}, hostId={}",
                    task.getId(), taskHost.getId(), host.getId(), e);
            return markDispatchFailed(taskHost.getId(), result, "下发失败: " + e.getMessage());
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
                                                       String message) {
        baselineTaskMapper.updateTaskHostStatus(taskHostId, STATUS_FAILED,
                writeJson(Map.of("dispatchAt", nowText(), "error", message)));
        result.setSent(false);
        result.setMessage(message);
        return result;
    }

    private List<HostEntity> loadHosts(List<Long> hostIds) {
        List<HostEntity> hosts = new ArrayList<>();
        for (Long hostId : hostIds) {
            HostEntity host = hostMapper.selectById(hostId);
            if (host == null) {
                throw new ResourceNotFoundException("主机不存在: " + hostId);
            }
            hosts.add(host);
        }
        return hosts;
    }

    private List<BaselineRuleEntity> loadRules(List<Long> ruleIds) {
        List<BaselineRuleEntity> rules = baselineRuleMapper.selectPublishedByIds(ruleIds);
        Set<Long> foundIds = rules.stream().map(BaselineRuleEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> missingIds = ruleIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missingIds.isEmpty()) {
            throw new ResourceNotFoundException("规则不存在、未启用或未发布: " + missingIds);
        }
        return rules;
    }

    private Map<Long, List<BaselineRuleItemEntity>> loadRuleItems(List<Long> ruleIds) {
        List<BaselineRuleItemEntity> items = baselineRuleMapper.selectItemsByRuleIds(ruleIds);
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
}
