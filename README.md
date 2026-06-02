# 后台管理系统

基于 Spring Boot 3 + MyBatis + Layui 的后台管理项目。当前版本在原有登录、用户表和后台框架基础上，补齐了左侧菜单对应的真实业务功能。

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

默认账号：

- 用户名：`admin`
- 密码：`admin`

密码在数据库中以 MD5 形式存储。

## 启动方式

```bash
mvn package -DskipTests
java -jar target/threat-platform-0.0.1-SNAPSHOT.jar
```

启动后访问：

- 登录页：`http://localhost:8080/login.html`
- 后台首页：`http://localhost:8080/index.html`

## 已实现功能

- 登录、退出登录、登录状态维护
- 登录拦截：除 `POST /api/user/login` 外，`/api/**` 接口均需携带 `Authorization: Bearer <token>`
- 用户管理：新增、编辑、删除、单个查询、分页列表、用户名模糊搜索、用户名/手机号/邮箱唯一性校验
- 个人信息：查看当前用户信息、修改手机号/邮箱/头像、修改密码
- 登录日志：记录登录成功和失败日志，支持分页、用户名搜索和状态筛选
- 后台主页统计：用户总数、今日登录次数、今日新增用户、近 7 天活跃用户、日志总数
- Layui 后台框架：左侧菜单通过 iframe 加载独立页面，便于后续权限控制

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
POST /api/user/changePassword
```

修改个人信息请求字段：

```json
{
  "userPhone": "13800138000",
  "userEmail": "admin@example.com",
  "userAvatar": "https://example.com/avatar.png"
}
```

修改密码请求字段：

```json
{
  "oldPwd": "admin",
  "newPwd": "123456"
}
```

### 登录日志

```text
GET /api/login-log/list?page=1&size=10&userName=admin&status=1
```

`status` 可选：

- `1`：成功
- `0`：失败

### 后台主页统计

```text
GET /api/dashboard/statistics
```

返回字段：

```json
{
  "totalUsers": 1,
  "todayLoginCount": 3,
  "todayNewUsers": 0,
  "weekActiveUsers": 1,
  "totalLogs": 10
}
```

## 前端页面

所有真实业务页面均放在 `src/main/resources/static` 下：

```text
src/main/resources/static/
├── index.html
├── login.html
├── dashboard.html
├── user-list.html
├── profile.html
├── login-log.html
├── css/
│   ├── login.css
│   └── main.css
└── js/
    ├── login.js
    ├── main.js
    └── request.js
```

`index.html` 左侧菜单当前指向：

- 后台主页：`dashboard.html`
- 用户管理：`user-list.html`
- 登录日志：`login-log.html`
- 个人信息：`profile.html`

## 数据表

- `user`：后台用户表
- `login_log`：登录日志表
- `test`：原有 CRUD 示例表

`login_log` 字段：

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

## 统一异常处理说明

- 后端通过 `GlobalExceptionHandler` 统一处理参数校验异常并返回可读错误信息：
  - `MethodArgumentNotValidException`（`@RequestBody` 校验失败）返回示例：

```json
{
  "code": 400,
  "message": "邮箱格式不正确",
  "data": null
}
```

  - `ConstraintViolationException`（方法参数/路径参数校验失败）返回示例：

```json
{
  "code": 400,
  "message": "手机号格式不正确",
  "data": null
}
```

- 前端统一封装请求：`src/main/resources/static/js/request.js` （`AppRequest.request`）。当返回 `code != 200` 时，前端会自动使用 `layui.layer.msg(message)` 展示错误提示，避免静默失败。

- 保存类操作（`POST` / `PUT`）默认会显示 “保存成功”；调用方也可以通过 `extraOptions.successMessage` 自定义成功提示。

- 影响面：系统中所有使用 `AppRequest.request` 的页面（用户管理、角色管理、权限管理等）将自动复用该异常提示逻辑。

```
