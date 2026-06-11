package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 在「安全日志」父菜单下注册「登录日志」「账户变更日志」两个子菜单，并初始化相关权限与角色授权。
 * 全部 insert-if-absent，幂等可重复执行。
 */
@Configuration
@RequiredArgsConstructor
public class SecuritySubLogMenuInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(5)
    public ApplicationRunner initSecuritySubLogMenu() {
        return args -> {
            insertPermission("login-security-log:view", "查看登录日志", "/api/login-security-log/**");
            insertPermission("account-change-log:view", "查看账户变更日志", "/api/account-change-log/**");

            Long loginViewPermissionId = permissionId("login-security-log:view");
            Long accountViewPermissionId = permissionId("account-change-log:view");
            Long parentId = ensureParentMenu(loginViewPermissionId);

            insertChildMenu("login_security_log", "登录日志", "./pages/login-security-log.html", "layui-icon-login",
                    loginViewPermissionId, nextSortOrder(), parentId);
            jdbcTemplate.update("""
                    UPDATE sys_menu
                    SET parent_id = ?, menu_path = './pages/login-security-log.html', permission_id = ?
                    WHERE menu_code = 'login_security_log'
                    """, parentId, loginViewPermissionId);

            insertChildMenu("account_change_log", "账户变更日志", "./pages/account-change-log.html", "layui-icon-friends",
                    accountViewPermissionId, nextSortOrder(), parentId);
            jdbcTemplate.update("""
                    UPDATE sys_menu
                    SET parent_id = ?, menu_path = './pages/account-change-log.html', permission_id = ?
                    WHERE menu_code = 'account_change_log'
                    """, parentId, accountViewPermissionId);

            grant("SECURITY_ADMIN", "login-security-log:view", "account-change-log:view");
            grant("ANALYST", "login-security-log:view", "account-change-log:view");
            grant("AUDITOR", "login-security-log:view", "account-change-log:view");
        };
    }

    /** 确保「安全日志」父菜单存在，返回其 id。 */
    private Long ensureParentMenu(Long fallbackPermissionId) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'security_log_parent', '安全日志', '#', 'layui-icon-log', ?, ?, 1, NULL
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'security_log_parent')
                """, fallbackPermissionId, nextSortOrder());
        return menuId("security_log_parent");
    }

    private int nextSortOrder() {
        Integer maxSort = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        return (maxSort == null ? 0 : maxSort) + 1;
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void insertChildMenu(String menuCode, String menuName, String menuPath, String menuIcon,
                                 Long permissionId, int sortOrder, Long parentId) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT ?, ?, ?, ?, ?, ?, 1, ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = ?)
                """, menuCode, menuName, menuPath, menuIcon, permissionId, sortOrder, parentId, menuCode);
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
