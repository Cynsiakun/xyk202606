# 威胁感知平台

Threat Awareness Platform

基于 Spring Boot 3、MyBatis、Spring Security、JWT 与 Layui 的后台管理项目。当前版本已包含登录、后台框架、仪表盘、用户管理、个人信息、系统日志，以及 RBAC 权限基础设施。

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.x

## 数据库初始化

1. 创建数据库：

```sql
CREATE DATABASE xyk2026 DEFAULT CHARACTER SET utf8mb4;
```

2. 执行 [schema.sql](/C:/DATA/2026实训/java/src/main/resources/schema.sql) 初始化表结构。

默认测试账号：

- 用户名：`admin`
- 密码：`admin`

说明：用户密码在数据库中以 MD5 形式存储。

## 启动方式

```bash
mvn package -DskipTests
java -jar target/threat-platform-0.0.1-SNAPSHOT.jar
```

也可以直接使用：

```bash
mvn spring-boot:run
```

启动后访问：

- 登录页：`http://localhost:8080/login.html`
- 后台首页：`http://localhost:8080/index.html`

## 已实现功能

- 登录、退出登录、JWT 登录状态维护
- Spring Security + JWT 鉴权
- RBAC 权限模型：用户、角色、权限、用户角色关联、角色权限关联
- 登录拦截：除 `POST /api/user/login` 外，受保护接口需携带 `Authorization: Bearer <token>`
- 用户管理：新增、编辑、删除、单个查询、分页列表、用户名模糊搜索、用户名/手机号/邮箱唯一性校验
- 个人信息：查看当前用户信息、修改手机号/邮箱、上传头像、修改密码
- 登录日志：记录登录成功和失败日志，支持分页、用户名搜索和状态筛选
- 后台主页统计：用户总数、今日登录次数、今日新增用户、近 7 天活跃用户、日志总数
- Layui 后台框架：左侧菜单通过 iframe 加载独立页面，便于后续权限控制

## 登录功能说明

- 登录页默认填充测试账号 `admin / admin`
- 登录成功后，前端保存 `token` 与当前用户名到 `localStorage`
- 进入后台后，顶部导航栏显示当前登录用户
- 点击退出登录后会调用后端退出接口，并清理本地登录态
- 未登录访问后台页或业务接口时，会自动跳转回登录页

## 登录流程说明

1. 在 `login.html` 输入用户名和密码
2. 前端通过统一请求模块调用 `POST /api/user/login`
3. Spring Security 使用 `UserDetailsService` 加载用户并校验 MD5 密码
4. 登录成功后生成 JWT，包含 `userId`、`userName`、角色和权限信息
5. 登录成功后更新 `last_login_time` 并写入登录日志
6. 前端保存 JWT 并跳转 `index.html`
7. 后续页面请求通过 `Authorization: Bearer <token>` 携带 JWT

## JWT 认证流程

1. 用户提交用户名密码到 `/api/user/login`
2. Spring Security 完成认证
3. 后端生成 JWT
4. JWT 载荷包含：
- `userId`
- `userName`
- `roles`
- `authorities`
5. 前端将 JWT 保存到 `localStorage`
6. JWT 过滤器解析令牌并恢复认证信息到 SecurityContext
7. `@PreAuthorize` 基于权限表达式控制访问

## 后端接口

### 登录与当前用户

```text
POST /api/user/login
POST /api/user/logout
GET  /api/user/current
GET  /api/current-user
```

登录请求示例：

```json
{
  "userName": "admin",
  "password": "admin"
}
```

### 用户管理

```text
GET    /api/user/list?page=1&size=10&userName=admin
POST   /api/user
GET    /api/user/{id}
PUT    /api/user/{id}
DELETE /api/user/{id}
```

新增用户请求字段：

```json
{
  "userName": "alice",
  "userPwd": "123456",
  "userPhone": "13800138000",
  "userEmail": "alice@example.com",
  "userAvatar": "https://example.com/avatar.png",
  "status": 1
}
```

### 个人信息

```text
PUT  /api/user/updateSelf
POST /api/user/avatar/upload
POST /api/user/changePassword
```

修改个人信息请求字段：

```json
{
  "userPhone": "13800138000",
  "userEmail": "admin@example.com"
}
```

修改密码请求字段：

