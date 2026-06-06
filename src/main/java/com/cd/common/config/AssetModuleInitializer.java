package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 资产探测模块初始化：启动时幂等地创建资产业务表、异常记录表，并注册菜单与权限。
 *
 * <p>四张资产表结构统一：id / task_id / host_name / mac_address / asset_count / asset_json / 时间戳 / deleted逻辑删除。
 * mq_error_logs 用于存储校验失败的原始消息，避免丢失与无限重试。</p>
 */
@Configuration
@RequiredArgsConstructor
public class AssetModuleInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(1)
    public ApplicationRunner initAssetModule() {
        return args -> {
            // ——— 业务表 ———
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS services (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS processes (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS apps (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            // ——— 异常记录表 ———
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS mq_error_logs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        queue_name VARCHAR(128),
                        raw_message LONGTEXT,
                        error_reason VARCHAR(512),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS asset_export_logs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_id BIGINT,
                        host_id BIGINT NOT NULL,
                        export_time DATETIME NOT NULL,
                        export_format VARCHAR(16) NOT NULL,
                        ip_address VARCHAR(64)
                    )
                    """);

            // ——— 兼容旧表：补建 deleted 列（幂等，列已存在时报错静默忽略） ———
            addColumnIfAbsent("accounts", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("services", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("processes", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("apps", "deleted", "TINYINT DEFAULT 0");

            // ——— 权限 ———
            insertPermission("asset:view", "查看资产管理", "/api/assets/**");
            insertPermission("asset:delete", "删除资产记录", "/api/assets/*/delete");
            insertPermission("asset:export", "导出资产清单", "/api/asset/export/**");

            // ——— 菜单（资产管理 > 5 个子项） ———
            insertAssetMenus();
        };
    }

    private void addColumnIfAbsent(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
            // 列已存在时忽略
        }
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void insertAssetMenus() {
        Integer maxSort = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        int baseSort = (maxSort == null ? 0 : maxSort) + 1;

        Integer assetViewPermId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_permission WHERE permission_code = 'asset:view'", Integer.class);

        // 迁移旧数据：将旧版扁平结构的父菜单（asset_overview 且无父级）重命名为 asset_parent
        jdbcTemplate.update("""
                UPDATE sys_menu SET menu_code = 'asset_parent', menu_name = '资产管理',
                menu_path = '#', menu_icon = 'layui-icon-component'
                WHERE menu_code = 'asset_overview' AND parent_id IS NULL
                """);

        // 一级菜单：资产管理（无页面，仅作分组折叠，# 占位）
        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'asset_parent', '资产管理', '#', 'layui-icon-component', ?, ?, 1, NULL
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'asset_parent')
                """, assetViewPermId, baseSort);

        // 获取父菜单 ID
        Long parentId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE menu_code = 'asset_parent'", Long.class);

        // 移除已废弃的“总览”子菜单（asset_overview 作为子级时）
        jdbcTemplate.update("DELETE FROM sys_menu WHERE menu_code = 'asset_overview'");

        // 二级菜单（按顺序）
        String[][] subMenus = {
                {"asset_account",    "账号资产",  "./pages/asset-account.html",  "layui-icon-user"},
                {"asset_service",    "服务资产",  "./pages/asset-service.html",  "layui-icon-service"},
                {"asset_process",    "进程资产",  "./pages/asset-process.html",  "layui-icon-engine"},
                {"asset_app",        "APP资产",   "./pages/asset-app.html",      "layui-icon-app"},
        };

        for (int i = 0; i < subMenus.length; i++) {
            int sort = baseSort + 1 + i;
            jdbcTemplate.update("""
                    INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                    SELECT ?, ?, ?, ?, ?, ?, 1, ?
                    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = ?)
                    """, subMenus[i][0], subMenus[i][1], subMenus[i][2], subMenus[i][3],
                    assetViewPermId, sort, parentId, subMenus[i][0]);
        }

        // 修正旧数据：将已存在但 parent_id 为 NULL 的子菜单关联到父菜单
        // （新插入的行已在上面设置了 parent_id，不会被此 UPDATE 影响）
        String[] subMenuCodes = {"asset_account", "asset_service", "asset_process", "asset_app"};
        for (String code : subMenuCodes) {
            jdbcTemplate.update(
                    "UPDATE sys_menu SET parent_id = ? WHERE menu_code = ? AND parent_id IS NULL",
                    parentId, code);
        }
    }
}
