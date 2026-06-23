# Progress 2

## Tenant foundation

- 已完成 `tenant` / `license` 基础模型初始化。
- 平台租户 `id = 0` 已落库。
- `license` 表无 `signature` 字段。

## V2 tenant migration

- 新增迁移脚本：`sql/migration/V2_add_tenant.sql`
- 本阶段仅为业务数据表补充 `tenant_id BIGINT NOT NULL DEFAULT 0`
- 规则库 / 知识库 / 字典类表未纳入本批：
  - `baseline_rule`
  - `baseline_rule_item`
  - `baseline_rule_history`
  - `patch_cve_map`
  - `patch_issue_intel`
  - `patch_risk_rule`
  - `vuln_rule`
  - `sys_*`

## Entity updates

- 已为本批业务实体补充 `private Long tenantId;`
- DTO 暂未增加 `tenantId`

## Mapper updates

- 已为相关业务 Mapper 补充 `tenant_id` 映射与写入默认值
- 查询逻辑保持原样，不引入 tenant 过滤

## Verification

- 本地数据库 `xyk2026` 已执行 V2 迁移
- 业务表 `tenant_id` 覆盖核验通过
- 历史数据默认归属 `tenant_id = 0`
- `mvn test` 通过


## V3 tenant context

- Added `TenantContextHolder` based on `ThreadLocal<Long>`.
- JWT now carries `tenantId` while keeping existing claims unchanged.
- Authentication filter parses `tenantId`, sets request attributes, and writes `TenantContextHolder`.
- Request completion clears `TenantContextHolder` in `finally`.
- Login flow sets tenant context after authentication succeeds.
- No business SQL tenant filtering has been introduced in this stage.
- Verified login token payload contains `"tenantId": 0`.
- `mvn test` passed.

## V4 Host tenant isolation

- Scope: Host module only.
- Host list/page/search now uses `tenant_id = TenantContextHolder.getTenantId()`.
- Host detail/update/delete now validate by `id + tenant_id`.
- Host creation writes `tenantId` from server-side tenant context.
- Host CSV import writes and matches hosts within the current tenant.
- Manual asset probe checks the host within the current tenant.
- RabbitMQ, heartbeat, auto-probe, scanner-facing legacy HostMapper methods are kept compatible.
- No MyBatis interceptor, SQL rewrite, or other module isolation was introduced.

## V4 Host API verification

- Started the packaged application locally and tested real HTTP APIs.
- Created temporary tenants `901` and `902`, users `it_host_tenant_a` and `it_host_tenant_b`, then removed them after verification.
- Login tokens carried `tenantId = 901` and `tenantId = 902`.
- Tenant A and Tenant B could each create Host records; database `hosts.tenant_id` was written as `901` and `902`.
- Host list/search returned only the current tenant's Host.
- Cross-tenant detail/update/delete returned business `code = 404`.
- Cross-tenant delete did not remove the other tenant's Host.
- Same-tenant update/delete succeeded.
- Temporary Host/user/tenant test data was cleaned up.

## V5 Asset tenant isolation

- Scope: Account, Service, Process, App asset domain.
- Asset overview, host-latest, list, detail, delete now use `TenantContextHolder.getTenantId()`.
- Asset AI analysis read/write-back now validates by `id + tenant_id`.
- Asset export validates Host by `id + tenant_id` and reads Process/Service/App latest records by current tenant.
- Asset export logs now write current `tenantId`.
- Added tenant-aware Mapper methods and XML SQL for Account/Service/Process/App while keeping legacy methods for MQ, vulnerability, scanner, and other not-yet-isolated paths.
- MQ asset ingestion was not modified.
- No MyBatis interceptor or SQL rewrite was introduced.
- `mvn clean test` passed.
- Packaged application startup reached `ACCEPTING_TRAFFIC`.

## V5 Asset API verification

- Started the packaged application locally and tested real HTTP APIs.
- Created temporary tenants `911` and `912`, users `it_asset_tenant_a` and `it_asset_tenant_b`, Hosts, and Account/Service/Process/App records, then removed them after verification.
- Login tokens carried `tenantId = 911` and `tenantId = 912`.
- Account/Service/Process/App list APIs returned only the current tenant's records.
- Account/Service/Process/App detail APIs blocked cross-tenant access with business `code = 404`.
- Account/Service/Process/App delete APIs blocked cross-tenant deletes with business `code = 404`.
- Same-tenant Account/Service/Process/App deletes succeeded.
- Account AI analysis endpoint blocked cross-tenant access before calling AI.
- Asset export blocked cross-tenant Host export and succeeded for own-tenant Host.
- Asset export read own-tenant Process/Service/App records and wrote export log `tenant_id = 911`.
- Temporary Host/asset/user/tenant test data was cleaned up.