```json
{
  "oldPwd": "admin",
  "newPwd": "123456"
}
```

头像上传返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "avatarUrl": "/uploads/avatar/xxxxxx.jpg"
  }
}
```

### 登录日志

```text
GET /api/login-log/list?page=1&size=10&userName=admin&status=1
```

`status` 可选：

- `1`：成功
- `0`：失败

### 主机管理

```text
GET    /api/host/list?page=1&size=10&keyword=renaissance
POST   /api/host
GET    /api/host/{id}
PUT    /api/host/{id}
DELETE /api/host/{id}
```

`keyword` 模糊匹配主机名 / IPv4 / MAC 地址。接口分别受 `host:view`、`host:create`、`host:update`、`host:delete` 权限控制。

主机数据除前端手动维护外，主要由 RabbitMQ 自动入库（见“RabbitMQ 主机信息上报”章节）。`status` 字段：`1`=在线，`0`=离线；消息消费入库时置为在线。

### 后台主页统计

```text
GET /api/dashboard/statistics
```

返回字段示例：

```json
{
  "totalUsers": 1,
  "todayLoginCount": 3,
  "todayNewUsers": 0,
  "weekActiveUsers": 1,
  "totalLogs": 10
}
```

## 前端目录结构

```text
src/main/resources/static/
├── index.html
├── login.html
├── css/
│   ├── common.css
│   ├── dashboard.css
│   ├── log.css
│   ├── login.css
│   ├── permission.css
│   ├── profile.css
│   ├── role.css
│   └── user.css
├── js/
│   ├── common/
│   │   ├── auth.js
│   │   ├── dialog.js
│   │   ├── request.js
│   │   ├── table.js
│   │   └── utils.js
│   └── pages/
│       ├── dashboard.js
│       ├── index.js
│       ├── log.js
│       ├── login.js
│       ├── permission.js
│       ├── profile.js
│       ├── role.js
│       └── user.js
└── pages/
    ├── dashboard.html
    ├── detect.html
    ├── log.html
    ├── permission.html
    ├── profile.html
    ├── risk.html
    ├── role.html
    ├── setting.html
    ├── threat.html
    └── user.html
