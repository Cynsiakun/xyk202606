package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 资产探测模块初始化：启动时幂等地创建资产业务表、异常记录表。
 *
 * <p>四张资产表结构统一：id / task_id / host_name / mac_address / asset_count / asset_json / 时间戳。
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
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        asset_count INT,
                        asset_json LONGTEXT,
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
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS mq_error_logs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        queue_name VARCHAR(128),
                        raw_message LONGTEXT,
                        error_reason VARCHAR(512),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        };
    }
}
