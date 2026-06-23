package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.dto.BaselineHostOverviewDTO;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineRuleOptionDTO;
import com.cd.dto.BaselineTaskCreateRequestDTO;
import com.cd.entity.BaselineResultEntity;
import com.cd.mapper.BaselineResultMapper;
import com.cd.mapper.BaselineQueryMapper;
import com.cd.mapper.BaselineTaskMapper;
import com.cd.service.BaselineHostService;
import com.cd.service.BaselineTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineHostServiceImpl implements BaselineHostService {

    private static final DateTimeFormatter NAME_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> LEVELS = Set.of("green", "yellow", "red");

    private final BaselineQueryMapper baselineQueryMapper;
    private final BaselineTaskMapper baselineTaskMapper;
    private final BaselineResultMapper baselineResultMapper;
    private final BaselineTaskService baselineTaskService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<BaselineHostOverviewDTO> listHostOverview(Integer page, Integer size, String keyword, String level) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String safeKeyword = normalizeText(keyword);
        String safeLevel = normalizeLevel(level);
        Long tenantId = currentTenantId();
        long total = baselineQueryMapper.countHostOverview(safeKeyword, safeLevel, tenantId);
        List<BaselineHostOverviewDTO> list = baselineQueryMapper.selectHostOverviewPage(
                safeKeyword, safeLevel, tenantId, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public List<BaselineHostResultItemDTO> listHostResults(Long hostId, boolean onlyFail) {
        return baselineQueryMapper.selectHostResults(hostId, onlyFail, currentTenantId());
    }

    @Override
    public BaselineActionResponseDTO scanHosts(List<Long> hostIds) {
        List<Long> distinctIds = distinctPositiveIds(hostIds);
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("主机范围不能为空");
        }
        List<Long> fallbackRuleIds = null;
        int success = 0;
        int failed = 0;
        for (Long hostId : distinctIds) {
            try {
                List<Long> ruleIds = resolveRuleIds(hostId);
                if (ruleIds.isEmpty()) {
                    if (fallbackRuleIds == null) {
                        fallbackRuleIds = allPublishedRuleIds();
                    }
                    ruleIds = fallbackRuleIds;
                }
                BaselineTaskCreateRequestDTO request = new BaselineTaskCreateRequestDTO();
                request.setTaskName("主机即时检测-" + hostId + "-" + LocalDateTime.now().format(NAME_TS));
                request.setExecuteType("MANUAL");
                request.setHostIds(List.of(hostId));
                request.setRuleIds(ruleIds);
                baselineTaskService.createAndDispatch(request);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("主机立即检测下发失败: hostId={}", hostId, e);
            }
        }

        BaselineActionResponseDTO response = new BaselineActionResponseDTO();
        response.setTotal(distinctIds.size());
        response.setSuccess(success);
        response.setFailed(failed);
        response.setMessage(failed == 0
                ? "已对 " + success + " 台主机下发检测"
                : "成功 " + success + " 台，失败 " + failed + " 台");
        return response;
    }

    @Override
    public BaselineActionResponseDTO recheckResults(List<Long> resultIds) {
        List<Long> distinctIds = distinctPositiveIds(resultIds);
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("复检目标不能为空");
        }
        List<BaselineResultEntity> results = baselineResultMapper.selectByIdsAndTenant(distinctIds, currentTenantId());
        Map<Long, List<BaselineResultEntity>> resultsByHost = new LinkedHashMap<>();
        for (BaselineResultEntity result : results) {
            if (result.getHostId() == null || result.getRuleId() == null) {
                continue;
            }
            resultsByHost.computeIfAbsent(result.getHostId(), ignored -> new ArrayList<>()).add(result);
        }

        int success = 0;
        int failed = distinctIds.size() - results.size();
        for (Map.Entry<Long, List<BaselineResultEntity>> entry : resultsByHost.entrySet()) {
            try {
                List<Long> ruleIds = entry.getValue().stream()
                        .map(BaselineResultEntity::getRuleId)
                        .distinct()
                        .toList();
                BaselineTaskCreateRequestDTO request = new BaselineTaskCreateRequestDTO();
                request.setTaskName("基线复检-" + entry.getKey() + "-" + LocalDateTime.now().format(NAME_TS));
                request.setExecuteType("MANUAL");
                request.setHostIds(List.of(entry.getKey()));
                request.setRuleIds(ruleIds);
                baselineTaskService.createAndDispatch(request);
                success += entry.getValue().size();
            } catch (Exception e) {
                failed += entry.getValue().size();
                log.warn("基线复检下发失败: hostId={}", entry.getKey(), e);
            }
        }

        BaselineActionResponseDTO response = new BaselineActionResponseDTO();
        response.setTotal(distinctIds.size());
        response.setSuccess(success);
        response.setFailed(Math.max(0, failed));
        response.setMessage(response.getFailed() == 0
                ? "已下发复检 " + success + " 项"
                : "成功 " + success + " 项，失败 " + response.getFailed() + " 项");
        return response;
    }

    private List<Long> resolveRuleIds(Long hostId) {
        String scope = baselineTaskMapper.selectLatestRuleScopeByHostAndTenant(hostId, currentTenantId());
        if (!StringUtils.hasText(scope)) {
            return List.of();
        }
        try {
            JsonNode ruleIdsNode = objectMapper.readTree(scope).path("ruleIds");
            if (!ruleIdsNode.isArray()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode node : ruleIdsNode) {
                if (node.isNumber()) {
                    ids.add(node.asLong());
                } else if (node.isTextual()) {
                    try {
                        ids.add(Long.parseLong(node.asText().trim()));
                    } catch (NumberFormatException ignored) {
                        // 跳过非法 id
                    }
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("解析主机最近任务规则范围失败: hostId={}, scope={}", hostId, scope, e);
            return List.of();
        }
    }

    private List<Long> allPublishedRuleIds() {
        return baselineQueryMapper.selectRuleOptions(null, List.of(), null).stream()
                .map(BaselineRuleOptionDTO::getId)
                .toList();
    }

    private List<Long> distinctPositiveIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 12;
        }
        return Math.min(size, 200);
    }

    private String normalizeText(String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private String normalizeLevel(String level) {
        if (level == null) {
            return null;
        }
        String normalized = level.trim().toLowerCase();
        return LEVELS.contains(normalized) ? normalized : null;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }
}
