package com.cd.common.license;

import com.cd.common.exception.LicenseAccessDeniedException;
import com.cd.common.security.PermissionChecker;
import com.cd.common.security.TenantContextHolder;
import com.cd.entity.LicenseEntity;
import com.cd.entity.LicensePlanEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.LicenseMapper;
import com.cd.mapper.LicensePlanMapper;
import com.cd.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component("licenseGuard")
@RequiredArgsConstructor
public class LicenseGuard {

    public static final String TRIAL = "TRIAL";
    public static final String STANDARD = "STANDARD";
    public static final String PROFESSIONAL = "PROFESSIONAL";

    private final LicenseMapper licenseMapper;
    private final LicensePlanMapper licensePlanMapper;
    private final HostMapper hostMapper;
    private final UserMapper userMapper;
    private final PermissionChecker permissionChecker;

    public boolean hasFeature(String featureName) {
        if (!StringUtils.hasText(featureName)) {
            return false;
        }
        try {
            return hasFeature(LicenseFeature.valueOf(normalizeFeatureName(featureName)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean hasFeature(LicenseFeature feature) {
        if (isPlatformTenant()) {
            return true;
        }
        LicenseEntity license = requireEffectiveLicense();
        return planOf(license).features().contains(feature);
    }

    public void requireFeature(LicenseFeature feature) {
        if (!hasFeature(feature)) {
            throw new LicenseAccessDeniedException("License feature not allowed: " + feature.name());
        }
    }

    public void requireHostQuotaBeforeCreate() {
        requireHostQuotaBeforeCreate(1);
    }

    public void requireHostQuotaBeforeCreate(int increment) {
        if (isPlatformTenant()) {
            return;
        }
        LicenseEntity license = requireEffectiveLicense();
        int limit = effectiveHostLimit(license);
        if (limit == 0) {
            return;
        }
        long current = hostMapper.countByTenantId(currentTenantId());
        if (current + Math.max(1, increment) > limit) {
            throw new LicenseAccessDeniedException("Host quota exceeded");
        }
    }

    public void requireUserQuotaBeforeCreate() {
        requireUserQuotaBeforeCreate(1);
    }

    public void requireUserQuotaBeforeCreate(int increment) {
        if (isPlatformTenant()) {
            return;
        }
        LicenseEntity license = requireEffectiveLicense();
        int limit = effectiveUserLimit(license);
        if (limit == 0) {
            return;
        }
        long current = userMapper.countAllByTenant(null, currentTenantId());
        if (current + Math.max(1, increment) > limit) {
            throw new LicenseAccessDeniedException("User quota exceeded");
        }
    }

    public int defaultHostLimit(String edition) {
        return planOf(normalizeEdition(edition)).hostLimit();
    }

    public int defaultUserLimit(String edition) {
        return planOf(normalizeEdition(edition)).userLimit();
    }

    public List<String> featureNames(String edition) {
        return planOf(normalizeEdition(edition)).features().stream()
                .map(LicenseFeature::name)
                .sorted()
                .toList();
    }

    public int effectiveHostLimit(LicenseEntity license) {
        return license.getHostLimit() == null ? planOf(license).hostLimit() : license.getHostLimit();
    }

    public int effectiveUserLimit(LicenseEntity license) {
        return license.getUserLimit() == null ? planOf(license).userLimit() : license.getUserLimit();
    }

    public boolean isProfessionalOrPlatform() {
        if (isPlatformTenant()) {
            return true;
        }
        LicenseEntity license = requireEffectiveLicense();
        return PROFESSIONAL.equalsIgnoreCase(normalizeEdition(license.getEdition()));
    }

    private LicenseEntity requireEffectiveLicense() {
        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(currentTenantId());
        if (license == null) {
            throw new LicenseAccessDeniedException("No effective License for current tenant");
        }
        return license;
    }

    private Plan planOf(LicenseEntity license) {
        return planOf(normalizeEdition(license.getEdition()));
    }

    private Plan planOf(String edition) {
        LicensePlanEntity plan = licensePlanMapper.selectByCode(edition);
        if (plan == null) {
            throw new LicenseAccessDeniedException("Unsupported License edition: " + edition);
        }
        return new Plan(
                plan.getUserLimit() == null ? 0 : plan.getUserLimit(),
                plan.getHostLimit() == null ? 0 : plan.getHostLimit(),
                parseFeatures(plan.getFeatureFlags())
        );
    }

    private String normalizeEdition(String edition) {
        if (!StringUtils.hasText(edition)) {
            throw new LicenseAccessDeniedException("License edition is required");
        }
        return edition.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPlatformTenant() {
        return permissionChecker.isSuperAdmin();
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private record Plan(int userLimit, int hostLimit, Set<LicenseFeature> features) {
    }

    private Set<LicenseFeature> parseFeatures(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> LicenseFeature.valueOf(normalizeFeatureName(value)))
                .collect(Collectors.toSet());
    }

    private String normalizeFeatureName(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "HOST_VIEW", "HOST_MANAGE", "HOST_AUTH" -> "HOST";
            case "ASSET_MANAGE", "ASSET_EXPORT" -> "ASSET";
            case "USER_MANAGE", "ROLE_MANAGE" -> "USER";
            case "AI_ANALYSIS", "AI_REMEDIATION", "AI_REPORT" -> "AI";
            default -> value;
        };
    }
}
