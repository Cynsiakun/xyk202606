package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class AssetFingerprintRuleMenuInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(5)
    public ApplicationRunner initAssetFingerprintRuleMenu() {
        return args -> {
            insertPermission("asset-fingerprint-rule:view", "查看端口资产规则", "/api/asset-fingerprint-rules/**");
            insertPermission("asset-fingerprint-rule:create", "新增端口资产规则", "/api/asset-fingerprint-rules");
            insertPermission("asset-fingerprint-rule:update", "修改端口资产规则", "/api/asset-fingerprint-rules/**");
            insertPermission("asset-fingerprint-rule:delete", "删除端口资产规则", "/api/asset-fingerprint-rules/**");

            Long parentId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_menu WHERE menu_code = 'asset_parent' LIMIT 1", Long.class);
            Long permissionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_permission WHERE permission_code = 'asset-fingerprint-rule:view' LIMIT 1", Long.class);
            Integer maxSort = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu WHERE parent_id = ?", Integer.class, parentId);
            int sortOrder = (maxSort == null ? 0 : maxSort) + 1;

            jdbcTemplate.update("""
                    INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                    SELECT 'asset_fingerprint_rule', '端口资产规则管理', './pages/asset-fingerprint-rule.html', 'layui-icon-auz', ?, ?, 1, ?
                    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'asset_fingerprint_rule')
                    """, permissionId, sortOrder, parentId);

            jdbcTemplate.update("""
                    UPDATE sys_menu
                    SET parent_id = ?, permission_id = ?, menu_path = './pages/asset-fingerprint-rule.html', menu_icon = 'layui-icon-auz'
                    WHERE menu_code = 'asset_fingerprint_rule'
                    """, parentId, permissionId);
        };
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }
}