## V6 Log tenant isolation

- Scope: user-visible log domain only.
- Covered login logs, security log center (`windows_event_logs`), login security logs, account change logs, and security events (`security_alerts`).
- List/page/search, detail, stats, host options, and CSV export now use `TenantContextHolder.getTenantId()`.
- Security event `ack` and `resolve` now update by `id + tenant_id`, preventing cross-tenant status changes.
- Log query DTOs gained server-side `tenantId` fields; controllers and frontend request parameters are unchanged.
- Login success logs inherit the authenticated user's tenant context; existing-user failed login logs set that user's tenant before recording.
- MQ log ingestion, alert rule engine internals, WebSocket popup queries, and other not-yet-isolated background paths were not changed.
- No MyBatis interceptor or SQL rewrite was introduced.
- Per user instruction, no interface/API test was run for this tenant isolation stage.
- `mvn clean test` passed.

## V6 follow-up: alert popup and dashboard tenant checks

- WebSocket popup alerts are now tenant-aware.
- WebSocket handshake stores `tenantId` from JWT in the session attributes.
- Normal tenant sessions receive only alerts whose `security_alerts.tenant_id` matches their own tenant.
- Platform super admin sessions (`tenantId = 0` with super-admin/wildcard authority) receive all popup alerts.
- Popup alert queries now select `tenant_id` for server-side routing.
- `PopupAlertDTO.tenantId` is marked `@JsonIgnore`, so WebSocket payloads do not expose tenantId.
- Current `*ResponseDTO` scan found no exposed `tenantId` field.
- `/api/dashboard/statistics` user totals and today's new users now use `tenant_id`; login-log statistics were already tenant-aware.
- Per user instruction, no interface/API test was run for this tenant isolation follow-up.
- `mvn clean test` passed.

## V7 Security business-domain tenant isolation

- Scope: vulnerability, user-side patch security, and baseline business data.
- Vulnerability domain now filters `host_vuln_result` and `host_vuln_task` by current tenant for summary, lists, affected hosts, active results, ignore, verify, retry, and dashboard raw SQL.
- Vulnerability rule matching reads Host and asset snapshots through tenant-aware methods and writes `host_vuln_result.tenant_id`.
- `vuln_rule` remains a platform-level rule library.
- Patch domain user-side summary, host risk list, risk details, scan target selection, analyze target selection, and installed patch CRUD now use current tenant.
- Patch risk upsert and fixed-marking are tenant-aware for `host_patch_risk`.
- `PatchScanServiceImpl`, MQ consumers, and MQ ingestion mappers were not modified.
- `patch_cve_map`, `patch_issue_intel`, and `patch_risk_rule` remain platform-level knowledge/rule tables.
- Baseline task creation writes `tenantId` to `baseline_task` and `baseline_task_host`; host validation uses tenant-aware Host lookup.
- Baseline query, detail, export, host overview, remediation, rollback, and workorder flows now filter by tenant.
- Baseline MQ result processing does not read `TenantContextHolder`; it resolves tenant from `baseline_task`/`baseline_task_host` and writes it to `baseline_check_data`, `baseline_result`, and `baseline_summary`.
- `baseline_rule`, `baseline_rule_item`, and `baseline_rule_history` remain platform-level rule tables.
- User management and RBAC tables were not isolated in this stage.
- No MyBatis interceptor or SQL automatic rewrite was introduced.
- Per user instruction, no interface/API test was run for this tenant isolation stage.
- `mvn clean test` passed.

## V8 User + RBAC tenant isolation

- Scope: `user`, `UserServiceImpl`, `UserMapper`, `UserMapper.xml`, and target-user checks in `RbacServiceImpl`.
- User list/page/search now uses `tenant_id = TenantContextHolder.getTenantId()`.
- User detail/update/delete now validate by `id + tenant_id`, preventing cross-tenant management operations.
- User creation writes `tenantId` from server-side tenant context.
- User CSV import writes current `tenantId` and only updates an existing user when the matched user belongs to the current tenant.
- Login flow and `selectByUserName` login lookup remain unchanged.
- Password change, self-update, and avatar upload remain on the existing current-user logic.
- Role, permission, menu, and role-permission tables remain platform-level configuration.
- Querying a user's role IDs and assigning roles now first validate that the target user belongs to the current tenant.
- No JWT, `TenantContextHolder`, MyBatis interceptor, SQL automatic rewrite, super-admin bypass, or License module changes were introduced.
- `mvn clean test` passed during implementation.

