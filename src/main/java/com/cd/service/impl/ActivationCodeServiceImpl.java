package com.cd.service.impl;

import com.cd.common.exception.LicenseAccessDeniedException;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.license.LicenseGuard;
import com.cd.common.license.LicenseSigner;
import com.cd.common.security.SecurityUtils;
import com.cd.dto.ActivationCodeCreateDTO;
import com.cd.dto.ActivationCodeResponseDTO;
import com.cd.dto.ClientMachineValidateResponseDTO;
import com.cd.dto.LicenseActivateDTO;
import com.cd.dto.LicenseDtoConverter;
import com.cd.dto.LicenseOfflineResponseDTO;
import com.cd.entity.ActivationCodeEntity;
import com.cd.entity.LicenseEntity;
import com.cd.entity.TenantEntity;
import com.cd.entity.TenantMachineEntity;
import com.cd.mapper.ActivationCodeMapper;
import com.cd.mapper.LicenseMapper;
import com.cd.mapper.TenantMachineMapper;
import com.cd.mapper.TenantMapper;
import com.cd.service.ActivationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationCodeServiceImpl implements ActivationCodeService {

    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_USED = "USED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ActivationCodeMapper activationCodeMapper;
    private final TenantMapper tenantMapper;
    private final LicenseMapper licenseMapper;
    private final TenantMachineMapper tenantMachineMapper;
    private final TenantMachineServiceImpl tenantMachineService;
    private final LicenseGuard licenseGuard;
    private final LicenseSigner licenseSigner;

    @Override
    @Transactional
    public ActivationCodeResponseDTO create(Long tenantId, ActivationCodeCreateDTO dto) {
        return createBatch(tenantId, dto).get(0);
    }

    @Override
    @Transactional
    public List<ActivationCodeResponseDTO> createBatch(Long tenantId, ActivationCodeCreateDTO dto) {
        requireTenantExists(tenantId);
        requireEffectiveLicense(tenantId);
        int quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        List<ActivationCodeResponseDTO> result = new java.util.ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            ActivationCodeEntity entity = new ActivationCodeEntity();
            entity.setTenantId(tenantId);
            entity.setLicenseId(null);
            entity.setCode(generateActivationCode());
            entity.setStatus(STATUS_NEW);
            entity.setExpireTime(dto.getExpireTime());
            entity.setCreatedBy(SecurityUtils.getCurrentUserId());
            entity.setRemark(trim(dto.getRemark()));
            activationCodeMapper.insert(entity);
            result.add(toResponse(activationCodeMapper.selectById(entity.getId())));
        }
        return result;
    }

    @Override
    public List<ActivationCodeResponseDTO> listByTenantId(Long tenantId) {
        requireTenantExists(tenantId);
        return activationCodeMapper.selectByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public LicenseOfflineResponseDTO activate(LicenseActivateDTO dto) {
        String activationCode = trimRequired(dto.getActivationCode(), "activationCode");
        String machineId = trimRequired(dto.getMachineId(), "machineId");
        String macAddress = normalizeMac(trimRequired(dto.getMacAddress(), "macAddress"));
        String hostName = trim(dto.getHostName());

        ActivationCodeEntity code = activationCodeMapper.selectByCode(activationCode);
        if (code == null) {
            throw new ResourceNotFoundException("ActivationCode not found: code=" + activationCode);
        }
        if (STATUS_DISABLED.equalsIgnoreCase(code.getStatus())) {
            throw new IllegalArgumentException("ActivationCode is disabled");
        }
        if (code.getExpireTime() == null || !code.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("ActivationCode is expired");
        }

        TenantEntity tenant = tenantMapper.selectById(code.getTenantId());
        if (tenant == null) {
            throw new ResourceNotFoundException("Tenant not found: id=" + code.getTenantId());
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new IllegalArgumentException("Tenant is disabled");
        }

        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(code.getTenantId());
        if (license == null) {
            throw new LicenseAccessDeniedException("No effective License for current tenant");
        }

        TenantMachineEntity machine = tenantMachineMapper.selectActiveByMachineOrMac(machineId, macAddress);
        if (machine != null && !code.getTenantId().equals(machine.getTenantId())) {
            throw new IllegalArgumentException("Machine is already bound to another tenant");
        }

        boolean sameMachineForCode = isSameMachine(code, machineId, macAddress);
        if (STATUS_USED.equalsIgnoreCase(code.getStatus()) && !sameMachineForCode) {
            throw new IllegalArgumentException("ActivationCode is already used");
        }

        if (machine == null) {
            tenantMachineService.requireMachineQuotaForActivation(code.getTenantId());
            TenantMachineEntity entity = new TenantMachineEntity();
            entity.setTenantId(code.getTenantId());
            entity.setMachineId(machineId);
            entity.setMacAddress(macAddress);
            entity.setHostName(hostName);
            entity.setRemark("Activated by " + activationCode);
            entity.setStatus(1);
            entity.setCreatedBy(0L);
            entity.setMachineBoundAt(LocalDateTime.now());
            tenantMachineMapper.insert(entity);
            machine = tenantMachineMapper.selectByIdAndTenant(entity.getId(), entity.getTenantId());
        } else if (!StringUtils.hasText(machine.getMachineId())) {
            tenantMachineService.requireMachineQuotaForActivation(code.getTenantId());
            tenantMachineMapper.bindMachineIdIfEmpty(machine.getId(), machineId);
            machine = tenantMachineMapper.selectByIdAndTenant(machine.getId(), machine.getTenantId());
        } else if (!machineId.equals(machine.getMachineId())) {
            throw new IllegalArgumentException("Machine is already bound to another device fingerprint");
        }

        tenantMachineService.syncAuthorizedHostTenantForActivation(machine.getTenantId(), machine.getMacAddress(), hostName);

        if (!STATUS_USED.equalsIgnoreCase(code.getStatus())) {
            activationCodeMapper.markUsed(code.getId(), machineId, macAddress, hostName, LocalDateTime.now());
        }

        LicenseEntity signedLicense = buildSignedLicenseView(license, machineId);
        return LicenseDtoConverter.toOfflineResponse(
                signedLicense,
                licenseGuard.featureNames(license.getEdition())
        );
    }

    private void requireTenantExists(Long tenantId) {
        if (tenantId == null || tenantMapper.selectById(tenantId) == null) {
            throw new ResourceNotFoundException("Tenant not found: id=" + tenantId);
        }
    }

    private void requireEffectiveLicense(Long tenantId) {
        if (licenseMapper.selectEffectiveByTenantId(tenantId) == null) {
            throw new LicenseAccessDeniedException("No effective License for current tenant");
        }
    }

    private String generateActivationCode() {
        for (int i = 0; i < 5; i++) {
            String code = "ACT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
            if (activationCodeMapper.selectByCode(code) == null) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique ActivationCode");
    }

    private ActivationCodeResponseDTO toResponse(ActivationCodeEntity entity) {
        ActivationCodeResponseDTO dto = new ActivationCodeResponseDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setLicenseId(entity.getLicenseId());
        dto.setCode(entity.getCode());
        dto.setStatus(entity.getStatus());
        dto.setExpireTime(entity.getExpireTime());
        dto.setBoundMachineId(entity.getBoundMachineId());
        dto.setBoundMacAddress(entity.getBoundMacAddress());
        dto.setBoundHostName(entity.getBoundHostName());
        dto.setUsedAt(entity.getUsedAt());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private LicenseEntity buildSignedLicenseView(LicenseEntity license, String machineId) {
        LicenseEntity signed = new LicenseEntity();
        signed.setId(license.getId());
        signed.setLicenseKey(license.getLicenseKey());
        signed.setTenantId(license.getTenantId());
        signed.setEdition(license.getEdition());
        signed.setHostLimit(licenseGuard.effectiveHostLimit(license));
        signed.setUserLimit(licenseGuard.effectiveUserLimit(license));
        signed.setExpireTime(license.getExpireTime());
        signed.setMachineId(machineId);
        signed.setStatus(license.getStatus());
        signed.setCreatedAt(license.getCreatedAt());
        signed.setSignature(licenseSigner.sign(signed));
        return signed;
    }

    private boolean isSameMachine(ActivationCodeEntity code, String machineId, String macAddress) {
        String boundMachineId = trim(code.getBoundMachineId());
        String boundMacAddress = normalizeMac(trim(code.getBoundMacAddress()));
        return StringUtils.hasText(boundMachineId)
                && StringUtils.hasText(boundMacAddress)
                && boundMachineId.equals(machineId)
                && boundMacAddress.equals(macAddress);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimRequired(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeMac(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT).replace(':', '-');
    }
}
