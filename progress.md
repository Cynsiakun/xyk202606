# 2026-06-11 合规基线进度

## 平台端与客户端 JSON 协议确认

本阶段采用“客户端负责执行检测并回传完整实际数据，平台端规则引擎负责判定 PASS/FAIL”的设计。这样规则判断逻辑集中在平台端，后续规则版本更新、复检、自动修复和工单闭环都不需要依赖客户端改判定逻辑。

### 平台端下发任务

交换机：`agent_exchange`
队列：`agent_{mac地址}_queue`
路由键：`{mac地址}`

```json
{
  "type": "baseline_scan",
  "taskId": 10001,
  "taskHostId": 30001,
  "hostId": 201,
  "macAddress": "00-11-22-33-44-55",
  "createdAt": "2026-06-11T16:30:00+08:00",
  "checks": [
    {
      "ruleId": 1,
      "ruleCode": "WIN-001",
      "ruleVersion": 3,
      "checkMethod": "REGISTRY",
      "checkScript": null,
      "items": [
        {
          "itemId": 10,
          "checkKey": "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\AutoAdminLogon",
          "operator": "=",
          "expectedValue": "0",
          "matchType": "EXACT",
          "valueType": "STRING"
        }
      ]
    }
  ]
}
```

字段说明：

- `type` 固定为 `baseline_scan`，便于 Agent 按任务类型分发。
- `taskId` 对应 `baseline_task.id`。
- `taskHostId` 对应 `baseline_task_host.id`，便于回传后直接更新单台主机任务状态。
- `hostId` 对应平台端主机 ID。
- `macAddress` 用于客户端校验和日志追踪，MQ 路由键仍然使用 MAC 地址。
- `ruleId` 对应 `baseline_rule.id`。
- `ruleCode` 对应 `baseline_rule.rule_code`，用于日志、展示和排错。
- `ruleVersion` 对应 `baseline_rule.version`，用于结果追溯。
- `items[].itemId` 对应 `baseline_rule_item.id`。
- `valueType` 用于平台端后续规则引擎做类型转换，默认可按 `STRING` 处理。

### 客户端回传结果

交换机：`sysinfo_exchange`
队列：`baseline_queue`
路由键：`baseline`

```json
{
  "type": "baseline_scan_result",
  "taskId": 10001,
  "taskHostId": 30001,
  "hostId": 201,
  "macAddress": "00-11-22-33-44-55",
  "scanTime": "2026-06-11T16:31:20+08:00",
  "results": [
    {
      "ruleId": 1,
      "ruleCode": "WIN-001",
      "ruleVersion": 3,
      "itemId": 10,
      "checkKey": "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\AutoAdminLogon",
      "executeStatus": "SUCCESS",
      "actualValue": "0",
      "message": "",
      "evidence": "Registry key AutoAdminLogon = 0"
    }
  ]
}
```

字段说明：

- `executeStatus` 表示客户端执行状态，建议值为 `SUCCESS/ERROR`。
- `actualValue` 是客户端采集到的实际值。
- `evidence` 保存原始证据，如注册表值、服务状态、命令输出片段。
- 最终 `PASS/FAIL/ERROR/UNKNOWN` 由平台端规则引擎写入 `baseline_result.status`。

## 当前实现目标

第一步只实现平台端任务创建与下发：当基线任务创建并关联主机后，平台向每台目标主机的 Agent 下发一个 `baseline_scan` JSON 指令，让 Agent 执行具体检测。

## 本轮完成

- 新增任务创建接口：`POST /api/baseline/tasks`。
- 入参包含：`taskName`、`executeType`、`cronExpr`、`hostIds`、`ruleIds`。
- 创建 `baseline_task` 主记录，并为每台目标主机创建 `baseline_task_host` 关联记录。
- 查询 `baseline_rule` 和 `baseline_rule_item`，只允许下发 `enabled=1` 且 `status=PUBLISHED` 的规则。
- 按每台主机组装 `baseline_scan` JSON，并通过 `agent_exchange` + `{mac地址}` 路由键下发到 Agent。
- 每台主机的下发结果会更新 `baseline_task_host.status`：成功为 `RUNNING`，失败为 `FAILED`。
- 任务整体状态会更新为 `RUNNING` 或 `FAILED`，并记录下发成功/失败数量。
- 新增 `baseline_queue` 与 `sysinfo_exchange` + `baseline` 的绑定配置，为下一步接收客户端回传结果做准备。

## 验证

- 已执行 `mvn test`，编译通过，现有 6 个测试全部通过。

---

## 第二步：接收客户端回传结果并原样入库（本轮完成）

### 目标

平台端消费客户端回传到 `baseline_queue` 的基线检测结果，将完整回传 JSON 原样写入
`baseline_check_data` 表，为后续规则引擎判定提供原始数据层。本轮只做接收与入库，不做任何规则比对。

### 实现

严格复用既有 `AssetDataListener` + `AssetDataService` 的 MQ 消费分层模式，未新建队列连接方式，
未改动原有消息分发架构。`baseline_queue` 与 `sysinfo_exchange` + `baseline` 的绑定上一轮已声明。

新增文件：

