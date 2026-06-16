package com.cd.common.license;

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
    private static final List<PathFeature> PATH_FEATURES = List.of(
            new PathFeature("/api/ai/**", LicenseFeature.AI_ANALYSIS),
            new PathFeature("/api/assets/**", LicenseFeature.ASSET_MANAGE),
            new PathFeature("/api/asset/export/**", LicenseFeature.ASSET_EXPORT),
            new PathFeature("/api/patch-security/**", LicenseFeature.PATCH),
            new PathFeature("/api/installed-patch/**", LicenseFeature.PATCH),
            new PathFeature("/api/patch-cve-map/**", LicenseFeature.PATCH),
            new PathFeature("/api/vuln-detection/**", LicenseFeature.VULN),
            new PathFeature("/api/vuln-verification/**", LicenseFeature.VULN),
            new PathFeature("/api/vuln-ops-dashboard/**", LicenseFeature.VULN),
            new PathFeature("/api/vuln-rule/**", LicenseFeature.VULN),
            new PathFeature("/vulnRule/**", LicenseFeature.VULN),
            new PathFeature("/api/baseline/**", LicenseFeature.BASELINE),
            new PathFeature("/api/login-log/**", LicenseFeature.LOG),
            new PathFeature("/api/security-log-center/**", LicenseFeature.LOG),
            new PathFeature("/api/security-event/**", LicenseFeature.LOG),
            new PathFeature("/api/login-security-log/**", LicenseFeature.LOG),
            new PathFeature("/api/account-change-log/**", LicenseFeature.LOG),
            new PathFeature("/api/host-log/**", LicenseFeature.LOG),
            new PathFeature("/api/rbac/**", LicenseFeature.ROLE_MANAGE)
    );

    private final LicenseGuard licenseGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        for (String pattern : SESSION_BASE_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) {
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
}
