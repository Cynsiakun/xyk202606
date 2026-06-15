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
    private static final String DEMO_PWD_MD5 = "e10adc3949ba59abbe56e057f20f883e"; // 123456

    @Bean
    @Order(4)
    public ApplicationRunner initBaselineMenu() {
        return args -> {
            createWorkorderTable();
            ensureRemediationColumns();
            ensureWorkorderColumns();

            insertPermission("baseline:view", "查看基线任务", "/api/baseline/**");
            insertPermission("baseline:create", "创建并下发基线任务", "/api/baseline/tasks");
            insertPermission("baseline:remediate", "主机加固与工单", "/api/baseline/**");
            insertPermission("baseline-rule:view", "查看基线规则", "/api/baseline/rule-management/**");
            insertPermission("baseline-rule:create", "新增基线规则", "/api/baseline/rule-management");
            insertPermission("baseline-rule:update", "修改基线规则", "/api/baseline/rule-management/**");
            insertPermission("baseline-rule:delete", "删除基线规则", "/api/baseline/rule-management/**");
            insertPermission("workorder:view", "查看安全工单", "/api/baseline/workorders/**");
            insertPermission("workorder:process", "处理安全工单", "/api/baseline/workorders/**");
            insertPermission("workorder:complete", "完成安全工单", "/api/baseline/workorders/**");

            insertRole("SECURITY_OPERATOR", "安全运维工程师");
            insertDemoUser("operator", "SECURITY_OPERATOR");

            insertMenus();

            grant("SECURITY_ADMIN", "baseline:view", "baseline:create", "baseline:remediate");
            grant("SECURITY_ADMIN", "baseline-rule:view", "baseline-rule:create", "baseline-rule:update", "baseline-rule:delete");
            grant("SECURITY_ADMIN", "workorder:view", "workorder:process", "workorder:complete");
            grant("SECURITY_OPERATOR", "workorder:view", "workorder:process", "workorder:complete");
            grant("ANALYST", "baseline:view", "baseline:create", "baseline:remediate");
            grant("AUDITOR", "baseline:view");
        };
    }

    private void createWorkorderTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS baseline_workorder (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    result_id BIGINT DEFAULT NULL,
                    host_id BIGINT NOT NULL,
                    rule_id BIGINT DEFAULT NULL,
                    title VARCHAR(255) DEFAULT NULL,
                    advice TEXT DEFAULT NULL,
                    assignee_id BIGINT DEFAULT NULL,
                    priority VARCHAR(20) DEFAULT 'MEDIUM',
                    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                    close_remark TEXT DEFAULT NULL,
                    create_by BIGINT DEFAULT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    start_time DATETIME DEFAULT NULL,
                    finish_time DATETIME DEFAULT NULL,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_workorder_host (host_id),
                    KEY idx_workorder_result (result_id),
                    KEY idx_workorder_assignee (assignee_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线整改工单'
                """);
    }

    private void ensureWorkorderColumns() {
        addColumnIfMissing("baseline_workorder", "result_id",
                "ALTER TABLE baseline_workorder ADD COLUMN result_id BIGINT DEFAULT NULL AFTER id");
        addColumnIfMissing("baseline_workorder", "title",
                "ALTER TABLE baseline_workorder ADD COLUMN title VARCHAR(255) DEFAULT NULL AFTER rule_id");
        addColumnIfMissing("baseline_workorder", "advice",
                "ALTER TABLE baseline_workorder ADD COLUMN advice TEXT DEFAULT NULL AFTER title");
        addColumnIfMissing("baseline_workorder", "assignee_id",
                "ALTER TABLE baseline_workorder ADD COLUMN assignee_id BIGINT DEFAULT NULL AFTER advice");
        addColumnIfMissing("baseline_workorder", "priority",
                "ALTER TABLE baseline_workorder ADD COLUMN priority VARCHAR(20) DEFAULT 'MEDIUM' AFTER assignee_id");
        addColumnIfMissing("baseline_workorder", "close_remark",
                "ALTER TABLE baseline_workorder ADD COLUMN close_remark TEXT DEFAULT NULL AFTER status");
        addColumnIfMissing("baseline_workorder", "create_by",
                "ALTER TABLE baseline_workorder ADD COLUMN create_by BIGINT DEFAULT NULL AFTER close_remark");
        addColumnIfMissing("baseline_workorder", "start_time",
                "ALTER TABLE baseline_workorder ADD COLUMN start_time DATETIME DEFAULT NULL AFTER create_time");
        addColumnIfMissing("baseline_workorder", "finish_time",
                "ALTER TABLE baseline_workorder ADD COLUMN finish_time DATETIME DEFAULT NULL AFTER start_time");
        addColumnIfMissing("baseline_workorder", "update_time",
                "ALTER TABLE baseline_workorder ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER finish_time");
    }

    private void ensureRemediationColumns() {
        if (!columnExists("baseline_remediation", "operator")) {
            jdbcTemplate.execute("""
                    ALTER TABLE baseline_remediation
                    ADD COLUMN operator VARCHAR(64) DEFAULT NULL COMMENT '执行操作人'
                    AFTER execute_script
                    """);
        }
        if (!columnExists("baseline_remediation", "message")) {
            jdbcTemplate.execute("""
                    ALTER TABLE baseline_remediation
                    ADD COLUMN message VARCHAR(2000) DEFAULT NULL COMMENT '执行结果消息'
                    AFTER operator
                    """);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
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

        Long ruleViewPermissionId = permissionId("baseline-rule:view");
        insertChildMenu("baseline_rule", "基线规则管理", "./pages/baseline-rule.html", "layui-icon-table",
                ruleViewPermissionId, baseSort + 3, baselineModuleId);
        updateMenuParent("baseline_rule", baselineModuleId, ruleViewPermissionId, "./pages/baseline-rule.html");

        Long workorderViewPermissionId = permissionId("workorder:view");
        insertChildMenu("baseline_workorder", "安全工单管理", "./pages/baseline-workorder.html", "layui-icon-survey",
                workorderViewPermissionId, baseSort + 4, baselineModuleId);
        updateMenuParent("baseline_workorder", baselineModuleId, workorderViewPermissionId, "./pages/baseline-workorder.html");
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void insertRole(String roleCode, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status)
                SELECT ?, ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = ?)
                """, roleCode, roleName, roleCode);
    }

    private void insertDemoUser(String userName, String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO user (user_name, user_pwd, status)
                SELECT ?, ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM user WHERE user_name = ?)
                """, userName, DEMO_PWD_MD5, userName);

        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT u.id, r.id
                FROM user u
                JOIN sys_role r ON r.role_code = ?
                WHERE u.user_name = ?
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
                  )
                """, roleCode, userName);
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        if (!columnExists(tableName, columnName)) {
            jdbcTemplate.execute(ddl);
        }
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
