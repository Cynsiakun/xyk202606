package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.HostCreateDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.dto.HostUpdateDTO;
import com.cd.entity.HostEntity;
import com.cd.mapper.HostMapper;
import com.cd.service.HostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HostServiceImpl implements HostService {

    private final HostMapper hostMapper;

    @Override
    public HostResponseDTO create(HostCreateDTO dto) {
        validateMacUnique(null, dto.getMacAddress());
        HostEntity entity = new HostEntity();
        entity.setHostname(dto.getHostname());
        entity.setIpv4(dto.getIpv4());
        entity.setMacAddress(dto.getMacAddress());
        entity.setOsName(dto.getOsName());
        entity.setOsVersion(dto.getOsVersion());
        entity.setOsArch(dto.getOsArch());
        entity.setOsRelease(dto.getOsRelease());
        entity.setCpuModel(dto.getCpuModel());
        entity.setCpuPhysicalCores(dto.getCpuPhysicalCores());
        entity.setCpuLogicalCores(dto.getCpuLogicalCores());
        entity.setMemTotal(dto.getMemTotal());
        entity.setMemUsed(dto.getMemUsed());
        entity.setMemAvailable(dto.getMemAvailable());
        entity.setMemUsage(dto.getMemUsage());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        hostMapper.insert(entity);
        return toResponse(hostMapper.selectById(entity.getId()));
    }

    @Override
    public HostResponseDTO update(Long id, HostUpdateDTO dto) {
        HostEntity existing = ensureExists(id);
        validateMacUnique(id, dto.getMacAddress());
        existing.setHostname(dto.getHostname());
        existing.setIpv4(dto.getIpv4());
        existing.setMacAddress(dto.getMacAddress());
        existing.setOsName(dto.getOsName());
        existing.setOsVersion(dto.getOsVersion());
        existing.setOsArch(dto.getOsArch());
        existing.setOsRelease(dto.getOsRelease());
        existing.setCpuModel(dto.getCpuModel());
        existing.setCpuPhysicalCores(dto.getCpuPhysicalCores());
        existing.setCpuLogicalCores(dto.getCpuLogicalCores());
        existing.setMemTotal(dto.getMemTotal());
        existing.setMemUsed(dto.getMemUsed());
        existing.setMemAvailable(dto.getMemAvailable());
        existing.setMemUsage(dto.getMemUsage());
        existing.setStatus(dto.getStatus() == null ? existing.getStatus() : dto.getStatus());
        hostMapper.updateById(existing);
        return toResponse(hostMapper.selectById(id));
    }

    @Override
    public void deleteById(Long id) {
        ensureExists(id);
        hostMapper.deleteById(id);
    }

    @Override
    public HostResponseDTO getById(Long id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<HostResponseDTO> list(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        String normalizedKeyword = emptyToNull(keyword);
        long total = hostMapper.countAll(normalizedKeyword);
        List<HostResponseDTO> list = hostMapper.selectPage(offset, size, normalizedKeyword)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    public void saveOrUpdateFromMessage(HostEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        hostMapper.upsertByMac(entity);
    }

    private HostEntity ensureExists(Long id) {
        HostEntity entity = hostMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("记录不存在: id=" + id);
        }
        return entity;
    }

    private void validateMacUnique(Long id, String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            return;
        }
        HostEntity hostByMac = hostMapper.selectByMac(macAddress);
        if (hostByMac != null && !hostByMac.getId().equals(id)) {
            throw new IllegalArgumentException("MAC地址已存在");
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private HostResponseDTO toResponse(HostEntity entity) {
        HostResponseDTO dto = new HostResponseDTO();
        dto.setId(entity.getId());
        dto.setHostname(entity.getHostname());
        dto.setIpv4(entity.getIpv4());
        dto.setMacAddress(entity.getMacAddress());
        dto.setOsName(entity.getOsName());
        dto.setOsVersion(entity.getOsVersion());
        dto.setOsArch(entity.getOsArch());
        dto.setOsRelease(entity.getOsRelease());
        dto.setCpuModel(entity.getCpuModel());
        dto.setCpuPhysicalCores(entity.getCpuPhysicalCores());
        dto.setCpuLogicalCores(entity.getCpuLogicalCores());
        dto.setMemTotal(entity.getMemTotal());
        dto.setMemUsed(entity.getMemUsed());
        dto.setMemAvailable(entity.getMemAvailable());
        dto.setMemUsage(entity.getMemUsage());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
