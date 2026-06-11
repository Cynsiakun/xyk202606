#合规基线相关数据库表的数据结构和一些必要的解释


1. baseline_rule（基线规则主表）
CREATE TABLE baseline_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    rule_code VARCHAR(50) NOT NULL UNIQUE COMMENT '规则编码，例如 WIN-001',

    rule_name VARCHAR(255) NOT NULL COMMENT '规则名称',

    category VARCHAR(100) COMMENT '分类：账户策略、日志审计、服务安全等',

    description TEXT COMMENT '规则说明',

    severity VARCHAR(20) COMMENT '风险等级：critical/high/medium/low',

    score INT DEFAULT 1 COMMENT '评分',

    os_type VARCHAR(50) DEFAULT 'Windows' COMMENT '适用系统',

    check_method VARCHAR(30) NOT NULL DEFAULT 'REGISTRY'
        COMMENT '检测方式：REGISTRY/SERVICE/COMMAND/WMI/SCRIPT',

    check_script TEXT COMMENT '检测脚本或命令',

    remediation_type VARCHAR(20) DEFAULT 'MANUAL'
        COMMENT '修复方式：AUTO/MANUAL/SEMI',

    remediation_script TEXT COMMENT '自动修复脚本',

    version INT DEFAULT 1 COMMENT '规则版本',

    is_mandatory TINYINT DEFAULT 1 COMMENT '是否强制要求',

    enabled TINYINT DEFAULT 1 COMMENT '是否启用',

    status VARCHAR(20) DEFAULT 'PUBLISHED'
        COMMENT '规则状态：DRAFT/PUBLISHED/ARCHIVED',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线规则主表';

2. baseline_rule_item（规则参数表）

