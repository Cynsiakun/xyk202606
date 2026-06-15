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

## 2026-06-12 规则数据修正

- 修复 `baseline_rule` / `baseline_rule_item` 中 Windows 反斜杠丢失问题，备份文件：
  `baseline_path_backup_20260612_195303.sql`。
- 修复注册表 `check_key` 中路径和值名粘连问题，例如：
  `...System.evtxMaxSize` -> `...System.evtx\MaxSize`。
- 将以下复杂输出规则改为 `POWERSHELL` 脚本直接输出 JSON 字典，客户端可按约定 JSON 精确取 `actualValue`：
  `WIN-5-1`、`WIN-5-2`、`WIN-5-3`、`WIN-5-4`、`WIN-6-3`、`WIN-7-1`、`WIN-7-11`、`WIN-7-12`、`WIN-7-21`、`WIN-7-22`。
- 本轮规则语义修正备份文件：
  `baseline_rules_semantic_backup_20260612_205528.sql`。

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

---

## 第四步：「基线任务管理」前端页面 + 配套查询接口（本轮完成）

### 目标

提供前端页面，让用户在页面上完成创建任务、查看任务结果、查看问题主机的全流程。
原仅有创建接口，本轮补齐只读查询接口并新建前端页面（layui，风格对齐漏洞/资产模块）。

### 新增后端只读接口（均在 `/api/baseline`，`@PreAuthorize` 守卫）

- `GET /api/baseline/tasks?page&size&status` → 任务分页列表（`baseline:view`）。
  行字段：id、taskName、executeType、ruleCount、hostCount、avgPassRate、status、createTime。
- `GET /api/baseline/tasks/{id}/result` → 结果概览（总主机/已完成主机/平均合规率/PASS 主机/FAIL 主机）。
- `GET /api/baseline/tasks/{id}/problem-hosts?page&size` → 问题主机分页（fail_count>0）：主机名/IP/合规率/失败规则数。
- `GET /api/baseline/rules?keyword` → 规则选项（仅 enabled=1 且 status=PUBLISHED）。
- 选主机复用既有 `GET /api/host/list`。
- 创建接口路径不变（`POST /api/baseline/tasks`），新增 `baseline:create` 守卫。

聚合口径：ruleCount 用 `JSON_LENGTH(JSON_EXTRACT(rule_scope,'$.ruleIds'))`；avgPassRate / avgComplianceRate
取 `AVG(baseline_summary.compliance_rate)`；PASS 主机 = summary.fail_count=0，FAIL 主机 = fail_count>0。

### 新增后端文件

- DTO：`BaselineTaskListItemDTO`、`BaselineTaskResultOverviewDTO`、`BaselineProblemHostDTO`、`BaselineRuleOptionDTO`。
- `mapper/BaselineQueryMapper.java` + `resources/mapper/BaselineQueryMapper.xml`。
- `service/BaselineQueryService.java` + `service/impl/BaselineQueryServiceImpl.java`。
- `common/config/BaselineMenuInitializer.java`：登记 `baseline:view/create` 权限并授权
  SECURITY_ADMIN / ANALYST / AUDITOR（超管通配放行），在 sys_menu 插入「合规基线 / 基线任务管理」
  指向 `./pages/baseline-task.html`。
- 改动：`controller/BaselineTaskController.java`（`@RequestMapping` 改为 `/api/baseline`，POST 映射到
  `/tasks` 保持对外路径不变；新增 4 个 GET）。

### 新增前端文件（layui，复用 App* 公共组件）

- `static/pages/baseline-task.html`、`static/css/baseline-task.css`、`static/js/pages/baseline-task.js`。
- 任务列表：分页表格 + 状态筛选 + 刷新 + 新建；操作列「查看结果」。
- 新建任务弹窗：任务名、执行方式（立即/定时，定时显示 Cron）、主机多选（搜索，来自 host/list）、
  规则多选（搜索 + 全选，默认全选，来自 baseline/rules）；提交 `POST /api/baseline/tasks` 后关闭并刷新。
