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
            insertPermission("vuln-detection:view", "查看漏洞检测结果", "/api/vuln-detection/**");
            insertPermission("vuln-detection:analyze", "执行漏洞规则匹配", "/api/vuln-detection/**");

            insertMenus();
            grant("SECURITY_ADMIN", "patch-security:view", "patch-security:analyze", "patch-security:scan");
            grant("ANALYST", "patch-security:view", "patch-security:analyze", "patch-security:scan");
            grant("AUDITOR", "patch-security:view");
            grant("SECURITY_ADMIN", "vuln-detection:view", "vuln-detection:analyze");
            grant("ANALYST", "vuln-detection:view", "vuln-detection:analyze");
            grant("AUDITOR", "vuln-detection:view");
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

        Long viewPermissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_permission WHERE permission_code = 'patch-security:view'", Long.class);
        Long vulnViewPermissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_permission WHERE permission_code = 'vuln-detection:view'", Long.class);

        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'risk_discovery', '风险发现', '#', 'layui-icon-vercode', ?, ?, 1, NULL
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'risk_discovery')
                """, viewPermissionId, baseSort);

        Long parentId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE menu_code = 'risk_discovery'", Long.class);

        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'patch_security', '补丁安全', './pages/patch-security.html', 'layui-icon-shield', ?, ?, 1, ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'patch_security')
                """, viewPermissionId, baseSort + 1, parentId);

        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'vuln_detection', '漏洞检测', './pages/vuln-detection.html', 'layui-icon-search', ?, ?, 1, ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'vuln_detection')
                """, vulnViewPermissionId, baseSort + 2, parentId);

        jdbcTemplate.update("""
                UPDATE sys_menu
                SET parent_id = ?, menu_path = './pages/patch-security.html', permission_id = ?
                WHERE menu_code = 'patch_security'
                """, parentId, viewPermissionId);

        jdbcTemplate.update("""
                UPDATE sys_menu
                SET parent_id = ?, menu_path = './pages/vuln-detection.html', permission_id = ?
                WHERE menu_code = 'vuln_detection'
                """, parentId, vulnViewPermissionId);
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
