CREATE TABLE IF NOT EXISTS test (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    status TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(50) NOT NULL UNIQUE,
    user_pwd VARCHAR(64) NOT NULL,
    user_avatar VARCHAR(255),
    user_phone VARCHAR(20) UNIQUE,
    user_email VARCHAR(100) UNIQUE,
    status TINYINT DEFAULT 1,
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_time DATETIME
);

INSERT INTO user (user_name, user_pwd, status)
SELECT 'admin', 'b358ecf888cf98e406d6017e740b7209', 1
WHERE NOT EXISTS (
    SELECT 1 FROM user WHERE user_name = 'admin'
);

CREATE TABLE IF NOT EXISTS login_log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    user_name VARCHAR(50),
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    status TINYINT,
    message VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS windows_event_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    host_id BIGINT NOT NULL COMMENT '对应 hosts.id',
    log_type VARCHAR(20) NOT NULL COMMENT 'Security/System/Application',
    event_id INT NOT NULL COMMENT 'Windows事件ID',
    event_time DATETIME NOT NULL COMMENT '事件发生时间',
    username VARCHAR(255) DEFAULT NULL COMMENT '用户名',
    level VARCHAR(20) DEFAULT NULL COMMENT 'Information/Warning/Error',
    message VARCHAR(1000) DEFAULT NULL COMMENT '摘要信息',
    record_number BIGINT NOT NULL COMMENT 'Windows RecordNumber，用于增量同步',
    raw_json LONGTEXT DEFAULT NULL COMMENT '完整XML日志，不建索引',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_host_log_rec (host_id, log_type, record_number),
    KEY idx_host_time (host_id, event_time),
    KEY idx_event_id (event_id),
    KEY idx_record_number (record_number),
    KEY idx_log_type (log_type)
);

CREATE TABLE IF NOT EXISTS login_security_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_log_id BIGINT NOT NULL COMMENT '对应 windows_event_logs.id',
    host_id BIGINT NOT NULL,
    event_id INT NOT NULL,
    event_time DATETIME NOT NULL,
    username VARCHAR(255) DEFAULT NULL,
    login_result VARCHAR(20) DEFAULT NULL COMMENT 'success/fail/logout',
    login_type INT DEFAULT NULL COMMENT '交互、RDP、服务登录等',
    source_ip VARCHAR(64) DEFAULT NULL,
    process_name VARCHAR(255) DEFAULT NULL,
    is_elevated TINYINT DEFAULT 0 COMMENT '是否提权',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_source_log (source_log_id),
    KEY idx_host_time (host_id, event_time),
    KEY idx_username (username),
    KEY idx_source_ip (source_ip),
    KEY idx_result (login_result)
);

CREATE TABLE IF NOT EXISTS account_change_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_log_id BIGINT NOT NULL COMMENT '对应 windows_event_logs.id',
    host_id BIGINT NOT NULL,
    event_id INT NOT NULL,
    event_time DATETIME NOT NULL,
    operator_username VARCHAR(255) DEFAULT NULL COMMENT '操作者',
    target_username VARCHAR(255) DEFAULT NULL COMMENT '目标用户',
    action_type VARCHAR(50) DEFAULT NULL COMMENT 'create/delete/add_admin等',
    details VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_source_log (source_log_id),
    KEY idx_host_time (host_id, event_time),
    KEY idx_operator (operator_username),
    KEY idx_target (target_username),
    KEY idx_action (action_type)
);

CREATE TABLE IF NOT EXISTS security_alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
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
    UNIQUE KEY uk_source_rule (source_log_id, rule_code),
    KEY idx_host_time (host_id, event_time),
    KEY idx_level (level),
    KEY idx_status (status),
    KEY idx_source_log (source_log_id),
    KEY idx_rule_code (rule_code),
    KEY idx_dedup_status_time (dedup_key, status, create_time)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    status TINYINT DEFAULT 1,
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    permission_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20) DEFAULT 'API',
    path VARCHAR(255),
    status TINYINT DEFAULT 1,
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_permission (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS asset_export_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    host_id BIGINT NOT NULL,
    export_time DATETIME NOT NULL,
    export_format VARCHAR(16) NOT NULL,
    ip_address VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    menu_code VARCHAR(50) NOT NULL UNIQUE,
    menu_name VARCHAR(100) NOT NULL,
    menu_path VARCHAR(255) NOT NULL,
    menu_icon VARCHAR(100),
    permission_id BIGINT,
    parent_id BIGINT DEFAULT NULL,
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1
);

