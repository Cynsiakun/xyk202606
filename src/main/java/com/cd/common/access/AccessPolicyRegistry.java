package com.cd.common.access;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AccessPolicyRegistry {

    private final Map<String, AccessPolicyDefinition> definitions = new LinkedHashMap<>();

    public AccessPolicyRegistry() {
        common("COMMON_DASHBOARD_VIEW", "dashboard:view");
        common("COMMON_PROFILE_VIEW", null);

        tenant("TENANT_HOST_VIEW", "HOST", AccessOperation.VIEW, "host:view", true);
        tenant("TENANT_HOST_CREATE", "HOST", AccessOperation.CREATE, "host:create", false);
        tenant("TENANT_HOST_UPDATE", "HOST", AccessOperation.UPDATE, "host:update", false);
        tenant("TENANT_HOST_DELETE", "HOST", AccessOperation.DELETE, "host:delete", false);
        tenant("TENANT_MACHINE_VIEW", "HOST", AccessOperation.VIEW, "tenant-machine:view", true);
        tenant("TENANT_MACHINE_CREATE", "HOST", AccessOperation.CREATE, "tenant-machine:create", false);
        tenant("TENANT_MACHINE_UPDATE", "HOST", AccessOperation.UPDATE, "tenant-machine:update", false);
        tenant("TENANT_MACHINE_DELETE", "HOST", AccessOperation.DELETE, "tenant-machine:delete", false);
        tenant("TENANT_ASSET_VIEW", "ASSET", AccessOperation.VIEW, "asset:view", true);
        tenant("TENANT_ASSET_STATS_VIEW", "ASSET_STATS", AccessOperation.VIEW, "asset-stats:view", true);
        tenant("TENANT_ASSET_PROBE", "ASSET", AccessOperation.EXECUTE, "host:probe", false);
        tenant("TENANT_ASSET_EXPORT", "ASSET", AccessOperation.EXPORT, "asset:export", false);
        tenant("TENANT_PATCH_VIEW", "PATCH", AccessOperation.VIEW, "patch-security:view", true);
        tenant("TENANT_VULN_VIEW", "VULN", AccessOperation.VIEW, "vuln-detection:view", true);
        tenant("TENANT_VULN_DASHBOARD_VIEW", "VULN", AccessOperation.VIEW, "vuln-ops-dashboard:view", true);
        tenant("TENANT_LOG_VIEW", "LOG", AccessOperation.VIEW, "security-log:view", true);
        tenant("TENANT_BASELINE_VIEW", "BASELINE", AccessOperation.VIEW, "baseline:view", true);
        tenant("TENANT_BASELINE_WORKORDER_VIEW", "BASELINE", AccessOperation.VIEW, "workorder:view", true);
        tenant("TENANT_USER_VIEW", "USER", AccessOperation.VIEW, "user:view", true);
        tenant("TENANT_USER_CREATE", "USER", AccessOperation.CREATE, "user:create", false);
        tenant("TENANT_USER_ASSIGN_ROLE", "USER", AccessOperation.ASSIGN, "user:role:assign", false);
        tenant("TENANT_AI_ANALYSIS", "AI", AccessOperation.EXECUTE, "asset:view", false);

        platform("PLATFORM_TENANT_VIEW", "TENANT", AccessOperation.VIEW, null, true);
        platform("PLATFORM_LICENSE_MANAGE", "LICENSE", AccessOperation.MANAGE, null, false);
        platform("PLATFORM_CVE_VIEW", "CVE_RULE", AccessOperation.VIEW, "patch-cve-map:view", true);
        platform("PLATFORM_VULN_RULE_VIEW", "VULN_RULE", AccessOperation.VIEW, "vuln-rule:view", true);
        platform("PLATFORM_BASELINE_RULE_VIEW", "BASELINE_RULE", AccessOperation.VIEW, "baseline-rule:view", true);
        platform("PLATFORM_ASSET_FINGERPRINT_RULE_VIEW", "ASSET_FINGERPRINT_RULE", AccessOperation.VIEW, "asset-fingerprint-rule:view", true);
        platform("PLATFORM_ASSET_STATS_VIEW", "ASSET_STATS", AccessOperation.VIEW, "asset-stats:view", true);
        platform("PLATFORM_RBAC_VIEW", "RBAC", AccessOperation.VIEW, null, true);
    }

    public AccessPolicyDefinition get(String key) {
        return definitions.get(key);
    }

    public Collection<AccessPolicyDefinition> all() {
        return definitions.values();
    }

    private void common(String key, String permissionCode) {
        add(new AccessPolicyDefinition(key, AccessDomain.COMMON, null, AccessOperation.VIEW, permissionCode, "common", true));
    }

    private void tenant(String key, String featureCode, AccessOperation operation, String permissionCode, boolean menuPolicy) {
        add(new AccessPolicyDefinition(key, AccessDomain.TENANT, featureCode, operation, permissionCode, "tenant", menuPolicy));
    }

    private void platform(String key, String featureCode, AccessOperation operation, String permissionCode, boolean menuPolicy) {
        add(new AccessPolicyDefinition(key, AccessDomain.PLATFORM, featureCode, operation, permissionCode, "platform", menuPolicy));
    }

    private void add(AccessPolicyDefinition definition) {
        definitions.put(definition.getKey(), definition);
    }
}
