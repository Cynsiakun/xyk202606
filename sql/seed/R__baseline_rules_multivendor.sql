-- ============================================================
-- 基线核查规则扩展：Web中间件 / 数据库 / 缓存（等保L3/L4/L5多等级）
-- 依赖：V5_baseline_multilevel.sql 已执行
-- 原则：check_key 小写下划线、不落脚本到规则层、通用规则 protection_level_id=NULL
-- ============================================================

-- ============================================================
-- 1. MySQL（15条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('DB-MY-01', '密码过期策略',                              '账户安全', '检查default_password_lifetime是否在有效期内',              'HIGH',     6, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-02', '密码最小长度',                              '账户安全', '检查validate_password.min_length不低于要求值',             'HIGH',     7, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-03', '密码复杂度策略',                            '账户安全', '检查validate_password.policy是否符合要求',                 'HIGH',     7, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-04', '登录失败锁定次数',                          '账户安全', '检查connection_control.failed_connections_threshold',       'MEDIUM',   5, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-05', '登录失败锁定时间',                          '账户安全', '检查connection_control.min_connection_delay(分钟)',         'MEDIUM',   5, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-06', '禁止远程root登录',                         '访问控制', '检查skip_networking或root@%是否不存在',                   'CRITICAL', 9, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MY-07', '删除匿名及测试用户',                        '访问控制', '检查mysql.user中是否无匿名用户和测试用户',                 'HIGH',     7, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MY-08', '禁用local_infile',                         '安全配置', '检查local_infile是否为OFF',                               'MEDIUM',   6, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MY-09', '禁用symbolic_links',                       '安全配置', '检查have_symlink是否为NO或skip_symbolic_links=YES',        'MEDIUM',   5, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MY-10', '最大连接数限制',                            '资源控制', '检查max_connections是否在限定范围内',                     'MEDIUM',   5, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-11', '错误日志级别',                              '日志审计', '检查log_error_verbosity是否符合等级要求',                  'MEDIUM',   4, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-12', '通用查询日志',                              '日志审计', '检查general_log是否按要求开启或关闭',                     'LOW',      3, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-13', '慢查询日志',                                '日志审计', '检查slow_query_log是否已启用',                             'MEDIUM',   4, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MY-14', '审计日志插件',                              '日志审计', '检查是否已安装并启用audit_log插件',                       'HIGH',     6, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MY-15', 'SSL加密连接',                               '通信安全', '检查have_ssl是否为YES或require_secure_transport=ON',       'HIGH',     7, 'MySQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND');

-- MySQL items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'default_password_lifetime', '<=', '90', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码过期不超过90天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-01' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'default_password_lifetime', '<=', '60', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码过期不超过60天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-01' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'default_password_lifetime', '<=', '30', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码过期不超过30天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-01' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'validate_password_length', '>=', '8', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于8位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-02' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'validate_password_length', '>=', '10', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于10位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-02' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'validate_password_length', '>=', '12', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于12位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-02' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'validate_password_policy', '=', 'MEDIUM', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '密码复杂度不低于MEDIUM'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-03' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'validate_password_policy', '=', 'STRONG', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '密码复杂度为STRONG'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-03' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'validate_password_policy', '=', 'STRONG', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '密码复杂度为STRONG'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-03' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_control_failed_threshold', '>=', '3', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值为3次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-04' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_control_failed_threshold', '>=', '3', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值为3次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-04' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_control_failed_threshold', '>=', '5', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值为5次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-04' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_control_min_delay', '>=', '15', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败最小延迟15分钟'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-05' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_control_min_delay', '>=', '30', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败最小延迟30分钟'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-05' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_control_min_delay', '>=', '60', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败最小延迟60分钟'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-05' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'remote_root_login_disabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '远程root登录已禁用'
FROM baseline_rule r WHERE r.rule_code = 'DB-MY-06';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'anonymous_user_count', '=', '0', 'EXACT', NULL, 'NUMBER', NULL, 1, 'AND', '不存在匿名用户'
FROM baseline_rule r WHERE r.rule_code = 'DB-MY-07';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'local_infile', '=', 'OFF', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'local_infile已关闭'
FROM baseline_rule r WHERE r.rule_code = 'DB-MY-08';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'have_symlink', '=', 'DISABLED', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', '符号链接已禁用'
FROM baseline_rule r WHERE r.rule_code = 'DB-MY-09';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_connections', '<=', '500', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大连接数不超过500'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-10' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_connections', '<=', '300', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大连接数不超过300'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-10' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_connections', '<=', '200', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大连接数不超过200'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-10' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_error_verbosity', '>=', '2', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '错误日志级别不低于2(error)'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-11' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_error_verbosity', '>=', '2', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '错误日志级别不低于2(error)'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-11' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_error_verbosity', '>=', '3', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '错误日志级别不低于3(warning)'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-11' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'general_log', '=', 'OFF', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '通用查询日志已关闭'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-12' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'general_log', '=', 'ON', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '通用查询日志已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-12' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'general_log', '=', 'ON', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '通用查询日志已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-12' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'slow_query_log', '=', 'ON', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', '慢查询日志已启用'
FROM baseline_rule r WHERE r.rule_code = 'DB-MY-13';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'audit_log_plugin_installed', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', '审计日志插件未安装'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-14' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'audit_log_plugin_installed', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', '审计日志插件已安装并启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-14' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'audit_log_plugin_installed', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', '审计日志插件已安装并启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-14' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'have_ssl', '=', 'YES', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'SSL已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-15' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'have_ssl', '=', 'YES', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'SSL已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-15' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'have_ssl', '=', 'YES', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'SSL已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MY-15' AND pl.level_code = 'L5';

