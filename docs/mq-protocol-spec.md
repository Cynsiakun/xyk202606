# 威胁感知平台 — 客户端/平台端 MQ 通信规范

> 版本: 1.1 | 更新: 2026-06-16 | 分支: feat/asset-inventory-baseline-upgrade

---

## 1. Exchange 总览

| Exchange | 类型 | 声明方 | 用途 |
|----------|------|--------|------|
| `sysinfo_exchange` | Direct | Agent 端 | Agent → 平台 上行消息 |
| `agent_exchange` | Direct | 平台端 | 平台 → Agent 下发指令 |
| `patch_exchange` | Direct | 平台端 | Agent → 平台 补丁扫描结果 |
| `log_exchange` | Direct | Agent 端 | Agent → 平台 Windows 事件日志 |

---

## 2. Agent → 平台 上行消息（同步通道）

### 2.1 心跳保活

| 属性 | 值 |
|------|-----|
| Exchange | `sysinfo_exchange` |
| Routing Key | `status` |
| 平台消费队列 | `status_queue` |
| 频率 | 每 3 秒 |

```json
{
  "mac_address": "00:1A:2B:3C:4D:5E"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `mac_address` | string | ✓ | MAC地址（原始格式，含分隔符） |

---

### 2.2 主机系统信息上报

| 属性 | 值 |
|------|-----|
| Exchange | `sysinfo_exchange` |
| Routing Key | `sysinfo` |
| 平台消费队列 | `sysinfo_queue` |
| 触发时机 | Agent 启动时 |

```json
{
  "主机名": "DESKTOP-ABC123",
  "本机IPv4地址": "192.168.1.100",
  "MAC地址": "00:1A:2B:3C:4D:5E",
  "操作系统信息": {
    "系统名称": "Microsoft Windows 10 Pro",
    "系统版本": "10.0.19045",
    "系统架构": "x64",
    "具体版本": "2009"
  },
  "CPU信息": {
    "CPU型号": "Intel(R) Core(TM) i7-10750H CPU @ 2.60GHz",
    "物理核心数": 6,
    "逻辑核心数": 12
  },
  "内存信息": {
    "总内存": "16.0 GB",
    "已使用内存": "8.5 GB",
    "可用内存": "7.5 GB",
    "使用率": "53.1%"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `主机名` | string | ✓ | 主机名 |
| `本机IPv4地址` | string | ✓ | IPv4 地址 |
| `MAC地址` | string | ✓ | 原始格式（含 `:`） |
| `操作系统信息.系统名称` | string | | OS 名称（含 Win10/Win11 区分） |
| `操作系统信息.系统版本` | string | | OS 版本号 |
| `操作系统信息.系统架构` | string | | x64/x86 |
| `操作系统信息.具体版本` | string | | 发布版本号（如 2009） |
| `CPU信息.CPU型号` | string | | CPU 型号 |
| `CPU信息.物理核心数` | number | | 物理核心数 |
| `CPU信息.逻辑核心数` | number | | 逻辑核心数 |
| `内存信息.总内存` | string | | 例: `"16.0 GB"` |
| `内存信息.已使用内存` | string | | |
| `内存信息.可用内存` | string | | |
| `内存信息.使用率` | string | | 例: `"53.1%"` |

---

### 2.3 资产探测结果（4 个独立队列）

| 队列 | Routing Key | 对应资产 |
|------|------------|----------|
| `account_queue` | `account` | Windows 本地账号 |
| `service_queue` | `service` | Windows 服务 |
| `process_queue` | `process` | 运行中进程 |
| `app_queue` | `app` | 已安装软件 |

**通用格式（以 account 为例）：**

```json
{
  "type": "account",
  "hostName": "DESKTOP-ABC123",
  "macAddress": "00:1A:2B:3C:4D:5E",
  "taskId": "optional-task-id",
  "accounts": [
    {"username": "Administrator", "enabled": true, "group": "Administrators"}
  ],
  "account_count": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `type` | string | ✓ | `"account"` / `"service"` / `"process"` / `"app"`（必须与队列匹配） |
| `hostName` | string | ✓ | 主机名 |
| `macAddress` | string | ✓ | MAC 地址 |
| `taskId` | string | | 透传任务 ID |
| `{array}` | array | ✓ | 资产数组，字段名与 type 对应：`accounts` / `services` / `processes` / `apps` |
| `{count}` | number | ✓ | 数组长度，字段名：`account_count` / `service_count` / `process_count` / `app_count` |

**各 type 的数组元素结构（客户端自行决定，不做校验）：**

```
type=account  → accounts[].{username, sid, enabled, group, ...}
type=service  → services[].{name, displayName, state, startMode, ...}
type=process  → processes[].{pid, name, path, user, ...}
type=app      → apps[].{name, version, publisher, installDate, ...}
```

---

### 2.4 端口扫描结果（**新增**）

| 属性 | 值 |
|------|-----|
| Exchange | `sysinfo_exchange` |
| Routing Key | `port_scan` |
| 平台消费队列 | `port_scan_queue` |
| 触发时机 | Agent 收到 `"port_scan"` 指令后执行 |

```json
{
  "type": "port_scan",
  "hostName": "DESKTOP-ABC123",
  "macAddress": "00:1A:2B:3C:4D:5E",
  "taskId": "optional-uuid",
  "scanRange": "common",
  "startTime": "2026-06-16T10:00:00+08:00",
  "endTime": "2026-06-16T10:00:45+08:00",
  "openPorts": [
    {
      "port": 22,
      "protocol": "tcp",
      "state": "open",
      "service": "ssh",
      "banner": "SSH-2.0-OpenSSH_8.9"
    },
    {
      "port": 80,
      "protocol": "tcp",
      "state": "open",
      "service": "http",
      "banner": "HTTP/1.1 200 OK\r\nServer: nginx/1.24.0\r\nContent-Type: text/html\r\nX-Powered-By: PHP/7.4.33"
    },
    {
      "port": 3306,
      "protocol": "tcp",
      "state": "open",
      "service": "mysql",
      "banner": "5.7.42-log"
    },
    {
      "port": 6379,
      "protocol": "tcp",
      "state": "open",
      "service": "redis",
      "banner": "+PONG"
    },
    {
      "port": 8080,
      "protocol": "tcp",
      "state": "open",
      "service": "http-proxy",
      "banner": "HTTP/1.1 200 OK\r\nServer: Apache-Coyote/1.1\r\n"
    }
  ],
  "openPortCount": 5,
  "scannedPortCount": 1000,
  "error": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `type` | string | ✓ | 固定 `"port_scan"` |
| `hostName` | string | ✓ | 主机名 |
| `macAddress` | string | ✓ | MAC 地址 |
| `taskId` | string | | 透传任务 ID |
| `scanRange` | string | | 使用的扫描范围：`"common"` / `"full"` / `"custom"` |
| `startTime` | string | | ISO8601 扫描开始时间 |
| `endTime` | string | | ISO8601 扫描结束时间 |
| `openPorts` | array | ✓ | 开放端口列表 |
| `openPorts[].port` | number | ✓ | 端口号 |
| `openPorts[].protocol` | string | ✓ | 协议：`tcp` / `udp` |
| `openPorts[].state` | string | | 状态：`open` / `closed` / `filtered` |
| `openPorts[].service` | string | | 推测服务名（可按端口号默认） |
| `openPorts[].banner` | string | | 原始 Banner 文本 |
| `openPortCount` | number | ✓ | 开放端口数量 |
| `scannedPortCount` | number | | 实际扫描端口数 |
| `error` | string | | 整机扫描失败时填充错误信息 |

---

### 2.5 补丁扫描结果

| 属性 | 值 |
|------|-----|
| Exchange | `patch_exchange` |
| Routing Key | `patch_scan` |
| 平台消费队列 | `patch_scan_queue` |
| 触发时机 | Agent 收到 `"patch_scan"` 指令后执行 |

```json
{
  "type": "patch_scan",
  "hostPatch": {
    "macAddress": "00:1A:2B:3C:4D:5E",
    "os_family": "Windows 10 Pro",
    "os_build": "19045.4529",
    "kernel_version": "10.0.19045",
    "support_status": "Supported",
    "last_boot_time": "2026-06-15 08:30:00",
    "pending_reboot": true,
    "asset_criticality": "Medium",
    "risk_level": "High",
    "missing_patch_count": 12,
    "scan_time": "2026-06-16 10:30:00"
  },
  "installedPatches": [
    {
      "patch_id": "KB5026361",
      "patch_type": "Security Update",
      "product_name": "Windows 10",
      "product_version": "19045",
      "install_time": "2026-06-01 14:22:00",
      "install_status": "Installed",
      "source": "Windows Update",
      "signature_status": "Signed",
      "reboot_required": true,
      "superseded_by": "KB5031356",
      "is_security_patch": true
    }
  ]
}
```

> 字段名同时支持 camelCase 和 snake_case（平台端做兼容转换）。

---

### 2.6 基线扫描结果

| 属性 | 值 |
|------|-----|
| Exchange | `sysinfo_exchange` |
| Routing Key | `baseline` |
| 平台消费队列 | `baseline_queue` |
| 触发时机 | Agent 收到 `"baseline_scan"` 指令后执行 |

#### 2.6.1 基线扫描结果

```json
{
  "type": "baseline_scan_result",
  "taskId": 42,
  "hostId": 10,
  "checkData": {
    "1001": {"checkKey": "dontdisplaylastusername", "currentValue": "0", "expectedValue": "1", "status": "FAIL"},
    "1002": {"checkKey": "MaxPasswordAge", "currentValue": "90", "expectedValue": "90", "status": "PASS"}
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `type` | string | ✓ | 固定 `"baseline_scan_result"` |
| `taskId` | number | ✓ | 基线任务 ID |
| `hostId` | number | ✓ | 主机 ID |
| `checkData` | object | ✓ | 检查结果键值对，key 为 itemId，value 包含 checkKey/currentValue/expectedValue/status |

#### 2.6.2 基线修复结果

```json
{
  "type": "baseline_remediation_result",
  "resultId": 5001,
  "remediationId": 8001,
  "success": true,
  "oldValue": "0",
  "newValue": "1",
  "backupData": "...",
  "message": "修复成功"
}
```

#### 2.6.3 基线回滚结果

```json
{
  "type": "baseline_rollback_result",
  "resultId": 5001,
  "remediationId": 8001,
  "success": true,
  "message": "回滚成功"
}
```

---

### 2.7 漏洞验证结果

| 属性 | 值 |
|------|-----|
| Exchange | `sysinfo_exchange` |
| Routing Key | `vuln_verify_result` |
| 平台消费队列 | `vuln_verify_result_queue` |
| 触发时机 | Agent 收到 `"vuln_verify"` 指令后执行 |

```json
{
  "type": "vuln_verify_result",
  "taskId": 1001,
  "results": [
    {"ruleId": 2001, "matched": true},
    {"ruleId": 2002, "matched": false}
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `type` | string | ✓ | 固定 `"vuln_verify_result"` |
| `taskId` | number/string | ✓ | 验证任务 ID |
| `results` | array | ✓ | 每条漏洞规则的验证结果 |
| `results[].ruleId` | number | ✓ | 漏洞规则 ID |
| `results[].matched` | bool | ✓ | true=命中漏洞, false=未受影响 |

---

### 2.8 Windows 事件日志

| 属性 | 值 |
|------|-----|
| Exchange | `log_exchange` |
| Routing Key | `security_log` |
| 平台消费队列 | `log_queue` |
| 触发时机 | Agent 持续轮询 Security/System/Application 通道 |

```json
{
  "mac_address": "00:1A:2B:3C:4D:5E",
  "record_number": 123456789,
  "log_type": "Security",
  "event_id": 4624,
  "event_time": "2026-06-16 10:30:15",
  "username": "DOMAIN\\jsmith",
  "level": "Information",
  "message": "An account was successfully logged on.",
  "raw_xml": "<Event xmlns=\"http://schemas.microsoft.com/win/2004/08/events/event\">...</Event>"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `mac_address` | string | ✓ | MAC 地址 |
| `record_number` | number | ✓ | Windows EventRecordID（用于断点续传） |
| `log_type` | string | ✓ | 日志通道：`Security` / `System` / `Application` |
| `event_id` | number | ✓ | Windows Event ID（如 4624=登录成功） |
| `event_time` | string | ✓ | 事件发生时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `username` | string | | 关联用户名 |
| `level` | string | | 级别：`Information` / `Warning` / `Error` |
| `message` | string | | 摘要信息 |
| `raw_xml` | string | | 完整 XML 日志 |

> 未注册的 MAC 地址消息会重试 3 次（header `x-retry-count`），超限后写入 `mq_error_logs` 表并 ACK。

---

## 3. 平台 → Agent 下发指令

所有下发指令均通过 `agent_exchange` → `agent_<normalized_mac>_queue` 通道。
路由键为 **原始 MAC 地址**（含分隔符）。

### 3.1 资产探测

```json
{
  "type": "assets",
  "account": 1,
  "service": 1,
  "process": 0,
  "app": 1,
  "macAddress": "00:1A:2B:3C:4D:5E"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定 `"assets"` |
| `account` | 0/1 | 是否探测本地账号 |
| `service` | 0/1 | 是否探测 Windows 服务 |
| `process` | 0/1 | 是否探测运行进程 |
| `app` | 0/1 | 是否探测已安装软件 |
| `macAddress` | string | 目标主机 MAC（回显确认） |

---

### 3.2 端口扫描（**新增**）

```json
{
  "type": "port_scan",
  "macAddress": "00:1A:2B:3C:4D:5E",
  "scanRange": "common",
  "customPorts": "",
  "grabBanner": true,
  "taskId": "optional-uuid"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `type` | string | ✓ | 固定 `"port_scan"` |
| `macAddress` | string | ✓ | 目标主机 MAC |
| `scanRange` | string | ✓ | 扫描范围：`"common"` / `"full"` / `"custom"` |
| `customPorts` | string | | 自定义端口列表（逗号分隔），如 `"22,80,443,3306,6379,8080"`，仅 scanRange=custom 时有效 |
| `grabBanner` | bool | ✓ | 是否抓取 Banner |
| `taskId` | string | | 透传任务 ID |

**Banner 抓取策略：**

| 端口 | 策略 |
|------|------|
| 80, 443, 8080, 8443, 9090 等 Web 端口 | TCP Connect → 主动发送 `HEAD / HTTP/1.0\r\nHost: <ip>\r\n\r\n`，读取响应头 |
| 22 (SSH) | TCP Connect → 被动读取握手 Banner |
| 3306 (MySQL) | TCP Connect → 被动读取握手包 |
| 6379 (Redis) | TCP Connect + 发送 `PING\r\n` |
| 1433 (SQL Server) | TCP Connect → 被动读取预登录包 |
| 25, 587 (SMTP) | TCP Connect → 被动读取 Banner |
| 其他端口 | TCP Connect → 被动读取初始响应 |

`common` 扫描范围建议至少包含：`21,22,23,25,53,80,110,135,139,143,443,445,993,995,1433,1521,3306,3389,5432,6379,8080,8443,9200,9090,27017`

---

### 3.3 基线检查任务

```json
{
  "type": "baseline_scan",
  "taskId": 42,
  "taskHostId": 501,
  "hostId": 10,
  "macAddress": "00:1A:2B:3C:4D:5E",
  "createdAt": "2026-06-16T10:30:00+08:00",
  "checks": [
    {
      "ruleId": 1,
      "ruleCode": "WIN-ACCOUNT-001",
      "ruleVersion": 2,
      "checkMethod": "REGISTRY",
      "checkScript": "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System\\dontdisplaylastusername",
      "items": [
        {
          "itemId": 101,
          "checkKey": "dontdisplaylastusername",
          "operator": "=",
          "expectedValue": "1",
          "matchType": "EXACT",
          "valueType": "NUMBER"
        }
      ]
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定 `"baseline_scan"` |
| `taskId` | number | 基线任务 ID |
| `taskHostId` | number | 任务-主机关联 ID |
| `hostId` | number | 主机 ID |
| `macAddress` | string | MAC 地址 |
| `checks[].ruleId` | number | 规则 ID |
| `checks[].ruleCode` | string | 规则编码 |
| `checks[].ruleVersion` | number | 规则版本号 |
| `checks[].checkMethod` | string | 检测方式：`REGISTRY` / `WMI` / `PROCESS` / `FILE` / `SERVICE` / `COMMAND` / `SCRIPT`（将来扩展：`WEB_MIDDLEWARE` / `DATABASE` / `CACHE`） |
| `checks[].checkScript` | string | 检测脚本或路径 |
| `checks[].items[].itemId` | number | 检查项 ID |
| `checks[].items[].checkKey` | string | 检查键 |
| `checks[].items[].operator` | string | 比较符：`=` / `!=` / `>` / `>=` / `<` / `<=` / `CONTAINS` / `NOT_CONTAINS` |
| `checks[].items[].expectedValue` | string | 期望值 |
| `checks[].items[].matchType` | string | 匹配类型：`EXACT` / `CONTAINS` / `REGEX` |
| `checks[].items[].valueType` | string | 值类型：`NUMBER` / `STRING` / `BOOLEAN` |

---

### 3.4 漏洞验证任务

```json
{
  "type": "vuln_verify",
  "taskId": "1001",
  "macAddress": "00:1A:2B:3C:4D:5E",
  "rules": [
    {
      "ruleId": 2001,
      "productType": "OS",
      "productName": "Microsoft Windows 10 Pro",
      "matchType": "version_range",
      "versionExpression": ">=10.0.19041 && <10.0.19045",
      "verifyType": "REGISTRY",
      "verifyRule": "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\CurrentBuild"
    },
    {
      "ruleId": 2002,
      "productType": "APP",
      "productName": "Google Chrome",
      "matchType": "exact",
      "versionExpression": "<=120.0.6099.109",
      "verifyType": "FILE_VERSION",
      "verifyRule": "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
    }
  ],
  "createdAt": "2026-06-16T10:30:00Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定 `"vuln_verify"` |
| `taskId` | string | 任务 ID |
| `macAddress` | string | MAC 地址 |
| `rules[].ruleId` | number | 漏洞规则 ID |
| `rules[].productType` | string | 产品类型 |
| `rules[].productName` | string | 产品名称 |
| `rules[].matchType` | string | 匹配类型 |
| `rules[].versionExpression` | string | 版本约束表达式 |
| `rules[].verifyType` | string | 验证方式 |
| `rules[].verifyRule` | string | 具体验证规则/路径 |

---

### 3.5 补丁扫描

```json
{
  "type": "patch_scan",
  "macAddress": "00:1A:2B:3C:4D:5E"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定 `"patch_scan"` |
| `macAddress` | string | MAC 地址 |

---

### 3.6 平台端 SysInfo 回显

平台在接收到 `sysinfo` 消息后，会将同一 JSON **原样回发**到该主机的 `agent_<mac>_queue`，作为注册成功确认。Agent 可忽略。

---

## 4. 全局探测策略配置

平台端通过 API 管理自动探测策略，Agent 无需感知。

| 端点 | 方法 | 说明 |
|------|------|------|
| `GET /api/host/probe-strategy` | GET | 查看当前策略 |
| `PUT /api/host/probe-strategy` | PUT | 更新策略 |

**请求/响应格式 (ProbeStrategyDTO)：**

```json
{
  "enabled": true,
  "periodHours": 8,
  "account": true,
  "service": true,
  "process": false,
  "app": true,
  "lastRunAt": "2026-06-16T08:00:00"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | bool | 是否启用自动探测 |
| `periodHours` | number | 探测周期（支持 1, 4, 8, 12, 24） |
| `account` | bool | 探测账号 |
| `service` | bool | 探测服务 |
| `process` | bool | 探测进程 |
| `app` | bool | 探测应用 |
| `lastRunAt` | ISO8601 | 上次执行时间（只读） |

---

## 5. 完整 MQ 拓扑图

```
                          Agent → Platform (上行)
                          =======================

  sysinfo_exchange
    ├── routingKey: "sysinfo"          → sysinfo_queue
    ├── routingKey: "status"           → status_queue (每 3s 心跳)
    ├── routingKey: "baseline"         → baseline_queue (基线结果/修复/回滚)
    ├── routingKey: "vuln_verify_result" → vuln_verify_result_queue
    ├── routingKey: "account"          → account_queue
    ├── routingKey: "service"          → service_queue
    ├── routingKey: "process"          → process_queue
    ├── routingKey: "app"              → app_queue
    └── routingKey: "port_scan"        → port_scan_queue (新增)

  patch_exchange
    └── routingKey: "patch_scan"       → patch_scan_queue

  log_exchange
    └── routingKey: "security_log"     → log_queue (手动 ACK)


                          Platform → Agent (下行)
                          =======================

  agent_exchange
    └── routingKey: <macAddress>       → agent_<mac>_queue (每台主机独享)
        消息类型:
          type: "assets"              资产探测
          type: "port_scan"           端口扫描 (新增)
          type: "baseline_scan"       基线检查
          type: "vuln_verify"         漏洞验证
          type: "patch_scan"          补丁扫描
          sysinfo 回显                注册确认
```

---

## 附录: Agent 队列命名规则

- 队列名: `agent_<normalized_mac>_queue`
- normalized_mac: 原始 MAC 地址去除非十六进制字符后转小写，如 `00:1A:2B:3C:4D:5E` → `001a2b3c4d5e`
- 队列 TTL: 消息最大存活 3 小时（10,800,000ms）
- 队列自动删除: 3 天无消费者后自动清理
