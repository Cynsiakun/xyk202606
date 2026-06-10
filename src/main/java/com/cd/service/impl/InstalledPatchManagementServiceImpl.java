package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.InstalledPatchCreateDTO;
import com.cd.dto.InstalledPatchResponseDTO;
import com.cd.dto.InstalledPatchUpdateDTO;
import com.cd.entity.InstalledPatchEntity;
import com.cd.mapper.InstalledPatchManagementMapper;
import com.cd.service.InstalledPatchManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InstalledPatchManagementServiceImpl implements InstalledPatchManagementService {

    private final InstalledPatchManagementMapper installedPatchManagementMapper;

    @Override
    public InstalledPatchResponseDTO create(InstalledPatchCreateDTO dto) {
        InstalledPatchEntity entity = new InstalledPatchEntity();
        apply(entity, dto);
        entity.setRebootRequired(normalizeFlag(dto.getRebootRequired()));
        entity.setIsSecurityPatch(normalizeFlag(dto.getIsSecurityPatch()));
        installedPatchManagementMapper.insert(entity);
        return toResponse(installedPatchManagementMapper.selectById(entity.getId()));
    }

    @Override
    public InstalledPatchResponseDTO update(Long id, InstalledPatchUpdateDTO dto) {
        InstalledPatchEntity entity = ensureExists(id);
        apply(entity, dto);
        entity.setRebootRequired(normalizeFlag(dto.getRebootRequired()));
        entity.setIsSecurityPatch(normalizeFlag(dto.getIsSecurityPatch()));
        installedPatchManagementMapper.updateById(entity);
        return toResponse(installedPatchManagementMapper.selectById(id));
    }

    @Override
    public void deleteById(Long id) {
        ensureExists(id);
        installedPatchManagementMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        installedPatchManagementMapper.deleteBatch(ids);
    }

    @Override
    public InstalledPatchResponseDTO getById(Long id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<InstalledPatchResponseDTO> list(int page, int size, String keyword, String installStatus) {
        int offset = (page - 1) * size;
        String normalizedKeyword = emptyToNull(keyword);
        String normalizedInstallStatus = emptyToNull(installStatus);
        long total = installedPatchManagementMapper.countAll(normalizedKeyword, normalizedInstallStatus);
        List<InstalledPatchResponseDTO> list = installedPatchManagementMapper.selectPage(
                        offset, size, normalizedKeyword, normalizedInstallStatus)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    private void apply(InstalledPatchEntity entity, InstalledPatchCreateDTO dto) {
        entity.setHostId(dto.getHostId());
        entity.setPatchId(dto.getPatchId());
        entity.setPatchType(dto.getPatchType());
        entity.setProductName(dto.getProductName());
        entity.setProductVersion(dto.getProductVersion());
        entity.setInstallTime(dto.getInstallTime());
        entity.setInstallStatus(dto.getInstallStatus());
        entity.setSource(dto.getSource());
        entity.setSignatureStatus(dto.getSignatureStatus());
        entity.setSupersededBy(dto.getSupersededBy());
        entity.setRawData(dto.getRawData());
        entity.setScanTime(dto.getScanTime());
    }

    private void apply(InstalledPatchEntity entity, InstalledPatchUpdateDTO dto) {
        entity.setHostId(dto.getHostId());
        entity.setPatchId(dto.getPatchId());
        entity.setPatchType(dto.getPatchType());
        entity.setProductName(dto.getProductName());
        entity.setProductVersion(dto.getProductVersion());
        entity.setInstallTime(dto.getInstallTime());
        entity.setInstallStatus(dto.getInstallStatus());
        entity.setSource(dto.getSource());
        entity.setSignatureStatus(dto.getSignatureStatus());
        entity.setSupersededBy(dto.getSupersededBy());
        entity.setRawData(dto.getRawData());
        entity.setScanTime(dto.getScanTime());
    }

    private int normalizeFlag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private InstalledPatchEntity ensureExists(Long id) {
        InstalledPatchEntity entity = installedPatchManagementMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("记录不存在 id=" + id);
        }
        return entity;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private InstalledPatchResponseDTO toResponse(InstalledPatchEntity entity) {
        InstalledPatchResponseDTO dto = new InstalledPatchResponseDTO();
        dto.setId(entity.getId());
        dto.setHostId(entity.getHostId());
        dto.setPatchId(entity.getPatchId());
        dto.setPatchType(entity.getPatchType());
        dto.setProductName(entity.getProductName());
        dto.setProductVersion(entity.getProductVersion());
        dto.setInstallTime(entity.getInstallTime());
        dto.setInstallStatus(entity.getInstallStatus());
        dto.setSource(entity.getSource());
        dto.setSignatureStatus(entity.getSignatureStatus());
        dto.setRebootRequired(entity.getRebootRequired());
        dto.setSupersededBy(entity.getSupersededBy());
        dto.setIsSecurityPatch(entity.getIsSecurityPatch());
        dto.setRawData(entity.getRawData());
        dto.setScanTime(entity.getScanTime());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
