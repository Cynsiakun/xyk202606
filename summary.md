# 平台端功能实现概要

## 已实现功能清单

- [x] SaaS 多租户管理
- [x] 授权管理（License）
- [x] 主机管理（含自动上报与心跳保活）
- [x] 资产探测管理（账号、进程、服务、APP）
- [x] 端口扫描与指纹识别
- [x] 补丁安全与风险分析
- [x] 漏洞检测（基于规则引擎）
- [x] 日志安全（安全日志、登录安全日志）
- [x] 基线合规检测与加固（支持等保 2.0 多等级）
- [x] RBAC 权限控制（菜单级/接口级/按钮级）
- [x] 态势感知大屏
- [x] 安全事件告警（实时推送）
- [x] AI 智能分析接入

---

## SaaS 多租户

平台通过 `tenant` 表维护租户基础信息，每张业务表（`hosts`、`accounts`、`services`、`processes` 等 25 张表）均扩展了 `tenant_id` 字段实现数据隔离。平台内置 ID=0 的租户作为超级管理端。创建租户时通过 `TenantServiceImpl.create()` 同时创建初始管理员账号，自动分配 `TENANT_ADMIN` 角色并继承安全管理员权限。所有业务查询通过 `TenantContextHolder.getTenantId()` 从请求上下文中获取当前租户 ID 进行行级过滤，实现天然的数据隔离。

## 授权管理（License）

License 模块基于 `license_plan`（套餐定义表）和 `license`（授权凭证表）实现。平台预定义 TRIAL、STANDARD、PROFESSIONAL 三种套餐，每个套餐包含用户数上限、主机数上限及功能特性列表。管理端可生成加密签名的 License Key，通过 `LicenseSigner` 签名防止篡改。租户激活时绑定机器 ID，`LicenseGuard` 在运行时实时校验功能特性与配额，超管租户默认全功能放行。

## 主机管理

主机管理以 `hosts` 表为核心，支持 REST 接口手动增删改查、CSV 批量导入和 Agent 自动上报三种数据来源。Agent 通过 RabbitMQ 向 `sysinfo_queue` 推送系统信息，`SysInfoListener` 按 MAC 地址 upsert 入库。主机心跳通过 `heartbeat()` 更新 `updated_at` 字段，后台定时任务每 5 秒将超时未心跳的主机标记为离线。主机管理同时是资产探测、补丁扫描、漏洞检测等功能的数据入口与分发目标。

## 资产探测与管理

资产探测管理涵盖账号（`accounts`）、进程（`processes`）、服务（`services`）和 APP（`apps`）四类资产数据。平台通过 `probe_strategy` 表配置探测策略（周期、启禁、端口范围等），`AssetProbeScheduler` 定时扫描在线主机列表，构建探测消息通过 RabbitMQ 下发给各主机 Agent 的专属队列。Agent 执行探测后将结果推回 MQ 对应的资产队列，`AssetDataListener` 解析入库。每次资产更新后自动触发漏洞规则引擎重新评估。

## 端口扫描与指纹识别

端口扫描流程：管理端通过 `probe_strategy` 表配置扫描周期与范围，定时任务按周期下发 `port_scan` 指令至各主机 Agent（按 MAC 地址路由到专属队列）。Agent 执行扫描后回传开放端口列表，平台接收并入库原始结果。指纹识别模块随后启动，提取端口、服务、banner 等信息，与指纹规则库进行多层匹配（banner 正则 > 端口+服务 > 端口兜底），生成最终的资产清单并关联分类、厂商、产品等属性。结果入库后自动触发漏洞规则引擎重新评估该主机的漏洞风险。

## 补丁安全

补丁模块管理已安装补丁（`installed_patch`）及主机补丁状态（`host_patch_status`）。平台通过 MQ 向 Agent 下发 `patch_scan` 扫描指令，Agent 扫描后回传缺失补丁列表及待重启标识。`PatchScanServiceImpl` 解析结果并 upsert 入库，随后调用 `RuleEngineService` 加载 `PatchRiskRule` 规则链（EolRule、MissingPatchRule、PendingRebootRule、KnownIssueRule、InstallFailureRule）逐条评估风险，结果写入 `host_patch_risk`。平台端提供风险总览、列表查询和分析/扫描操作入口。

