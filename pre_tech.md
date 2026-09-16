## 2.2 相关技术介绍

### 2.2.1 Spring Boot

本项目平台端使用 Spring Boot 3.3 作为基础框架。Spring Boot 基于"约定优于配置"的理念，通过自动配置和 Starter 依赖极大简化了 Spring 应用的搭建过程。平台端的 Web 接口、安全鉴权、消息队列、缓存、WebSocket 等模块均以 Spring Boot Starter 方式集成，无需繁琐的 XML 配置，使开发人员能专注于威胁感知业务逻辑的实现。

### 2.2.2 MySQL

本项目使用 MySQL 8.x 作为关系型数据库，存储用户、主机、资产、漏洞规则、基线策略、安全日志等核心业务数据。平台涉及 40 余张业务表，通过多租户字段（tenant_id）实现行级数据隔离。针对日志类高频写入场景，采用 INSERT IGNORE 幂等插入和组合唯一键防止数据重复；针对主机信息上报场景，采用 ON DUPLICATE KEY UPDATE 实现存在即更新。

### 2.2.3 MyBatis

本项目使用 MyBatis 作为持久层框架，通过 XML 映射文件和 Mapper 接口将 Java 对象与 SQL 语句关联。与 JPA/Hibernate 等全自动 ORM 不同，MyBatis 允许开发人员直接编写和优化 SQL，这在安全分析场景中尤为重要——许多统计查询涉及多表联查、CASE WHEN 条件聚合和复杂的状态流转统计，手写 SQL 能获得更好的可读性和执行效率。

### 2.2.4 RabbitMQ

本项目使用 RabbitMQ 作为平台与 Agent 客户端之间的消息中间件。平台与 Agent 之间共有 15 个队列覆盖心跳、资产探测、端口扫描、补丁扫描、漏洞验证、基线检查、Windows 日志采集等通信链路。上行消息通过四个 Exchange 按路由键分发至对应队列；下行指令通过按 MAC 地址创建的专属队列投递至特定主机。RabbitMQ 的持久化投递和手动 ACK 机制保证了消息的可靠传递。

### 2.2.5 Spring Security + JWT

本项目使用 Spring Security 结合 JSON Web Token（JWT）实现无状态认证与鉴权。用户登录成功后服务端签发 JWT，前端将其存入 localStorage 并在后续请求的 Authorization 头中携带。JWT 过滤器对每个请求实时从数据库加载用户的角色与权限信息，而非单纯信任令牌载荷，使得权限变更即时生效。JWT 注销通过内存黑名单实现，配合默认 24 小时过期时间，平衡安全性与可用性。

### 2.2.6 Caffeine Cache

本项目使用 Caffeine 作为本地内存缓存库，替代传统 Redis 方案以降低部署复杂度。Caffeine 基于 W-TinyLFU 淘汰算法，兼具高命中率与低内存开销。平台主要用于两处缓存：一是用户鉴权信息（角色与权限），写后 5 分钟过期，角色/权限变更时通过 @CacheEvict 主动失效；二是 MAC 地址与主机 ID 的映射关系，避免日志消费时每条消息都查库。最大容量均为 10,000 条，确保内存可控。

### 2.2.7 WebSocket

本项目使用 WebSocket 协议实现安全告警的实时推送。平台在启动时注册 WebSocket 端点，前端建立长连接后服务端通过 AlertWebSocketHandler 维护所有在线会话。连接建立时立即推送当前未处理的高危告警做初始同步，后续由定时任务检测新告警并通过 broadcast() 方法向匹配租户的会话推送增量数据。WebSocket 的全双工特性使前端无需轮询即可实时感知安全事件。

### 2.2.8 Layui

本项目前端采用 Layui 作为前端 UI 框架。Layui 是一款经典模块化前端框架，提供表格、表单、弹窗、分页等丰富的后台管理组件。前端页面通过 iframe 按子页加载各功能模块，左侧菜单由后端按用户权限动态下发并递归剪枝无权限节点。配合统一封装的请求模块（AppRequest），所有接口调用自动携带 JWT、处理 401/403 跳转和统一错误提示。

### 2.2.9 Apache POI & Commons CSV

本项目使用 Apache POI 和 Commons CSV 处理文件导入导出需求。主机信息支持 CSV 批量导入，通过 CsvImportUtil 统一解析表头映射和状态校验，存在则更新、不存在则新增。安全日志、登录日志等查询结果支持 CSV 导出，采用流式写入 HTTP 响应并自动添加 UTF-8 BOM 确保 Excel 兼容，单次导出上限 50,000 行防止大数据量导致内存溢出。
