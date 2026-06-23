-- ============================================================
-- V5: 基线检测多资产类型 + 等保2.0多等级 + 规则条件增强
-- 原则：零删除、零修改已有外键、全部新增字段NULLABLE
-- ============================================================

-- ---------- Phase 1: 字典表 ----------

-- 1. 资产类型字典
CREATE TABLE IF NOT EXISTS baseline_asset_type (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code   VARCHAR(30) NOT NULL UNIQUE COMMENT '资产类型编码：OS_WINDOWS/OS_LINUX/DB_MYSQL/CACHE_REDIS',
    type_name   VARCHAR(100) NOT NULL COMMENT '显示名称',
    category    VARCHAR(30) NOT NULL COMMENT '大类：OS/MIDDLEWARE/DATABASE/CACHE/NETWORK',
    sort_order  INT DEFAULT 0,
    enabled     TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线资产类型字典';

INSERT INTO baseline_asset_type (type_code, type_name, category, sort_order) VALUES
('OS_WINDOWS',    'Windows',           'OS',         1),
('OS_LINUX',      'Linux',             'OS',         2),
('MW_TOMCAT',     'Apache Tomcat',     'MIDDLEWARE', 10),
('MW_NGINX',      'Nginx',             'MIDDLEWARE', 11),
('MW_IIS',        'IIS',               'MIDDLEWARE', 12),
('DB_MYSQL',      'MySQL',             'DATABASE',   20),
('DB_ORACLE',     'Oracle',            'DATABASE',   21),
('DB_SQLSERVER',  'SQL Server',        'DATABASE',   22),
('DB_POSTGRESQL', 'PostgreSQL',        'DATABASE',   23),
('CACHE_REDIS',   'Redis',             'CACHE',      30),
('NET_FIREWALL',  'Firewall',          'NETWORK',    40)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

-- 2. 等保等级字典
CREATE TABLE IF NOT EXISTS baseline_protection_level (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    level_code  VARCHAR(20) NOT NULL UNIQUE COMMENT '等保等级编码：L1 / L2 / S3 / S3_PLUS / L3 / L4 / L5',
    level_name  VARCHAR(50) NOT NULL COMMENT '显示名称',
    level_order INT DEFAULT 0 COMMENT '数值排序，用于比较',
    description VARCHAR(255),
    enabled     TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等保2.0保护等级字典';

INSERT INTO baseline_protection_level (level_code, level_name, level_order) VALUES
('L1', '等保一级(L1)', 1),
('L2', '等保二级(L2)', 2),
('S3', '等保三级(S3)', 3),
('S3_PLUS', '等保三级增强(S3_PLUS)', 4),
('L3', '安全防护三级(L3)', 5),
('L4', '安全防护四级(L4)', 6),
('L5', '安全防护五级(L5)', 7)
ON DUPLICATE KEY UPDATE level_name = VALUES(level_name), level_order = VALUES(level_order);

-- ---------- Phase 2: 关联表 ----------

-- 3. 规则 → 资产类型多对多
CREATE TABLE IF NOT EXISTS baseline_rule_asset_ref (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id       BIGINT NOT NULL COMMENT 'FK→baseline_rule.id',
    asset_type_id BIGINT NOT NULL COMMENT 'FK→baseline_asset_type.id',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_asset_ref_rule FOREIGN KEY (rule_id) REFERENCES baseline_rule(id),
    CONSTRAINT fk_rule_asset_ref_type FOREIGN KEY (asset_type_id) REFERENCES baseline_asset_type(id),
    UNIQUE KEY uk_rule_asset (rule_id, asset_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则适用资产类型关联';

-- 存量迁移：根据 baseline_rule.os_type 创建 Windows 资产关联
INSERT IGNORE INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT br.id, bat.id
FROM baseline_rule br
JOIN baseline_asset_type bat ON UPPER(bat.type_code) = CONCAT('OS_', UPPER(br.os_type))
WHERE br.os_type IS NOT NULL;

-- ---------- Phase 3: 扩展现有表（全部ADD COLUMN，NULLABLE） ----------

-- 4. baseline_rule：等保等级标志 + 跨组聚合逻辑
ALTER TABLE baseline_rule
    ADD COLUMN protection_level_flag VARCHAR(100) DEFAULT NULL
        COMMENT '适用等保等级编码列表，逗号分隔：L1,L2。NULL=所有等级',
    ADD COLUMN group_combine_logic VARCHAR(10) DEFAULT 'AND'
        COMMENT '跨logic_group的连接符：AND / OR。默认AND=所有组必须通过';

-- 5. baseline_rule_item：等级差异 + 值类型 + 枚举值 + 逻辑分组
ALTER TABLE baseline_rule_item
    ADD COLUMN protection_level_id BIGINT DEFAULT NULL
        COMMENT 'FK→baseline_protection_level。NULL=适用于所有等级',
    ADD COLUMN value_type VARCHAR(20) DEFAULT NULL
        COMMENT 'NUMBER/STRING/BOOLEAN/ENUM。NULL时由引擎自动推断',
    ADD COLUMN value_set TEXT DEFAULT NULL
        COMMENT '枚举值JSON数组：["a","b"]。非NULL时覆盖expected_value做IN匹配',
    ADD COLUMN logic_group INT DEFAULT 1
        COMMENT '逻辑分组编号。同组内item用group_operator聚合',
    ADD COLUMN group_operator VARCHAR(10) DEFAULT 'AND'
        COMMENT '同logic_group内item的连接符：AND / OR。默认AND=全部通过';

-- 6. baseline_result：冗余维度字段 + item直接关联
ALTER TABLE baseline_result
    ADD COLUMN asset_type_id BIGINT DEFAULT NULL
        COMMENT '冗余资产类型ID，便于聚合统计，FK→baseline_asset_type',
    ADD COLUMN protection_level_id BIGINT DEFAULT NULL
        COMMENT '检测时使用的等保等级ID，FK→baseline_protection_level',
    ADD COLUMN item_id BIGINT DEFAULT NULL
        COMMENT '直接关联的rule_item ID，FK→baseline_rule_item';

-- 7. baseline_task：任务级等保等级过滤
ALTER TABLE baseline_task
    ADD COLUMN protection_level_id BIGINT DEFAULT NULL
        COMMENT '本次任务指定的等保等级。NULL=不限制';

-- 8. baseline_summary：多维度统计扩展
ALTER TABLE baseline_summary
    ADD COLUMN asset_type_id BIGINT DEFAULT NULL
        COMMENT '资产类型维度，FK→baseline_asset_type',
    ADD COLUMN protection_level_id BIGINT DEFAULT NULL
        COMMENT '等保等级维度，FK→baseline_protection_level';