## V8 User + RBAC API verification

- Built and started the packaged application locally for real HTTP API verification.
- Created temporary tenants `9701` and `9702`, users `it_rbac_a_*` and `it_rbac_b_*`, then removed them after verification.
- Temporary tenant users were granted the existing platform `SUPER_ADMIN` role only to cover the `user:role:assign` permission path; tenant data filtering still used their own `tenantId`.
- Login tokens carried `tenantId = 9701` and `tenantId = 9702`.
- Tenant A user list/search returned only Tenant A users and did not include Tenant B users.
- Cross-tenant user detail/update/delete returned business `code = 404`.
- Cross-tenant delete did not remove the target Tenant B user.
- Tenant A user creation succeeded and wrote `user.tenant_id = 9701`.
- Tenant A CSV user import succeeded and wrote `user.tenant_id = 9701`.
- Cross-tenant user-role query returned business `code = 404`.
- Cross-tenant role assignment returned business `code = 404` and did not change the target user's roles.
- Same-tenant user-role query and role assignment succeeded.
- Same-tenant user detail and delete succeeded.
- Temporary User/RBAC/tenant test data was cleaned up.

## V9 Platform tenant and License REST API foundation

- Scope: platform super-admin REST APIs for tenant management and tenant License generation.
- Added `PlatformTenantController` under `/api/platform/tenant`.
- All platform tenant and License APIs use the existing permission style with `@PreAuthorize("@perm.isSuperAdmin()")`.
- Added tenant list pagination/search/status filter, tenant detail, tenant creation, and tenant status enable/disable APIs.
- Protected platform tenant `id = 0` from being disabled.
- Added tenant License list and License generation APIs.
- License generation creates a server-side `licenseKey` and associates the record with `tenant_id`; RSA signing and client activation are intentionally not implemented.
- License editions are normalized and limited to `TRIAL`, `STANDARD`, and `PROFESSIONAL`.
- Extended `license` model and SQL mapping with `machine_id` and `signature`; `status` is now `TINYINT` in schema/migration.
- Added migration script `sql/migration/V3_license_platform_fields.sql` and applied the same column changes to local `xyk2026`.
- Added method-security `AccessDeniedException` handling so `@PreAuthorize` denials return business `code = 403` instead of falling through to generic `500`.
- RabbitMQ, User + RBAC business logic, JWT, and tenant context were not modified.

## V9 Platform tenant and License API verification

- Built and started the packaged application locally for real HTTP API verification.
- Created a temporary platform `SUPER_ADMIN` user and a temporary normal tenant admin user, then removed them after verification.
- Normal tenant admin was denied access to `/api/platform/tenant/list` with business `code = 403`.
- Platform `SUPER_ADMIN` created a tenant successfully.
- Tenant list/search, detail, disable, and enable APIs succeeded for `SUPER_ADMIN`.
- Disabling platform tenant `id = 0` was blocked with business `code = 400`.
- `SUPER_ADMIN` generated a License for the tenant; response included `licenseKey`, `tenantId`, `edition`, `hostLimit`, `userLimit`, `expireTime`, `machineId`, `signature`, and `status`.
- Tenant License list returned the generated License.
- Database verification confirmed the License persisted with the expected `tenant_id`, edition, limits, machine ID, and status.
- Temporary platform user, normal tenant user, tenant, and License test data were cleaned up.
- `mvn clean test` passed.

## V10 Platform License signing and activation

- Scope: platform-side License RSA signing and public activation REST API.
- Added `LicenseSigner` using `SHA256withRSA`; signature payload is built from `licenseKey`, `tenantId`, `edition`, `hostLimit`, `userLimit`, `expireTime`, and `machineId`.
- Added `license.rsa.private-key` configuration, reading from `${LICENSE_RSA_PRIVATE_KEY:}` in `application-dev.yml`.
- When no private key is configured in local development, an in-memory RSA key is generated so the application can still start; production should configure a stable PKCS#8 RSA private key.
- License generation now writes a server-side RSA signature immediately.
- Added public `POST /api/license/activate`.
- Added platform super-admin `POST /api/platform/license/offline` to generate a downloadable offline `license.dat` payload.
- Activation validates `licenseKey`, enabled status, expiration, host limit, and machine binding.
- `hostLimit = 0` is treated as unlimited.
- First activation binds `machineId`; repeated activation from the same machine succeeds; activation from another machine is rejected.
- Activation returns signed License information and persists the bound `machine_id` plus refreshed `signature`.
- Offline generation uses the same validation, machine binding, and signing path as online activation.
- Offline generation response shape is `{ payload, signature }`; payload includes only the signed License fields intended for client-side verification.
- Added dedicated host count query by `tenant_id` for host-limit validation.
- RabbitMQ, User + RBAC, JWT, `TenantContextHolder`, and frontend pages were not modified.
- `mvn clean test` passed.

