package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class PatchSecurityMenuInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(3)
    public ApplicationRunner initPatchSecurityMenu() {
        return args -> {
            insertPermission("patch-security:view", "查看补丁安全风险", "/api/patch-security/**");
            insertPermission("patch-security:analyze", "重新分析补丁风险", "/api/patch-security/**/analyze");
            insertPermission("patch-security:scan", "下发补丁扫描", "/api/patch-security/**/scan");

            insertPermission("installed-patch:view", "查看补丁管理", "/api/installed-patch/**");
            insertPermission("installed-patch:create", "新增补丁记录", "/api/installed-patch");
            insertPermission("installed-patch:update", "编辑补丁记录", "/api/installed-patch/{id}");
            insertPermission("installed-patch:delete", "删除补丁记录", "/api/installed-patch/**");

            insertPermission("patch-cve-map:view", "查看CVE映射", "/api/patch-cve-map/**");
            insertPermission("patch-cve-map:create", "新增CVE映射", "/api/patch-cve-map");
            insertPermission("patch-cve-map:update", "编辑CVE映射", "/api/patch-cve-map/{id}");
            insertPermission("patch-cve-map:delete", "删除CVE映射", "/api/patch-cve-map/**");

            insertPermission("vuln-detection:view", "查看漏洞检测结果", "/api/vuln-detection/**");
            insertPermission("vuln-detection:analyze", "执行漏洞规则匹配", "/api/vuln-detection/**");

            insertPermission("vuln-rule:view", "查看漏洞库规则", "/api/vuln-rule/**");
            insertPermission("vuln-rule:create", "新增漏洞库规则", "/api/vuln-rule");
            insertPermission("vuln-rule:update", "编辑漏洞库规则", "/api/vuln-rule/{id}");
            insertPermission("vuln-rule:delete", "删除漏洞库规则", "/api/vuln-rule/**");

            insertPermission("vuln-ops-dashboard:view", "查看漏洞运营仪表盘", "/api/vuln-ops-dashboard/**");

            insertMenus();

            grant("SECURITY_ADMIN",
                    "patch-security:view", "patch-security:analyze", "patch-security:scan",
                    "installed-patch:view", "installed-patch:create", "installed-patch:update", "installed-patch:delete",
                    "patch-cve-map:view", "patch-cve-map:create", "patch-cve-map:update", "patch-cve-map:delete",
                    "vuln-detection:view", "vuln-detection:analyze",
                    "vuln-rule:view", "vuln-rule:create", "vuln-rule:update", "vuln-rule:delete",
                    "vuln-ops-dashboard:view");
            grant("ANALYST",
                    "patch-security:view", "patch-security:analyze", "patch-security:scan",
                    "installed-patch:view", "installed-patch:create", "installed-patch:update",
                    "patch-cve-map:view", "patch-cve-map:create", "patch-cve-map:update",
                    "vuln-detection:view", "vuln-detection:analyze",
                    "vuln-rule:view", "vuln-rule:create", "vuln-rule:update",
                    "vuln-ops-dashboard:view");
            grant("AUDITOR",
                    "patch-security:view",
                    "installed-patch:view",
                    "patch-cve-map:view",
                    "vuln-detection:view",
                    "vuln-rule:view",
                    "vuln-ops-dashboard:view");
        };
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void insertMenus() {
        Integer maxSort = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        int baseSort = (maxSort == null ? 0 : maxSort) + 1;

        Long patchSecurityViewPermissionId = permissionId("patch-security:view");
        Long installedPatchViewPermissionId = permissionId("installed-patch:view");
        Long patchCveMapViewPermissionId = permissionId("patch-cve-map:view");
        Long vulnViewPermissionId = permissionId("vuln-detection:view");
        Long vulnRuleViewPermissionId = permissionId("vuln-rule:view");
        Long vulnOpsPermissionId = permissionId("vuln-ops-dashboard:view");

        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'risk_discovery', '风险发现', '#', 'layui-icon-vercode', ?, ?, 1, NULL
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'risk_discovery')
                """, patchSecurityViewPermissionId, baseSort);

        Long riskDiscoveryId = menuId("risk_discovery");

        insertChildMenu("patch_security", "补丁安全", "./pages/patch-security.html", "layui-icon-shield",
                patchSecurityViewPermissionId, baseSort + 1, riskDiscoveryId);
        insertChildMenu("patch_management", "补丁管理", "./pages/patch-management.html", "layui-icon-tabs",
                installedPatchViewPermissionId, baseSort + 2, riskDiscoveryId);
        insertChildMenu("cve_management", "CVE管理", "./pages/cve-management.html", "layui-icon-dialogue",
                patchCveMapViewPermissionId, baseSort + 3, riskDiscoveryId);
        insertChildMenu("vuln_detection", "漏洞检测", "./pages/vuln-detection.html", "layui-icon-search",
                vulnViewPermissionId, baseSort + 4, riskDiscoveryId);
        insertChildMenu("vuln_rule_management", "漏洞库管理", "./pages/vuln-rule-management.html", "layui-icon-table",
                vulnRuleViewPermissionId, baseSort + 5, riskDiscoveryId);
        insertChildMenu("vuln_ops_dashboard", "漏洞运营仪表盘", "./pages/vuln-ops-dashboard.html", "layui-icon-chart-screen",
                vulnOpsPermissionId, baseSort + 6, riskDiscoveryId);

        updateMenuParent("patch_security", riskDiscoveryId, patchSecurityViewPermissionId, "./pages/patch-security.html");
        updateMenuParent("patch_management", riskDiscoveryId, installedPatchViewPermissionId, "./pages/patch-management.html");
        updateMenuParent("cve_management", riskDiscoveryId, patchCveMapViewPermissionId, "./pages/cve-management.html");
        updateMenuParent("vuln_detection", riskDiscoveryId, vulnViewPermissionId, "./pages/vuln-detection.html");
        updateMenuParent("vuln_rule_management", riskDiscoveryId, vulnRuleViewPermissionId, "./pages/vuln-rule-management.html");
        updateMenuParent("vuln_ops_dashboard", riskDiscoveryId, vulnOpsPermissionId, "./pages/vuln-ops-dashboard.html");
    }

    private void insertChildMenu(String menuCode,
                                 String menuName,
                                 String menuPath,
                                 String menuIcon,
                                 Long permissionId,
                                 int sortOrder,
                                 Long parentId) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT ?, ?, ?, ?, ?, ?, 1, ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = ?)
                """, menuCode, menuName, menuPath, menuIcon, permissionId, sortOrder, parentId, menuCode);
    }

    private void updateMenuParent(String menuCode, Long parentId, Long permissionId, String menuPath) {
        jdbcTemplate.update("""
                UPDATE sys_menu
                SET parent_id = ?, menu_path = ?, permission_id = ?
                WHERE menu_code = ?
                """, parentId, menuPath, permissionId, menuCode);
    }

    private Long permissionId(String permissionCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_permission WHERE permission_code = ?", Long.class, permissionCode);
    }

    private Long menuId(String menuCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE menu_code = ?", Long.class, menuCode);
    }

    private void grant(String roleCode, String... permissionCodes) {
        for (String permissionCode : permissionCodes) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission (role_id, permission_id)
                    SELECT r.id, p.id
                    FROM sys_role r
                    JOIN sys_permission p ON p.permission_code = ?
                    WHERE r.role_code = ?
                      AND NOT EXISTS (
                        SELECT 1 FROM sys_role_permission rp
                        WHERE rp.role_id = r.id AND rp.permission_id = p.id
                    )
                    """, permissionCode, roleCode);
        }
    }
}
