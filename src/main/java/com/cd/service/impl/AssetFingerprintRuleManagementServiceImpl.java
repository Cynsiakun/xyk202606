package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.AssetFingerprintRuleManageRequestDTO;
import com.cd.entity.AssetFingerprintRuleEntity;
import com.cd.mapper.AssetFingerprintRuleMapper;
import com.cd.service.AssetFingerprintRuleManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AssetFingerprintRuleManagementServiceImpl implements AssetFingerprintRuleManagementService {

    private final AssetFingerprintRuleMapper assetFingerprintRuleMapper;

    @Override
    public PageResult<AssetFingerprintRuleEntity> list(Integer page, Integer size, String keyword,
                                                       String category, Integer port, Integer enabled) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 200);
        Integer safeEnabled = enabled == null ? null : (enabled == 1 ? 1 : 0);
        long total = assetFingerprintRuleMapper.countManagePage(trim(keyword), trim(category), port, safeEnabled);
        List<AssetFingerprintRuleEntity> list = assetFingerprintRuleMapper.selectManagePage(
                trim(keyword), trim(category), port, safeEnabled, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public AssetFingerprintRuleEntity detail(Long id) {
        AssetFingerprintRuleEntity entity = assetFingerprintRuleMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("端口资产规则不存在: id=" + id);
        }
        return entity;
    }

    @Override
    @Transactional
    public AssetFingerprintRuleEntity create(AssetFingerprintRuleManageRequestDTO request) {
        AssetFingerprintRuleEntity entity = new AssetFingerprintRuleEntity();
        entity.setRuleCode(requireText(request.getRuleCode(), "ruleCode不能为空"));
        ensureRuleCodeUnique(null, entity.getRuleCode());
        fillMutableFields(entity, request);
        assetFingerprintRuleMapper.insertManage(entity);
        return detail(entity.getId());
    }

    @Override
    @Transactional
    public AssetFingerprintRuleEntity update(Long id, AssetFingerprintRuleManageRequestDTO request) {
        AssetFingerprintRuleEntity entity = detail(id);
        String incomingRuleCode = requireText(request.getRuleCode(), "ruleCode不能为空");
        if (!incomingRuleCode.equals(entity.getRuleCode())) {
            throw new IllegalArgumentException("ruleCode不允许修改");
        }
        fillMutableFields(entity, request);
        entity.setId(id);
        assetFingerprintRuleMapper.updateManage(entity);
        return detail(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        detail(id);
        assetFingerprintRuleMapper.deleteById(id);
    }

    private void fillMutableFields(AssetFingerprintRuleEntity entity, AssetFingerprintRuleManageRequestDTO request) {
        entity.setName(requireText(request.getName(), "name不能为空"));
        entity.setCategory(requireText(request.getCategory(), "category不能为空").toLowerCase(Locale.ROOT));
        entity.setSubCategory(trim(request.getSubCategory()));
        entity.setProtocol(normalizeProtocol(request.getProtocol()));
        entity.setPort(request.getPort());
        entity.setBannerRegex(trim(request.getBannerRegex()));
        entity.setVendor(trim(request.getVendor()));
        entity.setProduct(requireText(request.getProduct(), "product不能为空"));
        entity.setVersionExpr(trim(request.getVersionExpr()));
        entity.setConfidence(request.getConfidence() == null ? 80 : request.getConfidence());
        entity.setDescription(trim(request.getDescription()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() != 0 ? 1 : 0);
        entity.setPriority(request.getPriority() == null ? 100 : request.getPriority());
    }

    private void ensureRuleCodeUnique(Long id, String ruleCode) {
        AssetFingerprintRuleEntity existing = assetFingerprintRuleMapper.selectByRuleCode(ruleCode);
        if (existing != null && (id == null || !id.equals(existing.getId()))) {
            throw new IllegalArgumentException("ruleCode已存在");
        }
    }

    private String normalizeProtocol(String protocol) {
        String value = requireText(protocol, "protocol不能为空").toLowerCase(Locale.ROOT);
        if (!"tcp".equals(value) && !"udp".equals(value)) {
            throw new IllegalArgumentException("protocol仅支持tcp/udp");
        }
        return value;
    }

    private String requireText(String value, String message) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