INSERT INTO sys_role (role_code, role_name, status)
SELECT 'SUPER_ADMIN', '超级管理员', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN'
);

INSERT INTO sys_role (role_code, role_name, status)
SELECT 'SECURITY_ADMIN', '安全管理员', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'SECURITY_ADMIN'
);

INSERT INTO sys_role (role_code, role_name, status)
SELECT 'ANALYST', '分析员', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'ANALYST'
);

INSERT INTO sys_role (role_code, role_name, status)
SELECT 'AUDITOR', '审计员', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'AUDITOR'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'dashboard:view', '查看仪表盘', 'API', '/api/dashboard/statistics', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'dashboard:view'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'user:view', '查看用户', 'API', '/api/user/list', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'user:view'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'user:create', '新增用户', 'API', '/api/user', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'user:create'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'user:update', '修改用户', 'API', '/api/user/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'user:update'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'user:delete', '删除用户', 'API', '/api/user/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'user:delete'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'login-log:view', '查看登录日志', 'API', '/api/login-log/list', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'login-log:view'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'role:view', '查看角色', 'API', '/api/rbac/role/list', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'role:view'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'role:create', '新增角色', 'API', '/api/rbac/role', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'role:create'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'role:update', '修改角色', 'API', '/api/rbac/role/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'role:update'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'role:delete', '删除角色', 'API', '/api/rbac/role/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'role:delete'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'role:permission:assign', '分配角色权限', 'API', '/api/rbac/role/{id}/permissions', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'role:permission:assign'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'permission:view', '查看权限', 'API', '/api/rbac/permission/list', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'permission:view'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'permission:create', '新增权限', 'API', '/api/rbac/permission', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'permission:create'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'permission:update', '修改权限', 'API', '/api/rbac/permission/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'permission:update'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'permission:delete', '删除权限', 'API', '/api/rbac/permission/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'permission:delete'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'user:role:assign', '分配用户角色', 'API', '/api/rbac/user/{userId}/roles', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'user:role:assign'
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM user u
         JOIN sys_role r ON r.role_code = 'SUPER_ADMIN'
WHERE u.user_name = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

-- 注：SUPER_ADMIN 采用“通配放行”（拥有 ROLE_SUPER_ADMIN 即视为全部权限），
-- 不再向 sys_role_permission 批量授予全部权限。鉴权统一走 @perm.has(...)。

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
SELECT 'dashboard', '后台主页', './pages/dashboard.html', 'layui-icon-home', p.id, 1, 1
FROM sys_permission p
WHERE p.permission_code = 'dashboard:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'dashboard');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
SELECT 'user', '用户管理', './pages/user.html', 'layui-icon-user', p.id, 2, 1
FROM sys_permission p
WHERE p.permission_code = 'user:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'user');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
SELECT 'log', '登录日志', './pages/log.html', 'layui-icon-log', p.id, 3, 1
FROM sys_permission p
WHERE p.permission_code = 'login-log:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'log');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
SELECT 'profile', '个人信息', './pages/profile.html', 'layui-icon-about', NULL, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'profile');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
SELECT 'role', '角色管理', './pages/role.html', 'layui-icon-group', p.id, 5, 1
FROM sys_permission p
WHERE p.permission_code = 'role:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'role');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
SELECT 'permission', '权限管理', './pages/permission.html', 'layui-icon-auz', p.id, 6, 1
FROM sys_permission p
WHERE p.permission_code = 'permission:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'permission');

CREATE TABLE IF NOT EXISTS hosts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    hostname VARCHAR(255),
    ipv4 VARCHAR(64),
    mac_address VARCHAR(64) NOT NULL UNIQUE,
    os_name VARCHAR(100),
    os_version VARCHAR(100),
    os_arch VARCHAR(50),
    os_release VARCHAR(100),
    cpu_model VARCHAR(255),
    cpu_physical_cores INT,
    cpu_logical_cores INT,
    mem_total VARCHAR(50),
    mem_used VARCHAR(50),
    mem_available VARCHAR(50),
    mem_usage VARCHAR(50),
    status TINYINT DEFAULT 1,
    last_scan_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'host:view', '查看主机', 'API', '/api/host/list', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'host:view'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'host:create', '新增主机', 'API', '/api/host', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'host:create'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'host:update', '修改主机', 'API', '/api/host/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'host:update'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'host:delete', '删除主机', 'API', '/api/host/{id}', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'host:delete'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'host:asset:view', '查看主机资产', 'API', '/api/assets/host-latest', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'host:asset:view'
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'asset:export', '导出资产清单', 'API', '/api/asset/export/**', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'asset:export'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND p.permission_code IN ('host:view', 'host:create', 'host:update', 'host:delete')
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
);

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status)
SELECT 'host', '主机管理', './pages/host.html', 'layui-icon-component', p.id, 7, 1
FROM sys_permission p
WHERE p.permission_code = 'host:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'host');

