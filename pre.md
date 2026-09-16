# 项目关键实现点与技术决策汇总

> 用于演示时向评审方解释各模块设计中的难点与解决方案。

---

## 一、整体架构

本系统采用 **Spring Boot 3.3 + MyBatis + Spring Security + JWT + RabbitMQ** 架构，平台端负责策略管理、任务调度与结果分析，Agent 端部署于被管主机执行采集和检测。平台与 Agent 之间通过 RabbitMQ 进行异步通信，数据流向清晰：Agent 持续上报心跳/资产/日志，平台按需下发扫描/验证/加固指令。

---

## 二、SaaS 多租户

**关键问题**：多租户之间数据必须完全隔离，绝不能出现租户A看到租户B数据的情况。

**解决方案**：所有业务表（共 25 张）统一增加 `tenant_id` 字段，不再是单纯在应用层做过滤，而是 **行级隔离**。JWT 中携带租户 ID，每次请求由 `JwtAuthenticationFilter` 解析出 `tenantId` 存入 `TenantContextHolder`，后续所有 SQL 查询强制带 `tenant_id` 过滤。同一用户名在不同租户下可重复，由 `uk_user_tenant_name` 组合唯一键保证。平台租户（ID=0）为内置超管，不走租户隔离逻辑。

---

## 三、License 授权安全

**关键问题**：License Key 必须防伪造、防篡改、防跨机器冒用。

**解决方案**：
- 采用 **RSA 数字签名**机制。平台生成 License 时用私钥对（tenantId + edition + hostLimit + userLimit + expireTime + machineId）拼接后签名，签名值存入 `license.signature`
- 激活时 `LicenseSigner.sign()` 重新计算签名并写入，客户端使用时可通过公钥验证合法性
- `LicenseGuard` 在每次操作前实时校验功能特性和配额，而非仅在登录时校验
- 机器绑定：激活时将 `machine_id` 写入 License，后续禁止换机使用

---

## 四、高并发日志采集与处理

**关键问题**：Windows 事件日志量极大（每台机器每天可达数万条），如果逐条入库会严重积压，消费速度远跟不上生产速度。

**解决方案（五层组合拳）**：
1. **批量写入**：`WindowsEventLogBatchWriter` 将消息先放入内存缓冲队列（`LinkedBlockingQueue`，容量 5000），由定时任务每秒 drain 最多 100 条（`batch-size`）组成一个批次，在一个事务内 `INSERT` 完成
2. **背压机制**：缓冲队列有界（5000），满时消费者线程 `put()` 阻塞，prefetch 上限 200（未确认消息不超过 200 条），形成自然背压，防止内存溢出
3. **手动 ACK**：日志队列使用 `MANUAL` 确认模式，入库成功才 ACK，保证可靠性不丢消息
4. **幂等去重**：主表 `uk_host_log_rec`（host_id + log_type + record_number）配合 `INSERT IGNORE`，重复消息直接跳过
5. **坏消息处理**：非法格式 → 写 `mq_error_logs` 后 ACK；主机未注册 → 最多重试 3 次后落 mq_error_logs；无死信队列导致无损

---

## 五、实时告警推送

**关键问题**：安全告警需要实时可见，不能等用户刷新页面才知道有攻击。

**解决方案**：
- **WebSocket 实时推送**：前端与平台建立 WebSocket 长连接，连接时下发当前未处理的 Critical/High 告警做初始同步
- **定时广播**：`AlertBroadcastTask` 定期扫描新增告警，通过 `AlertWebSocketHandler.broadcast()` 推送给所有在线会话
- **多租户隔离**：推送时根据 session 中携带的 `tenantId` 过滤，超管收到全平台告警，租户只收自己租户的
- **安全告警规则引擎**：在日志批量写入的事务内同步评估触发规则（暴力破解 ≥5次、密码喷洒 ≥5次、失败后成功 ≥3次等），生成 `security_alerts` 记录

---

## 六、RBAC 权限控制

**关键问题**：需要细粒度到菜单/接口/按钮三级控制，且修改权限后需要尽快生效。

