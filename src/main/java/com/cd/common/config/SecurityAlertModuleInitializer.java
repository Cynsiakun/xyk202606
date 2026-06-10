package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
public class SecurityAlertModuleInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(3)
    public ApplicationRunner initSecurityAlertModule() {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS security_alerts (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        source_log_id BIGINT NOT NULL COMMENT '对应 windows_event_logs.id',
                        host_id BIGINT NOT NULL,
                        event_id INT NOT NULL,
                        rule_code VARCHAR(64) DEFAULT NULL,
                        dedup_key VARCHAR(255) DEFAULT NULL,
                        alert_name VARCHAR(255) NOT NULL,
                        level VARCHAR(20) NOT NULL COMMENT 'Critical/High/Medium/Low',
                        risk_score INT DEFAULT 0,
                        description VARCHAR(1000) DEFAULT NULL,
                        evidence_json LONGTEXT,
                        status VARCHAR(20) DEFAULT 'new' COMMENT 'new/acked/resolved/ignored',
                        event_time DATETIME NOT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_host_time (host_id, event_time),
                        KEY idx_level (level),
                        KEY idx_status (status),
                        KEY idx_source_log (source_log_id),
                        KEY idx_rule_code (rule_code),
                        UNIQUE KEY uk_source_rule (source_log_id, rule_code),
                        KEY idx_dedup_status_time (dedup_key, status, create_time)
                    )
                    """);

            addColumnIfAbsent("security_alerts", "rule_code", "VARCHAR(64) DEFAULT NULL");
            addColumnIfAbsent("security_alerts", "dedup_key", "VARCHAR(255) DEFAULT NULL");
            addColumnIfAbsent("security_alerts", "risk_score", "INT DEFAULT 0");
            addColumnIfAbsent("security_alerts", "evidence_json", "LONGTEXT");
            addIndexIfAbsent("security_alerts", "idx_rule_code", "(rule_code)");
            addUniqueIndexIfAbsent("security_alerts", "uk_source_rule", "(source_log_id, rule_code)");
            addIndexIfAbsent("security_alerts", "idx_dedup_status_time", "(dedup_key, status, create_time)");
        };
    }

    private void addColumnIfAbsent(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
            // Column already exists.
        }
    }

    private void addIndexIfAbsent(String table, String indexName, String columns) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD INDEX " + indexName + " " + columns);
        } catch (Exception ignored) {
            // Index already exists.
        }
    }

    private void addUniqueIndexIfAbsent(String table, String indexName, String columns) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD UNIQUE INDEX " + indexName + " " + columns);
        } catch (Exception ignored) {
            // Index already exists.
        }
    }
}