-- ============================================================
-- 角色 → 权限矩阵（幂等授予）。SUPER_ADMIN 走通配放行，不在此列。
-- SECURITY_ADMIN 安全管理员：用户/主机增删改查 + 角色/权限/日志查看
-- ANALYST       分析员：仪表盘 + 主机查看与更新 + 日志查看
-- AUDITOR       审计员：只读（仪表盘/用户/主机/角色/权限/日志查看）
-- ============================================================

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p
              ON p.permission_code IN (
                  'dashboard:view',
                  'user:view', 'user:create', 'user:update', 'user:delete',
                  'host:view', 'host:create', 'host:update', 'host:delete',
                  'asset:export',
                  'role:view', 'permission:view', 'login-log:view'
              )
WHERE r.role_code = 'SECURITY_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p
              ON p.permission_code IN (
                  'dashboard:view',
                  'host:view', 'host:update', 'host:asset:view',
                  'login-log:view'
              )
WHERE r.role_code = 'ANALYST'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p
              ON p.permission_code IN (
                  'dashboard:view',
                  'user:view', 'host:view', 'host:asset:view',
                  'asset:export',
                  'role:view', 'permission:view', 'login-log:view'
              )
WHERE r.role_code = 'AUDITOR'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- ============================================================
-- 演示账号（密码均为 123456 的 MD5），用于验证非超管的受限视图。
-- ============================================================

INSERT INTO user (user_name, user_pwd, status)
SELECT 'security', 'e10adc3949ba59abbe56e057f20f883e', 1
WHERE NOT EXISTS (SELECT 1 FROM user WHERE user_name = 'security');

INSERT INTO user (user_name, user_pwd, status)
SELECT 'analyst', 'e10adc3949ba59abbe56e057f20f883e', 1
WHERE NOT EXISTS (SELECT 1 FROM user WHERE user_name = 'analyst');

INSERT INTO user (user_name, user_pwd, status)
SELECT 'auditor', 'e10adc3949ba59abbe56e057f20f883e', 1
WHERE NOT EXISTS (SELECT 1 FROM user WHERE user_name = 'auditor');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM user u
         JOIN sys_role r ON r.role_code = 'SECURITY_ADMIN'
WHERE u.user_name = 'security'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM user u
         JOIN sys_role r ON r.role_code = 'ANALYST'
WHERE u.user_name = 'analyst'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM user u
         JOIN sys_role r ON r.role_code = 'AUDITOR'
WHERE u.user_name = 'auditor'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

CREATE TABLE IF NOT EXISTS vuln_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_code VARCHAR(64),
    cve_id VARCHAR(64),
    category VARCHAR(32),
    product_type VARCHAR(32) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    match_type VARCHAR(32) NOT NULL,
    affected_version_expr VARCHAR(512),
    severity VARCHAR(32),
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    suggestion TEXT,
    verify_type VARCHAR(32),
    verify_rule LONGTEXT,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vuln_rule_enabled_type_name (enabled, product_type, product_name)
);

CREATE TABLE IF NOT EXISTS host_vuln_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT DEFAULT 0,
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
);

