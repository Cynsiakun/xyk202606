-- V4: tenant-aware login, database-backed License plans, and tenant admin bootstrap support.

ALTER TABLE user
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 0;

SET @idx_user_name := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND index_name = 'user_name'
);
SET @sql := IF(@idx_user_name > 0, 'ALTER TABLE user DROP INDEX user_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE user
    ADD UNIQUE KEY uk_user_tenant_name (tenant_id, user_name);

SET @idx_user_phone := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND index_name = 'user_phone'
);
SET @sql := IF(@idx_user_phone > 0, 'ALTER TABLE user DROP INDEX user_phone', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE user
    ADD UNIQUE KEY uk_user_tenant_phone (tenant_id, user_phone);

SET @idx_user_email := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND index_name = 'user_email'
);
SET @sql := IF(@idx_user_email > 0, 'ALTER TABLE user DROP INDEX user_email', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE user
    ADD UNIQUE KEY uk_user_tenant_email (tenant_id, user_email);

INSERT INTO sys_role (role_code, role_name, status)
SELECT 'TENANT_ADMIN', 'Tenant Admin', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'TENANT_ADMIN');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT tenant_role.id, rp.permission_id
FROM sys_role tenant_role
         JOIN sys_role source_role ON source_role.role_code = 'SECURITY_ADMIN'
         JOIN sys_role_permission rp ON rp.role_id = source_role.id
WHERE tenant_role.role_code = 'TENANT_ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code IN ('user:role:assign', 'host:asset:view', 'host:probe')
WHERE r.role_code = 'TENANT_ADMIN';

CREATE TABLE IF NOT EXISTS license_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    user_limit INT NOT NULL DEFAULT 0,
    host_limit INT NOT NULL DEFAULT 0,
    feature_flags TEXT,
    status TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO license_plan (code, name, user_limit, host_limit, feature_flags, status, sort_order)
VALUES
    ('TRIAL', 'Trial', 1, 10, 'HOST', 1, 1),
    ('STANDARD', 'Standard', 5, 100, 'HOST,ASSET,PATCH,VULN,LOG,BASELINE,USER', 1, 2),
    ('PROFESSIONAL', 'Professional', 20, 500, 'HOST,ASSET,PATCH,VULN,LOG,BASELINE,USER,AI', 1, 3)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    user_limit = VALUES(user_limit),
    host_limit = VALUES(host_limit),
    feature_flags = VALUES(feature_flags),
    status = VALUES(status),
    sort_order = VALUES(sort_order);

CREATE TABLE IF NOT EXISTS tenant_machine (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    machine_id VARCHAR(128),
    mac_address VARCHAR(64),
    host_name VARCHAR(255),
    remark VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    machine_bound_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_machine_machine_id (machine_id),
    UNIQUE KEY uk_tenant_machine_mac_address (mac_address),
    KEY idx_tenant_machine_tenant (tenant_id),
    KEY idx_tenant_machine_status (status)
);

ALTER TABLE tenant_machine
    ADD COLUMN IF NOT EXISTS machine_bound_at DATETIME AFTER created_by;

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'tenant-machine:view', '查看授权主机', 'API', '/api/tenant-machines/list', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'tenant-machine:view');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'tenant-machine:create', '新增授权主机', 'API', '/api/tenant-machines', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'tenant-machine:create');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'tenant-machine:update', '修改授权主机', 'API', '/api/tenant-machines/{id}', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'tenant-machine:update');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'tenant-machine:delete', '删除授权主机', 'API', '/api/tenant-machines/{id}', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'tenant-machine:delete');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p
              ON p.permission_code IN ('tenant-machine:view', 'tenant-machine:create', 'tenant-machine:update', 'tenant-machine:delete')
WHERE r.role_code = 'TENANT_ADMIN';
