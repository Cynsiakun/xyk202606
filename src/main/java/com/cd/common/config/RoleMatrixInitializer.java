package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 角色 → 权限矩阵初始化：启动时幂等地补齐内置角色、按矩阵授予权限，并创建演示账号。
 *
 * <p>在 {@link HostRbacInitializer}（{@code @Order(0)}）与资产/角色权限注册（{@code @Order(1)}）
 * 之后执行（{@code @Order(2)}），确保 host:*、asset:* 权限均已注册，授权才不会被静默跳过。</p>
 *
 * <p>超级管理员（{@code SUPER_ADMIN}）走通配放行，不在此矩阵内逐条授权。</p>
 */
@Configuration
@RequiredArgsConstructor
public class RoleMatrixInitializer {

    private final JdbcTemplate jdbcTemplate;

    private static final String DEMO_PWD_MD5 = "e10adc3949ba59abbe56e057f20f883e"; // 123456

    @Bean
    @Order(2)
    public ApplicationRunner initRoleMatrix() {
        return args -> {
            insertRole("SECURITY_ADMIN", "安全管理员");
            insertRole("ANALYST", "分析员");
            insertRole("AUDITOR", "审计员");

            grant("SECURITY_ADMIN",
                    "dashboard:view",
                    "user:view", "user:create", "user:update", "user:delete",
                    "host:view", "host:create", "host:update", "host:delete",
                    "asset:view", "asset:delete", "asset:export",
                    "role:view", "permission:view", "login-log:view");

            grant("ANALYST",
                    "dashboard:view",
                    "host:view", "host:update", "host:asset:view",
                    "login-log:view");

            grant("AUDITOR",
                    "dashboard:view",
                    "user:view", "host:view", "host:asset:view", "asset:export",
                    "role:view", "permission:view", "login-log:view");

            insertDemoUser("security", "SECURITY_ADMIN");
            insertDemoUser("analyst", "ANALYST");
            insertDemoUser("auditor", "AUDITOR");
        };
    }

    private void insertRole(String roleCode, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status)
                SELECT ?, ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = ?)
                """, roleCode, roleName, roleCode);
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
}