CREATE TABLE IF NOT EXISTS host_vuln_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_name VARCHAR(255),
    task_type VARCHAR(32) NOT NULL DEFAULT 'VULN',
    host_id BIGINT NOT NULL,
    mac_address VARCHAR(64) NOT NULL,
    scan_mode VARCHAR(32) NOT NULL DEFAULT 'SNAPSHOT',
    rule_count INT DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    triggered_by VARCHAR(64),
    started_at DATETIME,
    finished_at DATETIME,
    summary_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_host_vuln_task_host_status (host_id, status),
    INDEX idx_host_vuln_task_mac_status (mac_address, status),
    INDEX idx_host_vuln_task_created (created_at)
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'patch-security:view', '查看补丁安全风险', 'API', '/api/patch-security/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'patch-security:view');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'patch-security:analyze', '重新分析补丁风险', 'API', '/api/patch-security/**/analyze', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'patch-security:analyze');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'patch-security:scan', '下发补丁扫描', 'API', '/api/patch-security/**/scan', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'patch-security:scan');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'installed-patch:view', '查看补丁管理', 'API', '/api/installed-patch/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'installed-patch:view');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'installed-patch:create', '新增补丁记录', 'API', '/api/installed-patch', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'installed-patch:create');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'installed-patch:update', '编辑补丁记录', 'API', '/api/installed-patch/{id}', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'installed-patch:update');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'installed-patch:delete', '删除补丁记录', 'API', '/api/installed-patch/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'installed-patch:delete');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'patch-cve-map:view', '查看CVE映射', 'API', '/api/patch-cve-map/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'patch-cve-map:view');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'patch-cve-map:create', '新增CVE映射', 'API', '/api/patch-cve-map', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'patch-cve-map:create');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'patch-cve-map:update', '编辑CVE映射', 'API', '/api/patch-cve-map/{id}', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'patch-cve-map:update');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'patch-cve-map:delete', '删除CVE映射', 'API', '/api/patch-cve-map/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'patch-cve-map:delete');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'vuln-detection:view', '查看漏洞检测结果', 'API', '/api/vuln-detection/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'vuln-detection:view');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'vuln-detection:analyze', '执行漏洞规则匹配', 'API', '/api/vuln-detection/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'vuln-detection:analyze');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'vuln-ops-dashboard:view', '查看漏洞运营仪表盘', 'API', '/api/vuln-ops-dashboard/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'vuln-ops-dashboard:view');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code IN ('installed-patch:view', 'installed-patch:create', 'installed-patch:update', 'installed-patch:delete')
WHERE r.role_code IN ('SUPER_ADMIN', 'SECURITY_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code IN ('installed-patch:view', 'installed-patch:create', 'installed-patch:update')
WHERE r.role_code = 'ANALYST'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code = 'installed-patch:view'
WHERE r.role_code = 'AUDITOR'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code IN ('patch-cve-map:view', 'patch-cve-map:create', 'patch-cve-map:update', 'patch-cve-map:delete')
WHERE r.role_code IN ('SUPER_ADMIN', 'SECURITY_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code IN ('patch-cve-map:view', 'patch-cve-map:create', 'patch-cve-map:update')
WHERE r.role_code = 'ANALYST'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code = 'patch-cve-map:view'
WHERE r.role_code = 'AUDITOR'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code IN ('vuln-detection:view', 'vuln-detection:analyze')
WHERE r.role_code IN ('SUPER_ADMIN', 'SECURITY_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code = 'vuln-detection:view'
WHERE r.role_code IN ('ANALYST', 'AUDITOR')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.permission_code = 'vuln-ops-dashboard:view'
WHERE r.role_code IN ('SECURITY_ADMIN', 'ANALYST', 'AUDITOR', 'SUPER_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
SELECT 'risk_discovery', '风险发现', '#', 'layui-icon-vercode', p.id,
       COALESCE((SELECT MAX(m.sort_order) + 1 FROM sys_menu m), 10), 1, NULL
FROM sys_permission p
WHERE p.permission_code = 'patch-security:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'risk_discovery');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
SELECT 'patch_security', '补丁安全', './pages/patch-security.html', 'layui-icon-shield', p.id,
       COALESCE(parent_menu.sort_order + 1, 11), 1, parent_menu.id
FROM sys_permission p
         JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
WHERE p.permission_code = 'patch-security:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'patch_security');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
SELECT 'patch_management', '补丁管理', './pages/patch-management.html', 'layui-icon-tabs', p.id,
       COALESCE(parent_menu.sort_order + 2, 12), 1, parent_menu.id
FROM sys_permission p
         JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
WHERE p.permission_code = 'installed-patch:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'patch_management');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
SELECT 'cve_management', 'CVE管理', './pages/cve-management.html', 'layui-icon-dialogue', p.id,
       COALESCE(parent_menu.sort_order + 3, 13), 1, parent_menu.id
FROM sys_permission p
         JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
WHERE p.permission_code = 'patch-cve-map:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'cve_management');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
SELECT 'vuln_detection', '漏洞检测', './pages/vuln-detection.html', 'layui-icon-search', p.id,
       COALESCE(parent_menu.sort_order + 4, 14), 1, parent_menu.id
FROM sys_permission p
         JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
WHERE p.permission_code = 'vuln-detection:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'vuln_detection');

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
SELECT 'vuln_ops_dashboard', '漏洞运营仪表盘', './pages/vuln-ops-dashboard.html', 'layui-icon-chart-screen', p.id,
       COALESCE(parent_menu.sort_order + 5, 15), 1, parent_menu.id
FROM sys_permission p
         JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
WHERE p.permission_code = 'vuln-ops-dashboard:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'vuln_ops_dashboard');

UPDATE sys_menu child
    JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
    JOIN sys_permission p ON p.permission_code = 'patch-security:view'
SET child.parent_id = parent_menu.id,
    child.menu_path = './pages/patch-security.html',
    child.permission_id = p.id
WHERE child.menu_code = 'patch_security';

UPDATE sys_menu child
    JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
    JOIN sys_permission p ON p.permission_code = 'installed-patch:view'
SET child.parent_id = parent_menu.id,
    child.menu_path = './pages/patch-management.html',
    child.permission_id = p.id
WHERE child.menu_code = 'patch_management';

UPDATE sys_menu child
    JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
    JOIN sys_permission p ON p.permission_code = 'patch-cve-map:view'
SET child.parent_id = parent_menu.id,
    child.menu_path = './pages/cve-management.html',
    child.permission_id = p.id
WHERE child.menu_code = 'cve_management';

UPDATE sys_menu child
    JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
    JOIN sys_permission p ON p.permission_code = 'vuln-detection:view'
SET child.parent_id = parent_menu.id,
    child.menu_path = './pages/vuln-detection.html',
    child.permission_id = p.id
WHERE child.menu_code = 'vuln_detection';

UPDATE sys_menu child
    JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
    JOIN sys_permission p ON p.permission_code = 'vuln-ops-dashboard:view'
SET child.parent_id = parent_menu.id,
    child.menu_path = './pages/vuln-ops-dashboard.html',
    child.permission_id = p.id
WHERE child.menu_code = 'vuln_ops_dashboard';

-- ============================================================
-- Vulnerability rule management bootstrap
-- Ensures the rule library menu and permissions exist for both
-- fresh databases and upgraded environments.
-- ============================================================

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'vuln-rule:view', '查看漏洞库规则', 'API', '/api/vuln-rule/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'vuln-rule:view');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'vuln-rule:create', '新增漏洞库规则', 'API', '/api/vuln-rule', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'vuln-rule:create');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'vuln-rule:update', '编辑漏洞库规则', 'API', '/api/vuln-rule/{id}', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'vuln-rule:update');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
SELECT 'vuln-rule:delete', '删除漏洞库规则', 'API', '/api/vuln-rule/**', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'vuln-rule:delete');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('vuln-rule:view', 'vuln-rule:create', 'vuln-rule:update', 'vuln-rule:delete')
WHERE r.role_code IN ('SUPER_ADMIN', 'SECURITY_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('vuln-rule:view', 'vuln-rule:create', 'vuln-rule:update')
WHERE r.role_code = 'ANALYST'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'vuln-rule:view'
WHERE r.role_code = 'AUDITOR'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
SELECT 'vuln_rule_management', '漏洞库管理', './pages/vuln-rule-management.html', 'layui-icon-table', p.id,
       COALESCE(parent_menu.sort_order + 5, 15), 1, parent_menu.id
FROM sys_permission p
JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
WHERE p.permission_code = 'vuln-rule:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'vuln_rule_management');

UPDATE sys_menu child
JOIN sys_menu parent_menu ON parent_menu.menu_code = 'risk_discovery'
JOIN sys_permission p ON p.permission_code = 'vuln-rule:view'
SET child.parent_id = parent_menu.id,
    child.menu_path = './pages/vuln-rule-management.html',
    child.permission_id = p.id
WHERE child.menu_code = 'vuln_rule_management';

-- ============================================================
-- Tenant and license foundation
-- Current phase only creates base models and keeps the system
-- running in single-tenant mode.
-- ============================================================

CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    contact VARCHAR(100),
    status INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS license (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    license_key VARCHAR(64) NOT NULL UNIQUE,
    tenant_id BIGINT NOT NULL,
    edition VARCHAR(32),
    host_limit INT,
    user_limit INT,
    expire_time DATETIME,
    machine_id VARCHAR(128),
    signature VARCHAR(2048),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_license_tenant_id (tenant_id)
);

SET @OLD_SQL_MODE = @@SESSION.SQL_MODE;
SET SESSION SQL_MODE = CONCAT_WS(',', @@SESSION.SQL_MODE, 'NO_AUTO_VALUE_ON_ZERO');

UPDATE tenant
SET id = 0
WHERE name = 'Platform Tenant'
  AND contact = 'system'
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT id FROM tenant WHERE id = 0) existing_platform_tenant
  );

INSERT INTO tenant (id, name, contact, status)
SELECT 0, 'Platform Tenant', 'system', 1
WHERE NOT EXISTS (
    SELECT 1 FROM tenant WHERE id = 0
);

SET SESSION SQL_MODE = @OLD_SQL_MODE;
