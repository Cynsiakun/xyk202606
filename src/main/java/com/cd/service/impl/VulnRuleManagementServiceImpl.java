package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.VulnRuleCreateDTO;
import com.cd.dto.VulnRuleResponseDTO;
import com.cd.dto.VulnRuleUpdateDTO;
import com.cd.entity.VulnRuleEntity;
import com.cd.mapper.VulnRuleMapper;
import com.cd.service.VulnRuleCacheService;
import com.cd.service.VulnRuleManagementService;
import com.cd.util.CsvImportUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VulnRuleManagementServiceImpl implements VulnRuleManagementService {

    private static final Set<String> ALLOWED_SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final Set<String> ALLOWED_PRODUCT_TYPES = Set.of("os", "app", "service", "process");
    private static final Set<String> ALLOWED_VERIFY_TYPES = Set.of("VERSION", "EXISTENCE", "PROCESS_NAME");

    private final VulnRuleMapper vulnRuleMapper;
    private final VulnRuleCacheService vulnRuleCacheService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public VulnRuleResponseDTO create(VulnRuleCreateDTO dto) {
        validateRuleCodeUnique(null, dto.getRuleCode());
        VulnRuleEntity entity = new VulnRuleEntity();
        apply(entity, dto);
        entity.setEnabled(normalizeFlag(dto.getEnabled()));
        vulnRuleMapper.insert(entity);
        vulnRuleCacheService.refresh();
        return toResponse(vulnRuleMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    public VulnRuleResponseDTO update(Long id, VulnRuleUpdateDTO dto) {
        VulnRuleEntity entity = ensureExists(id);
        validateRuleCodeUnique(id, dto.getRuleCode());
        apply(entity, dto);
        entity.setEnabled(normalizeFlag(dto.getEnabled()));
        vulnRuleMapper.updateById(entity);
        vulnRuleCacheService.refresh();
        return toResponse(vulnRuleMapper.selectById(id));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        ensureExists(id);
        vulnRuleMapper.deleteById(id);
        vulnRuleCacheService.refresh();
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids不能为空");
        }
        vulnRuleMapper.deleteBatch(ids);
        vulnRuleCacheService.refresh();
    }

    @Override
    public VulnRuleResponseDTO getById(Long id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<VulnRuleResponseDTO> list(int page,
                                                int size,
                                                String ruleCode,
                                                String cveId,
                                                String productName,
                                                String severity,
                                                Integer enabled) {
        int offset = (page - 1) * size;
        String normalizedRuleCode = emptyToNull(ruleCode);
        String normalizedCveId = emptyToNull(cveId);
        String normalizedProductName = emptyToNull(productName);
        String normalizedSeverity = emptyToNull(severity);
        long total = vulnRuleMapper.countAll(normalizedRuleCode, normalizedCveId, normalizedProductName, normalizedSeverity, enabled);
        List<VulnRuleResponseDTO> list = vulnRuleMapper.selectPage(
                        offset, size, normalizedRuleCode, normalizedCveId, normalizedProductName, normalizedSeverity, enabled)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    @Transactional
    public CsvImportResultDTO importCsv(MultipartFile file) {
        CsvImportResultDTO result = CsvImportUtil.importCsv(file, this::mapCsvRecord, this::saveImportedRule);
        if (result.getSuccessCount() > 0) {
            vulnRuleCacheService.refresh();
        }
        return result;
    }

    private VulnRuleEntity mapCsvRecord(CSVRecord record) {
        VulnRuleEntity entity = new VulnRuleEntity();
        entity.setRuleCode(requireField(record, "rule_code", "ruleCode"));
        entity.setCveId(emptyToNull(CsvImportUtil.getValue(record, "cve_id", "cveId")));
        entity.setCategory(emptyToNull(CsvImportUtil.getValue(record, "category")));
        entity.setProductType(normalizeProductType(requireField(record, "product_type", "productType")));
        entity.setProductName(requireField(record, "product_name", "productName"));
        entity.setMatchType(requireField(record, "match_type", "matchType"));
        entity.setAffectedVersionExpr(emptyToNull(CsvImportUtil.getValue(record, "affected_version_expr", "affectedVersionExpr")));
        entity.setSeverity(normalizeSeverity(requireField(record, "severity")));
        entity.setTitle(requireField(record, "title"));
        entity.setDescription(emptyToNull(CsvImportUtil.getValue(record, "description")));
        entity.setSuggestion(emptyToNull(CsvImportUtil.getValue(record, "suggestion")));
        entity.setVerifyType(normalizeVerifyType(requireField(record, "verify_type", "verifyType")));
        entity.setVerifyRule(normalizeVerifyRule(emptyToNull(CsvImportUtil.getValue(record, "verify_rule", "verifyRule"))));
        entity.setEnabled(parseEnabled(CsvImportUtil.getValue(record, "enabled")));
        return entity;
    }

    private void saveImportedRule(VulnRuleEntity imported, CsvImportResultDTO result) {
        VulnRuleEntity existing = vulnRuleMapper.selectByRuleCode(imported.getRuleCode());
        if (existing == null) {
            vulnRuleMapper.insert(imported);
            result.incrementInserted();
            return;
        }
        imported.setId(existing.getId());
        vulnRuleMapper.updateById(imported);
        result.incrementUpdated();
    }

    private void apply(VulnRuleEntity entity, VulnRuleCreateDTO dto) {
        entity.setRuleCode(dto.getRuleCode().trim());
        entity.setCveId(emptyToNull(dto.getCveId()));
        entity.setCategory(emptyToNull(dto.getCategory()));
        entity.setSeverity(normalizeSeverity(dto.getSeverity()));
        entity.setTitle(dto.getTitle().trim());
        entity.setDescription(emptyToNull(dto.getDescription()));
        entity.setSuggestion(emptyToNull(dto.getSuggestion()));
        entity.setProductType(normalizeProductType(dto.getProductType()));
        entity.setProductName(dto.getProductName().trim());
        entity.setMatchType(dto.getMatchType().trim());
        entity.setAffectedVersionExpr(emptyToNull(dto.getAffectedVersionExpr()));
        entity.setVerifyType(normalizeVerifyType(dto.getVerifyType()));
        entity.setVerifyRule(normalizeVerifyRule(dto.getVerifyRule()));
    }

    private void apply(VulnRuleEntity entity, VulnRuleUpdateDTO dto) {
        entity.setRuleCode(dto.getRuleCode().trim());
        entity.setCveId(emptyToNull(dto.getCveId()));
        entity.setCategory(emptyToNull(dto.getCategory()));
        entity.setSeverity(normalizeSeverity(dto.getSeverity()));
        entity.setTitle(dto.getTitle().trim());
        entity.setDescription(emptyToNull(dto.getDescription()));
        entity.setSuggestion(emptyToNull(dto.getSuggestion()));
        entity.setProductType(normalizeProductType(dto.getProductType()));
        entity.setProductName(dto.getProductName().trim());
        entity.setMatchType(dto.getMatchType().trim());
        entity.setAffectedVersionExpr(emptyToNull(dto.getAffectedVersionExpr()));
        entity.setVerifyType(normalizeVerifyType(dto.getVerifyType()));
        entity.setVerifyRule(normalizeVerifyRule(dto.getVerifyRule()));
    }

    private void validateRuleCodeUnique(Long id, String ruleCode) {
        VulnRuleEntity entity = vulnRuleMapper.selectByRuleCode(ruleCode == null ? null : ruleCode.trim());
        if (entity != null && !entity.getId().equals(id)) {
            throw new IllegalArgumentException("规则编码已存在");
        }
    }

    private String normalizeVerifyRule(String verifyRule) {
        if (!StringUtils.hasText(verifyRule)) {
            return null;
        }
        String trimmed = verifyRule.trim();
        if (looksLikeJson(trimmed)) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(objectMapper.readTree(trimmed));
            } catch (Exception ex) {
                throw new IllegalArgumentException("verify_rule 看起来像 JSON，但格式不合法");
            }
        }
        return trimmed;
    }

    private boolean looksLikeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private String normalizeSeverity(String severity) {
        String value = requireText(severity, "severity").toUpperCase(Locale.ROOT);
        if (!ALLOWED_SEVERITIES.contains(value)) {
            throw new IllegalArgumentException("severity 仅支持 CRITICAL/HIGH/MEDIUM/LOW");
        }
        return value;
    }

    private String normalizeProductType(String productType) {
        String value = requireText(productType, "product_type").toLowerCase(Locale.ROOT);
        if (!ALLOWED_PRODUCT_TYPES.contains(value)) {
            throw new IllegalArgumentException("product_type 仅支持 os/app/service/process");
        }
        return value;
    }

    private String normalizeVerifyType(String verifyType) {
        String value = requireText(verifyType, "verify_type").toUpperCase(Locale.ROOT);
        if (!ALLOWED_VERIFY_TYPES.contains(value)) {
            throw new IllegalArgumentException("verify_type 仅支持 VERSION/EXISTENCE/PROCESS_NAME");
        }
        return value;
    }

    private int parseEnabled(String enabledText) {
        if (!StringUtils.hasText(enabledText)) {
            return 1;
        }
        String normalized = enabledText.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "y".equals(normalized)) {
            return 1;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized) || "n".equals(normalized)) {
            return 0;
        }
        throw new IllegalArgumentException("enabled 仅支持 1/0/true/false");
    }

    private int normalizeFlag(Integer enabled) {
        return enabled != null && enabled == 0 ? 0 : 1;
    }

    private String requireField(CSVRecord record, String... headerNames) {
        String value = CsvImportUtil.getValue(record, headerNames);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("必填字段缺失: " + headerNames[0]);
        }
        return value.trim();
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private VulnRuleEntity ensureExists(Long id) {
        VulnRuleEntity entity = vulnRuleMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("规则不存在，id=" + id);
        }
        return entity;
    }

    private VulnRuleResponseDTO toResponse(VulnRuleEntity entity) {
        VulnRuleResponseDTO dto = new VulnRuleResponseDTO();
        dto.setId(entity.getId());
        dto.setRuleCode(entity.getRuleCode());
        dto.setCveId(entity.getCveId());
        dto.setCategory(entity.getCategory());
        dto.setProductType(entity.getProductType());
        dto.setProductName(entity.getProductName());
        dto.setMatchType(entity.getMatchType());
        dto.setAffectedVersionExpr(entity.getAffectedVersionExpr());
        dto.setSeverity(entity.getSeverity());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setSuggestion(entity.getSuggestion());
        dto.setVerifyType(entity.getVerifyType());
        dto.setVerifyRule(entity.getVerifyRule());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