- 查看结果弹窗：概览卡片（总主机/已完成/平均合规率/PASS/FAIL）+ 问题主机分页表；全部合规时显示「全部合规通过」。
- 列表每 10 秒轮询：仅当存在 PENDING/RUNNING 任务时自动 `reloadData` 刷新状态。

### 验证

- 已执行 `mvn test`，编译通过，现有 6 个测试全部通过。
- 说明：本环境无浏览器，UI 未实跑，需在浏览器中按验收点核对（创建立即任务、列表刷新状态、查看结果弹窗）。
- 权限：菜单依赖 `baseline:view`；若已有用户登录态缓存了旧权限列表，需重新登录以拉取新权限/菜单。

---

# 第五步：主机合规总览页面（主机视角）

## 目标

在「合规基线」下新增「主机合规总览」子菜单，从主机视角查看合规健康度，并支持
查看检测结果、自动修复、创建工单、单台/批量立即检测，全部在同一页面内完成不跳转。

## 关键设计决策（经确认）

- 自动修复：**下发 MQ 修复消息**。镜像扫描下发逻辑向 `agent_<mac>_queue` 发
  `type=baseline_remediation`，并复用 `baseline_queue` 既有消费者按 `type` 分支接收
  `baseline_remediation_result` 回填修复状态（不新建队列、不改原分发架构）。
- 立即检测规则范围：**沿用该主机最近一次任务的 rule_scope**，无历史时回退全部已发布规则。
- 工单：**新建最小工单表** `baseline_workorder`（CREATE TABLE IF NOT EXISTS，幂等初始化）。

## 后端新增

- 新表 `baseline_workorder`（host_id/rule_id/result_id/assignee/remark/status/creator/时间），
  由 `BaselineMenuInitializer.createWorkorderTable()` 幂等建表。
- 新增 `BaselineHostController`（`/api/baseline`）：
  - `GET /hosts`：主机卡片分页（每主机取最近一次 baseline_summary join hosts），支持 keyword + level 筛选；`baseline:view`。
  - `GET /hosts/{hostId}/results`：主机最近一次任务逐规则结果，`onlyFail` 默认 true；`baseline:view`。
  - `POST /hosts/scan`：单台/批量立即检测，逐主机复用 `createAndDispatch`；`baseline:create`。
  - `POST /remediations`：自动修复下发；`baseline:remediate`。
  - `POST /workorders`：创建工单；`baseline:remediate`。
- 新增 DTO：BaselineHostOverviewDTO、BaselineHostResultItemDTO、BaselineScanHostsRequestDTO、
  BaselineRemediationRequestDTO、BaselineWorkorderRequestDTO、BaselineActionResponseDTO。
- 新增实体/Mapper：BaselineWorkorderEntity、BaselineWorkorderMapper(+XML)。
- 扩展 Mapper：
  - BaselineQueryMapper：selectHostOverviewPage/countHostOverview/selectHostResults（含合规等级筛选、最近一次任务取数）。
  - BaselineResultMapper：selectByIds/updateRemediationStatus/updateRemediationStatusByIds。
  - BaselineTaskMapper：selectLatestRuleScopeByHost。
- 新增服务：BaselineHostService、BaselineRemediationService、BaselineWorkorderService（及 impl）。
- 改动 `BaselineCheckDataServiceImpl`：`processBaselineResult` 按 `type` 分支，
  `baseline_scan_result` 走原入库+规则引擎；`baseline_remediation_result` 回填 remediation_status。
- 改动 `BaselineMenuInitializer`：登记 `baseline:remediate` 权限并授予 SECURITY_ADMIN/ANALYST；
  新增子菜单「主机合规总览」→ `./pages/baseline-host.html`。

remediation_status 字典：空/PENDING=可操作，IN_PROGRESS=修复中，COMPLETED=已修复，
FAILED=修复失败(可重试)，TICKETED=已派单。

## 前端新增（layui，复用 App* 与卡片栅格）