## 漏洞检测

漏洞检测基于规则引擎实现，规则定义在 `vuln_rule` 表中，支持按资产类型（OS/APP/SERVICE/PROCESS）和版本号表达式匹配。引擎在资产数据到达后自动触发，也可手动对指定主机进行评估。`VulnRuleEngineImpl.evaluateHostInternal()` 加载主机信息及其关联资产 JSON，遍历所有缓存规则进行名称模糊匹配与版本表达式比对，匹配结果生成 `host_vuln_result`。旧结果标记为 inactive 后批量插入新结果，支持忽略误报、批量验证和**一键修复**。修复为演示需要直接置 verify_status 为 FIXED，后续可扩展为下发真实修复指令。

## 日志安全

日志安全包含安全事件日志和登录安全日志两个模块。Agent 通过 MQ 推送 Windows 安全日志（Security/System/Application）及登录安全事件到平台，由 `WindowsEventLogListener` 和 `LoginSecurityLogListener` 消费入库。平台端提供分页查询、按类型/时间统计、CSV 导出（最多 5 万行）等功能，支持安全事件的状态流转（新发现→已确认→已修复）。结果数据通过 `AltertWebSocketHandler` 实时推送到前端弹窗告警。

## 基线合规检测与加固（含等保 2.0）

基线模块是国内等保 2.0 合规检测的核心功能。`baseline_asset_type` 和 `baseline_protection_level` 字典表定义了 11 种资产类型及 L1~L5 七个等保等级。检测流程：管理端创建任务并选择主机与规则，`BaselineTaskServiceImpl` 遍历主机按 OS 类型过滤规则后通过 MQ 下发 `baseline_scan` 指令。Agent 执行检查并回传结果，`BaselineRuleEngineImpl` 负责比对——逐条解析 `baseline_rule_item` 中的操作符（`=、!=、>、<、>=、<=、CONTAINS、REGEX`）与期望值，判定 PASS/FAIL，生成 `baseline_result` 明细与 `baseline_summary` 汇总。任务全部完成后自动生成工单，支持一键加固指令（通过 MQ 下发修复脚本）。

## RBAC 权限控制

RBAC 模块基于五表模型（用户-角色-权限-用户角色关联-角色权限关联）实现三级粒度控制。菜单级：`GET /api/rbac/menu/current` 根据用户权限动态下发菜单树，无权限的菜单节点自动剪枝。接口级：通过 `@PreAuthorize("@perm.has('code')")` 注解守卫，超管通配放行。按钮级：`GET /api/rbac/permission/current` 返回权限码列表供前端控制按钮渲染。权限变更通过 `@CacheEvict` 清除 Caffeine 缓存，确保修改即时生效。

## 态势感知

态势大屏是安全运营的综合视图，`ThreatScreenService.overview()` 聚合资产分布、漏洞严重性与转化漏斗、补丁风险、基线合规状态、近 7 天趋势、主机风险节点拓扑和实时事件流等数据。每个主机风险得分由漏洞（高风险 22 分、中风险 12 分等）、补丁风险 8 分、告警 4 分、基线不合规 2 分等加权计算，按临界/高危/中危/安全四级划分。超管视角下聚合全平台多租户数据，支持宏观监控。

## 安全事件告警

安全事件由 Agent 采集推送至 `security_alerts` 表，平台提供分页查询、统计分析、详情查看和状态管理（确认、修复）。`AlertBroadcastTask` 定时广播未处理告警，`AlertWebSocketHandler` 通过 WebSocket 实时推送高危及紧急告警到前端弹出提示，前端通过 `afterId` 轮询 `newHighCritical()` 接口实现增量拉取。

## AI 智能分析

平台接入 DashScope 大模型 API（`application.yml` 中配置），提供资产分析与风险报告生成能力。`AiService` / `AssetAiAnalysisService` 将资产概览、漏洞数据、补丁风险、基线结果等结构化为 Prompt 上下文，调用 AI 生成分析建议；`/api/ai/report` 接口支持基于全量数据的风险态势报告自动生成。
