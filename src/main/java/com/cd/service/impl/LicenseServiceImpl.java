package com.cd.service.impl;

import com.cd.common.license.LicenseSigner;
import com.cd.common.license.LicenseGuard;
import com.cd.dto.LicenseActivateDTO;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.PermissionChecker;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.LicenseCurrentDTO;
import com.cd.dto.LicenseGenerateDTO;
import com.cd.dto.LicensePlanResponseDTO;
import com.cd.entity.LicenseEntity;
import com.cd.entity.LicensePlanEntity;
import com.cd.entity.TenantEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.LicenseMapper;
import com.cd.mapper.LicensePlanMapper;
import com.cd.mapper.TenantMapper;
import com.cd.mapper.UserMapper;
import com.cd.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class LicenseServiceImpl implements LicenseService {

    private final LicenseMapper licenseMapper;
    private final LicensePlanMapper licensePlanMapper;
    private final TenantMapper tenantMapper;
    private final HostMapper hostMapper;
    private final UserMapper userMapper;
    private final LicenseSigner licenseSigner;
    private final LicenseGuard licenseGuard;
    private final PermissionChecker permissionChecker;

    @Override
    public LicenseEntity create(LicenseEntity entity) {
        requireTenantExists(entity.getTenantId());
        entity.setEdition(normalizeEdition(entity.getEdition()));
        applyDefaultLimits(entity);
        validateLimit(entity.getHostLimit(), "hostLimit");
        validateLimit(entity.getUserLimit(), "userLimit");
        validateStatus(entity.getStatus());
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setMachineId(trim(entity.getMachineId()));
        entity.setSignature(licenseSigner.sign(entity));
        licenseMapper.insert(entity);
        return licenseMapper.selectById(entity.getId());
    }

    @Override
    public LicenseEntity update(Long id, LicenseEntity entity) {
        ensureExists(id);
        requireTenantExists(entity.getTenantId());
        entity.setEdition(normalizeEdition(entity.getEdition()));
        applyDefaultLimits(entity);
        validateLimit(entity.getHostLimit(), "hostLimit");
        validateLimit(entity.getUserLimit(), "userLimit");
        validateStatus(entity.getStatus());
        entity.setId(id);
        entity.setMachineId(trim(entity.getMachineId()));
        entity.setSignature(licenseSigner.sign(entity));
        licenseMapper.updateById(entity);
        return licenseMapper.selectById(id);
    }

    @Override
    public void deleteById(Long id) {
        ensureExists(id);
        licenseMapper.deleteById(id);
    }

    @Override
    public LicenseEntity getById(Long id) {
        return ensureExists(id);
    }

    @Override
    public LicenseEntity getByLicenseKey(String licenseKey) {
        return licenseMapper.selectByLicenseKey(licenseKey);
    }

    @Override
    public List<LicenseEntity> listByTenantId(Long tenantId) {
        requireTenantExists(tenantId);
        return licenseMapper.selectByTenantId(tenantId);
    }

    @Override
    public LicenseEntity generateForTenant(Long tenantId, LicenseGenerateDTO dto) {
        requireTenantExists(tenantId);
        LicensePlanEntity plan = requirePlan(dto.getPlanCode() == null ? dto.getEdition() : dto.getPlanCode());
        LicenseEntity entity = new LicenseEntity();
        entity.setLicenseKey(generateLicenseKey());
        entity.setTenantId(tenantId);
        entity.setEdition(plan.getCode());
        entity.setHostLimit(dto.getHostLimit() == null ? plan.getHostLimit() : dto.getHostLimit());
        entity.setUserLimit(dto.getUserLimit() == null ? plan.getUserLimit() : dto.getUserLimit());
        entity.setExpireTime(dto.getExpireTime());
        entity.setMachineId(null);
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        return create(entity);
    }

    @Override
    public LicenseEntity activate(LicenseActivateDTO dto) {
        return bindMachineAndSign(dto);
    }

    @Override
    public LicenseEntity generateOffline(LicenseActivateDTO dto) {
        return bindMachineAndSign(dto);
    }

    @Override
    public LicenseCurrentDTO current() {
        Long tenantId = currentTenantId();
        LicenseCurrentDTO dto = new LicenseCurrentDTO();
        dto.setTenantId(tenantId);
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        dto.setTenantName(tenant == null ? null : tenant.getName());
        dto.setHostUsed(hostMapper.countByTenantId(tenantId));
        dto.setUserUsed(userMapper.countAllByTenant(null, tenantId));

        if (permissionChecker.isSuperAdmin()) {
            dto.setEdition("PLATFORM");
            dto.setHostLimit(0);
            dto.setUserLimit(0);
            dto.setStatus(1);
            dto.setEffective(true);
            dto.setMessage("Platform tenant");
            dto.setFeatureFlags(List.of(
                    "HOST",
                    "ASSET",
                    "PATCH",
                    "VULN",
                    "LOG",
                    "BASELINE",
                    "USER",
                    "AI"
            ));
            return dto;
        }

        LicenseEntity entity = licenseMapper.selectEffectiveByTenantId(tenantId);
        if (entity == null) {
            dto.setEdition("NONE");
            dto.setHostLimit(0);
            dto.setUserLimit(0);
            dto.setStatus(0);
            dto.setEffective(false);
            dto.setMessage("No effective License for current tenant");
            dto.setFeatureFlags(List.of());
            return dto;
        }

        dto.setEdition(entity.getEdition());
        dto.setHostLimit(licenseGuard.effectiveHostLimit(entity));
        dto.setUserLimit(licenseGuard.effectiveUserLimit(entity));
        dto.setExpireTime(entity.getExpireTime());
        dto.setStatus(entity.getStatus());
        dto.setEffective(true);
        dto.setMessage("License effective");
        dto.setFeatureFlags(licenseGuard.featureNames(entity.getEdition()));
        return dto;
    }

    @Override
    public List<LicensePlanResponseDTO> plans() {
        return licensePlanMapper.selectEnabled().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    private LicenseEntity bindMachineAndSign(LicenseActivateDTO dto) {
        String licenseKey = trimRequired(dto.getLicenseKey(), "licenseKey");
        String machineId = trimRequired(dto.getMachineId(), "machineId");
        LicenseEntity entity = licenseMapper.selectByLicenseKey(licenseKey);
        if (entity == null) {
            throw new ResourceNotFoundException("License not found: licenseKey=" + licenseKey);
        }
        if (entity.getStatus() == null || entity.getStatus() != 1) {
            throw new IllegalArgumentException("License is disabled");
        }
        if (entity.getExpireTime() == null) {
            throw new IllegalArgumentException("License expireTime is required");
        }
        if (!entity.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("License is expired");
        }
        String boundMachineId = trim(entity.getMachineId());
        if (StringUtils.hasText(boundMachineId) && !boundMachineId.equals(machineId)) {
            throw new IllegalArgumentException("License is already bound to another machine");
        }
        validateHostLimit(entity);
        entity.setMachineId(machineId);
        entity.setSignature(licenseSigner.sign(entity));
        licenseMapper.updateMachineAndSignature(entity.getId(), machineId, entity.getSignature());
        return licenseMapper.selectById(entity.getId());
    }

    private LicenseEntity ensureExists(Long id) {
        LicenseEntity entity = licenseMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("License not found: id=" + id);
        }
        return entity;
    }

    private void requireTenantExists(Long tenantId) {
        if (tenantId == null || tenantMapper.selectById(tenantId) == null) {
            throw new ResourceNotFoundException("Tenant not found: id=" + tenantId);
        }
    }

    private String normalizeEdition(String edition) {
        if (!StringUtils.hasText(edition)) {
            throw new IllegalArgumentException("edition must not be blank");
        }
        String normalized = edition.trim().toUpperCase(Locale.ROOT);
        requirePlan(normalized);
        return normalized;
    }

    private void validateLimit(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than or equal to 0");
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
    }

    private void applyDefaultLimits(LicenseEntity entity) {
        if (entity.getHostLimit() == null) {
            entity.setHostLimit(licenseGuard.defaultHostLimit(entity.getEdition()));
        }
        if (entity.getUserLimit() == null) {
            entity.setUserLimit(licenseGuard.defaultUserLimit(entity.getEdition()));
        }
    }

    private void validateHostLimit(LicenseEntity entity) {
        Integer hostLimit = entity.getHostLimit();
        if (hostLimit == null || hostLimit == 0) {
            return;
        }
        long hostCount = hostMapper.countByTenantId(entity.getTenantId());
        if (hostCount > hostLimit) {
            throw new IllegalArgumentException("Host limit exceeded");
        }
    }

    private String generateLicenseKey() {
        for (int i = 0; i < 5; i++) {
            String key = "LIC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
            if (licenseMapper.selectByLicenseKey(key) == null) {
                return key;
            }
        }
        throw new IllegalStateException("Failed to generate unique license key");
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

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private LicensePlanEntity requirePlan(String planCode) {
        if (!StringUtils.hasText(planCode)) {
            throw new IllegalArgumentException("planCode must not be blank");
        }
        String normalized = planCode.trim().toUpperCase(Locale.ROOT);
        LicensePlanEntity plan = licensePlanMapper.selectByCode(normalized);
        if (plan == null) {
            throw new IllegalArgumentException("Unsupported License plan: " + normalized);
        }
        return plan;
    }

    private LicensePlanResponseDTO toPlanResponse(LicensePlanEntity entity) {
        LicensePlanResponseDTO dto = new LicensePlanResponseDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setUserLimit(entity.getUserLimit());
        dto.setHostLimit(entity.getHostLimit());
        dto.setFeatureFlags(Arrays.stream((entity.getFeatureFlags() == null ? "" : entity.getFeatureFlags()).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList());
        return dto;
    }
}