- `static/pages/baseline-host.html`、`static/css/baseline-host.css`、`static/js/pages/baseline-host.js`。
- 顶部操作区：合规等级筛选 + 关键字搜索 + 刷新 + 批量检测（勾选卡片）。
- 卡片栅格：`grid auto-fill minmax(300px)` 响应式；合规率环形图，绿(≥90)/黄(60-90)/红(<60)；
  含勾选框、检测总数/符合/不符合、最后检测时间、查看详情/检测按钮。
- 主机详情弹窗：顶部概览 + 规则结果表（默认仅不合规，可切全部）；操作列按 remediationType/状态
  显示自动修复/创建工单，支持多选批量；创建工单弹窗填负责人 + 备注。
- 轮询：网格每 10 秒静默刷新（页面可见时）；详情表存在 IN_PROGRESS 时每 10 秒刷新。

## 验证

- 已执行 `mvn -o test`，编译通过，现有 6 个测试全部通过。
- MyBatis 合规等级筛选使用 `"green".equals(level)` 形式，避免 OGNL 多字符字符串 == 比较的坑。
- 本环境无浏览器，UI 未实跑，需在浏览器按验收点核对（卡片合规颜色、筛选/搜索、详情弹窗、
  单条/批量自动修复与创建工单、单台立即检测）。
- 自动修复 COMPLETED 回填依赖 agent 端实现 `baseline_remediation` 并回发 `baseline_remediation_result`；
  agent 未实现前状态停留在「修复中」，后端链路已就绪。
- 权限：菜单依赖 `baseline:view`，加固/工单依赖 `baseline:remediate`；老登录态需重新登录拉取新权限/菜单。

# 第六步：基线加固与回滚

## 已完成

- 自动修复下发前写入 `baseline_remediation`，记录 `result_id/host_id/rule_id/remediation_type/old_value/new_value/execute_script/status/start_time`。
- 修复结果回传 `baseline_remediation_result` 后，同时更新：
  - `baseline_remediation.status=SUCCESS/FAILED`，并保存客户端回传的 `oldValue/newValue/backupData`。
  - `baseline_result.remediation_status=COMPLETED/FAILED`。
- 新增回滚接口 `POST /api/baseline/remediations/rollback`，按 `resultId` 查最近一条 `SUCCESS` 修复记录，把备份数据下发给 Agent。
- 新增复检接口 `POST /api/baseline/results/recheck`，按所选结果涉及的主机和规则创建小范围检测任务。
- 主机详情结果查询改为每个 `hostId + ruleId + checkKey` 取最新结果，避免单条复检后详情表只剩复检规则。
- 主机详情表将“最后检测”列替换为“证据”，支持查看完整证据。
- 前端按钮状态：
  - 修复前：显示“自动修复”。
  - 修复完成：显示“回滚 + 复检”。
  - 回滚完成：恢复“自动修复”。
  - 支持批量自动修复、批量回滚、批量复检、批量创建工单。

## 修复下发 JSON

```json
{
  "type": "baseline_remediation",
  "remediationId": 50001,
  "resultId": 90001,
  "taskId": 20,
  "hostId": 201,
  "macAddress": "00-11-22-33-44-55",
  "ruleId": 1,
  "ruleCode": "WIN-001",
  "checkMethod": "REGISTRY",
  "checkKey": "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\AutoAdminLogon",
  "remediationType": "AUTO",
  "executionMode": "SCRIPT",
  "oldValue": "1",
  "actualValue": "1",
  "expectedValue": "0",
  "backupRequired": true,
  "remediationScript": "...",
  "createdAt": "2026-06-12T21:50:00+08:00"
}
```

## 修复回传 JSON

```json
{
  "type": "baseline_remediation_result",
  "remediationId": 50001,
  "resultId": 90001,
  "hostId": 201,
  "status": "SUCCESS",
  "oldValue": "1",
  "newValue": "0",
  "backupData": "{...}",
  "message": "remediation completed"
}
```

## 回滚下发 JSON

