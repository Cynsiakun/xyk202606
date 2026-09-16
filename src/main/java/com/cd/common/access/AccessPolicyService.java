package com.cd.common.access;

import com.cd.common.license.LicenseGuard;
import com.cd.common.security.PermissionChecker;
import com.cd.common.security.TenantContextHolder;
import com.cd.entity.LicenseEntity;
import com.cd.entity.TenantEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.LicenseMapper;
import com.cd.mapper.TenantMachineMapper;
import com.cd.mapper.TenantMapper;
import com.cd.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service("accessPolicy")
@RequiredArgsConstructor
public class AccessPolicyService {

    private final AccessPolicyRegistry registry;
    private final PermissionChecker permissionChecker;
    private final LicenseMapper licenseMapper;
    private final TenantMapper tenantMapper;
    private final HostMapper hostMapper;
    private final TenantMachineMapper tenantMachineMapper;
    private final UserMapper userMapper;
    private final LicenseGuard licenseGuard;

    public boolean can(String policyKey) {
        return evaluate(policyKey).isAllowed();
    }

    public AccessDecision evaluate(String policyKey) {
        AccessPolicyDefinition definition = registry.get(policyKey);
        if (definition == null) {
            return AccessDecision.deny(policyKey, "POLICY_NOT_FOUND", "Policy not found: " + policyKey);
        }
        if (definition.getDomain() == AccessDomain.COMMON) {
            return permissionAllowed(definition);
        }
        if (definition.getDomain() == AccessDomain.PLATFORM) {
            if (!permissionChecker.isSuperAdmin()) {
                return AccessDecision.deny(policyKey, "NOT_PLATFORM_ADMIN", "Platform access requires SUPER_ADMIN");
            }
            if (!Long.valueOf(0L).equals(currentTenantId())) {
                return AccessDecision.deny(policyKey, "NOT_PLATFORM_TENANT", "Platform access requires tenant_id=0");
            }
            return AccessDecision.allow(policyKey);
        }
        if (permissionChecker.isSuperAdmin()) {
            return AccessDecision.allow(policyKey);
        }
        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(currentTenantId());
        if (license == null) {
            return AccessDecision.deny(policyKey, "NO_EFFECTIVE_LICENSE", "No effective License for current tenant");
        }
        if ("ASSET_STATS".equals(definition.getFeatureCode()) && !licenseGuard.isProfessionalOrPlatform()) {
            return AccessDecision.deny(policyKey, "PLAN_NOT_ALLOWED", "Professional plan required: " + definition.getFeatureCode());
        }
        if (!hasTenantFeature(license, definition.getFeatureCode())) {
            return AccessDecision.deny(policyKey, "FEATURE_NOT_ALLOWED", "License feature not allowed: " + definition.getFeatureCode());
        }
        return permissionAllowed(definition);
    }

    public AccessEffectiveDTO currentEffective() {
        Long tenantId = currentTenantId();
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        AccessEffectiveDTO dto = new AccessEffectiveDTO();
        dto.setTenantId(tenantId);
        dto.setTenantName(tenant == null ? null : tenant.getName());
        dto.setHostUsed(tenantMachineMapper.countActivatedByTenant(tenantId));
        dto.setUserUsed(userMapper.countAllByTenant(null, tenantId));

        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(tenantId);
        if (permissionChecker.isSuperAdmin()) {
            dto.setEdition("PLATFORM");
            dto.setEffective(true);
            dto.setMessage("Platform");
            dto.setTenantFeatures(allTenantFeatures());
            dto.setPlatformFeatures(platformFeatures());
            dto.setAllowedPolicies(allowedPolicyKeys());
            dto.setHostLimit(0);
            dto.setUserLimit(0);
            return dto;
        }

        if (license == null) {
            dto.setEdition("NONE");
            dto.setEffective(false);
            dto.setMessage("No effective License for current tenant");
            dto.setTenantFeatures(List.of());
            dto.setPlatformFeatures(List.of());
            dto.setAllowedPolicies(allowedPolicyKeys());
            dto.setHostLimit(0);
            dto.setUserLimit(0);
            return dto;
        }

        dto.setEdition(license.getEdition());
        dto.setEffective(true);
        dto.setMessage("License effective");
        dto.setExpireTime(license.getExpireTime());
        dto.setTenantFeatures(licenseGuard.featureNames(license.getEdition()));
        dto.setPlatformFeatures(List.of());
        dto.setAllowedPolicies(allowedPolicyKeys());
        dto.setHostLimit(licenseGuard.effectiveHostLimit(license));
        dto.setUserLimit(licenseGuard.effectiveUserLimit(license));
        return dto;
    }

    private AccessDecision permissionAllowed(AccessPolicyDefinition definition) {
        String permissionCode = definition.getPermissionCode();
        if (!StringUtils.hasText(permissionCode) || permissionChecker.has(permissionCode)) {
            return AccessDecision.allow(definition.getKey());
        }
        return AccessDecision.deny(definition.getKey(), "PERMISSION_DENIED", "Permission denied: " + permissionCode);
    }

    private boolean hasTenantFeature(LicenseEntity license, String featureCode) {
        if (!StringUtils.hasText(featureCode)) {
            return true;
        }
        if ("ASSET_STATS".equals(featureCode)) {
            return licenseGuard.featureNames(license.getEdition()).contains("ASSET");
        }
        return licenseGuard.featureNames(license.getEdition()).contains(featureCode);
    }

    private List<String> allowedPolicyKeys() {
        List<String> result = new ArrayList<>();
        for (AccessPolicyDefinition definition : registry.all()) {
            AccessDecision decision = evaluate(definition.getKey());
            if (decision.isAllowed()) {
                result.add(definition.getKey());
            }
        }
        return result;
    }

    private List<String> allPolicyKeys() {
        return registry.all().stream()
                .map(AccessPolicyDefinition::getKey)
                .toList();
    }

    private List<String> allTenantFeatures() {
        return registry.all().stream()
                .filter(definition -> definition.getDomain() == AccessDomain.TENANT)
                .map(AccessPolicyDefinition::getFeatureCode)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> platformFeatures() {
        return registry.all().stream()
                .filter(definition -> definition.getDomain() == AccessDomain.PLATFORM)
                .map(AccessPolicyDefinition::getFeatureCode)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }
}