-- MySQL asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'DB-MY-%' AND a.type_code = 'DB_MYSQL';

-- ============================================================
-- 2. Redis（10条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('CA-RD-01', 'requirepass密码强度',                     '认证安全', '检查requirepass是否设置且长度符合要求',                   'CRITICAL', 9, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('CA-RD-02', '禁用危险命令',                             '安全配置', '检查FLUSHALL/FLUSHDB/CONFIG/KEYS/SHUTDOWN等是否已rename', 'CRITICAL', 9, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('CA-RD-03', 'bind绑定地址限制',                         '网络安全', '检查bind是否绑定到特定IP而非0.0.0.0',                     'HIGH',     8, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('CA-RD-04', 'protected-mode保护模式',                  '网络安全', '检查protected-mode是否为yes',                              'HIGH',     7, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('CA-RD-05', 'maxmemory内存限制',                       '资源控制', '检查maxmemory是否已设置且在合理范围内',                   'MEDIUM',   5, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('CA-RD-06', '日志级别',                                 '日志审计', '检查loglevel是否符合等级要求',                             'LOW',      3, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('CA-RD-07', '日志文件路径',                             '日志审计', '检查logfile是否已配置非空路径',                            'MEDIUM',   4, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('CA-RD-08', 'RDB持久化配置',                            '数据安全', '检查save配置是否存在有效的RDB持久化策略',                  'HIGH',     7, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('CA-RD-09', 'AOF持久化启用',                            '数据安全', '检查appendonly是否为yes',                                 'HIGH',     7, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('CA-RD-10', '最大客户端连接数',                          '资源控制', '检查maxclients是否在限定范围内',                           'MEDIUM',   4, 'Redis', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND');

-- Redis items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'requirepass_length', '>=', '8', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'requirepass密码长度不小于8位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-01' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'requirepass_length', '>=', '12', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'requirepass密码长度不小于12位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-01' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'requirepass_length', '>=', '16', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'requirepass密码长度不小于16位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-01' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'dangerous_commands_renamed', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '危险命令已rename或禁用'
FROM baseline_rule r WHERE r.rule_code = 'CA-RD-02';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'bind_bound_to', '!=', '0.0.0.0', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'bind未绑定到0.0.0.0'
FROM baseline_rule r WHERE r.rule_code = 'CA-RD-03';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 2, 'bind_not_empty', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 2, 'AND', 'bind配置非空'
FROM baseline_rule r WHERE r.rule_code = 'CA-RD-03';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'protected_mode', '=', 'yes', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'protected-mode已启用'
FROM baseline_rule r WHERE r.rule_code = 'CA-RD-04';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'maxmemory_set', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'maxmemory已设置'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-05' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'maxmemory_pct', '<=', '80', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'maxmemory不超过物理内存80%'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-05' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'maxmemory_pct', '<=', '70', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'maxmemory不超过物理内存70%'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-05' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'loglevel', '=', 'notice', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '日志级别为notice'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-06' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'loglevel', '=', 'warning', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '日志级别为warning'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-06' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'loglevel', '=', 'warning', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '日志级别为warning'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-06' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'logfile_not_empty', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '日志文件路径已配置'
FROM baseline_rule r WHERE r.rule_code = 'CA-RD-07';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'rdb_save_configured', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'RDB持久化save策略已配置'
FROM baseline_rule r WHERE r.rule_code = 'CA-RD-08';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'appendonly', '=', 'yes', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'AOF持久化已启用'
FROM baseline_rule r WHERE r.rule_code = 'CA-RD-09';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'maxclients', '<=', '10000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大客户端连接数不超过10000'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-10' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'maxclients', '<=', '5000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大客户端连接数不超过5000'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-10' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'maxclients', '<=', '3000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大客户端连接数不超过3000'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'CA-RD-10' AND pl.level_code = 'L5';

-- Redis asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'CA-RD-%' AND a.type_code = 'CACHE_REDIS';

-- ============================================================
-- 3. Tomcat（12条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('MW-TC-01', 'Server信息泄露保护',                     '信息保护', '检查server.xml中Connector的server属性是否隐藏版本信息',    'MEDIUM',   5, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-02', '关闭自动部署',                             '安全配置', '检查server.xml中Host的autoDeploy是否为false',              'MEDIUM',   5, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-03', '会话超时时间',                             '会话管理', '检查web.xml中session-timeout是否在合理范围(分钟)',         'MEDIUM',   5, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-TC-04', '访问日志Valve配置',                       '日志审计', '检查server.xml是否配置了AccessLogValve',                   'MEDIUM',   4, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-05', '自定义错误页面',                           '信息保护', '检查web.xml是否配置了error-page避免默认错误页泄露信息',   'LOW',      3, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-06', '禁用不安全HTTP方法',                       '访问控制', '检查是否限制了TRACE/PUT/DELETE/OPTIONS等不安全方法',       'HIGH',     7, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-07', 'Secure Cookie标志',                       '会话安全', '检查context.xml中CookieProcessor的secure属性',            'HIGH',     7, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-08', 'HttpOnly Cookie标志',                     '会话安全', '检查是否设置了useHttpOnly=true',                           'HIGH',     7, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-09', '移除默认应用',                             '安全配置', '检查webapps下是否清除了docs/examples/manager/host-manager','HIGH',     7, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-10', 'maxPostSize限制',                         '安全配置', '检查Connector的maxPostSize是否在安全范围(字节)',           'MEDIUM',   5, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-TC-11', '禁用AJP连接器',                            '网络安全', '检查server.xml中是否注释或移除了AJP Connector(如不需要)',  'MEDIUM',   5, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-TC-12', 'SSL/TLS协议配置',                          '通信安全', '检查Connector是否配置了SSL且协议版本不低于TLSv1.2',        'HIGH',     8, 'Tomcat', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND');

-- Tomcat items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'server_info_hidden', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'Server信息已隐藏'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-01';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'auto_deploy', '=', 'false', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'autoDeploy已关闭'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-02';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'session_timeout', '<=', '30', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '会话超时不超过30分钟'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-TC-03' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'session_timeout', '<=', '20', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '会话超时不超过20分钟'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-TC-03' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'session_timeout', '<=', '15', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '会话超时不超过15分钟'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-TC-03' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'access_log_valve_enabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'AccessLogValve已配置'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-04';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'error_page_configured', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '自定义错误页面已配置'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-05';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'unsafe_methods_restricted', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '不安全HTTP方法已限制'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-06';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'secure_cookie', '=', 'true', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'Cookie Secure标志已启用'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-07';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'httponly_cookie', '=', 'true', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'Cookie HttpOnly标志已启用'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-08';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'default_apps_removed', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '默认应用已移除'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-09';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_post_size', '<=', '2097152', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'maxPostSize不超过2MB'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-TC-10' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_post_size', '<=', '1048576', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'maxPostSize不超过1MB'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-TC-10' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_post_size', '<=', '524288', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', 'maxPostSize不超过512KB'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-TC-10' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ajp_connector_disabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'AJP连接器已禁用'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-11';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_enabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'SSL/TLS已启用'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-12';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 2, 'ssl_protocol_min', '>=', 'TLSv1.2', 'EXACT', NULL, 'STRING', NULL, 2, 'AND', '最低TLS版本为1.2'
FROM baseline_rule r WHERE r.rule_code = 'MW-TC-12';

-- Tomcat asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'MW-TC-%' AND a.type_code = 'MW_TOMCAT';

-- ============================================================
-- 4. Nginx（12条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('MW-NG-01', '隐藏版本号',                               '信息保护', '检查http块中server_tokens是否为off',                        'MEDIUM',   5, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-NG-02', '限制HTTP请求方法',                         '访问控制', '检查是否通过limit_except限制了GET/POST之外的HTTP方法',     'HIGH',     7, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-NG-03', '请求速率限制',                             '资源控制', '检查limit_req_zone和limit_req是否配置且速率合理(次/秒)',   'MEDIUM',   5, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-NG-04', '连接超时时间',                             '连接管理', '检查keepalive_timeout是否在限定范围(秒)',                   'LOW',      3, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-NG-05', '访问日志配置',                             '日志审计', '检查access_log是否已配置且路径非空',                        'MEDIUM',   4, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-NG-06', '错误日志级别',                             '日志审计', '检查error_log的日志级别是否符合等级要求',                   'LOW',      3, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-NG-07', '自定义错误页面',                           '信息保护', '检查是否配置了error_page自定义错误页面',                   'LOW',      3, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-NG-08', 'X-Frame-Options响应头',                   '安全头',   '检查add_header中X-Frame-Options配置',                       'MEDIUM',   5, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-NG-09', 'X-Content-Type-Options响应头',            '安全头',   '检查add_header中X-Content-Type-Options是否为nosniff',       'MEDIUM',   5, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-NG-10', 'Content-Security-Policy响应头',           '安全头',   '检查add_header中CSP是否已配置',                             'HIGH',     6, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-NG-11', 'SSL协议最低版本',                          '通信安全', '检查ssl_protocols配置的最低版本',                            'CRITICAL', 8, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-NG-12', '客户端请求体大小限制',                     '安全配置', '检查client_max_body_size是否在限定范围',                    'MEDIUM',   5, 'Nginx', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND');

-- Nginx items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'server_tokens', '=', 'off', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'server_tokens已关闭版本显示'
FROM baseline_rule r WHERE r.rule_code = 'MW-NG-01';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'request_methods_restricted', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'HTTP请求方法已限制'
FROM baseline_rule r WHERE r.rule_code = 'MW-NG-02';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'limit_req_rate', '<=', '100', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '请求速率限制不超过100r/s'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-03' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'limit_req_rate', '<=', '50', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '请求速率限制不超过50r/s'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-03' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'limit_req_rate', '<=', '20', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '请求速率限制不超过20r/s'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-03' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'keepalive_timeout', '<=', '60', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '连接超时不超过60秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-04' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'keepalive_timeout', '<=', '30', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '连接超时不超过30秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-04' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'keepalive_timeout', '<=', '15', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '连接超时不超过15秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-04' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'access_log_configured', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '访问日志已配置'
FROM baseline_rule r WHERE r.rule_code = 'MW-NG-05';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'error_log_level', '=', 'error', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '错误日志级别为error'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-06' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'error_log_level', '=', 'warn', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '错误日志级别为warn'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-06' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'error_log_level', '=', 'warn', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '错误日志级别为warn'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-06' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'error_page_configured', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '自定义错误页面已配置'
FROM baseline_rule r WHERE r.rule_code = 'MW-NG-07';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'x_frame_options', '=', 'SAMEORIGIN', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'X-Frame-Options为SAMEORIGIN'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-08' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'x_frame_options', '=', 'DENY', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'X-Frame-Options为DENY'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-08' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'x_frame_options', '=', 'DENY', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'X-Frame-Options为DENY'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-08' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'x_content_type_options', '=', 'nosniff', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'X-Content-Type-Options为nosniff'
FROM baseline_rule r WHERE r.rule_code = 'MW-NG-09';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'csp_configured', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'Content-Security-Policy已配置'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-10' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'csp_configured', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'Content-Security-Policy已配置'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-10' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'csp_strict_configured', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'Content-Security-Policy严格配置已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-10' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_protocol_min', '>=', 'TLSv1.2', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '最低SSL协议版本为TLSv1.2'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-11' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_protocol_min', '>=', 'TLSv1.2', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '最低SSL协议版本为TLSv1.2'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-11' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_protocol_min', '>=', 'TLSv1.3', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '最低SSL协议版本为TLSv1.3'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-11' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'client_max_body_size', '<=', '10', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '请求体大小不超过10MB'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-12' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'client_max_body_size', '<=', '5', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '请求体大小不超过5MB'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-12' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'client_max_body_size', '<=', '1', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '请求体大小不超过1MB'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-NG-12' AND pl.level_code = 'L5';

-- Nginx asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'MW-NG-%' AND a.type_code = 'MW_NGINX';

-- ============================================================
-- 5. IIS（10条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('MW-II-01', '关闭目录浏览',                             '信息保护', '检查Web站点是否关闭了目录浏览功能',                        'MEDIUM',   5, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-II-02', '自定义错误页面',                           '信息保护', '检查是否配置了自定义错误页面避免信息泄露',                 'LOW',      3, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-II-03', '日志记录启用',                             '日志审计', '检查IIS日志记录是否已启用且配置了W3C格式',                  'MEDIUM',   5, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-II-04', '请求过滤',                                 '访问控制', '检查是否启用了请求筛选并限制了危险文件扩展名',             'HIGH',     6, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-II-05', '禁用WebDAV',                               '安全配置', '检查WebDAV发布功能是否已禁用',                              'MEDIUM',   5, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-II-06', 'SSL/TLS最低协议版本',                      '通信安全', '检查站点SSL设置中最低协议版本',                             'HIGH',     7, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-II-07', '移除Server/X-Powered-By头',               '信息保护', '检查HTTP响应头是否移除了Server和X-Powered-By',               'LOW',      3, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-II-08', '应用程序池标识',                           '安全配置', '检查应用程序池是否使用独立标识而非NetworkService',         'MEDIUM',   5, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('MW-II-09', 'HTTP重定向保护',                           '安全配置', '检查是否配置了HTTPS重定向避免HTTP明文传输',                'HIGH',     7, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('MW-II-10', '连接超时',                                 '连接管理', '检查站点连接超时配置(秒)',                                  'LOW',      3, 'IIS', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND');

-- IIS items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'directory_browsing_disabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '目录浏览已禁用'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-01';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'custom_error_pages_configured', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '自定义错误页面已配置'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-02';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'logging_enabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '日志记录已启用'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-03';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'request_filtering_enabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '请求过滤已启用'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-04';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 2, 'dangerous_extensions_blocked', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 2, 'AND', '危险扩展名已阻止'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-04';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'webdav_disabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'WebDAV已禁用'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-05';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_protocol_min', '>=', 'TLSv1.2', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '最低SSL协议版本为TLSv1.2'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-06' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_protocol_min', '>=', 'TLSv1.2', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '最低SSL协议版本为TLSv1.2'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-06' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_protocol_min', '>=', 'TLSv1.3', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '最低SSL协议版本为TLSv1.3'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-06' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'server_header_removed', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'Server响应头已移除'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-07';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 2, 'x_powered_by_removed', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 2, 'AND', 'X-Powered-By响应头已移除'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-07';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'app_pool_identity', '=', 'ApplicationPoolIdentity', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '应用程序池使用ApplicationPoolIdentity'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-08' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'app_pool_identity_custom', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', '应用程序池使用自定义独立账户'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-08' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'app_pool_identity_custom', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', '应用程序池使用自定义独立账户'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-08' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'https_redirect_enabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'HTTPS重定向已启用'
FROM baseline_rule r WHERE r.rule_code = 'MW-II-09';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_timeout', '<=', '120', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '连接超时不超过120秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-10' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_timeout', '<=', '60', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '连接超时不超过60秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-10' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'connection_timeout', '<=', '30', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '连接超时不超过30秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'MW-II-10' AND pl.level_code = 'L5';

-- IIS asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'MW-II-%' AND a.type_code = 'MW_IIS';

-- ============================================================
-- 6. PostgreSQL（10条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('DB-PG-01', '密码加密方式',                             '认证安全', '检查password_encryption是否为scram-sha-256',               'HIGH',     7, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-PG-02', '客户端认证方式',                           '认证安全', '检查pg_hba.conf中认证方法是否符合等级要求',                'HIGH',     8, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-PG-03', '连接日志',                                 '日志审计', '检查log_connections是否为on',                              'MEDIUM',   4, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-PG-04', '断开日志',                                 '日志审计', '检查log_disconnections是否为on',                           'MEDIUM',   4, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-PG-05', '语句日志级别',                             '日志审计', '检查log_statement配置是否符合等级要求',                    'MEDIUM',   5, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-PG-06', 'SSL连接启用',                              '通信安全', '检查ssl是否为on',                                          'HIGH',     7, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-PG-07', '审计扩展(pgaudit)',                       '日志审计', '检查是否加载了pgaudit扩展',                                'HIGH',     6, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-PG-08', '最大连接数限制',                           '资源控制', '检查max_connections是否在限定范围内',                      'MEDIUM',   5, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-PG-09', '语句超时',                                 '资源控制', '检查statement_timeout是否在限定范围(秒)',                  'MEDIUM',   4, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-PG-10', '空闲连接超时',                             '连接管理', '检查idle_in_transaction_session_timeout是否在限定范围(秒)','MEDIUM',   4, 'PostgreSQL', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND');

-- PostgreSQL items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_encryption', '=', 'scram-sha-256', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', '密码加密方式为scram-sha-256'
FROM baseline_rule r WHERE r.rule_code = 'DB-PG-01';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'auth_method', '=', 'md5', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '认证方式不低于md5'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-02' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'auth_method', '=', 'scram-sha-256', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '认证方式为scram-sha-256'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-02' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'auth_method', '=', 'scram-sha-256', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '认证方式为scram-sha-256'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-02' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_connections', '=', 'on', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', '连接日志已启用'
FROM baseline_rule r WHERE r.rule_code = 'DB-PG-03';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_disconnections', '=', 'on', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', '断开日志已启用'
FROM baseline_rule r WHERE r.rule_code = 'DB-PG-04';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_statement', '=', 'ddl', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '语句日志记录级别为ddl'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-05' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_statement', '=', 'mod', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '语句日志记录级别为mod'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-05' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'log_statement', '=', 'all', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '语句日志记录级别为all'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-05' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl', '=', 'on', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'SSL已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-06' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl', '=', 'on', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'SSL已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-06' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl', '=', 'on', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', 'SSL已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-06' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'pgaudit_loaded', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'pgaudit审计扩展已加载'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-07' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'pgaudit_loaded', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'pgaudit审计扩展已加载'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-07' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'pgaudit_loaded', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'pgaudit审计扩展已加载'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-07' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_connections', '<=', '300', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大连接数不超过300'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-08' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_connections', '<=', '200', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大连接数不超过200'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-08' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_connections', '<=', '100', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大连接数不超过100'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-08' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'statement_timeout', '<=', '300000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '语句超时不超过300秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-09' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'statement_timeout', '<=', '120000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '语句超时不超过120秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-09' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'statement_timeout', '<=', '60000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '语句超时不超过60秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-09' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'idle_in_transaction_timeout', '<=', '600000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '空闲事务超时不超过600秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-10' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'idle_in_transaction_timeout', '<=', '300000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '空闲事务超时不超过300秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-10' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'idle_in_transaction_timeout', '<=', '120000', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '空闲事务超时不超过120秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-PG-10' AND pl.level_code = 'L5';

-- PostgreSQL asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'DB-PG-%' AND a.type_code = 'DB_POSTGRESQL';

-- ============================================================
-- 7. Oracle（8条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('DB-OR-01', '密码最大使用期限',                         '账户安全', '检查PASSWORD_LIFE_TIME配置(天)',                            'HIGH',     7, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-OR-02', '密码最小长度',                             '账户安全', '检查PASSWORD_MIN_LENGTH配置',                               'HIGH',     7, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-OR-03', '密码锁定阈值',                             '账户安全', '检查FAILED_LOGIN_ATTEMPTS配置',                              'HIGH',     7, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-OR-04', '审计启用',                                 '日志审计', '检查audit_trail参数是否已配置审计',                         'HIGH',     7, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-OR-05', '删除或锁定默认用户',                       '访问控制', '检查DBSNMP/OUTLN/SCOTT等默认账户是否已锁定或删除',          'CRITICAL', 9, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-OR-06', '监听器密码保护',                           '网络安全', '检查listener是否设置了密码保护',                            'HIGH',     7, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-OR-07', '监听器管理限制',                           '网络安全', '检查listener.ora中ADMIN_RESTRICTIONS是否启用',              'MEDIUM',   5, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-OR-08', '密码验证函数',                             '账户安全', '检查是否创建并启用了密码复杂度验证函数',                   'HIGH',     7, 'Oracle', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND');

-- Oracle items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_life_time', '<=', '90', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码有效期不超过90天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-01' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_life_time', '<=', '60', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码有效期不超过60天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-01' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_life_time', '<=', '30', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码有效期不超过30天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-01' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_min_length', '>=', '8', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于8位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-02' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_min_length', '>=', '10', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于10位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-02' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_min_length', '>=', '12', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于12位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-02' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'failed_login_attempts', '<=', '5', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值不超过5次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-03' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'failed_login_attempts', '<=', '3', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值不超过3次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-03' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'failed_login_attempts', '<=', '3', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值不超过3次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-03' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'audit_trail', '=', 'DB', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '审计模式为DB'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-04' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'audit_trail', '=', 'DB_EXTENDED', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '审计模式为DB,EXTENDED'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-04' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'audit_trail', '=', 'DB_EXTENDED', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '审计模式为DB,EXTENDED'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-OR-04' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'default_users_locked', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '默认用户已锁定或删除'
FROM baseline_rule r WHERE r.rule_code = 'DB-OR-05';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'listener_password_protected', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '监听器密码已设置'
FROM baseline_rule r WHERE r.rule_code = 'DB-OR-06';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'admin_restrictions', '=', 'ON', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', 'ADMIN_RESTRICTIONS已启用'
FROM baseline_rule r WHERE r.rule_code = 'DB-OR-07';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_verify_function_enabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '密码验证函数已启用'
FROM baseline_rule r WHERE r.rule_code = 'DB-OR-08';

-- Oracle asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'DB-OR-%' AND a.type_code = 'DB_ORACLE';

-- ============================================================
-- 8. SQL Server（8条规则）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('DB-MS-01', '认证模式',                                 '认证安全', '检查是否为Windows认证模式',                                 'CRITICAL', 8, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MS-02', '密码策略强制执行',                         '账户安全', '检查登录账户是否强制执行密码策略',                         'HIGH',     7, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MS-03', 'sa账户禁用',                               '访问控制', '检查sa账户是否已禁用或重命名',                              'CRITICAL', 9, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MS-04', '登录审计',                                 '日志审计', '检查登录审计配置是否符合等级要求',                         'HIGH',     6, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MS-05', '最大并发连接数',                           '资源控制', '检查max worker threads/并发连接是否在限定范围',            'MEDIUM',   5, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('DB-MS-06', '删除示例数据库',                           '安全配置', '检查AdventureWorks/pubs/Northwind等是否已删除',             'MEDIUM',   5, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MS-07', 'xp_cmdshell禁用',                         '安全配置', '检查xp_cmdshell是否已禁用',                                 'CRITICAL', 9, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', NULL,       'AND'),
('DB-MS-08', 'SSL加密连接',                              '通信安全', '检查是否启用了SSL加密连接',                                'HIGH',     7, 'SQL Server', 'SCRIPT', NULL, 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND');

-- SQL Server items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'authentication_mode', '=', 'Windows', 'EXACT', NULL, 'STRING', NULL, 1, 'AND', '认证模式为Windows'
FROM baseline_rule r WHERE r.rule_code = 'DB-MS-01';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_policy_enforced', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '密码策略已强制执行'
FROM baseline_rule r WHERE r.rule_code = 'DB-MS-02';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'sa_disabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'sa账户已禁用或重命名'
FROM baseline_rule r WHERE r.rule_code = 'DB-MS-03';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'login_audit', '=', 'FAILURE', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '登录审计仅记录失败'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-04' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'login_audit', '=', 'ALL', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '登录审计记录成功和失败'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-04' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'login_audit', '=', 'ALL', 'EXACT', pl.id, 'STRING', NULL, 1, 'AND', '登录审计记录成功和失败'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-04' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_worker_threads', '<=', '500', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大并发连接数不超过500'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-05' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_worker_threads', '<=', '300', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大并发连接数不超过300'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-05' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'max_worker_threads', '<=', '200', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '最大并发连接数不超过200'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-05' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'sample_databases_removed', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', '示例数据库已删除'
FROM baseline_rule r WHERE r.rule_code = 'DB-MS-06';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'xp_cmdshell_disabled', '=', '1', 'EXACT', NULL, 'BOOLEAN', NULL, 1, 'AND', 'xp_cmdshell已禁用'
FROM baseline_rule r WHERE r.rule_code = 'DB-MS-07';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_enabled', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'SSL加密连接已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-08' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_enabled', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'SSL加密连接已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-08' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'ssl_enabled', '=', '1', 'EXACT', pl.id, 'BOOLEAN', NULL, 1, 'AND', 'SSL加密连接已启用'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'DB-MS-08' AND pl.level_code = 'L5';

-- SQL Server asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'DB-MS-%' AND a.type_code = 'DB_SQLSERVER';

-- ============================================================
-- 9. Linux（5条规则，L3/L4/L5）
-- ============================================================
INSERT INTO baseline_rule (rule_code, rule_name, category, description, severity, score, os_type, check_method, check_script, remediation_type, remediation_script, version, is_mandatory, enabled, status, protection_level_flag, group_combine_logic) VALUES
('LIN-LEV-01', '等保Linux密码修改时间间隔', '等保等级保护-账户策略', '基于等保L3/L4/L5等级差异化检查Linux密码最大使用期限。L3<=90天，L4<=50天，L5<=20天', 'HIGH', 8, 'Linux', 'COMMAND', 'sed -nE "s/^[[:space:]]*PASS_MAX_DAYS[[:space:]]+([0-9]+).*/password_max_days=\\1/p" /etc/login.defs 2>/dev/null | tail -n 1', 'AUTO', 'grep -qE "^[[:space:]]*PASS_MAX_DAYS[[:space:]]+" /etc/login.defs && sed -ri "s/^[#[:space:]]*PASS_MAX_DAYS[[:space:]]+.*/PASS_MAX_DAYS ${targetValue}/" /etc/login.defs || printf "\\nPASS_MAX_DAYS %s\\n" "${targetValue}" >> /etc/login.defs', 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('LIN-LEV-02', '等保Linux密码最小长度', '等保等级保护-账户策略', '基于等保L3/L4/L5等级差异化检查Linux口令最小长度。L3>=8位，L4>=10位，L5>=12位', 'HIGH', 8, 'Linux', 'COMMAND', 'sed -nE "s/^[[:space:]]*minlen[[:space:]]*=[[:space:]]*([0-9]+).*/password_min_length=\\1/p" /etc/security/pwquality.conf 2>/dev/null | tail -n 1', 'AUTO', 'grep -qE "^[[:space:]]*minlen[[:space:]]*=" /etc/security/pwquality.conf && sed -ri "s/^[#[:space:]]*minlen[[:space:]]*=.*/minlen = ${targetValue}/" /etc/security/pwquality.conf || printf "\\nminlen = %s\\n" "${targetValue}" >> /etc/security/pwquality.conf', 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('LIN-LEV-03', '等保Linux密码历史记忆个数', '等保等级保护-账户策略', '基于等保L3/L4/L5等级差异化检查Linux口令历史记忆个数。L3>=5个，L4>=10个，L5>=15个', 'MEDIUM', 6, 'Linux', 'COMMAND', 'grep -rhoE "remember=[0-9]+" /etc/pam.d/system-auth /etc/pam.d/password-auth /etc/pam.d/common-password 2>/dev/null | head -n 1 | sed -E "s/remember=([0-9]+)/password_history_remember=\\1/"', 'MANUAL', 'sed -ri "/pam_pwhistory.so/ s/remember=[0-9]+/remember=${targetValue}/g" /etc/pam.d/system-auth /etc/pam.d/password-auth /etc/pam.d/common-password', 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('LIN-LEV-04', '等保Linux登录失败锁定阈值', '等保等级保护-访问控制', '基于等保L3/L4/L5等级差异化检查Linux登录失败锁定阈值。L3<=5次，L4<=3次，L5<=3次', 'HIGH', 8, 'Linux', 'COMMAND', 'sed -nE "s/^[[:space:]]*deny[[:space:]]*=[[:space:]]*([0-9]+).*/login_fail_lock_threshold=\\1/p" /etc/security/faillock.conf 2>/dev/null | tail -n 1', 'AUTO', 'grep -qE "^[[:space:]]*deny[[:space:]]*=" /etc/security/faillock.conf && sed -ri "s/^[#[:space:]]*deny[[:space:]]*=.*/deny = ${targetValue}/" /etc/security/faillock.conf || printf "\\ndeny = %s\\n" "${targetValue}" >> /etc/security/faillock.conf', 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND'),
('LIN-LEV-05', '等保Linux会话超时时间', '等保等级保护-会话策略', '基于等保L3/L4/L5等级差异化检查Linux交互会话超时时间。L3<=600秒，L4<=300秒，L5<=180秒', 'MEDIUM', 6, 'Linux', 'COMMAND', 'grep -REh "^[[:space:]]*TMOUT[[:space:]]*=" /etc/profile /etc/bashrc /etc/profile.d/*.sh 2>/dev/null | tail -n 1 | sed -E "s/.*=[[:space:]]*([0-9]+).*/shell_idle_timeout=\\1/"', 'AUTO', 'grep -qE "^[[:space:]]*TMOUT[[:space:]]*=" /etc/profile && sed -ri "s/^[#[:space:]]*TMOUT[[:space:]]*=.*/TMOUT=${targetValue}/" /etc/profile || printf "\\nTMOUT=%s\\nexport TMOUT\\n" "${targetValue}" >> /etc/profile', 1, 1, 1, 'PUBLISHED', 'L3,L4,L5', 'AND');

-- Linux items
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_max_days', '<=', '90', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最大使用期限不超过90天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-01' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_max_days', '<=', '50', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最大使用期限不超过50天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-01' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_max_days', '<=', '20', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最大使用期限不超过20天'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-01' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_min_length', '>=', '8', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于8位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-02' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_min_length', '>=', '10', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于10位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-02' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_min_length', '>=', '12', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码最小长度不小于12位'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-02' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_history_remember', '>=', '5', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码历史记忆个数不少于5个'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-03' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_history_remember', '>=', '10', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码历史记忆个数不少于10个'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-03' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'password_history_remember', '>=', '15', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '密码历史记忆个数不少于15个'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-03' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'login_fail_lock_threshold', '<=', '5', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值不超过5次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-04' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'login_fail_lock_threshold', '<=', '3', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值不超过3次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-04' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'login_fail_lock_threshold', '<=', '3', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '登录失败锁定阈值不超过3次'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-04' AND pl.level_code = 'L5';

INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'shell_idle_timeout', '<=', '600', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '交互会话超时时间不超过600秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-05' AND pl.level_code = 'L3';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'shell_idle_timeout', '<=', '300', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '交互会话超时时间不超过300秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-05' AND pl.level_code = 'L4';
INSERT INTO baseline_rule_item (rule_id, item_order, check_key, operator, expected_value, match_type, protection_level_id, value_type, value_set, logic_group, group_operator, remark)
SELECT r.id, 1, 'shell_idle_timeout', '<=', '180', 'EXACT', pl.id, 'NUMBER', NULL, 1, 'AND', '交互会话超时时间不超过180秒'
FROM baseline_rule r, baseline_protection_level pl WHERE r.rule_code = 'LIN-LEV-05' AND pl.level_code = 'L5';

-- Linux asset_ref
INSERT INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT r.id, a.id FROM baseline_rule r, baseline_asset_type a
WHERE r.rule_code LIKE 'LIN-LEV-%' AND a.type_code = 'OS_LINUX';

-- ============================================================
-- 验证查询
-- ============================================================
-- SELECT count(*) FROM baseline_rule WHERE rule_code LIKE 'DB-MY-%' OR rule_code LIKE 'DB-PG-%' OR rule_code LIKE 'DB-OR-%' OR rule_code LIKE 'DB-MS-%' OR rule_code LIKE 'CA-RD-%' OR rule_code LIKE 'MW-TC-%' OR rule_code LIKE 'MW-NG-%' OR rule_code LIKE 'MW-II-%';
-- SELECT count(*) FROM baseline_rule_item WHERE rule_id IN (SELECT id FROM baseline_rule WHERE rule_code LIKE 'DB-MY-%' OR rule_code LIKE 'DB-PG-%' OR rule_code LIKE 'DB-OR-%' OR rule_code LIKE 'DB-MS-%' OR rule_code LIKE 'CA-RD-%' OR rule_code LIKE 'MW-TC-%' OR rule_code LIKE 'MW-NG-%' OR rule_code LIKE 'MW-II-%');
-- SELECT count(*) FROM baseline_rule_asset_ref WHERE rule_id IN (SELECT id FROM baseline_rule WHERE rule_code LIKE 'DB-MY-%' OR rule_code LIKE 'DB-PG-%' OR rule_code LIKE 'DB-OR-%' OR rule_code LIKE 'DB-MS-%' OR rule_code LIKE 'CA-RD-%' OR rule_code LIKE 'MW-TC-%' OR rule_code LIKE 'MW-NG-%' OR rule_code LIKE 'MW-II-%');