```json
{
  "type": "baseline_rollback",
  "remediationId": 50001,
  "resultId": 90001,
  "hostId": 201,
  "macAddress": "00-11-22-33-44-55",
  "ruleId": 1,
  "ruleCode": "WIN-001",
  "checkMethod": "REGISTRY",
  "checkKey": "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\AutoAdminLogon",
  "rollbackMode": "BACKUP_DATA",
  "oldValue": "1",
  "newValue": "0",
  "backupData": "{...}",
  "executeScript": "...",
  "createdAt": "2026-06-12T21:52:00+08:00"
}
```

## 回滚回传 JSON

```json
{
  "type": "baseline_rollback_result",
  "remediationId": 50001,
  "resultId": 90001,
  "hostId": 201,
  "status": "SUCCESS",
  "message": "rollback completed"
}
```

## 状态约定

- `baseline_result.remediation_status=IN_PROGRESS`：修复或回滚下发中。
- `COMPLETED/FIXED/SUCCESS`：前端显示“回滚 + 复检”。
- `ROLLBACK/PENDING/FAILED`：前端可再次显示“自动修复”。
- `baseline_remediation.status=SUCCESS` 的最近一条记录作为回滚数据源；回滚成功后该记录置为 `ROLLBACK`。

## 客户端执行约定

- 加固默认使用 `executionMode=SCRIPT`：客户端以 PowerShell 执行平台下发的 `remediationScript`，不内置规则语义，不自行猜测注册表/服务/命令的修改方式。
- 加固脚本应在修改前完成备份，并输出 JSON。平台优先读取回传 JSON 中的 `backupData/oldValue/newValue/status/message`。
- `checkMethod/checkKey/actualValue/expectedValue` 是下发给客户端的上下文，主要用于脚本备份、日志、审计和必要的辅助定位；不是要求客户端绕过脚本自行修复。
- 回滚默认使用 `rollbackMode=BACKUP_DATA`：客户端优先按平台下发的 `backupData` 恢复，不应默认再次执行 `executeScript`，因为它是原修复脚本/审计上下文，不一定是反向脚本。
- 如果某条规则未来确实需要客户端内置语义修复，应单独扩展 `executionMode`，例如 `NATIVE_REGISTRY/NATIVE_SERVICE`，并要求平台下发完整 `checkMethod/checkKey/valueType/expectedValue`。

## 平台端消费修复结果闭环

- 消息队列沿用既有约定：
  - 平台下发：`agent_exchange` -> `agent_{mac}_queue`，routing key 为原始 MAC。
  - 客户端回传：`sysinfo_exchange` -> `baseline_queue`，routing key 为 `baseline`。
- `BaselineResultListener` 监听 `baseline_queue`，`BaselineCheckDataServiceImpl` 按 `type` 分发：
  - `baseline_scan_result`：原始检测结果入 `baseline_check_data`，触发规则引擎生成 `baseline_result`。
  - `baseline_remediation_result`：回填 `baseline_remediation` 和 `baseline_result.remediation_status`。
  - `baseline_rollback_result`：将成功修复记录置为 `ROLLBACK`，并恢复结果为可再次修复状态。
- 修复闭环规则：
  - 客户端回传 `remediationId` 时按该记录更新；未回传时平台按 `resultId` 找最近 `PENDING` 修复记录兜底。
  - `status=SUCCESS` 且存在 `backupData` 且能找到修复记录时，`baseline_remediation.status=SUCCESS`，`baseline_result.remediation_status=COMPLETED`。
  - 修复成功但缺少 `backupData`，或找不到对应修复记录时，平台按失败处理，避免前端出现无法真实回滚的“回滚”按钮。
  - 回滚下发前要求存在最近 `SUCCESS` 修复记录且 `backupData` 非空。
- 回滚成功后 `baseline_remediation.status=ROLLBACK`，`baseline_result.remediation_status=ROLLBACK`，前端恢复“自动修复”。

## 2026-06-12 主机详情可用性与修复闭环调整

