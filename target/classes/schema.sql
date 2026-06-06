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
SELECT 'admin', '21232f297a57a5a743894a0e4a801fc3', 1
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
