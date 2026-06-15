package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.BaselineRuleManageRequestDTO;
import com.cd.entity.BaselineRuleEntity;
import com.cd.mapper.BaselineRuleMapper;
import com.cd.service.BaselineRuleManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BaselineRuleManagementServiceImpl implements BaselineRuleManagementService {

    private static final Set<String> CHECK_METHODS = Set.of("REGISTRY", "SERVICE", "POWERSHELL", "WMI");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES = Set.of("PUBLISHED", "DRAFT", "ARCHIVED");

    private final BaselineRuleMapper baselineRuleMapper;

    @Override
    public PageResult<BaselineRuleEntity> list(Integer page, Integer size, String keyword,
                                               String category, String severity, String status, Integer enabled) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 200);
        String safeKeyword = normalizeText(keyword);
        String safeCategory = normalizeText(category);
        String safeSeverity = normalizeSeverity(severity, false);
        String safeStatus = normalizeStatus(status, false);
        Integer safeEnabled = enabled == null ? null : (enabled == 1 ? 1 : 0);
        long total = baselineRuleMapper.countManagePage(safeKeyword, safeCategory, safeSeverity, safeStatus, safeEnabled);
        List<BaselineRuleEntity> list = baselineRuleMapper.selectManagePage(
                safeKeyword, safeCategory, safeSeverity, safeStatus, safeEnabled, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public BaselineRuleEntity detail(Long id) {
        BaselineRuleEntity entity = baselineRuleMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("规则不存在");
        }
        return entity;
    }

    @Override
    @Transactional
    public BaselineRuleEntity create(BaselineRuleManageRequestDTO request) {
        BaselineRuleEntity entity = new BaselineRuleEntity();
        entity.setRuleCode(requireText(request.getRuleCode(), "规则编码不能为空"));
        ensureRuleCodeUnique(null, entity.getRuleCode());
        fillMutableFields(entity, request);
        entity.setVersion(1);
        baselineRuleMapper.insertManage(entity);
        return detail(entity.getId());
    }

    @Override
    @Transactional
    public BaselineRuleEntity update(Long id, BaselineRuleManageRequestDTO request) {
        BaselineRuleEntity entity = detail(id);
        String incomingCode = normalizeText(request.getRuleCode());
        if (StringUtils.hasText(incomingCode) && !incomingCode.equals(entity.getRuleCode())) {
            throw new IllegalArgumentException("规则编码不允许修改");
        }
        fillMutableFields(entity, request);
        baselineRuleMapper.updateManage(entity);
        return detail(id);
    }

    @Override
    @Transactional
    public void archive(Long id) {
        detail(id);
        baselineRuleMapper.archiveById(id);
    }

    @Override
    @Transactional
    public BaselineRuleEntity setEnabled(Long id, Integer enabled) {
        detail(id);
        int value = enabled != null && enabled == 1 ? 1 : 0;
        baselineRuleMapper.updateEnabled(id, value);
        return detail(id);
    }

    private void fillMutableFields(BaselineRuleEntity entity, BaselineRuleManageRequestDTO request) {
        entity.setRuleName(requireText(request.getRuleName(), "规则名称不能为空"));
        entity.setCategory(requireText(request.getCategory(), "分类不能为空"));
        entity.setDescription(normalizeText(request.getDescription()));
        entity.setSeverity(normalizeSeverity(request.getSeverity(), true));
        entity.setScore(request.getScore() == null || request.getScore() < 1 ? 1 : request.getScore());
        entity.setOsType(requireText(request.getOsType(), "适用系统不能为空"));
        entity.setCheckMethod(normalizeCheckMethod(request.getCheckMethod()));
        entity.setCheckScript(requireText(request.getCheckScript(), "检测脚本不能为空"));
        entity.setRemediationType(requireText(request.getRemediationType(), "修复方式不能为空").toUpperCase());
        entity.setRemediationScript(normalizeText(request.getRemediationScript()));
        entity.setIsMandatory(request.getIsMandatory() == null || request.getIsMandatory() != 0 ? 1 : 0);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() != 0 ? 1 : 0);
        entity.setStatus(normalizeStatus(request.getStatus(), true));
    }

    private void ensureRuleCodeUnique(Long id, String ruleCode) {
        BaselineRuleEntity existing = baselineRuleMapper.selectByRuleCode(ruleCode);
        if (existing != null && (id == null || !id.equals(existing.getId()))) {
            throw new IllegalArgumentException("规则编码已存在");
        }
    }

    private String normalizeCheckMethod(String value) {
        String normalized = requireText(value, "检测方式不能为空").toUpperCase();
        if (!CHECK_METHODS.contains(normalized)) {
            throw new IllegalArgumentException("检测方式仅支持 REGISTRY/SERVICE/POWERSHELL/WMI");
        }
        return normalized;
    }

    private String normalizeSeverity(String value, boolean required) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            if (required) {
                throw new IllegalArgumentException("风险等级不能为空");
            }
            return null;
        }
        normalized = normalized.toUpperCase();
        if (!SEVERITIES.contains(normalized)) {
            throw new IllegalArgumentException("风险等级仅支持 LOW/MEDIUM/HIGH/CRITICAL");
        }
        return normalized;
    }

    private String normalizeStatus(String value, boolean required) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            if (required) {
                return "PUBLISHED";
            }
            return null;
        }
        normalized = normalized.toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("规则状态仅支持 PUBLISHED/DRAFT/ARCHIVED");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}