- 修复成功闭环修正：
  - 客户端回传 `baseline_remediation_result.status=SUCCESS` 后，平台更新 `baseline_remediation.status=SUCCESS`。
  - 同时更新原 `baseline_result.remediation_status=FIXED`，即“脚本修复已成功执行”。
  - `backupData` 允许为空；回滚下发仍会带 `oldValue/newValue/checkKey` 供客户端兜底恢复，不能因为空备份把成功修复误判为失败。
  - 修复成功后平台自动创建小范围复检任务：`hostId + ruleId`。
  - 复检结果入库时会继承上一条同 `hostId + ruleId + checkKey` 的修复语义：
    - 复检 `PASS`：新结果 `status=PASS`，`remediation_status=FIXED`。
    - 复检 `FAIL/ERROR`：新结果 `status=FAIL/ERROR`，`remediation_status=FAILED`。
- `baseline_remediation` 增加 `operator` 字段，启动时由 `BaselineMenuInitializer` 幂等补列，记录下发修复的操作人。
- 主机详情页体验调整：
  - 新增状态筛选：全部、未修复、已修复、FAIL、PASS。
  - 新增分类筛选：按当前主机结果动态生成分类，如账户安全、日志审计等。
  - 新增关键字搜索：规则名、检测项、实际值、期望值、证据、修复状态。
  - 新增详情页刷新按钮。
  - 表格刷新、轮询、修复/回滚/复检后刷新均保留当前页码和每页数量，不再跳回第一页。
  - 表格新增“修复状态”“修复信息”列，展示最后一次修复状态、修复方式、操作人、修复时间。
  - 行操作新增“记录”，可查看该检测结果的全部修复历史，包括状态、方式、操作人、时间、旧值、新值、备份和执行脚本。

## 2026-06-13 当前快照统计与修复记录追溯修正

- 主机合规总览卡片不再使用 `baseline_summary` 最近任务汇总作为统计来源。
- 新统计方式为“当前状态快照”：
  - 对每个 `host_id + rule_id + check_key` 只取最新一条 `baseline_result`。
  - 再聚合当前最新结果：`PASS` 计符合，`FAIL/ERROR` 计不符合。
  - 复检单条规则不会再导致主机卡片变成 `1/1` 或合规率 `100%`。
- 修复记录与回滚不再只按当前 `resultId` 查找。
  - 原因：修复后自动复检会生成新的 `baseline_result.id`，详情页展示的是新结果；如果继续按新 `resultId` 精确查，会找不到旧 result 上的成功修复记录。
  - 新逻辑：按同一 `host_id + rule_id + check_key` 追溯最近成功修复记录。
  - “修复信息”“修复记录”“回滚”都会跨复检 result 追溯到同一检测项的历史修复记录。

## 2026-06-13 检测状态与修复状态最终状态机

> 以本节为准；上文早期出现的 `COMPLETED/PENDING/ROLLBACK` 旧状态流转说明已废弃，仅作为历史记录保留。

- `baseline_result.status` 只表示检测结果，只能由检测/复检/定时检测写入：`PASS/FAIL/ERROR/UNKNOWN`。
- 自动加固和回滚流程禁止直接修改 `baseline_result.status`。
- `baseline_result.remediation_status` 只表示修复流程状态：
  - `NONE`：未修复。
  - `IN_PROGRESS`：修复或回滚执行中。
  - `FIXED`：修复成功，等待或已经通过复检确认。
  - `FAILED`：修复后复检仍失败，或修复执行失败。
  - `ROLLED_BACK`：回滚成功，后续复检结果仍保留该修复状态。
- 初始检测结果写入 `remediation_status=NONE`。
- 自动修复下发只更新 `remediation_status=IN_PROGRESS`，不修改 `status`。
- 修复回传 `SUCCESS`：
  - 更新对应 `baseline_remediation.status=SUCCESS`，保存 `oldValue/newValue/backupData/message/end_time`。
  - 更新当前结果 `remediation_status=FIXED`。
  - 自动下发单规则复检。
- 修复后复检：
  - 复检 `PASS`：新结果 `status=PASS`，`remediation_status=FIXED`。
  - 复检 `FAIL/ERROR`：新结果 `status=FAIL/ERROR`，`remediation_status=FAILED`。