一条规则可以有多个检查项。
例如：
系统日志大小
系统日志覆盖方式
对应两条 item。
CREATE TABLE baseline_rule_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    rule_id BIGINT NOT NULL COMMENT '规则ID',

    item_order INT DEFAULT 1 COMMENT '检查顺序',

    check_key VARCHAR(255) NOT NULL COMMENT '检测键',

    operator VARCHAR(20) NOT NULL
        COMMENT '=、!=、>、>=、<、<=',

    expected_value TEXT COMMENT '期望值',

    match_type VARCHAR(20) DEFAULT 'EXACT'
        COMMENT '匹配方式：EXACT/CONTAINS/REGEX',

    remark VARCHAR(255) COMMENT '备注',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_baseline_rule_item_rule
        FOREIGN KEY (rule_id)
        REFERENCES baseline_rule(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线规则参数表';

3. baseline_rule_history（规则历史版本）
用于审计和历史追溯。
CREATE TABLE baseline_rule_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    rule_id BIGINT NOT NULL COMMENT '规则ID',

    version INT NOT NULL COMMENT '版本号',

    rule_snapshot JSON COMMENT '规则快照',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    CONSTRAINT fk_baseline_rule_history_rule
        FOREIGN KEY (rule_id)
        REFERENCES baseline_rule(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则历史版本表';

rule_snapshot 示例：

{
  "check_script": "...",
  "remediation_script": "...",
  "severity": "high",
  "expected_value": "0"
}

4. baseline_task（任务主表）
表示：
立即执行
定时执行
等任务。

CREATE TABLE baseline_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',

    execute_type VARCHAR(20) DEFAULT 'MANUAL'
        COMMENT '执行方式：MANUAL/SCHEDULED',

    cron_expr VARCHAR(100) COMMENT 'cron表达式',

    rule_scope JSON COMMENT '规则范围',

    rule_snapshot_json JSON COMMENT '规则版本快照',

    status VARCHAR(20) DEFAULT 'PENDING'
        COMMENT '任务状态：PENDING/RUNNING/FINISHED/FAILED',

    total_host_count INT DEFAULT 0 COMMENT '总主机数',

    success_count INT DEFAULT 0 COMMENT '成功数量',

    fail_count INT DEFAULT 0 COMMENT '失败数量',

    creator VARCHAR(50) COMMENT '创建人',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    start_time DATETIME COMMENT '开始时间',

    finish_time DATETIME COMMENT '结束时间'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线任务主表';

rule_scope 示例：

{
  "ruleIds": [1,2,3]
}

rule_snapshot_json：

[
  {
    "ruleId": 1,
    "version": 3
  },
  {
    "ruleId": 2,
    "version": 1
  }
]

5. baseline_task_host（任务-主机关联表）

记录：

某个任务下有哪些主机。

CREATE TABLE baseline_task_host (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT '任务ID',

    host_id BIGINT NOT NULL COMMENT '主机ID',

    status VARCHAR(20) DEFAULT 'PENDING'
        COMMENT '状态：PENDING/RUNNING/FINISHED/FAILED',

    result_summary JSON COMMENT '结果汇总',

    scan_time DATETIME COMMENT '扫描时间',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_baseline_task_host_task
        FOREIGN KEY (task_id)
        REFERENCES baseline_task(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务与主机关联表';



6. baseline_check_data（原始检测数据表）

保存 Agent 返回的原始数据。

例如：

{
    "FirewallEnabled": true,
    "AutoAdminLogon": 1,
    "DhcpService": "Running"
}

不经过规则引擎，原样保存。

CREATE TABLE baseline_check_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT '任务ID',

    host_id BIGINT NOT NULL COMMENT '主机ID',

    check_data JSON COMMENT 'Agent返回的原始检测数据',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    CONSTRAINT fk_baseline_check_data_task
        FOREIGN KEY (task_id)
        REFERENCES baseline_task(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线检测原始数据表';

7. baseline_result（基线检测结果表）

整个系统最核心的一张表。

一条记录表示：

某台主机某项规则（或规则项）的检查结果。

允许：

同一个 rule_id
多条 result
check_key 不同

例如：

rule_id	check_key	status
1	max_size	PASS
1	overwrite_mode	FAIL
CREATE TABLE baseline_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    task_id BIGINT NOT NULL COMMENT '任务ID',

    task_host_id BIGINT NOT NULL COMMENT '任务主机关联ID',

    host_id BIGINT NOT NULL COMMENT '主机ID',

    rule_id BIGINT NOT NULL COMMENT '规则ID',

    rule_version INT NOT NULL COMMENT '规则版本',

    check_key VARCHAR(255) COMMENT '检查项key',

    status VARCHAR(20) NOT NULL
        COMMENT 'PASS/FAIL/ERROR/UNKNOWN',

    actual_value TEXT COMMENT '实际值',

    expected_value TEXT COMMENT '期望值',

    message VARCHAR(2000) COMMENT '检测结果描述',

    evidence TEXT COMMENT '证据信息，原始命令输出',

    remediation_status VARCHAR(20) DEFAULT 'PENDING'
        COMMENT 'PENDING/FIXED/FAILED/MANUAL',

    scan_time DATETIME COMMENT '扫描时间',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    CONSTRAINT fk_baseline_result_task
        FOREIGN KEY (task_id)
        REFERENCES baseline_task(id),

    CONSTRAINT fk_baseline_result_task_host
        FOREIGN KEY (task_host_id)
        REFERENCES baseline_task_host(id),

    CONSTRAINT fk_baseline_result_rule
        FOREIGN KEY (rule_id)
        REFERENCES baseline_rule(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线检测结果表';

8. baseline_summary（统计汇总表）

用于：

主机视角
首页统计
合规率

建议以：

(host_id, task_id)

作为唯一组合。

CREATE TABLE baseline_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    host_id BIGINT NOT NULL COMMENT '主机ID',

    task_id BIGINT NOT NULL COMMENT '任务ID',

    pass_count INT DEFAULT 0 COMMENT '通过数量',

    fail_count INT DEFAULT 0 COMMENT '失败数量',

    score INT DEFAULT 0 COMMENT '得分',

    compliance_rate DECIMAL(5,2)
        COMMENT '合规率，例如95.50',

    last_scan_time DATETIME COMMENT '最后扫描时间',

    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_host_task(host_id, task_id),

    CONSTRAINT fk_baseline_summary_task
        FOREIGN KEY (task_id)
        REFERENCES baseline_task(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线统计汇总表';

9. baseline_remediation（自动修复记录表）

实现：

自动加固
配置备份
回滚

老师提到：

修改系统配置前必须备份。

因此必须保存：

old_value
new_value
CREATE TABLE baseline_remediation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    result_id BIGINT NOT NULL COMMENT '对应检测结果ID',

    host_id BIGINT NOT NULL COMMENT '主机ID',

    rule_id BIGINT NOT NULL COMMENT '规则ID',

    remediation_type VARCHAR(20) NOT NULL
        COMMENT 'AUTO/MANUAL',

    old_value TEXT COMMENT '修改前配置值',

    new_value TEXT COMMENT '修改后配置值',

    backup_data TEXT COMMENT '备份数据',

    execute_script TEXT COMMENT '执行的修复脚本',

    status VARCHAR(20) DEFAULT 'PENDING'
        COMMENT 'PENDING/SUCCESS/FAILED/ROLLBACK',

    start_time DATETIME COMMENT '开始时间',

    end_time DATETIME COMMENT '结束时间',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    CONSTRAINT fk_baseline_remediation_result
        FOREIGN KEY (result_id)
        REFERENCES baseline_result(id),

    CONSTRAINT fk_baseline_remediation_rule
        FOREIGN KEY (rule_id)
        REFERENCES baseline_rule(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动修复及回滚记录表';

10. baseline_workorder（人工工单表）

用于：

不能自动修复的项。

形成：

发现问题
↓
生成工单
↓
人工处理
↓
复检

不做复杂流程引擎。

CREATE TABLE baseline_workorder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    result_id BIGINT NOT NULL COMMENT '对应检测结果ID',

    host_id BIGINT NOT NULL COMMENT '主机ID',

    rule_id BIGINT NOT NULL COMMENT '规则ID',

    title VARCHAR(255) NOT NULL COMMENT '工单标题',

    advice TEXT COMMENT '修复建议',

    assignee VARCHAR(50) COMMENT '处理人',

    status VARCHAR(20) DEFAULT 'OPEN'
        COMMENT 'OPEN/PROCESSING/DONE',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    finish_time DATETIME COMMENT '完成时间',

    CONSTRAINT fk_baseline_workorder_result
        FOREIGN KEY (result_id)
        REFERENCES baseline_result(id),

    CONSTRAINT fk_baseline_workorder_rule
        FOREIGN KEY (rule_id)
        REFERENCES baseline_rule(id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工工单表';