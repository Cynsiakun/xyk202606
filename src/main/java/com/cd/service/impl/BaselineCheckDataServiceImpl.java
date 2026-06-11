package com.cd.service.impl;

import com.cd.entity.BaselineCheckDataEntity;
import com.cd.entity.MqErrorLogEntity;
import com.cd.mapper.BaselineCheckDataMapper;
import com.cd.mapper.MqErrorLogMapper;
import com.cd.service.BaselineCheckDataService;
import com.cd.service.BaselineRuleEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 基线检测回传结果处理实现。
 *
 * <p>校验规则：JSON 合法、{@code type} 为 {@code baseline_scan_result}、{@code taskId} 与
 * {@code hostId} 不为空且可解析为数字。通过则将完整消息体原样写入 {@code baseline_check_data}，
 * 失败则写入 {@code mq_error_logs}。所有异常在本层兜底，避免 MQ 无限重试。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineCheckDataServiceImpl implements BaselineCheckDataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EXPECTED_TYPE = "baseline_scan_result";

    private final BaselineCheckDataMapper baselineCheckDataMapper;
    private final MqErrorLogMapper mqErrorLogMapper;
    private final BaselineRuleEngine baselineRuleEngine;

    @Override
    public void processBaselineResult(String queueName, String message) {
        try {
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
            if (!EXPECTED_TYPE.equals(type)) {
                saveError(queueName, message, "type=" + type + " 与队列 " + queueName + " 不匹配，期望 " + EXPECTED_TYPE);
                return;
            }

            // 校验 taskId 不为空
            Long taskId = parseId(root.path("taskId"));
            if (taskId == null) {
                saveError(queueName, message, "缺少必要字段 taskId 或其值非法");
                return;
            }

            // 校验 hostId 不为空
            Long hostId = parseId(root.path("hostId"));
            if (hostId == null) {
                saveError(queueName, message, "缺少必要字段 hostId 或其值非法");
                return;
            }

            // 完整消息体原样入库
            BaselineCheckDataEntity entity = new BaselineCheckDataEntity();
            entity.setTaskId(taskId);
            entity.setHostId(hostId);
            entity.setCheckData(message);
            entity.setCreateTime(LocalDateTime.now());
            baselineCheckDataMapper.insert(entity);

            log.info("基线检测回传结果已入库: queue={}, taskId={}, hostId={}", queueName, taskId, hostId);

            // 入库成功后内联触发规则引擎判定；引擎异常已在其内部兜底，不影响 MQ 入库与 ACK
            triggerRuleEngine(entity);
        } catch (Exception e) {
            log.error("处理基线检测回传消息异常: queue={}", queueName, e);
            saveError(queueName, message, "服务端异常: " + e.getMessage());
        }
    }

    /** 入库后内联触发规则引擎判定，失败仅记日志，不回滚已入库的原始数据。 */
    private void triggerRuleEngine(BaselineCheckDataEntity entity) {
        try {
            baselineRuleEngine.evaluate(entity);
        } catch (Exception e) {
            log.warn("基线原始数据入库后规则引擎判定失败: checkDataId={}, taskId={}, hostId={}",
                    entity.getId(), entity.getTaskId(), entity.getHostId(), e);
        }
    }

    /** 解析 ID 字段：要求存在、非空、且能解析为数字，否则返回 null。 */
    private Long parseId(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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
