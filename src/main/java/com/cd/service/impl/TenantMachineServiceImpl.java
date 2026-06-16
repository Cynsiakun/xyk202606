package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.LicenseAccessDeniedException;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.SecurityUtils;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.ClientMachineValidateDTO;
import com.cd.dto.ClientMachineValidateResponseDTO;
import com.cd.dto.TenantMachineCreateDTO;
import com.cd.dto.TenantMachineResponseDTO;
import com.cd.dto.TenantMachineUpdateDTO;
import com.cd.entity.LicenseEntity;
import com.cd.entity.TenantEntity;
import com.cd.entity.TenantMachineEntity;
import com.cd.mapper.LicenseMapper;
import com.cd.mapper.TenantMachineMapper;
import com.cd.mapper.TenantMapper;
import com.cd.service.TenantMachineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TenantMachineServiceImpl implements TenantMachineService {

    private final TenantMachineMapper tenantMachineMapper;
    private final TenantMapper tenantMapper;
    private final LicenseMapper licenseMapper;
    private final com.cd.common.license.LicenseGuard licenseGuard;

    @Override
    public PageResult<TenantMachineResponseDTO> list(int page, int size, String keyword) {
        Long tenantId = currentTenantId();
        int offset = (page - 1) * size;
        long total = tenantMachineMapper.countAllByTenant(trim(keyword), tenantId);
        List<TenantMachineResponseDTO> list = tenantMachineMapper.selectPageByTenant(offset, size, trim(keyword), tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    public TenantMachineResponseDTO create(TenantMachineCreateDTO dto) {
        Long tenantId = currentTenantId();
        requireMachineOrMac(dto.getMachineId(), dto.getMacAddress());
        ensureUnique(null, dto.getMachineId(), dto.getMacAddress());
        if (dto.getStatus() == null || dto.getStatus() == 1) {
            requireMachineQuota(tenantId, 1);
        }

        TenantMachineEntity entity = new TenantMachineEntity();
        entity.setTenantId(tenantId);
        entity.setMachineId(trim(dto.getMachineId()));
        entity.setMacAddress(normalizeMac(dto.getMacAddress()));
        entity.setHostName(trim(dto.getHostName()));
        entity.setRemark(trim(dto.getRemark()));
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setCreatedBy(SecurityUtils.getCurrentUserId());
        tenantMachineMapper.insert(entity);
        return toResponse(tenantMachineMapper.selectByIdAndTenant(entity.getId(), tenantId));
    }

    @Override
    public TenantMachineResponseDTO update(Long id, TenantMachineUpdateDTO dto) {
        Long tenantId = currentTenantId();
        TenantMachineEntity existing = ensureExists(id, tenantId);
        requireMachineOrMac(dto.getMachineId(), dto.getMacAddress());
        ensureUnique(id, dto.getMachineId(), dto.getMacAddress());
        int nextStatus = dto.getStatus() == null ? existing.getStatus() : dto.getStatus();
        if ((existing.getStatus() == null || existing.getStatus() != 1) && nextStatus == 1) {
            requireMachineQuota(tenantId, 1);
        }

        existing.setMachineId(trim(dto.getMachineId()));
        existing.setMacAddress(normalizeMac(dto.getMacAddress()));
        existing.setHostName(trim(dto.getHostName()));
        existing.setRemark(trim(dto.getRemark()));
        existing.setStatus(nextStatus);
        tenantMachineMapper.updateByIdAndTenant(existing);
        return toResponse(tenantMachineMapper.selectByIdAndTenant(id, tenantId));
    }

    @Override
    public void delete(Long id) {
        Long tenantId = currentTenantId();
        ensureExists(id, tenantId);
        tenantMachineMapper.deleteByIdAndTenant(id, tenantId);
    }

    @Override
    public ClientMachineValidateResponseDTO validate(ClientMachineValidateDTO dto) {
        String machineId = trim(dto.getMachineId());
        String macAddress = normalizeMac(dto.getMacAddress());
        requireMachineOrMac(machineId, macAddress);

        TenantMachineEntity machine = tenantMachineMapper.selectActiveByMachineOrMac(machineId, macAddress);
        if (machine == null) {
            return deny("MACHINE_NOT_AUTHORIZED");
        }
        TenantEntity tenant = tenantMapper.selectById(machine.getTenantId());
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
            return deny("TENANT_DISABLED");
        }
        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(machine.getTenantId());
        if (license == null) {
            return deny("NO_EFFECTIVE_LICENSE");
        }

        ClientMachineValidateResponseDTO response = new ClientMachineValidateResponseDTO();
        response.setAllowed(true);
        response.setAuthorized(true);
        response.setReason("OK");
        response.setTenantId(machine.getTenantId());
        response.setTenantName(tenant.getName());
        response.setEdition(license.getEdition());
        response.setHostLimit(licenseGuard.effectiveHostLimit(license));
        response.setExpireTime(license.getExpireTime());
        response.setFeatureFlags(licenseGuard.featureNames(license.getEdition()));
        return response;
    }

    private TenantMachineEntity ensureExists(Long id, Long tenantId) {
        TenantMachineEntity entity = tenantMachineMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            throw new ResourceNotFoundException("Machine not found: id=" + id);
        }
        return entity;
    }

    private void requireMachineQuota(Long tenantId, int increment) {
        if (tenantId == 0L) {
            return;
        }
        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(tenantId);
        if (license == null) {
            throw new LicenseAccessDeniedException("No effective License for current tenant");
        }
        int limit = licenseGuard.effectiveHostLimit(license);
        if (limit == 0) {
            return;
        }
        long current = tenantMachineMapper.countEnabledByTenant(tenantId);
        if (current + Math.max(1, increment) > limit) {
            throw new LicenseAccessDeniedException("Host quota exceeded");
        }
    }

    private void ensureUnique(Long id, String machineId, String macAddress) {
        String normalizedMachineId = trim(machineId);
        if (normalizedMachineId != null) {
            TenantMachineEntity existing = tenantMachineMapper.selectByMachineId(normalizedMachineId);
            if (existing != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("machineId already exists");
            }
        }
        String normalizedMac = normalizeMac(macAddress);
        if (normalizedMac != null) {
            TenantMachineEntity existing = tenantMachineMapper.selectByMacAddress(normalizedMac);
            if (existing != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("macAddress already exists");
            }
        }
    }

    private void requireMachineOrMac(String machineId, String macAddress) {
        if (!StringUtils.hasText(machineId) && !StringUtils.hasText(macAddress)) {
            throw new IllegalArgumentException("machineId or macAddress is required");
        }
    }

    private ClientMachineValidateResponseDTO deny(String reason) {
        ClientMachineValidateResponseDTO response = new ClientMachineValidateResponseDTO();
        response.setAllowed(false);
        response.setAuthorized(false);
        response.setReason(reason);
        response.setFeatureFlags(List.of());
        return response;
    }

    private TenantMachineResponseDTO toResponse(TenantMachineEntity entity) {
        TenantMachineResponseDTO dto = new TenantMachineResponseDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setTenantName(entity.getTenantName());
        dto.setMachineId(entity.getMachineId());
        dto.setMacAddress(entity.getMacAddress());
        dto.setHostName(entity.getHostName());
        dto.setRemark(entity.getRemark());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeMac(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