- 回滚回传 `SUCCESS`：
  - 不覆盖原自动修复记录。
  - 新增一条 `baseline_remediation` 记录，`remediation_type=ROLLBACK`，记录回滚时间、结果、message。
  - 更新当前结果 `remediation_status=ROLLED_BACK`。
  - 自动下发单规则复检。
- 回滚后复检：
  - 复检结果只更新检测 `status`。
  - `remediation_status` 保持 `ROLLED_BACK`，例如 `status=FAIL + remediation_status=ROLLED_BACK` 表示已回滚且当前检测恢复为不合规。

## 2026-06-13 基线任务管理页面优化

- 任务列表支持按任务名称搜索，按执行方式、任务类型、任务状态筛选。
- 任务类型使用兼容识别：
  - `基线复检-*`、`修复后自动复检-*`、`主机即时检测-*` 显示为复检任务。
  - 其他任务显示为检测任务。
- 任务列表默认按创建时间/ID 倒序，刷新和轮询保留当前页码、分页大小和筛选条件。
- 任务执行中显示进度：已完成主机数 / 总主机数 + 百分比进度条。
- 新建任务弹窗：
  - 离线主机灰色展示并禁选。
  - 规则列表显示复选框状态。
  - 支持按规则分类一键全选。
  - 实时显示已选主机数和规则数。
  - 未选择主机或规则时提交按钮禁用。
- 任务结果由弹窗改为页面内结果面板，避免弹窗套弹窗。
- 结果统计卡片改为：总主机数、已完成主机数、平均合规率、问题主机数、问题规则数、PASS 规则数、FAIL 规则数、ERROR 规则数。
- 主机结果列表显示每台主机 PASS/FAIL/ERROR 规则数，并提供：
  - 查看详情：跳转到 `baseline-host.html?hostId=...`，主机详情页自动打开该主机详情。
  - 重新检测：复用 `/api/baseline/hosts/scan`。
- 任务结果面板支持局部刷新和 CSV 导出，导出包含主机、规则、状态、实际值、证据等字段。

## 2026-06-13 基线任务管理页面问题修复

- 查看任务结果时报 `Unknown column 't.id' in 'where clause'` 的根因不是 `baseline_task` 缺少 `id`，而是 MySQL 派生表内不能引用外层 `t.id`。
- 已修正任务结果概览 SQL：问题主机数子查询改用当前 `taskId` 参数，避免派生表作用域错误。
- 新建定时任务不再要求用户填写 Cron 表达式。
  - 前端改为“每天 / 每周 / 每月 + 时间”的直观选择。
  - 提交时再转换为后端现有 `cronExpr`，不改后端调度协议。
- 规则选择器改回稳定的原生 checkbox。
  - 移除自绘 `fake-check`。
  - 动态主机/规则 checkbox 增加 `lay-ignore`，避免 Layui 动态渲染状态不同步导致“数据选中但视觉不亮/不灭”。
- 已验证：
  - `node --check src/main/resources/static/js/pages/baseline-task.js` 通过。
  - `mvn test` 通过，12 个测试全部成功。
  - 使用数据库最新任务直接执行任务结果概览 SQL 可正常返回统计。

## 2026-06-13 复检任务结果边界修正

- 任务管理页的“查看结果”必须展示该任务自身的检测结果，不再混入主机合规详情页的当前快照。
- 新增任务内规则明细接口：
  - `GET /api/baseline/tasks/{taskId}/hosts/{hostId}/results`
  - 查询条件固定为 `task_id + host_id`，只返回本次任务产生的 `baseline_result`。
- 任务结果页点击主机行“查看详情”后，不再跳转到 `baseline-host.html`。
  - 页面内展开“本次任务规则明细”表。
  - 复检任务如果只复检了 1 台主机 + 1 条规则，就只展示这一条规则结果。
  - 规则明细包含规则、分类、状态、期望值、实际值、检测项、证据和检测时间。
- 规则选择列表继续使用和“全选”一致的原生 checkbox。
  - 每条规则前都有可见复选框。
  - 保留 `lay-ignore`，避免 Layui 动态渲染导致勾选视觉状态不同步。
