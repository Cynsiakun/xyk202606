package com.cd.common.license;

import com.cd.common.exception.LicenseAccessDeniedException;
import com.cd.common.security.TenantContextHolder;
import com.cd.entity.LicenseEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.LicenseMapper;
import com.cd.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component("licenseGuard")
@RequiredArgsConstructor
public class LicenseGuard {

    public static final String TRIAL = "TRIAL";
    public static final String STANDARD = "STANDARD";
    public static final String PROFESSIONAL = "PROFESSIONAL";

    private static final Plan TRIAL_PLAN = new Plan(1, 10, EnumSet.of(LicenseFeature.HOST_VIEW));
    private static final Plan STANDARD_PLAN = new Plan(5, 100, EnumSet.of(
            LicenseFeature.HOST_VIEW,
            LicenseFeature.HOST_MANAGE,
            LicenseFeature.ASSET_MANAGE,
            LicenseFeature.ASSET_EXPORT,
            LicenseFeature.PATCH,
            LicenseFeature.VULN,
            LicenseFeature.LOG,
            LicenseFeature.BASELINE,
            LicenseFeature.USER_MANAGE,
            LicenseFeature.ROLE_MANAGE
    ));
    private static final Plan PROFESSIONAL_PLAN = new Plan(20, 500, EnumSet.of(
            LicenseFeature.HOST_VIEW,
            LicenseFeature.HOST_MANAGE,
            LicenseFeature.ASSET_MANAGE,
            LicenseFeature.ASSET_EXPORT,
            LicenseFeature.PATCH,
            LicenseFeature.VULN,
            LicenseFeature.LOG,
            LicenseFeature.BASELINE,
            LicenseFeature.USER_MANAGE,
            LicenseFeature.ROLE_MANAGE,
            LicenseFeature.AI_ANALYSIS,
            LicenseFeature.AI_REMEDIATION,
            LicenseFeature.AI_REPORT
    ));
    private static final Map<String, Plan> PLANS = Map.of(
            TRIAL, TRIAL_PLAN,
            STANDARD, STANDARD_PLAN,
            PROFESSIONAL, PROFESSIONAL_PLAN
    );

    private final LicenseMapper licenseMapper;
    private final HostMapper hostMapper;
    private final UserMapper userMapper;

    public boolean hasFeature(String featureName) {
        if (!StringUtils.hasText(featureName)) {
            return false;
        }
        try {
            return hasFeature(LicenseFeature.valueOf(featureName.trim().toUpperCase(Locale.ROOT)));
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
        Plan plan = PLANS.get(edition);
        if (plan == null) {
            throw new LicenseAccessDeniedException("Unsupported License edition: " + edition);
        }
        return plan;
    }

    private String normalizeEdition(String edition) {
        if (!StringUtils.hasText(edition)) {
            throw new LicenseAccessDeniedException("License edition is required");
        }
        return edition.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPlatformTenant() {
        return currentTenantId() == 0L;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private record Plan(int userLimit, int hostLimit, Set<LicenseFeature> features) {
    }
}