```

## 公共模块说明

- `static/js/common/request.js`：统一请求入口，处理请求头、JSON 序列化、失败提示、成功提示以及 401/403 自动跳转
- `static/js/common/auth.js`：统一管理 token、当前用户名、登录态判断和跳转登录
- `static/js/common/utils.js`：通用工具方法，如日期格式化、兜底值处理、模板读取
- `static/js/common/dialog.js`：对 Layui `layer` 的成功、失败、确认弹窗进行二次封装
- `static/js/common/table.js`：封装 Layui 表格分页、查询参数与列表刷新逻辑

## 页面模块说明

- `static/js/pages/login.js`：处理登录校验与登录请求
- `static/js/pages/index.js`：处理后台框架、菜单切换、当前用户展示、退出登录
- `static/js/pages/dashboard.js`：加载首页统计数据
- `static/js/pages/user.js`：处理用户管理列表、搜索、新增、编辑、删除
- `static/js/pages/profile.js`：处理个人信息展示、资料修改、头像上传预览、密码修改
- `static/js/pages/log.js`：处理登录日志查询与分页
- `static/js/pages/role.js`：处理角色管理列表、搜索、新增、编辑、删除、权限分配
- `static/js/pages/permission.js`：处理权限管理列表、搜索、新增、编辑、删除

## 页面结构

- 登录页：`/login.html`
- 后台首页框架：`/index.html`
- 仪表盘：`/pages/dashboard.html`
- 用户管理：`/pages/user.html`
- 个人信息：`/pages/profile.html`
- 系统日志：`/pages/log.html`
- 角色管理：`/pages/role.html`
- 权限管理：`/pages/permission.html`
- 其他占位模块：`/pages/threat.html`、`/pages/risk.html`、`/pages/detect.html`、`/pages/setting.html`

## 前端技术栈

- Layui
- HTML
- CSS
- JavaScript

## 页面访问方式

- 登录页：`/login.html`
- 后台首页：`/index.html`

## 头像上传说明

- 个人信息页支持选择并上传头像图片
- 支持格式：`jpg`、`jpeg`、`png`、`gif`
- 大小限制：5MB 以内
- 上传接口：`POST /api/user/avatar/upload`
- 上传成功后会自动更新当前登录用户头像字段，并立即刷新页面预览

## 系统截图位置

预留截图展示章节，可后续补充登录页、后台首页、用户管理、日志页截图。

## 后续规划

- 用户管理增强
- 威胁分析
- 风险评估
- 系统日志扩展
- AI 检测任务管理
- 模型调用管理
- 风险报告生成

## 数据表

- `user`：后台用户表
- `login_log`：登录日志表
- `test`：原有 CRUD 示例表
- `sys_role`：角色表
- `sys_permission`：权限表
- `sys_user_role`：用户角色关联表
- `sys_role_permission`：角色权限关联表
- `hosts`：主机信息表（MAC 地址唯一）

## RabbitMQ 主机信息上报

服务启动即监听队列 `sysinfo_queue`，自动消费采集端上报的主机系统信息并入库。

- 连接配置：见 `src/main/resources/application-dev.yml` 的 `spring.rabbitmq`
- 交换机：`sysinfo_exchange`，路由键：`sysinfo`，队列：`sysinfo_queue`
- 入库逻辑：按 `mac_address` 唯一性做“存在即更新、不存在即插入”（`INSERT ... ON DUPLICATE KEY UPDATE`）
- 消息为中文键的嵌套 JSON，由 `com.cd.mq.SysInfoListener` 解析；缺少 MAC 地址的消息会被跳过
- `hosts` 表、`host:*` 权限、主机管理菜单在应用启动时由 `HostRbacInitializer` 幂等创建

上报消息示例：

```json
{
  "主机名": { "主机名": "renaissance" },
  "本机IPv4地址": { "本机IPv4": "10.135.48.158" },
  "MAC地址": { "MAC地址": "CC:5E:F8:A1:31:B4" },
  "操作系统信息": { "系统名称": "Windows", "系统版本": "10.0.26200", "系统架构": "64位", "具体版本": "Windows 11" },
  "CPU信息": { "CPU型号": "AMD64 Family 25 Model 116 Stepping 1, AuthenticAMD", "物理核心数": 8, "逻辑核心数": 16 },
  "内存信息": { "总内存": "31.31 GB", "已使用内存": "27.08 GB", "可用内存": "4.23 GB", "使用率": "86.5%" }
}
```

## RBAC 权限模型

- 用户 ←→ 角色：`sys_user_role`
- 角色 ←→ 权限：`sys_role_permission`
- 角色定义：`sys_role`
- 权限定义：`sys_permission`
- 接口权限控制：`@PreAuthorize("hasAuthority('xxx')")`

## 默认角色说明

- `SUPER_ADMIN`：超级管理员，拥有全部权限
- `SECURITY_ADMIN`：安全管理员
- `ANALYST`：分析员
- `AUDITOR`：审计员
- 默认账号 `admin` 自动拥有 `SUPER_ADMIN`

## 数据库设计说明

本阶段新增 RBAC 相关表：

```sql
sys_role
sys_permission
sys_user_role
sys_role_permission
```

说明：

- `sys_role` 保存角色编码和角色名称
- `sys_permission` 保存权限编码、权限名称、类型和路径
- `sys_user_role` 建立用户与角色的多对多关系
- `sys_role_permission` 建立角色与权限的多对多关系

`login_log` 表结构：

```sql
CREATE TABLE IF NOT EXISTS login_log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    user_name VARCHAR(50),
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    status TINYINT,
    message VARCHAR(255)
);
```

## 统一返回格式

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

失败：

```json
{
  "code": 401,
  "message": "未登录或登录状态已失效",
  "data": null
}
```

## 统一异常处理说明

- 后端通过 `GlobalExceptionHandler` 统一处理参数校验异常并返回可读错误信息
- `MethodArgumentNotValidException` 用于处理 `@RequestBody` 校验失败
- `ConstraintViolationException` 用于处理方法参数与路径参数校验失败
- 前端统一通过 `src/main/resources/static/js/common/request.js` 处理接口异常提示
- 当后端返回 `code != 200` 且包含 `message` 时，前端会直接使用 `layui.layer.msg` 展示该错误信息
- 所有使用 `AppRequest.request` 的页面都会复用一致的错误处理逻辑