- `entity/BaselineCheckDataEntity.java`：对应 `baseline_check_data` 表，字段 `taskId`、`hostId`、`checkData`、`createTime`。
- `mapper/BaselineCheckDataMapper.java` + `resources/mapper/BaselineCheckDataMapper.xml`：`insert` 单条原始数据。
- `service/BaselineCheckDataService.java` + `service/impl/BaselineCheckDataServiceImpl.java`：消息校验与入库。
- `mq/BaselineResultListener.java`：`@RabbitListener(queues = baseline_queue)`，默认 auto-ACK，异常业务层兜底。

### 消费与校验逻辑

- 监听 `baseline_queue`，消费 `type = "baseline_scan_result"` 的消息。
- 基本字段校验：JSON 合法、`type` 匹配、`taskId` 与 `hostId` 不为空且可解析为数字（兼容数字或字符串形式）。
- 将**完整消息体 JSON 原样**存入 `check_data` 字段，同时填入 `task_id`、`host_id`，`create_time` 取当前时间。
- 校验或入库失败：写入 `mq_error_logs` 表并记录 WARN 日志，消息正常 ACK，不重试、不崩溃。
- 消费成功正常 ACK（默认 auto-ACK 模式，与 `AssetDataListener` 一致）。

### 验证

- 已执行 `mvn test`，编译通过，现有 6 个测试全部通过。
- 联调验收：客户端发送一条回传消息后，`baseline_check_data` 表出现对应记录，`check_data` JSON 与发送内容一致；连续消费不丢消息；格式错误时落 `mq_error_logs` 且服务不崩溃。

---

## 第三步：规则引擎判定原始数据并入库（本轮完成）

### 目标

消费 `baseline_check_data` 中的原始数据，逐条与规则参数比对，判定 PASS/FAIL/ERROR，写入
`baseline_result`，并更新任务与主机执行状态、写入汇总统计。本轮只做规则判定与结果入库，不涉及修复与工单。

### 触发方式

入库后内联触发：`BaselineCheckDataServiceImpl` 在原始数据入库成功后直接调用 `BaselineRuleEngine.evaluate(...)`，
与既有资产入库后内联触发漏洞匹配（`triggerStaticVulnMatch`）模式一致。引擎异常在其内部兜底并由调用处
再包一层 try-catch，仅记日志，不回滚已入库的原始数据、不影响 MQ ACK。

### 新增文件

- `entity/BaselineResultEntity.java`：对应 `baseline_result` 表。
- `entity/BaselineSummaryEntity.java`：对应 `baseline_summary` 表（`compliance_rate` 用 `BigDecimal`）。
- `mapper/BaselineResultMapper.java` + xml：`insert`、`countByTaskHostId`（幂等判断）。
- `mapper/BaselineSummaryMapper.java` + xml：`upsert`（基于 `uk_host_task` 的 `INSERT ... ON DUPLICATE KEY UPDATE`）。
- `service/BaselineRuleEngine.java` + `service/impl/BaselineRuleEngineImpl.java`：核心判定逻辑。

### 扩展文件

- `mapper/BaselineRuleMapper`：新增 `selectItemsByItemIds`（按 itemId 反查规则项）、`selectByIds`（按规则 id 取评分等元数据）。
- `mapper/BaselineTaskMapper`：新增 `selectTaskById`、`selectTaskHostByTaskAndHost`、`finishTaskHost`、
  `countUnfinishedHosts`、`countHostsByStatus`，并补充 task / task_host 的 resultMap。
- `service/impl/BaselineCheckDataServiceImpl`：注入 `BaselineRuleEngine`，入库后内联触发。

### 判定逻辑

1. 解析 `check_data` 的 `results` 数组；按 `(taskId, hostId)` 反查 `baseline_task_host` 拿到 `taskHostId`。
2. **幂等**：`baseline_result` 中该 `task_host_id` 已有记录则整条跳过，不重复生成。
3. 逐条 result：`executeStatus=ERROR` 直接记 `ERROR`；否则按 `itemId` 反查规则项，按 `match_type`
   （EXACT/CONTAINS/REGEX）与 `operator`（= != > >= < <=）比对 `actualValue` 与 `expected_value`，
   得 `PASS`/`FAIL`；找不到规则项记 `UNKNOWN`。每条生成一条 `baseline_result`，`remediation_status=PENDING`。
   - EXACT：数值运算符按数字比较；`=`/`!=` 优先数字比较，不可解析时按字符串（忽略大小写）。
   - CONTAINS：实际值是否包含期望值，`!=` 取反。
   - REGEX：实际值是否匹配期望值正则，`!=` 取反；正则非法记 WARN 并按不匹配处理。
4. 该 task_host 处理完：写入 `baseline_summary`（pass/fail/score/合规率，按 host+task upsert），
   `baseline_task_host` 置 `FINISHED` 并写 `scan_time` 与汇总 JSON。
5. 任务下所有主机进入终态（FINISHED/FAILED）后，`baseline_task` 置 `FINISHED` 并回填成功/失败主机数。

合规率 = pass /(pass+fail) × 100，保留两位；UNKNOWN 不计入分母。score 累加命中规则的 `baseline_rule.score`。

### 验证

- 已执行 `mvn test`，编译通过，现有 6 个测试全部通过。
- 联调验收：一台主机回传后 `baseline_result` 按 item 逐条生成，状态判定正确；`baseline_task_host`
  变 `FINISHED` 且 `baseline_summary` 出现对应汇总；同份 `check_data` 重复消费不重复生成 result；
  任务下所有主机完成后 `baseline_task` 变 `FINISHED`。