## V10 Platform License API verification

- Built and started the packaged application locally for real HTTP API verification.
- Created temporary tenants `9801` through `9805`, temporary License rows, temporary Host rows, and a temporary platform `SUPER_ADMIN` user, then removed them after verification.
- Public activation succeeded without login for an enabled, unexpired License.
- First activation wrote `machine_id = MACHINE-A` and a non-empty RSA signature.
- Re-activation from the same machine succeeded and returned the same signed License information.
- Activation from a different machine returned business `code = 400`.
- Disabled License activation returned business `code = 400`.
- Expired License activation returned business `code = 400`.
- Host-limit exceeded activation returned business `code = 400`.
- Platform `SUPER_ADMIN` License generation returned a non-empty RSA signature.
- Database verification confirmed activated/generated License signatures were persisted.
- Temporary License/tenant/user/host test data was cleaned up.

## V10 follow-up: offline License generation API verification

- Built and started the packaged application locally for real HTTP API verification.
- Created temporary tenants `9811` and `9812`, a temporary License, a temporary platform `SUPER_ADMIN` user, and a temporary normal tenant user, then removed them after verification.
- Normal tenant user was denied access to `POST /api/platform/license/offline` with business `code = 403`.
- Platform `SUPER_ADMIN` successfully generated offline License data.
- Offline response returned `payload.licenseKey`, `payload.tenantId`, `payload.edition`, `payload.hostLimit`, `payload.userLimit`, `payload.expireTime`, `payload.machineId`, and Base64 `signature`.
- Offline generation bound `machine_id = OFFLINE-MACHINE-1` and persisted a non-empty RSA signature.
- Re-generating offline License for a different machine was rejected with business `code = 400`.
- Temporary License/tenant/user test data was cleaned up.
- `mvn clean test` passed.

## V10 follow-up: RSA key pair for client integration

- Generated a stable 2048-bit RSA key pair for client integration testing.
- Private key file is stored outside the repository at `C:\DATA\2026实训\license-keys\license_private_pkcs8.pem`.
- Public key file is stored outside the repository at `C:\DATA\2026实训\license-keys\license_public.pem`.
- User-level environment variable `LICENSE_RSA_PRIVATE_KEY` was set to the private key Base64 body for local platform startup.
- Verified that starting the platform without the environment variable still falls back to the in-memory development key and is not suitable for client integration.
- Verified that starting the platform with the generated private key signs offline License data that can be validated by OpenSSL with `license_public.pem`.
- Verification result: `Verified OK`.
- Temporary License/tenant/user test data was cleaned up.

## V11 License edition feature tiers and quotas

- Scope: server-side License feature gating and quota checks for `TRIAL`, `STANDARD`, and `PROFESSIONAL`.
- Added `LicenseGuard`, `LicenseFeature`, `LicenseFeatureInterceptor`, and `LicenseAccessDeniedException`.
- Effective License lookup now uses the current tenant's newest enabled, unexpired License.
- Platform tenant `tenantId = 0` is not constrained by tenant License gating.
- Trial allows host basic viewing only.
- Standard allows host management, asset management/export, patch, vulnerability, log, baseline, user management, and role management.
- Professional allows all Standard capabilities plus AI analysis, AI remediation, and AI report capabilities.
- License generation now applies default limits when not explicitly provided:
  - `TRIAL`: `userLimit = 1`, `hostLimit = 10`
  - `STANDARD`: `userLimit = 5`, `hostLimit = 100`
  - `PROFESSIONAL`: `userLimit = 20`, `hostLimit = 500`
