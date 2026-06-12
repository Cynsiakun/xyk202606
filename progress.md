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