**解决方案**：
- **三级粒度**：菜单级（`GET /api/rbac/menu/current` 递归剪枝）、接口级（`@PreAuthorize("@perm.has('code')")`）、按钮级（前端 `AppAuth.hasPermission()` 控制渲染）
- **实时查库 + 缓存**：JWT 过滤器按 `userId` 实时查库加载角色权限（非依赖 JWT 载荷），Caffeine 缓存 5 分钟写后过期。角色/权限变更时 `@CacheEvict` 主动失效缓存
- **超管通配**：只在一处判断——`PermissionChecker`，持 `ROLE_SUPER_ADMIN` 对所有 `@perm.has()` 放行，无需逐条授权
- **MD5 密码**：使用 Spring Security 的 `PasswordEncoder` 接口适配 MD5，兼容历史密码格式

---

## 七、平台与 Agent 通信设计

**关键问题**：多台主机如何可靠接收下行指令？同一个交换机下消息不会串到别的机器吗？

**解决方案**：
- **按 MAC 独享队列**：每台 Agent 注册时，平台为其动态创建 `agent_<normalized_mac>_queue`，routing key 为原始 MAC 地址，消息只投递到指定主机的队列
- **队列生命周期**：消息 TTL = 3 小时，队列无消费者 3 天后自动删除，避免长期离线主机堆积残留指令
- **心跳保活**：Agent 每 3 秒向 `status_queue` 发送心跳，平台端 4 秒无心跳标记离线。心跳用自动 ACK 模式，丢几跳不影响业务
- **端口扫描指令防残留**：端口扫描指令 TTL = 2 分钟，超过 2 分钟自动过期。启动时 `PortScanCommandStartupCleaner` 遍历所有主机队列清空残留的 `port_scan` 类型消息
- **指令清理服务**：当用户关闭探针策略或停用端口扫描时，`AgentCommandCleanupService` 主动清空对应队列中的残留指令

---

## 八、漏洞规则引擎

**关键问题**：如何通过"产品名 + 版本号"精确匹配漏洞规则，处理各种版本格式的差异（`1.2.3`、`v1.2.3`、`10.0.19041.1` 等）。

**解决方案**：
- **六种匹配模式**：`name_only`（仅名称命中）、`exact`（精确匹配）、`contains`（模糊包含）、`version_lt/le/gt/ge`（版本号比较）、`version_range`（范围约束，如 `>=10.0.19041,<10.0.19045`）、`regex`（正则）
- **版本号智能归一化**：`VersionCompareUtil.compare()` 从文本中提取数字段，按点号拆分为数组逐段比较，处理非标准后缀（如 `-rc1`, `+build`）
- **缓存优化**：`VulnRuleCacheService` 将规则库全量加载到内存（`ConcurrentHashMap`），匹配阶段无数据库查询
- **自动触发**：资产数据入库后立即触发该主机的漏洞评估，旧结果标 inactive 后批量插入新结果，避免增量对比复杂度

---

## 九、基线合规检测与等保 2.0

**关键问题**：等保 2.0 有 L1~L5 七个等级，不同等级对同一检查项的标准不同；资产类型不仅限于 OS，还有数据库、中间件等 11 种。

**解决方案**：
- **多等级规则**：同一条规则（`baseline_rule`）可勾选多个 `protection_level_flag`（如 L1,L2,L3），不同等级的检查项通过 `baseline_rule_item` 的 `protection_level_id` 区分，各自有不同的 `expected_value`
- **多资产类型**：通过 `baseline_asset_type` 字典 + `baseline_rule_asset_ref` 多对多关联，同一规则可适配多种资产类型
- **任务化执行**：创建任务时指定等保等级，`BaselineTaskServiceImpl` 按等级过滤规则后下发 MQ，Agent 执行后将结果回传
- **规则引擎比对**：支持 `=、!=、>、<、>=、<=、CONTAINS、REGEX` 八种操作符和 `EXACT/CONTAINS/REGEX` 三种匹配类型，`valueType` 为 `NUMBER/STRING/BOOLEAN/ENUM` 时自动推断比较逻辑
- **幂等保证**：同一 task_host 已有结果则跳过，支持重试重新匹配

---

## 十、漏洞一键修复（演示方案）

**关键问题**：演示时需要有修复闭环展示，但来不及对接真实的软件升级/补丁安装流程。

