package com.cd.service.impl;

import com.cd.entity.BaselineCheckDataEntity;
import com.cd.entity.BaselineResultEntity;
import com.cd.entity.BaselineRuleEntity;
import com.cd.entity.BaselineRuleItemEntity;
import com.cd.entity.BaselineSummaryEntity;
import com.cd.entity.BaselineTaskHostEntity;
import com.cd.mapper.BaselineResultMapper;
import com.cd.mapper.BaselineRuleMapper;
import com.cd.mapper.BaselineSummaryMapper;
import com.cd.mapper.BaselineTaskMapper;
import com.cd.service.BaselineRuleEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基线检测规则引擎实现。
 *
 * <p>核心流程：解析 {@code baseline_check_data.check_data} 中的 {@code results} 数组，
 * 逐条通过 {@code itemId} 反查 {@code baseline_rule_item}，按 {@code match_type} 与
 * {@code operator} 比对 {@code actualValue} 与 {@code expected_value}，判定 PASS/FAIL；
 * {@code executeStatus=ERROR} 时直接记 ERROR。每个 item 生成一条 {@code baseline_result}，
 * 该 task_host 全部处理完后更新状态为 FINISHED 并写入汇总，任务下所有主机终态后任务置 FINISHED。</p>
 *
 * <p>幂等：同一 task_host 已存在 result 时直接跳过，不重复生成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineRuleEngineImpl implements BaselineRuleEngine {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_ERROR = "ERROR";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final String HOST_STATUS_FINISHED = "FINISHED";
    private static final String HOST_STATUS_FAILED = "FAILED";
    private static final String TASK_STATUS_FINISHED = "FINISHED";
    private static final String REMEDIATION_PENDING = "PENDING";

    private static final String MATCH_EXACT = "EXACT";
    private static final String MATCH_CONTAINS = "CONTAINS";
    private static final String MATCH_REGEX = "REGEX";

    private final BaselineRuleMapper baselineRuleMapper;
    private final BaselineResultMapper baselineResultMapper;
    private final BaselineSummaryMapper baselineSummaryMapper;
    private final BaselineTaskMapper baselineTaskMapper;

    @Override
    @Transactional
    public void evaluate(BaselineCheckDataEntity checkData) {
        if (checkData == null || checkData.getTaskId() == null || checkData.getHostId() == null) {
            log.warn("基线规则引擎跳过：check_data 为空或缺少 taskId/hostId");
            return;
        }
        Long taskId = checkData.getTaskId();
        Long hostId = checkData.getHostId();

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(checkData.getCheckData());
        } catch (Exception e) {
            log.error("基线规则引擎解析 check_data 失败: checkDataId={}, taskId={}, hostId={}",
                    checkData.getId(), taskId, hostId, e);
            return;
        }

        // 解析 taskHostId：优先取消息内字段，缺失时按 (taskId, hostId) 反查
        BaselineTaskHostEntity taskHost = resolveTaskHost(root, taskId, hostId);
        if (taskHost == null) {
            log.warn("基线规则引擎跳过：未找到 task_host 关联记录, taskId={}, hostId={}", taskId, hostId);
            return;
        }
        Long taskHostId = taskHost.getId();

        // 幂等：同一 task_host 已生成结果则跳过
        if (baselineResultMapper.countByTaskHostId(taskHostId) > 0) {
            log.info("基线规则引擎跳过重复处理: taskHostId={}, taskId={}, hostId={}", taskHostId, taskId, hostId);
            return;
        }

        JsonNode results = root.path("results");
        if (!results.isArray()) {
            log.warn("基线规则引擎跳过：results 不是数组, taskId={}, hostId={}, taskHostId={}", taskId, hostId, taskHostId);
            results = OBJECT_MAPPER.createArrayNode();
        }

        LocalDateTime scanTime = parseScanTime(root.path("scanTime"));
        LocalDateTime now = LocalDateTime.now();

        // 预加载本次涉及的规则项与规则元数据
        List<Long> itemIds = collectLongs(results, "itemId");
        List<Long> ruleIds = collectLongs(results, "ruleId");
        Map<Long, BaselineRuleItemEntity> itemMap = loadItemMap(itemIds);
        Map<Long, BaselineRuleEntity> ruleMap = loadRuleMap(ruleIds);

        int passCount = 0;
        int failCount = 0;
        int score = 0;

        for (JsonNode result : results) {
            BaselineResultEntity entity = buildResult(result, taskId, taskHostId, hostId, itemMap, scanTime, now);
            baselineResultMapper.insert(entity);
            switch (entity.getStatus()) {
                case STATUS_PASS -> {
                    passCount++;
                    score += ruleScore(ruleMap, entity.getRuleId());
                }
                case STATUS_FAIL, STATUS_ERROR -> failCount++;
                default -> { /* UNKNOWN 不计入合规率分母 */ }
            }
        }

        // 写入汇总并将 task_host 置为 FINISHED
        writeSummary(hostId, taskId, passCount, failCount, score, scanTime != null ? scanTime : now);
        String summaryJson = buildHostSummaryJson(passCount, failCount, score);
        baselineTaskMapper.finishTaskHost(taskHostId, HOST_STATUS_FINISHED, summaryJson,
                scanTime != null ? scanTime : now);
        log.info("基线规则引擎完成主机判定: taskId={}, hostId={}, taskHostId={}, pass={}, fail={}",
                taskId, hostId, taskHostId, passCount, failCount);

        // 任务下所有主机终态后，将任务置为 FINISHED
        maybeFinishTask(taskId);
    }

    private BaselineTaskHostEntity resolveTaskHost(JsonNode root, Long taskId, Long hostId) {
        return baselineTaskMapper.selectTaskHostByTaskAndHost(taskId, hostId);
    }

    private BaselineResultEntity buildResult(JsonNode result,
                                             Long taskId,
                                             Long taskHostId,
                                             Long hostId,
                                             Map<Long, BaselineRuleItemEntity> itemMap,
                                             LocalDateTime scanTime,
                                             LocalDateTime now) {
        Long ruleId = longValue(result.path("ruleId"));
        Long itemId = longValue(result.path("itemId"));
        Integer ruleVersion = intValue(result.path("ruleVersion"));
        String actualValue = textValue(result.path("actualValue"));
        String executeStatus = textValue(result.path("executeStatus"));
        String agentMessage = textValue(result.path("message"));
        String evidence = textValue(result.path("evidence"));
        String checkKey = textValue(result.path("checkKey"));

        BaselineRuleItemEntity item = itemId == null ? null : itemMap.get(itemId);
        if (item != null && !StringUtils.hasText(checkKey)) {
            checkKey = item.getCheckKey();
        }
        String expectedValue = item == null ? null : item.getExpectedValue();

        BaselineResultEntity entity = new BaselineResultEntity();
        entity.setTaskId(taskId);
        entity.setTaskHostId(taskHostId);
        entity.setHostId(hostId);
        entity.setRuleId(ruleId != null ? ruleId : (item != null ? item.getRuleId() : 0L));
        entity.setRuleVersion(ruleVersion != null ? ruleVersion : 1);
        entity.setCheckKey(checkKey);
        entity.setActualValue(actualValue);
        entity.setExpectedValue(expectedValue);
        entity.setEvidence(evidence);
        entity.setRemediationStatus(REMEDIATION_PENDING);
        entity.setScanTime(scanTime);
        entity.setCreateTime(now);

        boolean missingRegistryValue = isMissingRegistryValue(agentMessage);

        // 判定状态与描述。注册表值缺失是可比对事实：期望为空则合规，期望非空则不合规。
        if (STATUS_ERROR.equalsIgnoreCase(executeStatus) && !missingRegistryValue) {
            entity.setStatus(STATUS_ERROR);
            entity.setMessage(StringUtils.hasText(agentMessage) ? agentMessage : "客户端执行失败");
        } else if (item == null) {
            entity.setStatus(STATUS_UNKNOWN);
            entity.setMessage("未找到规则项 itemId=" + itemId + "，无法比对");
        } else {
            boolean pass = compare(item, actualValue);
            entity.setStatus(pass ? STATUS_PASS : STATUS_FAIL);
            entity.setMessage(buildMessage(pass, item, actualValue, missingRegistryValue ? null : agentMessage));
        }
        return entity;
    }

    /**
     * 按 match_type 与 operator 比对实际值与期望值。
     *
     * <ul>
     *   <li>EXACT：数值运算符（&gt; &gt;= &lt; &lt;=）按数字比较；= / != 优先数字比较，不可解析时按字符串。</li>
     *   <li>CONTAINS：判断实际值是否包含期望值，= 表示需包含，!= 表示需不包含。</li>
     *   <li>REGEX：实际值是否匹配期望值正则，= 表示需匹配，!= 表示需不匹配。</li>
     * </ul>
     */
    private boolean compare(BaselineRuleItemEntity item, String actualValue) {
        String expected = item.getExpectedValue();
        String operator = StringUtils.hasText(item.getOperator()) ? item.getOperator().trim() : "=";
        String matchType = StringUtils.hasText(item.getMatchType()) ? item.getMatchType().trim().toUpperCase() : MATCH_EXACT;
        String actual = actualValue == null ? "" : actualValue;
        String exp = expected == null ? "" : expected;

        return switch (matchType) {
            case MATCH_CONTAINS -> {
                boolean contains = actual.contains(exp);
                yield "!=".equals(operator) ? !contains : contains;
            }
            case MATCH_REGEX -> {
                boolean matches;
                try {
                    matches = Pattern.compile(exp).matcher(actual).find();
                } catch (Exception e) {
                    log.warn("基线规则正则非法: itemId={}, expr={}", item.getId(), exp);
                    matches = false;
                }
                yield "!=".equals(operator) ? !matches : matches;
            }
            default -> compareExact(operator, actual, exp);
        };
    }

    private boolean compareExact(String operator, String actual, String expected) {
        Double expectedNum = parseStrictDouble(expected);
        Double actualNum = expectedNum == null ? null : parseActualDouble(actual);
        boolean numeric = actualNum != null && expectedNum != null;
        switch (operator) {
            case "=":
                return numeric ? actualNum.compareTo(expectedNum) == 0 : actual.equalsIgnoreCase(expected);
            case "!=":
                return numeric ? actualNum.compareTo(expectedNum) != 0 : !actual.equalsIgnoreCase(expected);
            case ">":
                return numeric && actualNum > expectedNum;
            case ">=":
                return numeric && actualNum >= expectedNum;
            case "<":
                return numeric && actualNum < expectedNum;
            case "<=":
                return numeric && actualNum <= expectedNum;
            default:
                log.warn("基线规则未知运算符: {}", operator);
                return actual.equalsIgnoreCase(expected);
        }
    }

    private String buildMessage(boolean pass, BaselineRuleItemEntity item, String actualValue, String agentMessage) {
        if (StringUtils.hasText(agentMessage)) {
            return limit(agentMessage, 2000);
        }
        String text = (pass ? "合规：" : "不合规：") + "实际值[" + (actualValue == null ? "" : actualValue) + "]"
                + " " + safe(item.getOperator()) + " 期望值[" + safe(item.getExpectedValue()) + "]"
                + "（match=" + safe(item.getMatchType()) + "）";
        return limit(text, 2000);
    }

    private void writeSummary(Long hostId, Long taskId, int passCount, int failCount, int score, LocalDateTime scanTime) {
        BaselineSummaryEntity summary = new BaselineSummaryEntity();
        summary.setHostId(hostId);
        summary.setTaskId(taskId);
        summary.setPassCount(passCount);
        summary.setFailCount(failCount);
        summary.setScore(score);
        summary.setComplianceRate(complianceRate(passCount, failCount));
        summary.setLastScanTime(scanTime);
        baselineSummaryMapper.upsert(summary);
    }

    private void maybeFinishTask(Long taskId) {
        if (baselineTaskMapper.countUnfinishedHosts(taskId) > 0) {
            return;
        }
        int finished = baselineTaskMapper.countHostsByStatus(taskId, HOST_STATUS_FINISHED);
        int failed = baselineTaskMapper.countHostsByStatus(taskId, HOST_STATUS_FAILED);
        baselineTaskMapper.updateTaskStatus(taskId, TASK_STATUS_FINISHED, finished, failed);
        log.info("基线任务全部主机已终态，任务置为 FINISHED: taskId={}, finished={}, failed={}", taskId, finished, failed);
    }

    private BigDecimal complianceRate(int passCount, int failCount) {
        int total = passCount + failCount;
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(passCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private String buildHostSummaryJson(int passCount, int failCount, int score) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("passCount", passCount);
        summary.put("failCount", failCount);
        summary.put("score", score);
        summary.put("complianceRate", complianceRate(passCount, failCount));
        try {
            return OBJECT_MAPPER.writeValueAsString(summary);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Long, BaselineRuleItemEntity> loadItemMap(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return baselineRuleMapper.selectItemsByItemIds(itemIds).stream()
                .collect(Collectors.toMap(BaselineRuleItemEntity::getId, item -> item, (a, b) -> a));
    }

    private Map<Long, BaselineRuleEntity> loadRuleMap(List<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        return baselineRuleMapper.selectByIds(ruleIds).stream()
                .collect(Collectors.toMap(BaselineRuleEntity::getId, rule -> rule, (a, b) -> a));
    }

    private int ruleScore(Map<Long, BaselineRuleEntity> ruleMap, Long ruleId) {
        BaselineRuleEntity rule = ruleId == null ? null : ruleMap.get(ruleId);
        if (rule == null || rule.getScore() == null) {
            return 0;
        }
        return rule.getScore();
    }

    private List<Long> collectLongs(JsonNode results, String field) {
        List<Long> values = new ArrayList<>();
        for (JsonNode result : results) {
            Long value = longValue(result.path(field));
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private LocalDateTime parseScanTime(JsonNode node) {
        String text = textValue(node);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(text).toLocalDateTime();
        } catch (Exception ignore) {
            try {
                return LocalDateTime.parse(text);
            } catch (Exception e) {
                return null;
            }
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
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer intValue(JsonNode node) {
        Long value = longValue(node);
        return value == null ? null : value.intValue();
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private boolean isMissingRegistryValue(String message) {
        return StringUtils.hasText(message)
                && (message.contains("注册表路径或值不存在")
                || message.contains("registry path or value does not exist")
                || message.contains("The system cannot find the file specified")
                || message.contains("系统找不到指定的文件"));
    }

    private Double parseStrictDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseActualDouble(String value) {
        Double strict = parseStrictDouble(value);
        if (strict != null || !StringUtils.hasText(value)) {
            return strict;
        }
        Matcher matcher = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