- Explicit `hostLimit` / `userLimit` values still override edition defaults; `0` remains unlimited.
- User creation and user CSV import enforce current-tenant user quota before inserting new users.
- Host creation and host CSV import enforce current-tenant host quota before inserting new hosts.
- AI calls are blocked unless the current effective License is `PROFESSIONAL`.
- Asset export is blocked for Trial.
- Trial is blocked from asset management, patch, vulnerability, log, baseline, user management, and RBAC endpoints.
- A path-level License interceptor was added as a defensive guard so feature checks do not depend only on method-level permission expressions.
- License edition changes take effect on the next request because feature checks query the effective License in real time.
- RabbitMQ, JWT, `TenantContextHolder`, frontend pages, and client activation protocol were not modified.
- `mvn clean test` passed.

## V11 License edition API verification

- Built and started the packaged application locally for real HTTP API verification.
- Created temporary Trial tenant `9901`, Standard tenant `9902`, Professional tenant `9903`, users, hosts, and Licenses, then removed them after verification.
- Temporary tenant users were granted the existing platform `SUPER_ADMIN` role only to cover application permission paths; License gating still used each user's own tenant.
- Trial host list succeeded with business `code = 200`.
- Trial AI endpoint returned business `code = 403`.
- Trial asset export returned business `code = 403`.
- Trial patch, vulnerability, baseline, and log endpoints returned business `code = 403`.
- Standard AI endpoint returned business `code = 403`.
- Standard tenant with 5 users could not create another user; response business `code = 403` with quota exceeded message.
- Professional tenant host creation succeeded at the configured limit edge.
- Professional tenant host creation beyond the configured host limit returned business `code = 403`.
- Updating the Standard tenant License edition to `PROFESSIONAL` took effect without re-activation; the AI endpoint was no longer blocked by License and proceeded to the external AI provider call.
- Temporary License/tenant/user/host test data was cleaned up.

## V12 Frontend SaaS and License phase 1/2

- Added backend `GET /api/license/current` for logged-in users to read current tenant License status, quotas, usage, and `featureFlags`.
- `/api/license/current` does not require super-admin access and returns an ineffective state instead of blocking when a normal tenant has no effective License.
- RBAC current menu/permission endpoints are exempted from License feature gating so Trial tenants can still load the shell menu and permission state.
- Frontend login state now stores `tenantId` from JWT and caches current License info.
- Frontend super-admin detection now uses permission semantics (`*` / `ROLE_SUPER_ADMIN`) and never treats `tenantId = 0` as super admin.
- Common request/table handling now keeps the user logged in on business `403`; only `401` clears login and redirects.
- Main shell loads current user, permissions, current License, and menu in order.
- Main shell displays tenant, edition, expiry, host quota, and user quota in the header.
- Main shell filters menus by backend License `featureFlags` while keeping backend authorization authoritative.
- Added super-admin-only platform menu entry for tenant management based on permissions.
- Added frontend platform tenant page `pages/platform-tenant.html`.
- Platform tenant page supports tenant list/search/status filter, tenant creation, detail, enable/disable, License list, License generation, and offline `license.dat` generation/download.
- `mvn test` passed.

## V13 SaaS tenant login, plans, and authorized machines

- Login now requires selecting an enabled tenant/company from `GET /api/tenant/options`; username/password are validated within that tenant.
- `UserLoginDTO` now carries `tenantId`, and login responses include `tenantId` for frontend login state.
- `selectByUserName` remains available for legacy login lookup compatibility, while the active login flow uses `selectByUserNameAndTenant`.
- User uniqueness is tenant-scoped for username, phone, and email; `schema.sql` fresh bootstrap now matches the tenant-scoped unique indexes.
- Creating a tenant from the platform tenant API now also creates the tenant's initial administrator account.
- Added platform `TENANT_ADMIN` bootstrap and initial role assignment for newly created tenant administrators.
- Added `license_plan` as the server-side source of Trial/Standard/Professional defaults and feature flags.
- Platform License generation now uses `planCode`; online SaaS Licenses are tenant-level and no longer bind `machineId`.
- Added super-admin-only `GET /api/platform/license/plans` for frontend plan rendering.
- Added tenant-managed authorized machine records in `tenant_machine`.
- Added tenant admin REST APIs under `/api/tenant-machines` for list/create/update/delete, guarded by existing host permissions and License features.
- Added public client validation API `POST /api/client/machine/validate`; clients submit `machineId` and/or `macAddress` and receive tenant, edition, expiry, and feature flags only when authorized.
- Authorized machine creation/enabling enforces the current tenant's effective License host quota.
- License platform bypass now uses existing `SUPER_ADMIN` permission semantics instead of treating `tenantId = 0` as super admin.
- `/api/license/current` now reports platform full capability only for actual `SUPER_ADMIN`; normal tenant-0 users receive normal License state.
- Frontend login page now loads tenant options and submits `{ tenantId, userName, password }`.
- Frontend platform tenant page now captures initial tenant admin fields and reads plans from the backend instead of hard-coding package choices.
- Added frontend authorized machine page and menu entry, filtered by backend permissions and `HOST_VIEW`.
- Added migration script `sql/migration/V4_tenant_login_plan_machine.sql` and applied it to local `xyk2026`.

