package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class RbacMenuInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public ApplicationRunner initRbacMenus() {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sys_menu (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        menu_code VARCHAR(50) NOT NULL UNIQUE,
                        menu_name VARCHAR(100) NOT NULL,
                        menu_path VARCHAR(255) NOT NULL,
                        menu_icon VARCHAR(100),
                        permission_id BIGINT,
                        sort_order INT DEFAULT 0,
                        status TINYINT DEFAULT 1
                    )
                    """);

            insertMenu("dashboard", "后台主页", "./pages/dashboard.html", "layui-icon-home", "dashboard:view", 1);
            insertMenu("user", "用户管理", "./pages/user.html", "layui-icon-user", "user:view", 2);
            insertMenu("log", "登录日志", "./pages/log.html", "layui-icon-log", "login-log:view", 3);
            insertMenuWithoutPermission("profile", "个人信息", "./pages/profile.html", "layui-icon-about", 4);
            insertMenu("role", "角色管理", "./pages/role.html", "layui-icon-group", "role:view", 5);
            insertMenu("permission", "权限管理", "./pages/permission.html", "layui-icon-auz", "permission:view", 6);
        };
    }

    private void insertMenu(String menuCode,
                            String menuName,
                            String menuPath,
                            String menuIcon,
                            String permissionCode,
                            int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
                SELECT ?, ?, ?, ?, p.id, ?, 1
                FROM sys_permission p
                WHERE p.permission_code = ?
                  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = ?)
                """, menuCode, menuName, menuPath, menuIcon, sortOrder, permissionCode, menuCode);
    }

    private void insertMenuWithoutPermission(String menuCode,
                                             String menuName,
                                             String menuPath,
                                             String menuIcon,
                                             int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
                SELECT ?, ?, ?, ?, NULL, ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = ?)
                """, menuCode, menuName, menuPath, menuIcon, sortOrder, menuCode);
    }
}
