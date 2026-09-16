CREATE TABLE IF NOT EXISTS activation_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    license_id BIGINT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    expire_time DATETIME NOT NULL,
    bound_machine_id VARCHAR(128) NULL,
    bound_mac_address VARCHAR(64) NULL,
    bound_host_name VARCHAR(255) NULL,
    used_at DATETIME NULL,
    created_by BIGINT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_activation_code_tenant (tenant_id),
    KEY idx_activation_code_status (status)
);