## V13 SaaS tenant login and authorized machine verification

- `mvn test` passed.
- `mvn -DskipTests package` passed.
- `node --check` passed for `login.js`, `platform-tenant.js`, `tenant-machine.js`, and `index.js`.
- Started the packaged application on port `8081` for verification, then stopped it after testing.
- Super admin login with selected tenant `0` succeeded and `/api/license/current` returned platform capability.
- Normal tenant-0 `security` user login succeeded but `/api/license/current` returned no effective License instead of platform capability.
- `GET /api/platform/license/plans` returned Trial, Standard, and Professional plans from the database.
- Super admin created temporary tenants with initial tenant administrators; tenant admin login succeeded by selecting the created tenant.
- Standard License generation using `planCode = STANDARD` applied plan defaults and returned a signed License.
- Tenant admin could create and list an authorized machine under an effective Standard License.
- Public client machine validation succeeded for an authorized `machineId`/`macAddress` and returned tenant, edition, expiry, and feature flags.
- Host quota verification with `hostLimit = 1` allowed the first authorized machine and rejected the second with business `code = 403`.
- User quota verification with `userLimit = 1` rejected creating another user with business `code = 403`.
- Standard AI access returned business `code = 403`.
- Trial AI access returned business `code = 403`.
- Trial asset export returned business `code = 403`.
- Professional `/api/license/current` returned Professional feature flags including AI capabilities and default `20` user / `500` host limits.
- Temporary tenant, user, License, login-log, and authorized-machine verification data was cleaned up.

## V13 follow-up: tenant menu, package display, RBAC assignment, and tenant name UX

- Fixed Trial tenant menu loading by removing the broad class-level `ROLE_MANAGE` License gate from `RbacController`.
- `/api/rbac/menu/current` and `/api/rbac/permission/current` remain available for all logged-in users so the application shell can load under Trial.
- RBAC role/permission platform maintenance endpoints now require actual `SUPER_ADMIN`.
- Tenant administrators can read assignable platform roles through `/api/rbac/role/all` and assign roles to users in their own tenant through `/api/rbac/user/{userId}/roles`.
- Tenant administrators still cannot create/update/delete roles, manage permissions, assign role permissions, or open the role/permission maintenance lists.
- `TENANT_ADMIN` bootstrap now explicitly includes `user:role:assign` in addition to copied `SECURITY_ADMIN` permissions.
- Added the same `TENANT_ADMIN` permission bootstrap to `sql/migration/V4_tenant_login_plan_machine.sql` and `schema.sql`; local `xyk2026` was updated.
- Platform tenant list responses now include `licenseEdition` and `licenseStatus`; tenant `id = 0` displays as `PROFESSIONAL`.
- Platform tenant frontend list/detail now displays each tenant's package.
- `/api/license/current` now returns `tenantName`.
- `/api/current-user` now returns `tenantId` and `tenantName`.
- Frontend login stores the selected tenant name as a fallback.
- Main shell now displays tenant name in the header instead of only `tenantId`.
- Frontend License menu filtering hides role/permission maintenance pages from non-super-admin users while keeping user management visible when `USER_MANAGE` is enabled.

## V13 follow-up verification

- Started the packaged application on port `8081` for verification, then stopped it after testing.
- Trial tenant administrator login succeeded; `/api/rbac/menu/current` returned business `code = 200` instead of menu-load failure.
- Trial `/api/license/current` returned the tenant name and `edition = TRIAL`.
- Platform tenant list showed temporary Trial and Standard tenants with `licenseEdition = TRIAL` / `STANDARD`.
- Standard tenant administrator could call `/api/rbac/role/all`, assign a role to a user in the same tenant, and read back the assigned role IDs.
- Standard tenant administrator was still blocked from `/api/rbac/role/list` with business `code = 403`.
- Temporary tenant, user, License, login-log, and role-assignment verification data was cleaned up.
- `node --check` passed for `auth.js`, `login.js`, `index.js`, and `platform-tenant.js`.
- `mvn test` passed.

## V14 Policy Backbone and menu decision convergence

