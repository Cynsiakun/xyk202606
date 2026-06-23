package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class TenantMachineRbacInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(3)
    public ApplicationRunner initTenantMachineRbac() {
        return args -> {
            insertRole("TENANT_ADMIN", "Tenant Admin");
            insertPermission("tenant-machine:view", "查看授权主机", "/api/tenant-machines/list");
            insertPermission("tenant-machine:create", "新增授权主机", "/api/tenant-machines");
            insertPermission("tenant-machine:update", "修改授权主机", "/api/tenant-machines/{id}");
            insertPermission("tenant-machine:delete", "删除授权主机", "/api/tenant-machines/{id}");

            grant("TENANT_ADMIN", "tenant-machine:view");
            grant("TENANT_ADMIN", "tenant-machine:create");
            grant("TENANT_ADMIN", "tenant-machine:update");
            grant("TENANT_ADMIN", "tenant-machine:delete");
        };
    }

    private void insertRole(String roleCode, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status)
                SELECT ?, ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = ?)
                """, roleCode, roleName, roleCode);
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void grant(String roleCode, String permissionCode) {
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
