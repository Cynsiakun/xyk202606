package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.config.RabbitMQConfig;
import com.cd.dto.PatchRiskDetailDTO;
import com.cd.dto.PatchRiskHostDTO;
import com.cd.dto.PatchSecurityActionResultDTO;
import com.cd.dto.PatchSecuritySummaryDTO;
import com.cd.entity.HostEntity;
import com.cd.entity.HostPatchRiskEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.HostPatchRiskMapper;
import com.cd.mapper.PatchSecurityMapper;
import com.cd.service.PatchSecurityService;
import com.cd.service.RuleEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchSecurityServiceImpl implements PatchSecurityService {

    private static final int PROBE_ONLINE_THRESHOLD_SECONDS = 15;

    private final PatchSecurityMapper patchSecurityMapper;
    private final HostMapper hostMapper;
    private final HostPatchRiskMapper hostPatchRiskMapper;
    private final RuleEngineService ruleEngineService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmqpAdmin amqpAdmin;

    @Override
    public PatchSecuritySummaryDTO summary() {
        PatchSecuritySummaryDTO summary = patchSecurityMapper.selectSummary();
        return summary == null ? new PatchSecuritySummaryDTO() : summary;
    }

    @Override
    public PageResult<PatchRiskHostDTO> listRiskHosts(int page,
                                                      int size,
                                                      String keyword,
                                                      String riskLevel,
                                                      String riskType,
                                                      Integer pendingReboot,
                                                      String osName) {
        int offset = (page - 1) * size;
        String normalizedKeyword = emptyToNull(keyword);
        String normalizedRiskLevel = emptyToNull(riskLevel);
        String normalizedRiskType = emptyToNull(riskType);
        String normalizedOsName = emptyToNull(osName);
        long total = patchSecurityMapper.countRiskHosts(
                normalizedKeyword, normalizedRiskLevel, normalizedRiskType, pendingReboot, normalizedOsName);
        List<PatchRiskHostDTO> list = patchSecurityMapper.selectRiskHostPage(
                offset, size, normalizedKeyword, normalizedRiskLevel, normalizedRiskType, pendingReboot, normalizedOsName);
        return new PageResult<>(total, list);
    }

    @Override
    public List<PatchRiskDetailDTO> listHostRisks(Long hostId) {
        return patchSecurityMapper.selectRiskDetailsByHostId(hostId);
    }

    @Override
    public PatchSecurityActionResultDTO analyze(List<Long> hostIds) {
        List<Long> targets = hostIds == null || hostIds.isEmpty()
                ? patchSecurityMapper.selectHostIdsWithPatchStatus()
                : hostIds;
        PatchSecurityActionResultDTO result = new PatchSecurityActionResultDTO();
        result.setTotal(targets.size());
        for (Long hostId : targets) {
            try {
                analyzeHost(hostId);
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                log.error("补丁风险分析失败: hostId={}", hostId, e);
            }
        }
        return result;
    }

    @Override
    public PatchSecurityActionResultDTO scan(List<Long> hostIds) {
        List<Long> targets = hostIds == null || hostIds.isEmpty()
                ? patchSecurityMapper.selectOnlineHostIds()
                : hostIds;
        PatchSecurityActionResultDTO result = new PatchSecurityActionResultDTO();
        result.setTotal(targets.size());
        for (Long hostId : targets) {
            try {
                sendPatchScan(hostId);
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                log.error("补丁扫描任务下发失败: hostId={}", hostId, e);
            }
        }
        return result;
    }

    @Override
    public List<HostPatchRiskEntity> analyzeHost(Long hostId) {
        List<HostPatchRiskEntity> risks = ruleEngineService.execute(hostId);
        List<String> activeRiskIds = risks.stream()
                .map(HostPatchRiskEntity::getRiskId)
                .toList();
        hostPatchRiskMapper.markFixedByHostIdExceptRiskIds(hostId, activeRiskIds);
        return risks;
    }

    private void sendPatchScan(Long hostId) {
        HostEntity host = hostMapper.selectById(hostId);
        if (host == null) {
            throw new IllegalArgumentException("主机不存在: " + hostId);
        }
        if (!StringUtils.hasText(host.getMacAddress())) {
            throw new IllegalArgumentException("主机缺少MAC地址: " + hostId);
        }
        LocalDateTime updatedAt = host.getUpdatedAt();
        if (updatedAt == null
                || Duration.between(updatedAt, LocalDateTime.now()).getSeconds() > PROBE_ONLINE_THRESHOLD_SECONDS) {
            throw new IllegalArgumentException("主机已下线，无法执行补丁扫描");
        }

        String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX + normalizeMac(host.getMacAddress()) + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
        if (amqpAdmin.getQueueProperties(queueName) == null) {
            throw new IllegalArgumentException("客户端队列不存在，无法发送补丁扫描指令");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "patch_scan");
        payload.put("macAddress", host.getMacAddress());

        String message;
        try {
            message = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("组装补丁扫描消息失败", e);
        }
        rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, host.getMacAddress(), message);
        log.info("补丁扫描任务已下发: hostId={}, routingKey={}, payload={}", hostId, host.getMacAddress(), message);
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
