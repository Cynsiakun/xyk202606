package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 合规基线模块的权限与侧边栏菜单初始化：登记 {@code baseline:view/create} 权限、授权角色，
 * 并在 sys_menu 中插入「合规基线」分组与「基线任务管理」页面项。幂等执行。
 */
@Configuration
@RequiredArgsConstructor
public class BaselineMenuInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(4)
    public ApplicationRunner initBaselineMenu() {
        return args -> {
            createWorkorderTable();

            insertPermission("baseline:view", "查看基线任务", "/api/baseline/**");
            insertPermission("baseline:create", "创建并下发基线任务", "/api/baseline/tasks");
            insertPermission("baseline:remediate", "主机加固与工单", "/api/baseline/**");

            insertMenus();

            grant("SECURITY_ADMIN", "baseline:view", "baseline:create", "baseline:remediate");
            grant("ANALYST", "baseline:view", "baseline:create", "baseline:remediate");
            grant("AUDITOR", "baseline:view");
        };
    }

    private void createWorkorderTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS baseline_workorder (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    host_id BIGINT NOT NULL,
                    rule_id BIGINT DEFAULT NULL,
                    result_id BIGINT DEFAULT NULL,
                    assignee VARCHAR(64) DEFAULT NULL,
                    remark VARCHAR(512) DEFAULT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                    creator VARCHAR(64) DEFAULT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_workorder_host (host_id),
                    KEY idx_workorder_result (result_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线整改工单'
                """);
    }

    private void insertMenus() {
        Integer maxSort = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        int baseSort = (maxSort == null ? 0 : maxSort) + 1;

        Long baselineViewPermissionId = permissionId("baseline:view");

        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'baseline_module', '合规基线', '#', 'layui-icon-component', ?, ?, 1, NULL
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'baseline_module')
                """, baselineViewPermissionId, baseSort);

        Long baselineModuleId = menuId("baseline_module");

        insertChildMenu("baseline_task", "基线任务管理", "./pages/baseline-task.html", "layui-icon-list",
                baselineViewPermissionId, baseSort + 1, baselineModuleId);
        updateMenuParent("baseline_task", baselineModuleId, baselineViewPermissionId, "./pages/baseline-task.html");

        insertChildMenu("baseline_host", "主机合规总览", "./pages/baseline-host.html", "layui-icon-screen-full",
                baselineViewPermissionId, baseSort + 2, baselineModuleId);
        updateMenuParent("baseline_host", baselineModuleId, baselineViewPermissionId, "./pages/baseline-host.html");
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
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