- 已验证：
  - `node --check src/main/resources/static/js/pages/baseline-task.js` 通过。
  - `mvn test` 通过，12 个测试全部成功。
  - 使用数据库最新任务 `task_id=30` 查询任务内规则结果，只返回该任务该主机的 1 条规则结果。

## 2026-06-13 基线人工工单管理闭环

- 新增安全运维工程师角色：
  - 角色编码：`SECURITY_OPERATOR`。
  - 角色名称：安全运维工程师。
  - 仅授予：`workorder:view`、`workorder:process`、`workorder:complete`。
  - 不授予用户管理、权限管理、基线规则管理、基线任务管理权限。
  - 启动初始化会幂等创建演示账号 `operator / 123456` 并绑定该角色，便于验收。
- 新增工单权限与菜单：
  - `workorder:view`：查看安全工单。
  - `workorder:process`：开始处理、重新检测。
  - `workorder:complete`：完成工单。
  - 菜单：合规基线 / 安全工单管理，页面为 `baseline-workorder.html`。
- `baseline_workorder` 按新表结构使用 `assignee_id` 关联用户。
  - 启动初始化会幂等补齐 `title/advice/assignee_id/priority/close_remark/create_by/start_time/finish_time/update_time` 等必要列。
  - 旧的手填 `assignee` 模式已废弃。
- 主机详情页创建工单改造：
  - 处理人不再手工输入。
  - 下拉框调用 `/api/baseline/workorders/operators`，只展示拥有 `SECURITY_OPERATOR` 角色且启用的用户。
  - 后端再次校验 `assigneeId` 必须属于安全运维工程师，不能绕过前端。
- 创建工单逻辑：
  - 根据 `resultId` 反查主机、规则、检测项、实际值、期望值。
  - 自动生成工单标题。
  - 自动生成修复建议/检测上下文。
  - 自动继承规则 `severity` 作为优先级，规范为 `LOW/MEDIUM/HIGH/CRITICAL`，缺省为 `MEDIUM`。
  - 创建后将对应 `baseline_result.remediation_status` 标记为 `TICKETED`。
- 新增安全工单管理页面：
  - 支持按工单标题、主机名称/IP 搜索。
  - 支持按状态、优先级筛选。
  - 列表字段：工单ID、标题、主机名称、规则名称、处理人、优先级、状态、创建时间、完成时间、操作。
  - 操作：查看详情、开始处理、完成工单、重新检测。
  - 页面采用“工单列表 -> 工单详情”的页面内切换，不做弹窗套弹窗。
- 工单详情展示：
  - 工单标题、主机信息、规则名称、分类、修复建议、优先级、处理人、状态、创建时间、开始时间、完成时间、创建人、检测状态、检测上下文、处理说明。
- 状态流转：
  - `OPEN`：待处理。
  - `PROCESSING`：处理中。
  - `DONE`：已完成。
  - 只有 `OPEN` 允许开始处理，并记录 `start_time`。
  - 只有 `PROCESSING` 允许完成，完成时必须填写 `close_remark`，并记录 `finish_time`。
  - 只有 `DONE` 允许重新检测。
- 权限边界：
  - 超级管理员与 `SECURITY_ADMIN` 可查看全部工单。
  - `SECURITY_OPERATOR` 只能查看/处理/完成 `assignee_id` 为自己的工单。
  - 后端服务层统一裁剪查询和详情访问，禁止通过接口访问其他工程师工单。
- 重新检测：
  - 工单完成后点击“重新检测”会创建 `工单复检-*` 基线任务。
  - 复检范围严格限定为当前工单的 `hostId + ruleId`。
  - 后续 PASS/FAIL 仍由基线检测结果入库和规则引擎负责。
- 已验证：
  - `node --check src/main/resources/static/js/pages/baseline-workorder.js` 通过。
  - `node --check src/main/resources/static/js/pages/baseline-host.js` 通过。
  - `mvn test` 通过，12 个测试全部成功。
  - `mvn clean test` 通过，12 个测试全部成功，确认从干净编译开始无问题。

