-- Add two baseline rule groups that cover L1-L5 so the UI can
-- show L1/L2 with real rules behind them.

SET @RULE_MW_TC_LEV_02 := (SELECT id FROM baseline_rule WHERE rule_code = 'MW-TC-LEV-02' LIMIT 1);

INSERT INTO baseline_rule (
    rule_code, rule_name, category, description, severity, score, level, asset_type, os_type,
    check_method, check_script, remediation_type, remediation_script, version,
    is_mandatory, enabled, status, protection_level_flag, group_combine_logic
)
SELECT
    'MW-TC-LEV-02', 'Tomcat session timeout by level (L1-L5)', 'Baseline-Level-Session',
    'Check Tomcat session timeout for L1-L5.', 'HIGH', 5, 1, 'MIDDLEWARE', 'Tomcat',
    'COMMAND', 'tomcat.session_timeout', 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED',
    'L1,L2,L3,L4,L5', 'AND'
WHERE @RULE_MW_TC_LEV_02 IS NULL;

SET @RULE_MW_TC_LEV_02 := (SELECT id FROM baseline_rule WHERE rule_code = 'MW-TC-LEV-02' LIMIT 1);

INSERT IGNORE INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT @RULE_MW_TC_LEV_02, id
FROM baseline_asset_type
WHERE type_code = 'MW_TOMCAT'
  AND @RULE_MW_TC_LEV_02 IS NOT NULL;

INSERT INTO baseline_rule_item (
    rule_id, item_order, check_key, operator, expected_value, match_type, remark,
    protection_level_id, value_type, logic_group, group_operator
)
SELECT
    @RULE_MW_TC_LEV_02, 1, 'session_timeout', '<=', vals.expected_value, 'EXACT', vals.remark,
    bpl.id, 'NUMBER', 1, 'AND'
FROM baseline_protection_level bpl
JOIN (
    SELECT 'L1' AS level_code, '60' AS expected_value, 'L1 session timeout <= 60 minutes' AS remark
    UNION ALL SELECT 'L2', '45', 'L2 session timeout <= 45 minutes'
    UNION ALL SELECT 'L3', '30', 'L3 session timeout <= 30 minutes'
    UNION ALL SELECT 'L4', '20', 'L4 session timeout <= 20 minutes'
    UNION ALL SELECT 'L5', '15', 'L5 session timeout <= 15 minutes'
) vals ON vals.level_code = bpl.level_code
WHERE @RULE_MW_TC_LEV_02 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM baseline_rule_item bri
      WHERE bri.rule_id = @RULE_MW_TC_LEV_02
        AND bri.check_key = 'session_timeout'
        AND bri.protection_level_id = bpl.id
  );

SET @RULE_DB_MY_LEV_02 := (SELECT id FROM baseline_rule WHERE rule_code = 'DB-MY-LEV-02' LIMIT 1);

INSERT INTO baseline_rule (
    rule_code, rule_name, category, description, severity, score, level, asset_type, os_type,
    check_method, check_script, remediation_type, remediation_script, version,
    is_mandatory, enabled, status, protection_level_flag, group_combine_logic
)
SELECT
    'DB-MY-LEV-02', 'MySQL password lifetime by level (L1-L5)', 'Baseline-Level-Account',
    'Check MySQL default_password_lifetime for L1-L5.', 'HIGH', 5, 1, 'DATABASE', 'MySQL',
    'COMMAND', 'mysql.default_password_lifetime', 'MANUAL', NULL, 1, 1, 1, 'PUBLISHED',
    'L1,L2,L3,L4,L5', 'AND'
WHERE @RULE_DB_MY_LEV_02 IS NULL;

SET @RULE_DB_MY_LEV_02 := (SELECT id FROM baseline_rule WHERE rule_code = 'DB-MY-LEV-02' LIMIT 1);

INSERT IGNORE INTO baseline_rule_asset_ref (rule_id, asset_type_id)
SELECT @RULE_DB_MY_LEV_02, id
FROM baseline_asset_type
WHERE type_code = 'DB_MYSQL'
  AND @RULE_DB_MY_LEV_02 IS NOT NULL;

INSERT INTO baseline_rule_item (
    rule_id, item_order, check_key, operator, expected_value, match_type, remark,
    protection_level_id, value_type, logic_group, group_operator
)
SELECT
    @RULE_DB_MY_LEV_02, 1, 'default_password_lifetime', '<=', vals.expected_value, 'EXACT', vals.remark,
    bpl.id, 'NUMBER', 1, 'AND'
FROM baseline_protection_level bpl
JOIN (
    SELECT 'L1' AS level_code, '180' AS expected_value, 'L1 password lifetime <= 180 days' AS remark
    UNION ALL SELECT 'L2', '120', 'L2 password lifetime <= 120 days'
    UNION ALL SELECT 'L3', '90', 'L3 password lifetime <= 90 days'
    UNION ALL SELECT 'L4', '50', 'L4 password lifetime <= 50 days'
    UNION ALL SELECT 'L5', '20', 'L5 password lifetime <= 20 days'
) vals ON vals.level_code = bpl.level_code
WHERE @RULE_DB_MY_LEV_02 IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM baseline_rule_item bri
      WHERE bri.rule_id = @RULE_DB_MY_LEV_02
        AND bri.check_key = 'default_password_lifetime'
        AND bri.protection_level_id = bpl.id
  );