**解决方案**：
- 漏洞检测结果的状态流转为：`PENDING` → `VERIFYING` → `VERIFIED` → `FIXED`
- "一键修复"按钮直接对当前主机/漏洞下所有 `VERIFIED` 状态的结果执行 `UPDATE verify_status = 'FIXED'`，纯数据库操作无 MQ 下发
- 前端实时刷新统计（`verifiedCount → fixedCount`），形成"检测→验证→修复→已修复"的完整闭环展示
- 后续可扩展为通过 MQ 下发真实修复指令（升级软件、安装补丁等）

---

## 十一、日志导出与数据量控制

**关键问题**：安全日志、登录日志导出时可能涉及百万级数据，直接查询会导致 OOM 或响应超时。

**解决方案**：
- **导出上限**：所有 CSV 导出接口限制最多 5 万行，超过部分截断提示
- **流式写入**：使用 `PrintWriter` 流式写入 HTTP 响应，设置 `Content-Disposition` 附件名，不先全部加载到内存
- **UTF-8 BOM**：CSV 文件头写入 BOM（`\uFEFF`），确保 Excel 打开不乱码

---

## 十二、数据库设计要点

**关键问题**：多租户环境下如何保证唯一性约束仍然生效；MAC 地址如何高效查询。

**解决方案**：
- **组合唯一键**：用户名、手机号、邮箱的唯一约束均扩展为 `(tenant_id, field)` 形式（如 `uk_user_tenant_name`），同一租户内唯一但跨租户可重复
- **MAC 规范化查库**：所有 MAC 地址入库前转为小写并去除非十六进制字符（`normalizeMac()`）。HostMapper 通过 `selectByNormalizedMac()` 匹配，虽然需要 `REPLACE()` 函数无法走索引，但配合本地 Caffeine 缓存（写后 5 分钟过期、最大 10,000 条）大幅降低了查库频率
- **INSERT IGNORE / ON DUPLICATE KEY UPDATE**：日志类数据用 `INSERT IGNORE` 防重复，主机信息上报用 `upsertByMac`（`ON DUPLICATE KEY UPDATE`）实现存在即更新

---

## 十三、定时任务与自动探测

**关键问题**：如何在不影响系统性能的前提下，自动对所有在线主机周期性地执行资产探测？

**解决方案**：
- `AssetProbeScheduler` 每 60 秒检查一次策略配置，按 `periodHours`（1/4/8/12/24 可配）判断是否到达下一个探测周期
- 每次最多下发 20 台主机（`AUTO_PROBE_LIMIT = 20`），防止瞬间大量 MQ 消息冲垮系统
- 按策略配置的 `probeAccount/probeService/probeProcess/probeApp` 和 `probePortScan` 开关选择性下发，不探测不需要的资产类型
- 人工手动探测有 8 小时有效期保护（`MANUAL_PROBE_VALID_DURATION`），过期前重复触发需用户确认

---

## 十四、JWT 安全与退出

**关键问题**：JWT 无状态，签发后无法主动撤销，如何实现"退出登录"？

**解决方案**：
- **Token 黑名单**：`JwtTokenBlacklistService` 用内存 `ConcurrentHashMap` 存储已注销的 JWT ID（`jti`），配合 Caffeine 大小限制（10,000）防止内存膨胀。每次请求由 `JwtAuthenticationFilter` 先校验黑名单
- **短过期时间**：JWT 默认过期时间 24 小时，减少黑名单存活时间
- **退出接口**：`POST /api/user/logout` 将当前 token 加入黑名单并清空前端的 `localStorage`
- **TenantContextHolder 清理**：请求结束后在 `finally` 块中 `clear()`，防止线程池复用导致的租户 ID 错乱

---

## 十五、端口指纹识别

**关键问题**：如何从原始 Banner 中自动识别出服务/中间件/数据库的具体产品名和版本？

**解决方案**：
- 平台预置 `asset_fingerprint_rule` 规则表，定义正则匹配模式
- `PortFingerprintServiceImpl.processPortScanResult()` 解析端口扫描结果的 `openPorts` JSON，逐个提取 `port + service + banner`
- 将 `banner` 与规则库逐条正则匹配，命中则生成 `host_asset_inventory` 记录（product_name / version / confidence）
- 每次新的端口扫描结果入库后自动触发指纹识别，历史数据可通过 `processExistingResults()` 回填
