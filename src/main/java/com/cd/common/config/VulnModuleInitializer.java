package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class VulnModuleInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(2)
    public ApplicationRunner initVulnModule() {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS vuln_rule (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        product_type VARCHAR(32) NOT NULL,
                        product_name VARCHAR(255) NOT NULL,
                        match_type VARCHAR(32) NOT NULL,
                        affected_version_expr VARCHAR(512),
                        severity VARCHAR(32),
                        title VARCHAR(255) NOT NULL,
                        suggestion TEXT,
                        enabled TINYINT DEFAULT 1,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_vuln_rule_enabled_type_name (enabled, product_type, product_name)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS host_vuln_result (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        host_id BIGINT NOT NULL,
                        rule_id BIGINT NOT NULL,
                        severity VARCHAR(32),
                        vuln_name VARCHAR(255),
                        product_name VARCHAR(255),
                        product_version VARCHAR(255),
                        suggestion TEXT,
                        status TINYINT DEFAULT 1,
                        verify_status VARCHAR(32) DEFAULT 'PENDING',
                        evidence_json LONGTEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_host_vuln_result_host_status (host_id, status),
                        INDEX idx_host_vuln_result_rule (rule_id)
                    )
                    """);

            addColumnIfAbsent("host_vuln_result", "verify_status", "VARCHAR(32) DEFAULT 'PENDING'");
            addColumnIfAbsent("host_vuln_result", "evidence_json", "LONGTEXT");

            insertPermission("vuln-detection:view", "查看漏洞检测结果", "/api/vuln-detection/**");
            insertPermission("vuln-detection:analyze", "执行漏洞规则匹配", "/api/vuln-detection/**");
            grantPermission("SUPER_ADMIN", "vuln-detection:view");
            grantPermission("SUPER_ADMIN", "vuln-detection:analyze");
            grantPermission("SECURITY_ADMIN", "vuln-detection:view");
            grantPermission("SECURITY_ADMIN", "vuln-detection:analyze");
            grantPermission("ANALYST", "vuln-detection:view");
            grantPermission("AUDITOR", "vuln-detection:view");
        };
    }

    private void addColumnIfAbsent(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
            // Column already exists.
        }
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void grantPermission(String roleCode, String permissionCode) {
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