- Added a policy backbone under `com.cd.common.access`.
- Added `AccessPolicyRegistry` with stable `policyKey` definitions for common, tenant, and platform domains.
- Added `AccessPolicyService` and `AccessDecision` as the unified decision layer for policy-key evaluation.
- Added `GET /api/access/effective` to return the current user's effective policy snapshot for frontend rendering.
- Effective access now separates tenant features from platform features.
- Platform users now receive `edition = PLATFORM` and platform policies only, not Professional tenant policies.
- License plan feature flags were normalized from action-like names to module-level tenant features:
  - Trial: `HOST`
  - Standard: `HOST,ASSET,PATCH,VULN,LOG,BASELINE,USER`
  - Professional: `HOST,ASSET,PATCH,VULN,LOG,BASELINE,USER,AI`
- `LicenseGuard` keeps backward-compatible normalization for older feature names such as `HOST_VIEW`, `HOST_MANAGE`, `ASSET_EXPORT`, and `AI_ANALYSIS`.
- Updated local `xyk2026.license_plan` to the normalized module feature values.
- Platform tenant `id = 0` now displays as `PLATFORM` instead of `PROFESSIONAL` in tenant management.
- Platform asset/rule APIs are now forced through policy gate in `LicenseFeatureInterceptor`:
  - CVE management: `PLATFORM_CVE_VIEW`
  - Vulnerability rule management: `PLATFORM_VULN_RULE_VIEW`
  - Baseline rule management: `PLATFORM_BASELINE_RULE_VIEW`
- CVE management, vulnerability rule management, and baseline rule management remain platform-only regardless of tenant package.
- Frontend shell menu rendering now uses a static menu schema with `policyKey` instead of filename-based feature guessing.
- Frontend shell stores `/api/access/effective` as `accessInfo` and filters menus by `allowedPolicies`.
- Frontend shell logs an `[ACCESS MENU SNAPSHOT]` to the browser console with tenant, edition, allowed policies, and visible menu titles.
- Host page action buttons now use policy keys for asset view/export/probe and host update/delete/create.
- Authorized host page action buttons now use policy keys for create/update/delete.
- RabbitMQ, MQ tenant mapping, host mapping semantics, and client activation protocol were not modified in this stage.

## V14 Policy Backbone verification

- `mvn test` passed.
- `node --check` passed for `auth.js`, `request.js`, `index.js`, `host.js`, `tenant-machine.js`, and `platform-tenant.js`.
- Built and started the packaged application locally on port `8081`, then stopped it after verification.
- Created temporary Trial, Standard, and Professional tenants and removed them after verification.
- Trial `/api/access/effective` returned tenant feature `HOST` and host policies only.
- Standard `/api/access/effective` returned tenant features `ASSET,BASELINE,HOST,LOG,PATCH,USER,VULN` and no AI policy.
- Professional `/api/access/effective` returned tenant features including `AI` and policy `TENANT_AI_ANALYSIS`.
- Platform `/api/access/effective` returned `edition = PLATFORM`, platform features, and platform policies only.
- Trial, Standard, and Professional tenant administrators were all blocked from CVE management, vulnerability rule management, and baseline rule management APIs with business `code = 403`.
- Platform super admin could access CVE management, vulnerability rule management, and baseline rule management APIs with business `code = 200`.
- Temporary tenant, user, License, login-log, and role-assignment verification data was cleaned up.

## V14 follow-up: Standard/Professional asset policy correction

- Corrected `TENANT_ASSET_VIEW` to use the asset module permission `asset:view` instead of host-only `host:asset:view`.
- Standard and Professional tenant administrators now retain asset management menu access when their License includes `ASSET`.
- `TENANT_ADMIN` bootstrap now explicitly includes `host:asset:view` and `host:probe`, so host-page asset viewing and asset probe buttons are available when the package permits `ASSET`.
- Trial remains limited to host-oriented capabilities and still does not receive asset management policies.
- Added the same `TENANT_ADMIN` permission bootstrap to `sql/migration/V4_tenant_login_plan_machine.sql` and `schema.sql`; local `xyk2026` was updated.
- `mvn test` passed.
- `node --check` passed for `auth.js`, `index.js`, and `host.js`.

## V14 follow-up: platform super admin full visibility

- Platform `SUPER_ADMIN` effective access now returns all common, tenant, and platform policy keys for debugging and operations.
- Platform `SUPER_ADMIN` still displays `edition = PLATFORM`, but now also receives all tenant feature names in the effective access snapshot.
- Tenant users remain controlled by License features plus RBAC; platform-only CVE, vulnerability rule, and baseline rule APIs remain unavailable to tenants.
- `mvn test` passed.
- `node --check` passed for `auth.js`, `index.js`, and `host.js`.

