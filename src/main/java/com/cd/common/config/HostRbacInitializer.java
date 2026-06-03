package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 主机管理初始化：启动时幂等地创建 {@code hosts} 表、注册 host:* 权限、
 * 将权限补授给 SUPER_ADMIN，并注册"主机管理"菜单。
 *
 * <p>Order 设为较小值，确保在 {@link RbacMenuInitializer} 之前完成，使得权限/角色授权
 * 在菜单初始化前就绪。</p>
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
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            insertPermission("host:view", "查看主机", "/api/host/list");
            insertPermission("host:create", "新增主机", "/api/host");
            insertPermission("host:update", "修改主机", "/api/host/{id}");
            insertPermission("host:delete", "删除主机", "/api/host/{id}");

            grantAllPermissionsToSuperAdmin();

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

    private void grantAllPermissionsToSuperAdmin() {
        jdbcTemplate.update("""
                INSERT INTO sys_role_permission (role_id, permission_id)
                SELECT r.id, p.id
                FROM sys_role r
                JOIN sys_permission p
                WHERE r.role_code = 'SUPER_ADMIN'
                  AND NOT EXISTS (
                    SELECT 1 FROM sys_role_permission rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id
                  )
                """);
    }

    private void insertHostMenu() {
        Integer maxSort = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        int sortOrder = (maxSort == null ? 0 : maxSort) + 1;
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
                SELECT 'host', '主机管理', './pages/host.html', 'layui-icon-screen', p.id, ?, 1
                FROM sys_permission p
                WHERE p.permission_code = 'host:view'
                  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'host')
                """, sortOrder);
    }
}
