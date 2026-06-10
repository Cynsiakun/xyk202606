package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.PatchCveMapCreateDTO;
import com.cd.dto.PatchCveMapResponseDTO;
import com.cd.dto.PatchCveMapUpdateDTO;
import com.cd.entity.PatchCveMapEntity;
import com.cd.mapper.PatchCveMapMapper;
import com.cd.service.PatchCveMapService;
import com.cd.util.CsvImportUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PatchCveMapServiceImpl implements PatchCveMapService {

    private static final Set<String> ALLOWED_SEVERITIES = Set.of("critical", "high", "medium", "low");
    private static final Set<String> ALLOWED_EXPLOIT_STATUS = Set.of("none", "poc", "active");

    private final PatchCveMapMapper patchCveMapMapper;

    @Override
    @Transactional
    public PatchCveMapResponseDTO create(PatchCveMapCreateDTO dto) {
        PatchCveMapEntity entity = new PatchCveMapEntity();
        apply(entity, dto);
        entity.setKevFlag(normalizeFlag(dto.getKevFlag()));
        patchCveMapMapper.insert(entity);
        return toResponse(patchCveMapMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional
    public PatchCveMapResponseDTO update(Long id, PatchCveMapUpdateDTO dto) {
        PatchCveMapEntity entity = ensureExists(id);
        apply(entity, dto);
        entity.setKevFlag(normalizeFlag(dto.getKevFlag()));
        patchCveMapMapper.updateById(entity);
        return toResponse(patchCveMapMapper.selectById(id));
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        ensureExists(id);
        patchCveMapMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids不能为空");
        }
        patchCveMapMapper.deleteBatch(ids);
    }

    @Override
    public PatchCveMapResponseDTO getById(Long id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<PatchCveMapResponseDTO> list(int page, int size, String keyword, String severity, Integer kevFlag) {
        int offset = (page - 1) * size;
        String normalizedKeyword = emptyToNull(keyword);
        String normalizedSeverity = emptyToNull(severity);
        long total = patchCveMapMapper.countAll(normalizedKeyword, normalizedSeverity, kevFlag);
        List<PatchCveMapResponseDTO> records = patchCveMapMapper.selectPage(offset, size, normalizedKeyword, normalizedSeverity, kevFlag)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public CsvImportResultDTO importCsv(MultipartFile file) {
        return CsvImportUtil.importCsv(file, this::mapCsvRecord, this::saveImportedRecord);
    }

    private PatchCveMapEntity mapCsvRecord(CSVRecord record) {
        PatchCveMapEntity entity = new PatchCveMapEntity();
        entity.setPatchId(requireField(record, "patch_id", "patchId"));
        entity.setCveId(requireField(record, "cve_id", "cveId"));
        entity.setVendor(emptyToNull(CsvImportUtil.getValue(record, "vendor")));
        entity.setProduct(emptyToNull(CsvImportUtil.getValue(record, "product")));
        entity.setAffectedVersionRange(emptyToNull(CsvImportUtil.getValue(record, "affected_version_range", "affectedVersionRange")));
        entity.setFixedVersion(emptyToNull(CsvImportUtil.getValue(record, "fixed_version", "fixedVersion")));
        entity.setFixType(emptyToNull(CsvImportUtil.getValue(record, "fix_type", "fixType")));
        entity.setExploitStatus(normalizeExploitStatus(CsvImportUtil.getValue(record, "exploit_status", "exploitStatus")));
        entity.setKevFlag(parseKevFlag(CsvImportUtil.getValue(record, "kev_flag", "kevFlag")));
        entity.setCvssScore(parseCvssScore(CsvImportUtil.getValue(record, "cvss_score", "cvssScore")));
        entity.setSeverity(normalizeSeverity(CsvImportUtil.getValue(record, "severity")));
        entity.setReferenceUrl(emptyToNull(CsvImportUtil.getValue(record, "reference_url", "referenceUrl")));
        return entity;
    }

    private void saveImportedRecord(PatchCveMapEntity imported, CsvImportResultDTO result) {
        PatchCveMapEntity existing = patchCveMapMapper.selectByPatchIdAndCveId(imported.getPatchId(), imported.getCveId());
        if (existing == null) {
            patchCveMapMapper.insert(imported);
            result.incrementInserted();
            return;
        }
        imported.setId(existing.getId());
        patchCveMapMapper.updateById(imported);
        result.incrementUpdated();
    }

    private void apply(PatchCveMapEntity entity, PatchCveMapCreateDTO dto) {
        entity.setPatchId(dto.getPatchId());
        entity.setCveId(dto.getCveId());
        entity.setVendor(dto.getVendor());
        entity.setProduct(dto.getProduct());
        entity.setAffectedVersionRange(dto.getAffectedVersionRange());
        entity.setFixedVersion(dto.getFixedVersion());
        entity.setFixType(dto.getFixType());
        entity.setExploitStatus(normalizeExploitStatus(dto.getExploitStatus()));
        entity.setCvssScore(dto.getCvssScore());
        entity.setSeverity(normalizeSeverity(dto.getSeverity()));
        entity.setReferenceUrl(dto.getReferenceUrl());
    }

    private void apply(PatchCveMapEntity entity, PatchCveMapUpdateDTO dto) {
        entity.setPatchId(dto.getPatchId());
        entity.setCveId(dto.getCveId());
        entity.setVendor(dto.getVendor());
        entity.setProduct(dto.getProduct());
        entity.setAffectedVersionRange(dto.getAffectedVersionRange());
        entity.setFixedVersion(dto.getFixedVersion());
        entity.setFixType(dto.getFixType());
        entity.setExploitStatus(normalizeExploitStatus(dto.getExploitStatus()));
        entity.setCvssScore(dto.getCvssScore());
        entity.setSeverity(normalizeSeverity(dto.getSeverity()));
        entity.setReferenceUrl(dto.getReferenceUrl());
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return null;
        }
        String value = severity.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SEVERITIES.contains(value)) {
            throw new IllegalArgumentException("severity 仅支持 critical/high/medium/low");
        }
        return value;
    }

    private String normalizeExploitStatus(String exploitStatus) {
        if (!StringUtils.hasText(exploitStatus)) {
            return null;
        }
        String value = exploitStatus.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXPLOIT_STATUS.contains(value)) {
            throw new IllegalArgumentException("exploit_status 仅支持 none/poc/active");
        }
        return value;
    }

    private BigDecimal parseCvssScore(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            BigDecimal score = new BigDecimal(value.trim());
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
                throw new IllegalArgumentException("cvss_score 必须在 0 到 10 之间");
            }
            return score;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("cvss_score 必须是数字");
        }
    }

    private int parseKevFlag(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "y".equals(normalized)) {
            return 1;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized) || "n".equals(normalized)) {
            return 0;
        }
        throw new IllegalArgumentException("kev_flag 仅支持 1/0/true/false");
    }

    private int normalizeFlag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private String requireField(CSVRecord record, String... headerNames) {
        String value = CsvImportUtil.getValue(record, headerNames);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("必填字段缺失: " + headerNames[0]);
        }
        return value.trim();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private PatchCveMapEntity ensureExists(Long id) {
        PatchCveMapEntity entity = patchCveMapMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("记录不存在，id=" + id);
        }
        return entity;
    }

    private PatchCveMapResponseDTO toResponse(PatchCveMapEntity entity) {
        PatchCveMapResponseDTO dto = new PatchCveMapResponseDTO();
        dto.setId(entity.getId());
        dto.setPatchId(entity.getPatchId());
        dto.setCveId(entity.getCveId());
        dto.setVendor(entity.getVendor());
        dto.setProduct(entity.getProduct());
        dto.setAffectedVersionRange(entity.getAffectedVersionRange());
        dto.setFixedVersion(entity.getFixedVersion());
        dto.setFixType(entity.getFixType());
        dto.setExploitStatus(entity.getExploitStatus());
        dto.setKevFlag(entity.getKevFlag());
        dto.setCvssScore(entity.getCvssScore());
        dto.setSeverity(entity.getSeverity());
        dto.setReferenceUrl(entity.getReferenceUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
