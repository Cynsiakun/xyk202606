package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 主机管理初始化：启动时幂等地创建 {@code hosts} 表、注册 host:* 权限，并注册"主机管理"菜单。
 *
 * <p>超级管理员通过通配放行（{@code ROLE_SUPER_ADMIN}）自动拥有这些权限，无需逐条授予。</p>
 */
@Configuration
@RequiredArgsConstructor
public class HostRbacInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(0)
    public ApplicationRunner initHostModule() {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS hosts (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        hostname VARCHAR(255),
                        ipv4 VARCHAR(64),
                        mac_address VARCHAR(64) NOT NULL UNIQUE,
                        os_name VARCHAR(100),
                        os_version VARCHAR(100),
                        os_arch VARCHAR(50),
                        os_release VARCHAR(100),
                        cpu_model VARCHAR(255),
                        cpu_physical_cores INT,
                        cpu_logical_cores INT,
                        mem_total VARCHAR(50),
                        mem_used VARCHAR(50),
                        mem_available VARCHAR(50),
                        mem_usage VARCHAR(50),
                        status TINYINT DEFAULT 1,
                        last_scan_time DATETIME,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);
            addColumnIfAbsent("hosts", "last_scan_time", "DATETIME");

            // ——— 全局自动探测策略表（系统仅维护一行 id=1） ———
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS probe_strategy (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        enabled TINYINT DEFAULT 0,
                        period_hours INT DEFAULT 8,
                        probe_account TINYINT DEFAULT 1,
                        probe_service TINYINT DEFAULT 1,
                        probe_process TINYINT DEFAULT 1,
                        probe_app TINYINT DEFAULT 1,
                        last_run_at DATETIME,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);
            jdbcTemplate.update("""
                    INSERT INTO probe_strategy (id, enabled, period_hours, probe_account, probe_service, probe_process, probe_app)
                    SELECT 1, 0, 8, 1, 1, 1, 1
                    WHERE NOT EXISTS (SELECT 1 FROM probe_strategy WHERE id = 1)
                    """);

            insertPermission("host:view", "查看主机", "/api/host/list");
            insertPermission("host:create", "新增主机", "/api/host");
            insertPermission("host:update", "修改主机", "/api/host/{id}");
            insertPermission("host:delete", "删除主机", "/api/host/{id}");
            insertPermission("host:probe", "资产探测", "/api/host/probe");
            insertPermission("host:asset:view", "查看主机资产", "/api/assets/host-latest");

            insertHostMenu();
        };
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void addColumnIfAbsent(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
            // Column already exists.
        }
    }

    private void insertHostMenu() {
        Integer maxSort = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        int sortOrder = (maxSort == null ? 0 : maxSort) + 1;
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
                SELECT 'host', '主机管理', './pages/host.html', 'layui-icon-component', p.id, ?, 1
                FROM sys_permission p
                WHERE p.permission_code = 'host:view'
                  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'host')
                """, sortOrder);
        jdbcTemplate.update("""
                UPDATE sys_menu
                SET menu_icon = 'layui-icon-component'
                WHERE menu_code = 'host'
                  AND (menu_icon IS NULL OR menu_icon = '' OR menu_icon IN ('layui-icon-screen', 'layui-icon-app'))
                """);
    }
}
