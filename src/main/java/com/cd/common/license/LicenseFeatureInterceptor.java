package com.cd.common.license;

import com.cd.common.access.AccessDecision;
import com.cd.common.access.AccessPolicyService;
import com.cd.common.exception.LicenseAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LicenseFeatureInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> SESSION_BASE_PATHS = List.of(
            "/api/rbac/menu/current",
            "/api/rbac/permission/current"
    );
    private static final List<PathPolicy> PLATFORM_POLICIES = List.of(
            new PathPolicy("/api/patch-cve-map/**", "PLATFORM_CVE_VIEW"),
            new PathPolicy("/api/vuln-rule/**", "PLATFORM_VULN_RULE_VIEW"),
            new PathPolicy("/vulnRule/**", "PLATFORM_VULN_RULE_VIEW"),
            new PathPolicy("/api/baseline/rule-management/**", "PLATFORM_BASELINE_RULE_VIEW"),
            new PathPolicy("/api/asset-fingerprint-rules/**", "PLATFORM_ASSET_FINGERPRINT_RULE_VIEW"),
            new PathPolicy("/api/asset-statistics/**", "TENANT_ASSET_STATS_VIEW")
    );
    private static final List<PathFeature> PATH_FEATURES = List.of(
            new PathFeature("/api/ai/**", LicenseFeature.AI),
            new PathFeature("/api/assets/**", LicenseFeature.ASSET),
            new PathFeature("/api/asset/export/**", LicenseFeature.ASSET),
            new PathFeature("/api/patch-security/**", LicenseFeature.PATCH),
            new PathFeature("/api/installed-patch/**", LicenseFeature.PATCH),
            new PathFeature("/api/vuln-detection/**", LicenseFeature.VULN),
            new PathFeature("/api/vuln-verification/**", LicenseFeature.VULN),
            new PathFeature("/api/vuln-ops-dashboard/**", LicenseFeature.VULN),
            new PathFeature("/api/baseline/**", LicenseFeature.BASELINE),
            new PathFeature("/api/login-log/**", LicenseFeature.LOG),
            new PathFeature("/api/security-log-center/**", LicenseFeature.LOG),
            new PathFeature("/api/security-event/**", LicenseFeature.LOG),
            new PathFeature("/api/login-security-log/**", LicenseFeature.LOG),
            new PathFeature("/api/account-change-log/**", LicenseFeature.LOG),
            new PathFeature("/api/host-log/**", LicenseFeature.LOG),
            new PathFeature("/api/rbac/user/*/roles", LicenseFeature.USER),
            new PathFeature("/api/rbac/role/all", LicenseFeature.USER)
    );

    private final LicenseGuard licenseGuard;
    private final AccessPolicyService accessPolicyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        for (String pattern : SESSION_BASE_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        for (PathPolicy pathPolicy : PLATFORM_POLICIES) {
            if (PATH_MATCHER.match(pathPolicy.pattern(), path)) {
                AccessDecision decision = accessPolicyService.evaluate(pathPolicy.policyKey());
                if (!decision.isAllowed()) {
                    throw new LicenseAccessDeniedException(decision.getReasonCode() + ": " + decision.getMessage());
                }
                return true;
            }
        }
        for (PathFeature pathFeature : PATH_FEATURES) {
            if (PATH_MATCHER.match(pathFeature.pattern(), path)) {
                licenseGuard.requireFeature(pathFeature.feature());
                break;
            }
        }
        return true;
    }

    private record PathFeature(String pattern, LicenseFeature feature) {
    }

    private record PathPolicy(String pattern, String policyKey) {
    }
}
