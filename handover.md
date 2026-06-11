# 合规基线模块 交接文档（给前端）

更新时间：2026-06-11

本文档面向接手前端开发的会话。后端已完成「任务下发 → 接收回传 → 规则引擎判定入库」三步，
本次交接说明**已就绪的能力、现有接口、数据流、以及前端要做的可视化所依赖的数据来源**。

---

## 一、整体数据流（已全部跑通后端逻辑）

```
平台创建任务 (POST /api/baseline/tasks)
   ↓ 写 baseline_task + baseline_task_host，按 MAC 下发 baseline_scan 指令到 Agent
Agent 执行检测，回传结果到 MQ baseline_queue
   ↓ BaselineResultListener 消费
原始 JSON 原样入库 baseline_check_data
   ↓ 入库后内联触发规则引擎
规则引擎逐 item 判定 PASS/FAIL/ERROR → 写 baseline_result
   ↓ 该主机处理完
写 baseline_summary（合规率/得分）+ baseline_task_host 置 FINISHED
   ↓ 任务下所有主机终态
baseline_task 置 FINISHED
```

修复、工单、复检尚未实现（后续阶段）。

---

## 二、现有 HTTP 接口

统一返回包裹（见 `common/Result.java`）：

```json
{ "code": 200, "message": "success", "data": { ... } }
```

成功 `code=200`；失败由 `GlobalExceptionHandler` 统一处理，`code` 非 200，`message` 为错误原因。

### 1. 创建并下发基线任务

- **POST** `/api/baseline/tasks`
- 请求体：

```json
{
  "taskName": "周度合规检查",
  "executeType": "MANUAL",        // 可选，MANUAL/SCHEDULED，默认 MANUAL
  "cronExpr": null,                // 可选，executeType=SCHEDULED 时填
  "hostIds": [201, 202],           // 必填，目标主机 ID 列表
  "ruleIds": [1, 2, 3]             // 必填，规则 ID 列表（仅 enabled=1 且 status=PUBLISHED 可下发）
}
```

- 响应 `data`（`BaselineTaskDispatchResponseDTO`）：

```json
{
  "taskId": 10001,
  "status": "RUNNING",             // RUNNING=至少一台下发成功；FAILED=全部失败
  "totalHostCount": 2,
  "sentCount": 2,
  "failedCount": 0,
  "message": "基线任务已创建并下发",
  "hosts": [
    { "hostId": 201, "taskHostId": 30001, "macAddress": "00-11-22-33-44-55", "sent": true, "message": "下发成功" }
  ]
}
```

> ⚠️ 目前**只有这一个写接口**。查询类接口（任务列表、结果详情、主机视角合规率、统计概览、导出）
> **后端尚未提供**，需要前端列出需求后由后端补；或前端先按下方表结构 mock。

---

## 三、前端可视化所需数据的来源（库表）

数据库：MySQL `127.0.0.1:3306`，库名 `xyk2026`，root/root。表结构详见 `database.md`。
下面只列前端可视化最相关的几张表与关键字段：

### baseline_task（任务主表）
任务列表 / 任务详情头部用。
- `id, task_name, execute_type, status(PENDING/RUNNING/FINISHED/FAILED)`
- `total_host_count, success_count, fail_count, creator, create_time, start_time, finish_time`

### baseline_task_host（任务-主机关联）
任务详情里「各主机执行情况」用。
- `id, task_id, host_id, status(PENDING/RUNNING/FINISHED/FAILED)`
- `result_summary`（JSON，引擎写入：`{passCount, failCount, score, complianceRate}`）
- `scan_time`

### baseline_result（检测结果明细）★核心
结果列表、不合规项钻取用。一条 = 某主机某规则某检查项的结果。
- `id, task_id, task_host_id, host_id, rule_id, rule_version, check_key`
- `status`：**PASS / FAIL / ERROR / UNKNOWN**
- `actual_value`（实际值）, `expected_value`（期望值）, `message`（判定描述）, `evidence`（原始证据）
- `remediation_status`：目前固定 `PENDING`（修复阶段未做）
- `scan_time, create_time`

### baseline_summary（汇总统计）★主机视角
主机合规率、首页统计用。唯一键 `(host_id, task_id)`。
- `host_id, task_id, pass_count, fail_count, score`
- `compliance_rate`：DECIMAL(5,2)，如 `95.50`，= pass/(pass+fail)×100，UNKNOWN 不计入分母
- `last_scan_time, update_time`

### baseline_rule / baseline_rule_item（规则与检查项）
规则管理页、结果里展示规则名称/分类/风险等级用。
- rule：`rule_code, rule_name, category, severity(critical/high/medium/low), score, enabled, status`
- item：`rule_id, check_key, operator, expected_value, match_type, remark`

---

## 四、状态值字典（前端做标签/颜色映射用）

| 字段 | 取值 | 含义 |
|------|------|------|
| baseline_task.status | PENDING / RUNNING / FINISHED / FAILED | 任务整体状态 |
| baseline_task_host.status | PENDING / RUNNING / FINISHED / FAILED | 单台主机执行状态 |
| baseline_result.status | PASS / FAIL / ERROR / UNKNOWN | 单项判定结果 |
| baseline_result.remediation_status | PENDING（当前唯一值） | 修复状态，后续阶段启用 |
| baseline_rule.severity | critical / high / medium / low | 风险等级 |
| executeType | MANUAL / SCHEDULED | 执行方式 |

判定语义补充：
- `ERROR` = 客户端执行失败（`executeStatus=ERROR`），非规则不合规。
- `UNKNOWN` = 平台找不到对应规则项无法比对，属异常数据。
- 合规率分母只算 PASS+FAIL，ERROR/UNKNOWN 不计入。

---

## 五、后端代码位置（前端联调时排查用）

- 控制器：`controller/BaselineTaskController.java`（仅创建接口）
- 任务下发：`service/impl/BaselineTaskServiceImpl.java`
- MQ 消费：`mq/BaselineResultListener.java` + `service/impl/BaselineCheckDataServiceImpl.java`
- 规则引擎：`service/impl/BaselineRuleEngineImpl.java`
- Mapper：`mapper/Baseline*.java` + `resources/mapper/Baseline*.xml`
- 进度全记录：`progress.md`（含三步详细实现与回传协议 JSON 样例）

---

## 六、前端建议优先级 & 需要后端补的查询接口

需求见 `requirements.md`（规则管理、基线检测、加固、工单、导出、主机视角可视化）。
当前后端只有「创建任务」写接口，前端要做可视化，建议**先向后端提以下查询接口需求**（这些都还没写）：

1. 任务列表（分页 + status 过滤）：`GET /api/baseline/tasks`
2. 任务详情（含各主机执行情况）：`GET /api/baseline/tasks/{id}`
3. 结果明细列表（按 task / host / status 过滤）：`GET /api/baseline/results`
4. 主机视角合规概览（读 baseline_summary）：`GET /api/baseline/summary?hostId=`
5. 规则列表（规则管理页）：`GET /api/baseline/rules`

下发指令需要的 `hostIds` / `ruleIds`，前端可复用既有主机列表接口与（待补的）规则列表接口。

---

## 七、本地起服务

- 启动类：`ThreatPlatformApplication`
- 构建/测试：`mvn test`（当前 6 个测试全绿）
- MQ：`139.155.139.242:15333` admin/admin（队列绑定已在 `RabbitMQConfig` 声明）
