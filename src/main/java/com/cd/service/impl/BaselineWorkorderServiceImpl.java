package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.exception.UnauthorizedException;
import com.cd.common.security.PermissionChecker;
import com.cd.common.security.SecurityUtils;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.dto.BaselineOperatorOptionDTO;
import com.cd.dto.BaselineTaskCreateRequestDTO;
import com.cd.dto.BaselineWorkorderDetailDTO;
import com.cd.dto.BaselineWorkorderListItemDTO;
import com.cd.entity.BaselineResultEntity;
import com.cd.entity.BaselineRuleEntity;
import com.cd.entity.BaselineWorkorderEntity;
import com.cd.mapper.BaselineResultMapper;
import com.cd.mapper.BaselineQueryMapper;
import com.cd.mapper.BaselineRuleMapper;
import com.cd.mapper.BaselineWorkorderMapper;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.service.BaselineWorkorderService;
import com.cd.service.BaselineTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineWorkorderServiceImpl implements BaselineWorkorderService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_DONE = "DONE";
    private static final String REMEDIATION_TICKETED = "TICKETED";
    private static final String SECURITY_OPERATOR = "SECURITY_OPERATOR";

    private final BaselineResultMapper baselineResultMapper;
    private final BaselineQueryMapper baselineQueryMapper;
    private final BaselineRuleMapper baselineRuleMapper;
    private final BaselineWorkorderMapper baselineWorkorderMapper;
    private final BaselineTaskService baselineTaskService;
    private final PermissionChecker permissionChecker;

    @Override
    @Transactional
    public BaselineActionResponseDTO create(List<Long> resultIds, Long assigneeId, String remark) {
        List<Long> distinctIds = distinctPositiveIds(resultIds);
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("工单目标不能为空");
        }
        if (assigneeId == null || assigneeId <= 0) {
            throw new IllegalArgumentException("请选择安全运维工程师");
        }
        Long tenantId = currentTenantId();
        if (!baselineWorkorderMapper.userHasRole(assigneeId, SECURITY_OPERATOR, tenantId)) {
            throw new IllegalArgumentException("处理人必须是安全运维工程师");
        }
        List<BaselineResultEntity> results = baselineResultMapper.selectByIdsAndTenant(distinctIds, tenantId);
        List<Long> ruleIds = results.stream()
                .map(BaselineResultEntity::getRuleId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, BaselineRuleEntity> rulesById = ruleIds.isEmpty()
                ? Map.of()
                : baselineRuleMapper.selectByIds(ruleIds).stream()
                .collect(Collectors.toMap(BaselineRuleEntity::getId, Function.identity(), (a, b) -> a));
        Long creator = SecurityUtils.getCurrentUserId();
        String safeRemark = StringUtils.hasText(remark) ? remark.trim() : null;

        List<Long> ticketedIds = new ArrayList<>();
        for (BaselineResultEntity result : results) {
            BaselineHostResultItemDTO latest = null;
            try {
                latest = baselineQueryMapper.selectTaskHostResults(result.getTaskId(), result.getHostId(), tenantId).stream()
                        .filter(item -> result.getId().equals(item.getResultId()))
                        .findFirst()
                        .orElse(null);
            } catch (Exception ignored) {
                // 创建工单不依赖展示查询，失败时使用兜底标题。
            }
            BaselineRuleEntity rule = rulesById.get(result.getRuleId());
            BaselineWorkorderEntity workorder = new BaselineWorkorderEntity();
            workorder.setTenantId(tenantId);
            workorder.setHostId(result.getHostId());
            workorder.setRuleId(result.getRuleId());
            workorder.setResultId(result.getId());
            workorder.setTitle(buildTitle(result, latest, rule));
            workorder.setAdvice(buildAdvice(result, latest, rule, safeRemark));
            workorder.setAssigneeId(assigneeId);
            workorder.setPriority(normalizePriority(rule == null ? null : rule.getSeverity()));
            workorder.setStatus(STATUS_OPEN);
            workorder.setCloseRemark(null);
            workorder.setCreateBy(creator);
            baselineWorkorderMapper.insert(workorder);
            ticketedIds.add(result.getId());
        }
        if (!ticketedIds.isEmpty()) {
            baselineResultMapper.updateRemediationStatusByIdsAndTenant(ticketedIds, REMEDIATION_TICKETED, tenantId);
        }

        int success = ticketedIds.size();
        int failed = distinctIds.size() - success;
        BaselineActionResponseDTO response = new BaselineActionResponseDTO();
        response.setTotal(distinctIds.size());
        response.setSuccess(success);
        response.setFailed(failed);
        response.setMessage(failed == 0
                ? "已创建 " + success + " 个工单"
                : "成功 " + success + " 个，失败 " + failed + " 个");
        return response;
    }

    @Override
    public PageResult<BaselineWorkorderListItemDTO> list(Integer page, Integer size, String keyword, String status, String priority) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String safeKeyword = normalizeText(keyword);
        String safeStatus = normalizeEnum(status, List.of(STATUS_OPEN, STATUS_PROCESSING, STATUS_DONE));
        String safePriority = normalizeEnum(priority, List.of("LOW", "MEDIUM", "HIGH", "CRITICAL"));
        Long assigneeScope = currentAssigneeScope();
        Long tenantId = currentTenantId();
        long total = baselineWorkorderMapper.countPage(safeKeyword, safeStatus, safePriority, assigneeScope, tenantId);
        List<BaselineWorkorderListItemDTO> list = baselineWorkorderMapper.selectPage(
                safeKeyword, safeStatus, safePriority, assigneeScope, tenantId, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public BaselineWorkorderDetailDTO detail(Long id) {
        BaselineWorkorderDetailDTO detail = baselineWorkorderMapper.selectDetail(id, currentTenantId());
        if (detail == null) {
            throw new ResourceNotFoundException("工单不存在");
        }
        ensureReadable(detail.getAssigneeId());
        return detail;
    }

    @Override
    @Transactional
    public BaselineActionResponseDTO start(Long id) {
        BaselineWorkorderEntity entity = requireAccessibleEntity(id);
        if (!STATUS_OPEN.equals(entity.getStatus())) {
            throw new IllegalArgumentException("只有待处理工单可以开始处理");
        }
        int updated = baselineWorkorderMapper.markProcessing(id, currentTenantId());
        return actionResponse(1, updated, updated == 1 ? "工单已进入处理中" : "工单状态已变化，请刷新后重试");
    }

    @Override
    @Transactional
    public BaselineActionResponseDTO complete(Long id, String closeRemark) {
        BaselineWorkorderEntity entity = requireAccessibleEntity(id);
        if (!STATUS_PROCESSING.equals(entity.getStatus())) {
            throw new IllegalArgumentException("只有处理中工单可以完成");
        }
        String safeRemark = normalizeText(closeRemark);
        if (!StringUtils.hasText(safeRemark)) {
            throw new IllegalArgumentException("处理说明不能为空");
        }
        int updated = baselineWorkorderMapper.markDone(id, safeRemark, currentTenantId());
        return actionResponse(1, updated, updated == 1 ? "工单已完成" : "工单状态已变化，请刷新后重试");
    }

    @Override
    public BaselineActionResponseDTO recheck(Long id) {
        BaselineWorkorderEntity entity = requireAccessibleEntity(id);
        if (!STATUS_DONE.equals(entity.getStatus())) {
            throw new IllegalArgumentException("只有已完成工单可以重新检测");
        }
        BaselineTaskCreateRequestDTO request = new BaselineTaskCreateRequestDTO();
        request.setTaskName("工单复检-" + entity.getId() + "-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        request.setExecuteType("MANUAL");
        request.setHostIds(List.of(entity.getHostId()));
        request.setRuleIds(List.of(entity.getRuleId()));
        baselineTaskService.createAndDispatch(request);
        return actionResponse(1, 1, "已下发工单复检任务");
    }

    @Override
    public List<BaselineOperatorOptionDTO> operatorOptions() {
        return baselineWorkorderMapper.selectOperatorOptions(currentTenantId());
    }

    private BaselineWorkorderEntity requireAccessibleEntity(Long id) {
        BaselineWorkorderEntity entity = baselineWorkorderMapper.selectEntityById(id, currentTenantId());
        if (entity == null) {
            throw new ResourceNotFoundException("工单不存在");
        }
        ensureReadable(entity.getAssigneeId());
        return entity;
    }

    private void ensureReadable(Long assigneeId) {
        Long scope = currentAssigneeScope();
        if (scope != null && !scope.equals(assigneeId)) {
            throw new UnauthorizedException("无权访问其他工程师的工单");
        }
    }

    private Long currentAssigneeScope() {
        if (permissionChecker.isSuperAdmin() || hasRole("SECURITY_ADMIN")) {
            return null;
        }
        return SecurityUtils.getCurrentUserId();
    }

    private boolean hasRole(String roleCode) {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + roleCode).equals(authority.getAuthority()));
    }

    private BaselineActionResponseDTO actionResponse(int total, int success, String message) {
        BaselineActionResponseDTO response = new BaselineActionResponseDTO();
        response.setTotal(total);
        response.setSuccess(success);
        response.setFailed(total - success);
        response.setMessage(message);
        return response;
    }

    private String buildTitle(BaselineResultEntity result, BaselineHostResultItemDTO latest, BaselineRuleEntity rule) {
        String ruleName = rule == null ? null : rule.getRuleName();
        if (!StringUtils.hasText(ruleName) && latest != null) {
            ruleName = latest.getRuleName();
        }
        if (!StringUtils.hasText(ruleName)) {
            ruleName = "规则#" + result.getRuleId();
        }
        return "基线人工处置 - " + ruleName;
    }

    private String buildAdvice(BaselineResultEntity result, BaselineHostResultItemDTO latest, BaselineRuleEntity rule, String remark) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(remark)) {
            lines.add("派单说明：" + remark);
        }
        if (rule != null && StringUtils.hasText(rule.getDescription())) {
            lines.add("规则说明：" + rule.getDescription());
        }
        if (latest != null && StringUtils.hasText(latest.getCheckKey())) {
            lines.add("检测项：" + latest.getCheckKey());
        } else if (StringUtils.hasText(result.getCheckKey())) {
            lines.add("检测项：" + result.getCheckKey());
        }
        if (StringUtils.hasText(result.getExpectedValue())) {
            lines.add("期望值：" + result.getExpectedValue());
        }
        if (StringUtils.hasText(result.getActualValue())) {
            lines.add("实际值：" + result.getActualValue());
        }
        lines.add("请按基线规则要求完成人工整改，完成后在工单详情中填写处理说明并触发重新检测。");
        return String.join("\n", lines);
    }

    private String normalizePriority(String severity) {
        if (!StringUtils.hasText(severity)) {
            return "MEDIUM";
        }
        String value = severity.trim().toUpperCase();
        return switch (value) {
            case "LOW", "低", "低危" -> "LOW";
            case "HIGH", "高", "高危" -> "HIGH";
            case "CRITICAL", "严重", "危急", "严重风险" -> "CRITICAL";
            default -> "MEDIUM";
        };
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
            return 10;
        }
        return Math.min(size, 200);
    }

    private String normalizeText(String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private String normalizeEnum(String value, List<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return allowed.contains(normalized) ? normalized : null;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }
}
