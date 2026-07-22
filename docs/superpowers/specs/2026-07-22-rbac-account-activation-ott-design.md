# 2026-07-22 RBAC 账号首次激活 OTT 设计

**日期：** 2026-07-22

**状态：** 已确认，实施计划已生成

**实施计划：** `docs/superpowers/plans/2026-07-22-rbac-account-activation-ott.md`

**目标：** 在保持 Spring Boot 3.5.16、Spring Security 6.5.11、WebFlux、JWT 和浏览器 HttpOnly Cookie 认证架构不变的前提下，引入 Spring Security Reactive One-Time Token（OTT）模型，为管理员预创建账号提供一次性的首次激活和首个密码设置流程。

---

## 1. 已确认的产品结论

- 本期只实现 OTT，不实现 Passkey。
- 保留 WebFlux，不迁移 Spring MVC。
- OTT 只用于管理员预创建账号的首次激活，不用于日常登录或找回密码。
- 管理员创建账号时邮箱必填，不再设置初始密码。
- 创建账号成功后自动生成激活 Token，并在用户管理页支持重新生成。
- 激活链接有效期为 24 小时。
- 重新生成后，旧链接立即失效。
- 本期没有真实邮件能力，使用 Mock Delivery。
- Mock 激活链接只在管理员创建或重新生成接口的响应中返回，不写入日志。
- 用户打开激活链接后设置首个密码。
- 激活成功后不自动登录、不签发 JWT、不写认证 Cookie，而是跳转登录页。
- 现有公开密码注册保留，不强制所有账号都由管理员创建。

---

## 2. 范围

### 2.1 本期范围

- 管理员创建待激活账号。
- Spring Security Reactive OTT 持久化适配。
- 24 小时激活 Token 的生成、哈希存储、消费和失效。
- 创建账号后自动生成 Mock 激活链接。
- 管理员重新生成激活链接。
- 用户公开激活页面。
- 首个密码沿用现有 RSA-OAEP 传输加密。
- 激活 Token 消费、首个密码设置和账号状态变更的原子事务。
- 激活状态展示、RBAC 按钮控制、审计和安全测试。

### 2.2 明确不在本期范围

- Passkey 或 WebAuthn。
- 日常 OTT/Magic Link 登录。
- OTT 找回密码或修改密码。
- 邮件、短信或第三方消息发送。
- 公开申请激活链接。
- 关闭现有公开注册。
- 激活成功后自动登录。
- 用户修改邮箱后的再次验证。
- Spring MVC 迁移。
- 现有 JWT、refresh token、Cookie 或 Bearer 认证体系重构。

---

## 3. 当前实现约束

当前后端具有以下约束：

- `be/pom.xml` 使用 Spring Boot 3.5.16，并依赖 `spring-boot-starter-webflux` 和 `spring-boot-starter-security`。
- Spring Security 版本为 6.5.11。
- `application.yaml` 强制 `spring.main.web-application-type: reactive`。
- `RbacSecurityConfig` 使用 `SecurityWebFilterChain` 和 `ServerHttpSecurity`。
- `RbacAuthApplication` 负责密码登录、JWT 双 Token、refresh token 轮换和当前用户信息组装。
- 浏览器使用 HttpOnly access/refresh Cookie，非浏览器客户端继续支持 Bearer Token。
- 密码通过 `PasswordTransportEncryptionService` 使用 RSA-OAEP 加密后传输。
- RBAC 用户和 Token 持久化使用阻塞式 JPA，WebFlux Controller 通过既有受控调度方式调用。

Spring Security 6.5 为 WebFlux 提供 `ReactiveOneTimeTokenService`，默认实现是内存存储；`oneTimeTokenLogin()` 的默认语义是消费 Token 后建立认证。当前需求是“激活并设置密码，但不建立登录态”，不能直接照搬默认登录流程。

官方参考：