## V15 tenant License center

- Added authenticated `GET /api/license/plans` for tenant-side package display; it reuses database-backed `license_plan` data and does not hard-code Trial/Standard/Professional in the frontend.
- Right-header License status is now clickable and opens a License status dialog with edition, expire time, status, host quota, and user quota.
- License status dialog shows upgrade, renew, and contact-sales actions only for `ROLE_TENANT_ADMIN`; normal users are read-only and platform `SUPER_ADMIN` has no purchase entry.
- Added `pages/license-center.html`, `js/pages/license-center.js`, and `css/license-center.css`.
- License center displays current package, quota usage, and package cards from `/api/license/plans`; the current package is highlighted.
- Upgrade and renew actions intentionally do not integrate payment and only show `请联系销售或管理员开通服务`.
- Added License expiry reminders: 30 days or less uses warning styling; 7 days or less uses danger styling.
- RBAC model was not changed; frontend role display logic uses the existing current-permission snapshot with role authorities included.
- `mvn test` passed.
- `node --check` passed for `auth.js`, `index.js`, and `license-center.js`.

## V15 follow-up: client machine authorization contract

- Added public client compatibility endpoint `POST /api/license/check`.
- The endpoint accepts `machineId` and/or `macAddress` and delegates to the existing tenant authorized-machine validation logic.
- Response now includes both `authorized` and legacy `allowed`, plus `tenantId`, `tenantName`, `edition`, `hostLimit`, `expireTime`, `featureFlags`, and `reason`.
- `POST /api/client/machine/validate` remains available for backward compatibility.
- Added `/api/license/check` to security and License-interceptor public exclusions.
- `mvn test` passed.

## V15 follow-up: MAC-first machine binding

- Tenant administrators now authorize machines by `macAddress`; `machineId` is no longer manually entered in the authorized-machine form.
- Added nullable `tenant_machine.machine_bound_at`.
- Client authorization check now looks up active authorized machines by `macAddress` first.
- If the authorized machine has no `machine_id`, the first client check writes the submitted `machineId` and `machine_bound_at`.
- If `machine_id` is already bound, the submitted `machineId` must match; mismatches return `authorized = false` and `reason = MACHINE_ID_MISMATCH`.
- Authorized-machine list now displays `machineId` and binding time, with unbound machines shown as not bound.
- Applied `machine_bound_at` to local `xyk2026`.
- Verified real HTTP flow on port `8081`: first check bound machineId, second check passed, mismatched machineId returned `MACHINE_ID_MISMATCH`; temporary data was cleaned up.
- `mvn test` passed.
- `node --check` passed for `tenant-machine.js`.

## V15 follow-up: MAC format normalization

- Fixed client authorization lookup to treat MAC separators case-insensitively and consistently.
- Platform now normalizes incoming MAC addresses to lowercase hyphen format, so `CC:5E:F8:A1:31:B3` matches stored `cc-5e-f8-a1-31-b3`.
- `TenantMachineMapper.selectByMacAddress` and `selectActiveByMacAddress` now compare with `REPLACE(LOWER(mac_address), ':', '-')`.
- Confirmed local `xyk2026.tenant_machine` contains active authorized host `id = 11`, `tenant_id = 9905`, `mac_address = cc-5e-f8-a1-31-b3`.
- `mvn test` and `mvn compile` passed.
- `node --check` passed for `tenant-machine.js`.

## V15 follow-up: authorized machine host ownership sync

- Authorized machines now synchronize host ownership into `hosts`.
- Creating or enabling a tenant authorized machine claims any existing Host with the same normalized MAC to the current tenant.
- Successful client `/api/license/check` authorization also claims the matching Host to the authorized tenant.
- If no Host exists yet, platform creates a minimal Host placeholder with the authorized tenant, MAC, host name, and online status.
- Added `HostMapper.claimTenantByNormalizedMac` and `insertAuthorizedPlaceholder`.
- Tenant-machine create/update/delete/check write paths are now transactional where they touch multiple tables.
- Applied historical ownership backfill to local `xyk2026`: authorized Host records now have `hosts.tenant_id = tenant_machine.tenant_id`.
- Verified local records: `CC:5E:F8:A1:31:B3` and `00:0C:29:15:8D:66` now belong to tenant `9905` in `hosts`.
- `mvn test` passed.
- `node --check` passed for `tenant-machine.js`.
