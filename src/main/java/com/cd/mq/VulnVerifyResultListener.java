package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.entity.HostVulnTaskEntity;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.mapper.HostVulnTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VulnVerifyResultListener {

    private static final String TYPE_VULN_VERIFY_RESULT = "vuln_verify_result";
    private static final int TASK_STATUS_FINISHED = 2;
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_NOT_AFFECTED = "NOT_AFFECTED";

    private final HostVulnResultMapper hostVulnResultMapper;
    private final HostVulnTaskMapper hostVulnTaskMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.VULN_VERIFY_RESULT_QUEUE)
    public void onMessage(String message) {
        try {
            process(message);
        } catch (Exception e) {
            log.error("处理漏洞验证回传失败，消息已ACK避免阻塞队列: {}", message, e);
        }
    }

    @Transactional
    protected void process(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        String type = text(root, "type");
        if (StringUtils.hasText(type) && !TYPE_VULN_VERIFY_RESULT.equals(type)) {
            log.warn("收到非漏洞验证回传消息，已忽略: type={}, message={}", type, message);
            return;
        }

        Long taskId = longValue(root.path("taskId"));
        if (taskId == null) {
            log.warn("漏洞验证回传缺少taskId，已忽略: {}", message);
            return;
        }

        HostVulnTaskEntity task = hostVulnTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Vuln verify result ignored because task does not exist: taskId={}", taskId);
            return;
        }
        Long tenantId = task.getTenantId() == null ? 0L : task.getTenantId();

        JsonNode results = root.path("results");
        if (!results.isArray()) {
            log.warn("漏洞验证回传results不是数组，taskId={}, message={}", taskId, message);
            return;
        }

        int total = 0;
        int matched = 0;
        int notAffected = 0;
        for (JsonNode item : results) {
            Long ruleId = longValue(item.path("ruleId"));
            if (ruleId == null) {
                log.warn("漏洞验证回传缺少ruleId，taskId={}, item={}", taskId, item);
                continue;
            }

            boolean isMatched = item.path("matched").asBoolean(false);
            String verifyStatus = isMatched ? STATUS_VERIFIED : STATUS_NOT_AFFECTED;
            int updated = hostVulnResultMapper.updateVerifyStatusByTaskAndRuleAndTenant(
                    taskId, ruleId, verifyStatus, tenantId);
            if (updated == 0) {
                log.warn("未找到待更新的漏洞验证结果: taskId={}, ruleId={}, status={}", taskId, ruleId, verifyStatus);
            }

            total++;
            if (isMatched) {
                matched++;
            } else {
                notAffected++;
            }
        }

        String summaryJson = summaryJson(total, matched, notAffected);
        int taskUpdated = hostVulnTaskMapper.markFinishedByTenant(
                taskId, TASK_STATUS_FINISHED, summaryJson, tenantId);
        if (taskUpdated == 0) {
            log.warn("未找到待更新的漏洞验证任务: taskId={}, summary={}", taskId, summaryJson);
        }
        log.info("漏洞验证回传处理完成: taskId={}, total={}, matched={}, notAffected={}",
                taskId, total, matched, notAffected);
    }

    private String summaryJson(int total, int matched, int notAffected) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("matched", matched);
        summary.put("notAffected", notAffected);
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{\"total\":" + total + ",\"matched\":" + matched + ",\"notAffected\":" + notAffected + "}";
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.asLong();
        }
        String value = node.asText();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