- [Spring Security 6.5 Reactive One-Time Token Login](https://docs.spring.io/spring-security/reference/6.5/reactive/authentication/onetimetoken.html)
- [ReactiveOneTimeTokenService API](https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/authentication/ott/reactive/ReactiveOneTimeTokenService.html)

---

## 4. 方案比较与选型

### 4.1 方案 A：Spring Security OTT SPI + 业务激活编排（采用）

- 实现持久化的 `ReactiveOneTimeTokenService`。
- 复用 Spring Security 的 `OneTimeToken`、`GenerateOneTimeTokenRequest` 和相关类型。
- 不暴露通用 `/ott/generate`。
- 不使用默认 OTT 登录成功处理器。
- 由 RBAC 应用服务编排生成、重新生成和激活。
- 在同一事务中完成 Token 消费、密码设置和账号激活。

优点：保留 WebFlux，真正复用 Spring Security OTT 抽象，同时准确满足“不登录”的业务语义。

### 4.2 方案 B：直接启用 `oneTimeTokenLogin()`

默认登录过滤器会在 Token 验证后建立 Spring Security 认证。为了先设置密码且不登录，还需要临时 WebSession 或 activation grant，给当前 JWT/Cookie 认证体系引入第二套状态。

结论：不采用。

### 4.3 方案 C：完全自定义激活 Token

不使用 Spring Security OTT 类型，自行设计 Token 服务和接口。实现可以略少，但无法达到“接入 Spring Security OTT”的目标，也不利于后续复用。

结论：不采用。

---

## 5. 总体架构

```text
管理员创建账号
  -> 创建 PENDING_ACTIVATION 用户
  -> 生成并哈希保存 Spring Security OTT
  -> Mock Delivery 向管理员返回激活链接
  -> 管理员将链接交给用户
  -> 用户打开 /activate#token=...
  -> 用户设置首个密码
  -> 后端事务内消费 OTT + 设置密码 + 激活账号
  -> 前端跳转 /login?activated=1
  -> 用户使用新密码正式登录
```

组件边界固定为：

### 5.1 `RbacOneTimeTokenStore`

- 同步、事务型 JPA 持久化核心。
- 负责生成、查询、加锁、消费和作废账号激活 Token。
- 数据库只保存 Token 哈希。
- 提供应用服务所需的原子操作，不暴露原始 Token 查询。

### 5.2 `RbacAccountActivationTokenService`

- 扩展 Spring Security `ReactiveOneTimeTokenService`，增加携带首个密码设置命令的原子激活操作。
- `RbacReactiveOneTimeTokenService` 作为具体实现，生成和普通消费继续符合 Spring Security SPI；原子激活操作委托给同一个 Token Store，避免产生两套 Token 规则。
- 将阻塞式 Token Store 调用包装到项目既有的受控调度器。
- 维持 Spring Security OTT 合同，供本期应用编排和未来扩展复用。

### 5.3 `RbacAccountActivationApplication`

- 编排管理员创建待激活账号。
- 编排重新生成激活链接。
- 编排 Token 验证、密码解密、密码策略校验、Token 消费和账号激活。
- 负责激活相关审计。

### 5.4 `ActivationLinkDelivery`

- 定义激活链接交付策略。
- 本期实现 `MockActivationLinkDelivery`。
- 未来增加邮件实现时，不改变 Token 和激活主流程。

本期只使用 Strategy 模式隔离 Mock 与未来邮件交付这一明确变化点。账号生命周期只有待激活和已激活两个状态，使用显式字段，不引入 State、Factory、责任链或额外继承层次。

---

## 6. 账号状态设计

在 `sys_user` 增加：

```text
activated_at TIMESTAMP WITH TIME ZONE NULL
```

派生状态：

- `activated_at IS NULL`：`PENDING_ACTIVATION`。
- `activated_at IS NOT NULL`：`ACTIVATED`。

`status`、`locked` 和激活状态相互独立：

- 已禁用或已锁定账号不能完成激活。
- 待激活账号不能密码登录。
- 激活不会绕过角色、权限、禁用或锁定状态。

### 6.1 存量用户

迁移时将所有存量用户的 `activated_at` 回填为 `created_at`。这样已有用户、已经公开注册的用户和历史管理员不会在上线后被锁死。

### 6.2 公开注册用户

现有 `/api/auth/register` 保持不变。公开注册创建用户时直接设置：

```text
activated_at = now()
```

注册成功后仍按现状签发 JWT/Cookie。

### 6.3 Bootstrap 管理员

内置管理员创建或更新时确保 `activated_at` 非空，不进入激活流程。

### 6.4 管理员创建用户

- `email` 必填。
- 移除 `initialPassword`。
- 创建请求不再接受 `status`，新用户固定以 ENABLED 创建；管理员如需禁用，只能在创建完成后使用现有状态操作。
- 后端生成至少 256 bit 的不可猜测随机字符串，并使用现有 `PasswordEncoder` 编码后写入 `password_hash`。
- `password_hash` 保持 NOT NULL，不修改现有数据库约束。
- `password_expired = true`。
- `activated_at = NULL`。
- 角色分配逻辑保持不变。

随机凭证不返回给管理员或用户，也不写日志。即使随机凭证意外匹配，登录校验仍会因为 `activated_at` 为空而拒绝。

---

## 7. 数据库设计

当前 Flyway 迁移头为 V52。本期数据库变化全部放入一个新文件：

```text
be/src/main/resources/db/migration/V53__add_rbac_account_activation_ott.sql
```

不得修改、重命名或重新格式化任何已有迁移。

### 7.1 `sys_user` 变更

```sql
ALTER TABLE sys_user
    ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE;

UPDATE sys_user
SET activated_at = created_at
WHERE activated_at IS NULL;
```

新列保持可空，以表达待激活状态。

### 7.2 `sys_one_time_token`

表结构固定为：

```text
sys_one_time_token
├── id BIGINT PRIMARY KEY
├── user_id BIGINT NOT NULL
├── purpose VARCHAR(32) NOT NULL
├── token_hash VARCHAR(64) NOT NULL
├── expires_at TIMESTAMP WITH TIME ZONE NOT NULL
├── created_at TIMESTAMP WITH TIME ZONE NOT NULL
└── created_by BIGINT
```

约束和索引：

- `token_hash` 唯一。
- `(user_id, purpose)` 唯一。
- `user_id` 外键关联 `sys_user`。
- `created_by` 记录生成 Token 的管理员，可为空以兼容系统生成。
- `expires_at` 建索引，便于未来清理过期 Token。
- `purpose` 本期固定为 `ACCOUNT_ACTIVATION`。

表中只保存当前有效 Token。成功消费、重新生成、用户删除、禁用、锁定或修改待激活邮箱时，删除对应 Token。历史通过审计表保留，不在 Token 表增加已消费历史状态。

---

## 8. Token 设计

### 8.1 生成

- 使用 `SecureRandom` 生成至少 32 字节随机值。
- 使用 Base64URL 无填充编码为原始 Token。
- 使用 SHA-256 计算 `token_hash`。
- 只把哈希写入数据库。
- 原始 Token 只存在于当前请求内存和 Mock 管理员响应中。

### 8.2 有效期

- 默认有效期为 24 小时。
- 通过 `mario.security.ott.activation-token-ttl=PT24H` 配置。
- 判断过期使用服务端时间。

### 8.3 唯一性和重新生成

- 每个用户每种用途只能有一个有效 Token。
- 重新生成时先对当前 Token（如存在）加锁，再对用户加锁，然后删除旧 Token 并插入新 Token；与激活流程保持相同锁顺序，避免交叉死锁。
- 已激活用户不能重新生成激活 Token。
- 重新生成提交后，旧链接立即失效。

### 8.4 激活链接

公共地址只读取可信配置：

```yaml
mario:
  security:
    ott:
      enabled: true
      activation-token-ttl: PT24H
      public-base-url: http://localhost:5173
      delivery-mode: MOCK
      mock-delivery-allowed: ${OTT_MOCK_DELIVERY_ALLOWED:false}
```

不得从 `Host`、`X-Forwarded-Host` 或其他未经验证的请求头拼接链接。

链接格式：

```text
http://localhost:5173/activate#token=<raw-token>
```

使用 URL Fragment，而不是 Query：

- Fragment 不会被发送给前端服务器、反向代理和普通访问日志。
- 激活页读取 Token 后立即使用 `history.replaceState` 清除地址栏 Fragment。
- Token 不写入 localStorage、sessionStorage 或 Cookie。

---

## 9. 后端 API 设计

### 9.1 管理员创建用户

保留路径：

```http
POST /api/admin/users
```

请求示例：

```json
{
  "accountNo": "mario",
  "username": "mario",
  "nickname": "Mario",
  "email": "mario@example.com",
  "roleIds": [1, 2]
}
```

合同变化：

- `email` 改为必填并验证格式和唯一性。
- 删除 `initialPassword`。
- 删除 `status`，管理员创建的新账号固定为 ENABLED。
- 创建用户、分配角色和写入激活 Token 在同一个数据库事务中完成。
- Delivery 在数据库事务提交后执行；未来真实交付失败时保留待激活用户和 Token，并由管理员使用重新生成操作恢复，不回滚已经提交的账号。

响应改为 `AdminUserCreateResponse`：

```json
{
  "user": {
    "id": 100,
    "accountNo": "mario",
    "activationStatus": "PENDING_ACTIVATION"
  },
  "activationDelivery": {
    "mode": "MOCK",
    "expiresAt": "2026-07-23T13:00:00Z",
    "mockActivationUrl": "http://localhost:5173/activate#token=..."
  }
}
```

该变化是管理员创建接口的明确合同升级，现有前端同步修改。

### 9.2 重新生成激活链接

新增：

```http
POST /api/admin/users/{userId}/activation-token
```

规则：

- 需要管理员 API 权限。
- 只接受待激活、未删除、未锁定且状态为 ENABLED 的用户。
- 删除旧 Token 并生成新 Token。
- Mock 模式返回新的 `ActivationDeliveryResponse`。
- 已激活用户返回 `AUTH_USER_ALREADY_ACTIVATED`。

新增按钮权限：

```text
btn:system:user:resendActivation
```

现有 `api:rbac:admin:*` 继续覆盖管理员 API，不新增角色或菜单。

### 9.3 完成激活

新增公开接口：

```http
POST /api/auth/activation/complete
```

请求：

```json
{
  "token": "...",
  "passwordKeyId": "...",
  "encryptedPassword": "..."
}
```

密码处理：

1. 激活页调用现有 `GET /api/auth/password-key`。
2. 前端使用现有 RSA-OAEP 工具加密新密码。
3. 后端解密密码。
4. 执行现有 8–128 位密码策略。
5. 数据库只保存 `PasswordEncoder` 生成的密码哈希。

事务内执行：

1. 对原始 Token 计算 SHA-256。
2. 加锁查询 Token。
3. 校验用途和有效期。
4. 加锁查询目标用户。
5. 校验用户仍为待激活、ENABLED、未锁定、未删除。
6. 解密并校验新密码。
7. 设置新密码哈希。
8. 设置 `password_expired = false`。
9. 设置 `activated_at = now()`。
10. 删除 Token。
11. 写入激活审计。
12. 提交事务。

成功响应为 `ApiResponse<Void>`，不包含用户、权限、JWT 或 refresh token，也不写 access/refresh Cookie。

前端跳转：

```text
/login?activated=1
```

登录页提示“账号激活成功，请使用新密码登录”。

---

## 10. Mock Delivery 设计

本期不增加 `spring-boot-starter-mail`，不配置 SMTP。

`MockActivationLinkDelivery` 行为：

- 只向有权限的管理员创建/重新生成接口返回 `mockActivationUrl`。
- 不把完整 URL 或原始 Token 写入日志。
- 不把原始 Token 写入审计表。
- 不建立公开 Mock 发件箱。
- 不向普通用户详情、列表或其他通用接口返回链接。
- 普通用户不能主动申请链接。

配置保护：

- Mock 只有在 `delivery-mode=MOCK` 且 `mock-delivery-allowed=true` 时启用；本地和测试配置必须显式设置允许开关。
- `prod` Profile 下无论允许开关为何值都禁止使用 Mock；若选择 Mock，应用启动失败。
- 启用 OTT 但没有可用 Delivery 实现时启动失败，不能静默创建无法激活的用户。
- 未来新增 `MailActivationLinkDelivery` 后，Token 生成和激活流程保持不变，正式交付响应中的 `mockActivationUrl` 为 null。

---

## 11. 前端设计

### 11.1 管理员用户创建

修改 `fe/src/modules/rbac/users/UserEditorDrawer.tsx`：

- 新建模式移除初始密码输入。
- 新建模式邮箱必填。
- 新建模式不显示状态选择，新用户固定为 ENABLED；编辑和列表中的现有状态操作保持不变。
- 编辑模式保持现有用户资料编辑能力。

创建成功后：

- 弹出“账号已创建”Modal。
- 展示链接失效时间。
- 提供“复制激活链接”按钮。
- Modal 关闭后不在其他前端状态或持久化存储中保留链接。

### 11.2 用户列表

修改 `fe/src/modules/rbac/users/UserListPage.tsx`：

- 增加“激活状态”列。
- `PENDING_ACTIVATION` 显示“待激活”。
- `ACTIVATED` 显示“已激活”。
- 待激活用户显示“重新生成激活链接”。
- 已激活用户继续显示“重置密码”。
- 待激活用户隐藏或禁用“重置密码”，避免绕过首次激活。
- 重新生成按钮受 `btn:system:user:resendActivation` 控制。

### 11.3 激活页面

在 `fe/src/app/routes.tsx` 增加无需登录的：

```text
/activate
```

页面行为：

- 从 `window.location.hash` 读取 Token。
- 读取后立即通过 `history.replaceState` 清除 Fragment。
- Token 只保存在页面组件内存中。
- 提供“新密码”和“确认密码”。
- 前端先校验两次输入一致。
- 复用现有密码公钥获取和 RSA-OAEP 加密工具。
- 成功后清空组件内存中的 Token 并跳转 `/login?activated=1`。
- 失败统一显示“激活链接无效或已过期，请联系管理员重新发送”。
- 页面刷新后不恢复 Token，避免 Token 进入持久化存储。

现有 `/register` 页面和公开注册流程不变。

---

## 12. 关联业务规则

### 12.1 登录

现有 `ensureUserCanLogin` 增加 `activated_at` 检查。待激活用户返回：

```text
AUTH_USER_NOT_ACTIVATED
```

不签发任何 Token。

### 12.2 管理员重置密码

- 已激活用户维持现有行为。
- 待激活用户不能通过管理员重置密码绕过激活。
- 前端对待激活用户不显示重置密码按钮。
- 后端仍必须独立校验并拒绝待激活用户。

### 12.3 修改待激活用户邮箱

- 更新成功后作废现有激活 Token。
- 更新接口不返回新链接。
- 前端提示管理员重新生成激活链接。

已激活用户修改邮箱不会重新进入待激活状态。本期激活表示账号初始化，不承担持续邮箱验证职责。

### 12.4 禁用、锁定和删除

- 禁用、锁定或删除待激活用户时，作废现有 Token。
- 重新启用后必须由管理员重新生成链接。
- 删除已激活用户的现有行为保持不变。

---

## 13. 安全设计

### 13.1 Token 安全

- Token 至少具有 256 bit 熵。
- 数据库只保存 SHA-256 哈希。
- 原始 Token 不进数据库、日志和审计。
- 原始 Token 不写 localStorage、sessionStorage 或 Cookie。
- Mock URL 只返回给已授权管理员。
- 链接使用固定公共地址和 URL Fragment。

### 13.2 错误收敛

无效、过期、已消费或已被重新生成替换的 Token 统一返回：

```text
AUTH_ACTIVATION_TOKEN_INVALID
```

不能通过错误差异判断 Token 曾经是否有效。

其他业务错误：

- 已激活用户重新生成：`AUTH_USER_ALREADY_ACTIVATED`。
- 用户禁用或锁定：`AUTH_ACTIVATION_USER_UNAVAILABLE`。
- 密码不满足策略：`RBAC_USER_PASSWORD_INVALID`。
- 邮箱重复：沿用 `RBAC_USER_EMAIL_DUPLICATED`。
- Mock 配置不安全：应用启动阶段失败。

### 13.3 CSRF 与公开接口

- `/api/auth/activation/complete` 加入静态公开 POST 白名单。
- 激活页仍使用现有浏览器请求头和 CSRF Token 流程。
- 不通过 GET 消费 Token。
- 不为激活流程创建 WebSession 或认证上下文。

### 13.4 限流

- 使用项目现有 Redis 能力按来源 IP 记录失败次数：滚动 10 分钟内最多允许 20 次失败激活请求，第 21 次返回 HTTP 429 和 `AUTH_ACTIVATION_RATE_LIMITED`。
- 只累计 Token、账号状态、密码解密或密码策略失败；成功激活不累计失败次数。
- 管理员创建和重新生成由现有 RBAC 高风险管理 API 保护，不增加公开生成入口。
- 限流日志和指标不得包含原始 Token。

---

## 14. 事务与并发

- 激活时对 Token 记录和用户记录加悲观锁。
- 两个并发激活请求只能有一个成功。
- 重新生成和激活并发时，按锁和事务提交顺序产生唯一结果。
- 密码解密、密码校验、用户更新或审计失败时事务回滚，Token 仍可重试。
- 只有账号状态、密码和 Token 删除全部成功后，激活才提交。
- 不存在“Token 已消费但密码未设置”或“密码已设置但账号未激活”的中间状态。

---

## 15. 审计

新增审计动作：

- `RBAC_USER_CREATE_PENDING`
- `AUTH_ACTIVATION_TOKEN_ISSUED`
- `AUTH_ACTIVATION_TOKEN_REISSUED`
- `AUTH_ACCOUNT_ACTIVATED`
- `AUTH_ACTIVATION_TOKEN_REVOKED`

审计可记录：

- 操作管理员。
- 目标用户 ID。
- 动作。
- Token 失效时间。
- 现有 IP 和 User-Agent 信息。

禁止记录：

- 原始 Token。
- Token 哈希。
- 完整激活 URL。
- 明文密码。
- 加密密码请求体。

---

## 16. 兼容性

保持不变：

- 公开注册路径和页面。
- 密码登录。
- JWT access/refresh token。
- 浏览器 HttpOnly Cookie。
- 非浏览器 Bearer 调用。
- refresh token 轮换和撤销。
- 管理员 Bootstrap。
- 已激活用户重置密码。
- 用户角色、权限、菜单和按钮计算。
- `sys_user.password_hash NOT NULL`。

需要同步升级：

- 管理员创建用户请求删除 `initialPassword` 和 `status`，并要求 `email`；新账号固定以 ENABLED 创建。
- 管理员创建用户响应改为用户与交付结果包装。
- `UserResponse` 增加 `activatedAt` 和派生的 `activationStatus`。
- 管理员用户管理前端适配新合同。

---

## 17. 测试设计

### 17.1 后端测试

- V53 是唯一新增 Flyway 迁移。
- 存量用户迁移后全部为已激活。
- 公开注册用户直接已激活。
- Bootstrap 管理员直接已激活。
- 管理员创建用户时邮箱必填且不接受初始密码。
- 管理员创建用户时不接受状态输入，账号固定以 ENABLED 创建。
- 待激活用户无法密码登录。
- 随机占位密码不对外暴露。
- 数据库只保存 Token 哈希。
- Token 默认 24 小时有效。
- 重新生成后旧 Token 立即失效。
- 无效、过期、已消费 Token 返回统一错误。
- 密码校验失败时 Token 不被消费。
- 激活成功设置真实密码、`activated_at` 和 `password_expired`。
- 激活成功不签发 JWT，不写认证 Cookie。
- 激活后使用新密码可以登录。
- 两个并发激活请求只有一个成功。
- 修改待激活邮箱、禁用、锁定和删除会作废 Token。
- Mock URL 只出现在管理员响应中。
- Mock 模式在生产配置下启动失败。
- 公开激活接口正确进入安全白名单并遵守现有 CSRF 规则。
- 已激活用户和公开注册回归行为不变。

### 17.2 前端测试

- 管理员创建表单不再显示初始密码。
- 管理员创建表单邮箱必填。
- 创建成功展示复制链接 Modal。
- 用户列表正确显示待激活/已激活状态。
- 待激活用户显示重新生成按钮且不显示重置密码。
- 无按钮权限时不显示重新生成操作。
- 激活页从 Fragment 读取并立即清除 Token。
- 激活页不把 Token 写入持久化存储。
- 新密码使用现有密码传输加密。
- 激活成功跳转登录页且不写本地认证状态。
- 公开注册和现有登录页面回归通过。

### 17.3 验证顺序

1. 后端激活相关定向测试。
2. RBAC 认证、安全、用户管理回归测试。
3. 前端 auth/RBAC 定向测试。
4. 后端完整测试。
5. 前端完整测试和构建。

只运行测试、编译和构建，不自动启动项目。

---

## 18. 验收标准

- 管理员可以创建邮箱必填、无初始密码的待激活账号。
- 创建成功后管理员获得一次 Mock 激活链接。
- 待激活账号不能登录。
- 链接 24 小时有效。
- 重新生成立即废止旧链接。
- 用户可以通过链接设置首个密码。
- Token 消费、密码设置和账号激活原子提交。
- 激活成功后不自动登录。
- 用户可以使用新密码正式登录。
- 公开注册、存量用户和 Bootstrap 管理员行为不变。
- 不包含 Passkey 依赖、配置、接口或页面。
- 只新增 V53，不修改任何已有 Flyway 文件。
- 原始 Token 不进入数据库、日志、审计或浏览器持久化存储。

---

## 19. 实施计划边界

后续 implementation plan 必须：

- 以本设计为唯一范围来源。
- 按 TDD 顺序先写失败测试，再写实现。
- 将 V53 作为本期唯一迁移文件。
- 保持 WebFlux、JWT、Cookie、Bearer 和公开注册兼容。
- 将后端持久化、激活应用服务、API、安全配置、前端管理员流程和公开激活页拆为可独立验证的任务。
- 每个任务完成后单独提交。
- 最终执行定向测试、完整测试和前端构建，但不启动应用。