## 2026-06-13 基线规则管理 CRUD

- 新增菜单：合规基线 / 基线规则管理。
  - 页面：`baseline-rule.html`。
  - 脚本：`baseline-rule.js`。
  - 样式：`baseline-rule.css`。
- 新增规则管理权限：
  - `baseline-rule:view`：查看基线规则。
  - `baseline-rule:create`：新增基线规则。
  - `baseline-rule:update`：修改、启用、停用基线规则。
  - `baseline-rule:delete`：归档删除基线规则。
  - `SECURITY_ADMIN` 自动授予上述权限；`SECURITY_OPERATOR` 不授予。
- 新增后端 API：
  - `GET /api/baseline/rule-management`：分页查询规则。
  - `GET /api/baseline/rule-management/{id}`：查看规则详情。
  - `POST /api/baseline/rule-management`：新增规则。
  - `PUT /api/baseline/rule-management/{id}`：编辑规则。
  - `DELETE /api/baseline/rule-management/{id}`：逻辑删除，设置 `status=ARCHIVED` 且 `enabled=0`。
  - `PATCH /api/baseline/rule-management/{id}/enabled?enabled=0|1`：启用/停用。
- 查询支持：
  - 关键词：`rule_code/rule_name/category`。
  - 分类：`category`。
  - 风险等级：`LOW/MEDIUM/HIGH/CRITICAL`。
  - 状态：`PUBLISHED/DRAFT/ARCHIVED`。
  - 启用状态：`enabled=1/0`。
- 规则表单采用右侧抽屉，不使用弹窗嵌套。
  - 新增必填：`ruleCode/ruleName/category/severity/osType/checkMethod/checkScript/remediationType`。
  - 编辑时 `ruleCode` 禁止修改。
  - 可维护：说明、评分、状态、启用、强制项、检测脚本、修复脚本。
- 规则版本控制：
  - 新增规则默认 `version=1`。
  - 每次编辑执行 `version = COALESCE(version, 1) + 1`。
  - 已被任务引用的规则编辑时，前端抽屉展示风险提示；当前项目默认“直接覆盖当前版本并递增 version”。
- 删除策略：
  - 不物理删除规则，避免破坏历史任务引用。
  - 删除操作实际归档：`status=ARCHIVED`，`enabled=0`。
- 检测方式约束：
  - 后端限制 `checkMethod` 只能是 `REGISTRY/SERVICE/POWERSHELL/WMI`。
  - 客户端仍统一按已约定方式执行脚本类规则；平台不再新增 COMMAND/SCRIPT 语义分支。
- 与任务联动：
  - 任务创建页仍使用原 `/api/baseline/rules` 选项接口。
  - 该接口只返回 `enabled=1 AND status='PUBLISHED'` 的规则，因此停用或归档规则不会再参与新任务选择/下发。
- 已验证：
  - `node --check src/main/resources/static/js/pages/baseline-rule.js` 通过。
  - `mvn clean test` 通过，12 个测试全部成功。
  - `mvn test` 通过，12 个测试全部成功，并确认新增静态资源已复制到 `target/classes`。

## 2026-06-13 抽屉层级遮挡修复

- 问题：Layui 表格固定操作列（如“查看详情/编辑/停用”）层级高于自定义抽屉，导致抽屉滑出后操作栏仍浮在抽屉上方。
- 修复位置：`src/main/resources/static/css/common.css`。
- 修复策略：
  - 降低 `.layui-table-fixed/.layui-table-fixed-r/.layui-table-fixed-l` 的页面层级。
  - 全局提高 `.drawer-mask` 层级到 `9000`。
  - 全局提高常见抽屉容器 `.rule-drawer/.risk-drawer/.host-detail-drawer/.log-detail-drawer/.evt-detail-drawer` 层级到 `9001`。
- 影响范围：所有使用这些抽屉类名的页面，不只基线规则管理页。
- 已验证：
  - `mvn test` 通过，12 个测试全部成功。
  - `target/classes/static/css/common.css` 已同步更新。
