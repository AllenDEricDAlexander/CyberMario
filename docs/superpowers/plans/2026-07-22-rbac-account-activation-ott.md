# RBAC Account Activation OTT Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring Boot 3.5.16 + Spring Security 6.5.11 + WebFlux + JWT/Cookie 认证架构中，为管理员预创建账号实现一次性 OTT 首次激活和首个密码设置流程。

**Architecture:** 使用 Spring Security `ReactiveOneTimeTokenService` 作为 OTT 合同，使用阻塞式 JPA Store 保存 Token 哈希，并通过现有 `blockingScheduler` 与 WebFlux 集成。`RbacAccountActivationApplication` 负责创建、重发、完成激活和限流编排；`ActivationLinkDelivery` 是唯一新增的 Strategy 变化点，本期只有受配置保护的 Mock 实现。不启用 `oneTimeTokenLogin()`，激活成功不建立认证态。

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security 6.5.11 Reactive OTT, WebFlux, Spring Data JPA, PostgreSQL/H2, Flyway, Redis, React 19, TypeScript 6, Ant Design 6, Vitest, Testing Library

## Global Constraints

- 本期只实现 OTT，不引入 Passkey、WebAuthn 或相关依赖。
- 保持 Spring Boot 3.5.16、Spring Security 6.5.11、WebFlux、JWT、HttpOnly Cookie 和 Bearer 认证架构不变。
- OTT 只用于管理员预创建账号的首次激活，不用于日常登录、找回密码或修改密码。
- 激活 Token 默认有效期固定为 `PT24H`，原始 Token 至少 256 bit 熵，数据库只保存 SHA-256 哈希。
- 创建和重发只向已授权管理员响应返回 Mock URL，原始 Token、Token 哈希和完整 URL 不进入日志或审计。
- Mock 仅当 `delivery-mode=MOCK` 且 `mock-delivery-allowed=true` 时启用；`prod` Profile 下一律启动失败。
- 链接必须使用可信 `public-base-url` 和 Fragment：`/activate#token=<raw-token>`，不读取 Host 或转发请求头。
- 完成激活时 Token 消费、密码设置、`activated_at` 更新和审计必须在同一事务中，失败时 Token 可重试。
- 激活成功只返回 `ApiResponse<Void>`，不签发 JWT、不写 access/refresh Cookie、不自动登录。
- 保留现有公开注册；公开注册用户和 Bootstrap 管理员直接已激活。
- 滚动 10 分钟内最多记录 20 次失败激活，第 21 次返回 HTTP 429 / `AUTH_ACTIVATION_RATE_LIMITED`。
- 只新增 `V53__add_rbac_account_activation_ott.sql`，不修改、重命名或格式化任何已有 Flyway 迁移。
- 每个新 Java 顶层类型都按现有 RBAC 风格添加一条类级 Javadoc，内容直接使用下方 File Structure 中该文件的职责句；不为显而易见的 getter、record accessor 或私有转换方法添加新注释格式。
- 每个任务单独提交；最后只运行测试、编译和构建，不启动应用。

---

## File Structure

### Database and activation state

- Create `be/src/main/resources/db/migration/V53__add_rbac_account_activation_ott.sql`: add `sys_user.activated_at`, backfill existing users, and create the single-current-token table.
- Create `be/src/test/java/top/egon/mario/rbac/activation/RbacAccountActivationSchemaMigrationTests.java`: prove V53 is unique, contains the required constraints, and backfills a V52 user.
- Modify `be/src/main/java/top/egon/mario/rbac/po/UserPo.java`: persist activation timestamp.
- Modify `be/src/main/java/top/egon/mario/rbac/dto/response/UserResponse.java`: expose `activatedAt` and derived `activationStatus`.
- Create `be/src/main/java/top/egon/mario/rbac/dto/enums/ActivationStatus.java`: public `PENDING_ACTIVATION` / `ACTIVATED` contract.
- Modify `be/src/main/java/top/egon/mario/rbac/converter/RbacDtoConverter.java`: derive activation status instead of persisting a second state.
- Modify `be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java`: mark public registrations activated and reject pending login/refresh/access authentication.
- Modify `be/src/main/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrap.java`: keep built-in admin activated.
- Modify the focused RBAC auth/bootstrap/converter tests to cover these compatibility rules.

### OTT configuration and delivery strategy

- Create `be/src/main/java/top/egon/mario/rbac/activation/config/RbacOttProperties.java`: typed OTT settings and safe defaults.
- Create `be/src/main/java/top/egon/mario/rbac/activation/config/RbacOttConfiguration.java`: register `Clock`, `SecureRandom`, and the selected delivery strategy; reject unsafe Mock startup.
- Create `be/src/main/java/top/egon/mario/rbac/activation/delivery/ActivationDeliveryMode.java`: delivery discriminator with only `MOCK` in this release.
- Create `be/src/main/java/top/egon/mario/rbac/activation/delivery/ActivationLinkDelivery.java`: Strategy contract.
- Create `be/src/main/java/top/egon/mario/rbac/activation/delivery/MockActivationLinkDelivery.java`: trusted-base URL builder that returns, but never logs, the raw link.
- Create `be/src/main/java/top/egon/mario/rbac/dto/response/ActivationDeliveryResponse.java`: admin-only delivery result.
- Create `be/src/main/java/top/egon/mario/rbac/activation/model/IssuedActivationToken.java`: in-memory-only pairing of user id and Spring `OneTimeToken`.
- Modify `be/src/main/resources/application.yaml`, `application-auto.yaml`, and `be/src/test/resources/application.yaml`: add explicit OTT and Mock safety settings.
- Create focused property/delivery tests.

### Persistent Spring Security OTT service

- Create `be/src/main/java/top/egon/mario/rbac/po/OneTimeTokenPo.java`: JPA mapping containing only token hash and metadata.
- Create `be/src/main/java/top/egon/mario/rbac/po/enums/OneTimeTokenPurpose.java`: fixed `ACCOUNT_ACTIVATION` purpose.
- Create `be/src/main/java/top/egon/mario/rbac/repository/OneTimeTokenRepository.java`: locked hash lookup, per-user replacement, and revocation.
- Modify `be/src/main/java/top/egon/mario/rbac/repository/UserRepository.java`: pessimistic user lookup for issue/activate concurrency.
- Create `be/src/main/java/top/egon/mario/rbac/activation/store/RbacOneTimeTokenStore.java`: raw-token generation, SHA-256 hashing, persistence, lookup, deletion, and revocation.
- Create `be/src/main/java/top/egon/mario/rbac/activation/model/StoredActivationToken.java`: package model carrying the locked row and reconstructed Spring token.
- Create `be/src/main/java/top/egon/mario/rbac/activation/model/ActivationTokenIssueReason.java`: issue/reissue audit action selection.
- Create `be/src/main/java/top/egon/mario/rbac/activation/model/CompleteAccountActivationCommand.java`: atomic activation input.
- Create `be/src/main/java/top/egon/mario/rbac/activation/model/AccountActivationResult.java`: activated user identity.
- Create `be/src/main/java/top/egon/mario/rbac/activation/service/RbacAccountActivationTokenService.java`: extends `ReactiveOneTimeTokenService` with synchronous business operations used on the blocking scheduler.
- Create `be/src/main/java/top/egon/mario/rbac/activation/service/RbacReactiveOneTimeTokenService.java`: SPI adapter plus transactional activation implementation.
- Create integration tests for hashing, TTL, replacement, rollback, successful activation, and concurrency.

### Public activation endpoint and rate limiting

- Create `be/src/main/java/top/egon/mario/rbac/dto/request/CompleteAccountActivationRequest.java`: validated public payload.
- Create `be/src/main/java/top/egon/mario/rbac/activation/service/ActivationFailureRateLimiter.java`: Redis sorted-set rolling window.
- Create `be/src/main/java/top/egon/mario/rbac/application/RbacAccountActivationApplication.java`: request orchestration and counted-failure policy.
- Modify `be/src/main/java/top/egon/mario/rbac/web/AuthController.java`: add POST `/api/auth/activation/complete` without cookie/token response handling.
- Modify `be/src/main/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicy.java` and `be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java`: allow only the explicit public POST while retaining browser CSRF.
- Modify `be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java`: map only the activation rate-limit code to 429.
- Extend security/controller/handler tests.

### Admin creation, reissue, lifecycle revocation, and RBAC resource

- Modify `be/src/main/java/top/egon/mario/rbac/dto/request/CreateUserRequest.java`: require valid email; remove initial password and status.
- Create `be/src/main/java/top/egon/mario/rbac/dto/response/AdminUserCreateResponse.java`: user plus admin-only delivery result.
- Modify `be/src/main/java/top/egon/mario/rbac/service/RbacUserService.java` and `impl/RbacUserServiceImpl.java`: create pending users with random placeholder hashes; block pending reset; revoke tokens on lifecycle changes.
- Modify `be/src/main/java/top/egon/mario/rbac/application/RbacAccountActivationApplication.java`: transactionally create/reissue, then deliver after commit.
- Modify `be/src/main/java/top/egon/mario/rbac/web/AdminUserController.java`: return the new create contract and add POST `/{id}/activation-token`.
- Modify `be/src/main/java/top/egon/mario/rbac/service/resource/RbacAdminConsoleResourceProvider.java`: seed `btn:system:user:resendActivation`.
- Extend service/application/controller/resource tests.

### Frontend contracts and admin flow

- Modify `fe/src/modules/rbac/rbacTypes.ts`: activation status and admin response types; remove create-only password/status fields.
- Modify `fe/src/modules/rbac/rbacService.ts`: typed create response and reissue request.
- Modify `fe/src/modules/rbac/rbacService.test.ts`: verify both API contracts.
- Modify `fe/src/modules/rbac/rbacPermissionCodes.ts`: add resend permission code.
- Modify `fe/src/modules/rbac/users/UserEditorDrawer.tsx`: new-user email-only activation contract.
- Create `fe/src/modules/rbac/users/UserEditorDrawer.test.tsx`: form-mode contract tests.
- Create `fe/src/modules/rbac/users/ActivationLinkModal.tsx`: ephemeral link display/copy UI.
- Create `fe/src/modules/rbac/users/ActivationLinkModal.test.tsx`: copy and close-state tests.
- Modify `fe/src/modules/rbac/users/UserListPage.tsx`: activation column, mutually exclusive reset/reissue actions, and delivery modal state.
- Create `fe/src/modules/rbac/users/UserListPage.test.tsx`: permission and activation-state action tests.

### Public activation page and login handoff

- Modify `fe/src/modules/auth/authService.ts`: encrypted `completeAccountActivation` request with stale-key retry.
- Modify `fe/src/modules/auth/authService.test.ts`: prove raw password is never sent.
- Create `fe/src/modules/auth/pages/ActivationPage.tsx`: fragment-only token capture, password confirmation, uniform failure copy, and login redirect.
- Create `fe/src/modules/auth/pages/ActivationPage.test.tsx`: fragment clearing, no storage, encryption call, failure, and redirect tests.
- Modify `fe/src/modules/auth/pages/LoginPage.tsx`: display activation success banner.
- Create `fe/src/modules/auth/pages/LoginPage.test.tsx`: query-driven banner test.
- Modify `fe/src/app/routes.tsx` and `fe/src/app/routes.test.ts`: register public `/activate` route.

---

### Task 1: Add the database contract and derived user activation state

**Files:**
- Create: `be/src/main/resources/db/migration/V53__add_rbac_account_activation_ott.sql`
- Create: `be/src/test/java/top/egon/mario/rbac/activation/RbacAccountActivationSchemaMigrationTests.java`
- Create: `be/src/main/java/top/egon/mario/rbac/dto/enums/ActivationStatus.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/po/UserPo.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/dto/response/UserResponse.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/converter/RbacDtoConverter.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrap.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrapTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/converter/RbacDtoConverterTests.java`

**Interfaces:**
- Consumes: existing `UserPo`, `RbacAuthApplication`, `RbacDtoConverter`, and Flyway V1–V52 schema.
- Produces: `UserPo#getActivatedAt(): Instant`, `UserResponse#getActivationStatus(): ActivationStatus`, and `ActivationStatus { PENDING_ACTIVATION, ACTIVATED }` for all later tasks.

- [ ] **Step 1: Write the failing migration and activation-state tests**

Create the migration test with both source assertions and an isolated V52 → V53 backfill:

```java
package top.egon.mario.rbac.activation;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RbacAccountActivationSchemaMigrationTests {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");
    private static final Path MIGRATION = MIGRATION_DIRECTORY.resolve(
            "V53__add_rbac_account_activation_ott.sql");

    @Test
    void v53IsTheOnlyAccountActivationMigrationAndDefinesTheRequiredSchema() throws Exception {
        try (var migrations = Files.list(MIGRATION_DIRECTORY)) {
            assertThat(migrations
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V53__")))
                    .containsExactly("V53__add_rbac_account_activation_ott.sql");
        }
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains(
                "ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE",
                "SET activated_at = created_at",
                "CREATE TABLE sys_one_time_token",
                "CONSTRAINT uk_one_time_token_hash UNIQUE (token_hash)",
                "CONSTRAINT uk_one_time_token_user_purpose UNIQUE (user_id, purpose)",
                "CONSTRAINT fk_one_time_token_user FOREIGN KEY (user_id) REFERENCES sys_user (id)",
                "CREATE INDEX idx_one_time_token_expires ON sys_one_time_token (expires_at)"
        );
    }

    @Test
    void v53BackfillsUsersThatExistAtV52() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rbac_activation_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("52")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO sys_user
                    (account_no, username, password_hash, status, locked, password_expired,
                     created_at, updated_at, version, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "legacy", "legacy", "hash", 1, false, false,
                java.time.OffsetDateTime.parse("2026-01-02T03:04:05Z"),
                java.time.OffsetDateTime.parse("2026-01-02T03:04:05Z"), 0L, false);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("53")).load().migrate();

        assertThat(jdbc.queryForObject(
                "SELECT activated_at = created_at FROM sys_user WHERE account_no = 'legacy'", Boolean.class))
                .isTrue();
    }
}
```

Extend existing tests with these exact assertions:

```java
assertThat(user.getActivatedAt()).isNotNull();
assertThat(response.user().getActivationStatus()).isEqualTo(ActivationStatus.ACTIVATED);
```

Add a pending-login test to `RbacAuthApplicationTests`:

```java
@Test
void pendingActivationUserCannotLogin() {
    UserPo user = new UserPo();
    user.setAccountNo("pending");
    user.setUsername("pending");
    user.setEmail("pending@example.com");
    user.setPasswordHash(passwordEncoder.encode("secret123"));
    user.setStatus(RbacStatus.ENABLED);
    user.setPasswordExpired(true);
    userRepository.save(user);

    assertThatThrownBy(() -> authApplication.login(
            loginRequest("pending", "secret123"), "127.0.0.1", "test"))
            .extracting("code")
            .isEqualTo("AUTH_USER_NOT_ACTIVATED");
    assertThat(refreshTokenRepository.findAll()).isEmpty();
}
```

Set `activatedAt = Instant.now()` in the three existing `RbacAuthApplicationTests` fixtures that are expected to log in, refresh, or authenticate. Add a bootstrap assertion that the saved admin has non-null `activatedAt`, and add this converter test:

```java
@Test
void derivesUserActivationStatusFromActivatedAt() {
    UserPo pending = new UserPo();
    UserResponse pendingResponse = rbacDtoConverter.toUserResponse(pending);
    assertThat(pendingResponse.getActivationStatus()).isEqualTo(ActivationStatus.PENDING_ACTIVATION);

    pending.setActivatedAt(Instant.parse("2026-07-22T00:00:00Z"));
    UserResponse activatedResponse = rbacDtoConverter.toUserResponse(pending);
    assertThat(activatedResponse.getActivationStatus()).isEqualTo(ActivationStatus.ACTIVATED);
}
```

Inject `RbacDtoConverter` into `RbacDtoConverterTests`; do not assert the derived property through the raw MapStruct Plus `Converter`.

- [ ] **Step 2: Run the focused tests and verify the red state**

Run:

```bash
cd be
./mvnw -Dtest=RbacAccountActivationSchemaMigrationTests,RbacAuthApplicationTests,RbacAdminBootstrapTests,RbacDtoConverterTests test
```

Expected: FAIL because V53, `activatedAt`, `ActivationStatus`, and the pending-login guard do not exist yet.

- [ ] **Step 3: Create the one allowed migration**

Create `V53__add_rbac_account_activation_ott.sql` with exactly:

```sql
ALTER TABLE sys_user
    ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE;

UPDATE sys_user
SET activated_at = created_at
WHERE activated_at IS NULL;

CREATE TABLE sys_one_time_token
(
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id    BIGINT                   NOT NULL,
    purpose    VARCHAR(32)              NOT NULL,
    token_hash VARCHAR(64)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by BIGINT,
    CONSTRAINT uk_one_time_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_one_time_token_user_purpose UNIQUE (user_id, purpose),
    CONSTRAINT fk_one_time_token_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_one_time_token_expires ON sys_one_time_token (expires_at);
```

- [ ] **Step 4: Add the activation state to persistence and responses**

Add to `UserPo`:

```java
@Column(name = "activated_at")
private Instant activatedAt;
```

Create the DTO enum:

```java
package top.egon.mario.rbac.dto.enums;

/**
 * Derived account activation state exposed by user APIs.
 */
public enum ActivationStatus {
    PENDING_ACTIVATION,
    ACTIVATED
}
```

Add to `UserResponse`:

```java
private Instant activatedAt;
private ActivationStatus activationStatus;
```

Derive it centrally in `RbacDtoConverter#toUserResponse`:

```java
public UserResponse toUserResponse(UserPo userPo) {
    UserResponse response = converter.convert(userPo, UserResponse.class);
    response.setActivationStatus(userPo.getActivatedAt() == null
            ? ActivationStatus.PENDING_ACTIVATION
            : ActivationStatus.ACTIVATED);
    return response;
}
```

- [ ] **Step 5: Preserve registration/bootstrap compatibility and reject pending authentication**

In `RbacAuthApplication#register`, use the existing `now` value for both activation and role grants:

```java
Instant now = Instant.now();
user.setActivatedAt(now);
UserPo savedUser = userRepository.save(user);
userRoleRepository.saveAll(defaultRoles.stream()
        .map(role -> {
            UserRolePo relation = new UserRolePo();
            relation.setUserId(savedUser.getId());
            relation.setRoleId(role.getId());
            relation.setGrantedAt(now);
            return relation;
        })
        .toList());
```

Make activation the first `ensureUserCanLogin` check so pending users receive the specific code:

```java
private void ensureUserCanLogin(UserPo user) {
    if (user.getActivatedAt() == null) {
        throw new RbacException("AUTH_USER_NOT_ACTIVATED", "user account is not activated");
    }
    if (user.getStatus() != RbacStatus.ENABLED || user.isLocked() || user.isPasswordExpired()) {
        throw new RbacException("AUTH_USER_DISABLED", "user cannot login");
    }
}
```

In `RbacAdminBootstrap#ensureAdminUser`, add:

```java
if (user.getActivatedAt() == null) {
    user.setActivatedAt(Instant.now());
}
```

This deliberately activates both a new bootstrap admin and a legacy bootstrap admin without changing its password.

- [ ] **Step 6: Run focused tests and compile**

Run:

```bash
cd be
./mvnw -Dtest=RbacAccountActivationSchemaMigrationTests,RbacAuthApplicationTests,RbacAdminBootstrapTests,RbacDtoConverterTests test
./mvnw -DskipTests compile
```

Expected: both commands exit 0; the migration test reports 2 passing tests and the focused RBAC tests have no failures.

- [ ] **Step 7: Commit Task 1**

```bash
git add be/src/main/resources/db/migration/V53__add_rbac_account_activation_ott.sql \
  be/src/main/java/top/egon/mario/rbac/po/UserPo.java \
  be/src/main/java/top/egon/mario/rbac/dto/enums/ActivationStatus.java \
  be/src/main/java/top/egon/mario/rbac/dto/response/UserResponse.java \
  be/src/main/java/top/egon/mario/rbac/converter/RbacDtoConverter.java \
  be/src/main/java/top/egon/mario/rbac/application/RbacAuthApplication.java \
  be/src/main/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrap.java \
  be/src/test/java/top/egon/mario/rbac/activation/RbacAccountActivationSchemaMigrationTests.java \
  be/src/test/java/top/egon/mario/rbac/application/RbacAuthApplicationTests.java \
  be/src/test/java/top/egon/mario/rbac/service/bootstrap/RbacAdminBootstrapTests.java \
  be/src/test/java/top/egon/mario/rbac/converter/RbacDtoConverterTests.java
git commit -m "feat(auth): add account activation state"
```

### Task 2: Add safe OTT configuration and the Mock delivery Strategy

**Files:**
- Create: `be/src/main/java/top/egon/mario/rbac/activation/config/RbacOttProperties.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/config/RbacOttConfiguration.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/delivery/ActivationDeliveryMode.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/delivery/ActivationLinkDelivery.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/delivery/MockActivationLinkDelivery.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/model/IssuedActivationToken.java`
- Create: `be/src/main/java/top/egon/mario/rbac/dto/response/ActivationDeliveryResponse.java`
- Modify: `be/src/main/resources/application.yaml`
- Modify: `be/src/main/resources/application-auto.yaml`
- Modify: `be/src/test/resources/application.yaml`
- Create: `be/src/test/java/top/egon/mario/rbac/activation/config/RbacOttConfigurationTests.java`
- Create: `be/src/test/java/top/egon/mario/rbac/activation/delivery/MockActivationLinkDeliveryTests.java`

**Interfaces:**
- Consumes: Spring `Environment`, `Clock`, `SecureRandom`, and Spring Security `OneTimeToken`.
- Produces: `RbacOttProperties`, `IssuedActivationToken(Long userId, OneTimeToken token)`, `ActivationLinkDelivery#deliver(IssuedActivationToken): ActivationDeliveryResponse`, and the exact admin-only response fields used by Tasks 5–8.

- [ ] **Step 1: Write the failing configuration and delivery tests**

Use `ApplicationContextRunner` to prove allowed test startup and forbidden production startup:

```java
package top.egon.mario.rbac.activation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.mario.rbac.activation.delivery.ActivationLinkDelivery;

import static org.assertj.core.api.Assertions.assertThat;

class RbacOttConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RbacOttConfiguration.class)
            .withPropertyValues(
                    "mario.security.ott.enabled=true",
                    "mario.security.ott.activation-token-ttl=PT24H",
                    "mario.security.ott.public-base-url=http://localhost:5173",
                    "mario.security.ott.delivery-mode=MOCK"
            );

    @Test
    void startsMockDeliveryOnlyWhenExplicitlyAllowed() {
        contextRunner.withPropertyValues("mario.security.ott.mock-delivery-allowed=true")
                .run(context -> assertThat(context).hasSingleBean(ActivationLinkDelivery.class));
    }

    @Test
    void rejectsMockDeliveryWhenAllowFlagIsFalse() {
        contextRunner.withPropertyValues("mario.security.ott.mock-delivery-allowed=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("mock delivery is not allowed");
                });
    }

    @Test
    void rejectsMockDeliveryInProductionEvenWhenAllowFlagIsTrue() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("mario.security.ott.mock-delivery-allowed=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("mock delivery is forbidden in prod");
                });
    }
}
```

Test the exact Fragment URL and response metadata:

```java
package top.egon.mario.rbac.activation.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MockActivationLinkDeliveryTests {

    @Test
    void buildsAnAdminOnlyFragmentUrlFromTheTrustedBaseUrl() {
        RbacOttProperties properties = new RbacOttProperties(true, Duration.ofHours(24),
                URI.create("https://console.example.test/"), ActivationDeliveryMode.MOCK, true, true);
        Instant expiresAt = Instant.parse("2026-07-23T00:00:00Z");
        IssuedActivationToken issued = new IssuedActivationToken(7L,
                new DefaultOneTimeToken("raw_token-1", "mario", expiresAt));

        var response = new MockActivationLinkDelivery(properties).deliver(issued);

        assertThat(response.mode()).isEqualTo(ActivationDeliveryMode.MOCK);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.mockActivationUrl())
                .isEqualTo("https://console.example.test/activate#token=raw_token-1");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
cd be
./mvnw -Dtest=RbacOttConfigurationTests,MockActivationLinkDeliveryTests test
```

Expected: compilation fails because the OTT configuration and delivery types do not exist.

- [ ] **Step 3: Implement typed properties and response models**

Create the enum and records:

```java
package top.egon.mario.rbac.activation.delivery;

public enum ActivationDeliveryMode {
    MOCK
}
```

```java
package top.egon.mario.rbac.activation.model;

import org.springframework.security.authentication.ott.OneTimeToken;

public record IssuedActivationToken(Long userId, OneTimeToken token) {
}
```

```java
package top.egon.mario.rbac.dto.response;

import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;

import java.time.Instant;

public record ActivationDeliveryResponse(
        ActivationDeliveryMode mode,
        Instant expiresAt,
        String mockActivationUrl
) {
}
```

Create properties with exact defaults and base-URL validation:

```java
package top.egon.mario.rbac.activation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "mario.security.ott")
public record RbacOttProperties(
        boolean enabled,
        Duration activationTokenTtl,
        URI publicBaseUrl,
        ActivationDeliveryMode deliveryMode,
        boolean mockDeliveryAllowed,
        boolean rateLimitEnabled
) {
    public RbacOttProperties {
        activationTokenTtl = activationTokenTtl == null ? Duration.ofHours(24) : activationTokenTtl;
        publicBaseUrl = publicBaseUrl == null ? URI.create("http://localhost:5173") : publicBaseUrl;
        deliveryMode = deliveryMode == null ? ActivationDeliveryMode.MOCK : deliveryMode;
        if (activationTokenTtl.isZero() || activationTokenTtl.isNegative()) {
            throw new IllegalArgumentException("activation token ttl must be positive");
        }
        if (!publicBaseUrl.isAbsolute()
                || !("http".equalsIgnoreCase(publicBaseUrl.getScheme())
                || "https".equalsIgnoreCase(publicBaseUrl.getScheme()))) {
            throw new IllegalArgumentException("OTT public base URL must be an absolute HTTP(S) URI");
        }
    }
}
```

- [ ] **Step 4: Implement the Strategy and protected configuration**

Create the interface and Mock implementation:

```java
package top.egon.mario.rbac.activation.delivery;

import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.dto.response.ActivationDeliveryResponse;

public interface ActivationLinkDelivery {
    ActivationDeliveryResponse deliver(IssuedActivationToken issuedToken);
}
```

```java
package top.egon.mario.rbac.activation.delivery;

import lombok.RequiredArgsConstructor;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.dto.response.ActivationDeliveryResponse;

@RequiredArgsConstructor
public class MockActivationLinkDelivery implements ActivationLinkDelivery {

    private final RbacOttProperties properties;

    @Override
    public ActivationDeliveryResponse deliver(IssuedActivationToken issuedToken) {
        String baseUrl = properties.publicBaseUrl().toString().replaceFirst("/+$", "");
        String activationUrl = baseUrl + "/activate#token=" + issuedToken.token().getTokenValue();
        return new ActivationDeliveryResponse(ActivationDeliveryMode.MOCK,
                issuedToken.token().getExpiresAt(), activationUrl);
    }
}
```

Create configuration; it must not log properties or URLs:

```java
package top.egon.mario.rbac.activation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;
import top.egon.mario.rbac.activation.delivery.ActivationLinkDelivery;
import top.egon.mario.rbac.activation.delivery.MockActivationLinkDelivery;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RbacOttProperties.class)
public class RbacOttConfiguration {

    @Bean
    Clock rbacOttClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom rbacOttSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    ActivationLinkDelivery activationLinkDelivery(RbacOttProperties properties, Environment environment) {
        if (!properties.enabled()) {
            throw new IllegalStateException("RBAC OTT must be enabled for pending account creation");
        }
        if (properties.deliveryMode() != ActivationDeliveryMode.MOCK) {
            throw new IllegalStateException("no activation link delivery is available for mode "
                    + properties.deliveryMode());
        }
        if (!properties.mockDeliveryAllowed()) {
            throw new IllegalStateException("mock delivery is not allowed");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("mock delivery is forbidden in prod");
        }
        return new MockActivationLinkDelivery(properties);
    }
}
```

The Strategy is justified because real email delivery is the one confirmed future variation point. Keep account-state checks and token rules out of this interface; State, Factory, Chain of Responsibility, and extra provider abstractions do not simplify this two-state, one-provider release.

- [ ] **Step 5: Add exact environment configuration**

Under `mario.security` in `application.yaml` add:

```yaml
ott:
  enabled: ${OTT_ENABLED:true}
  activation-token-ttl: ${OTT_ACTIVATION_TOKEN_TTL:PT24H}
  public-base-url: ${OTT_PUBLIC_BASE_URL:http://localhost:5173}
  delivery-mode: ${OTT_DELIVERY_MODE:MOCK}
  mock-delivery-allowed: ${OTT_MOCK_DELIVERY_ALLOWED:false}
  rate-limit-enabled: ${OTT_RATE_LIMIT_ENABLED:true}
```

Under `mario.security` in `application-auto.yaml` add:

```yaml
ott:
  enabled: true
  activation-token-ttl: PT24H
  public-base-url: ${AUTO_OTT_PUBLIC_BASE_URL:http://localhost:5173}
  delivery-mode: MOCK
  mock-delivery-allowed: true
  rate-limit-enabled: true
```

Under `mario.security` in `be/src/test/resources/application.yaml` add:

```yaml
ott:
  enabled: true
  activation-token-ttl: PT24H
  public-base-url: http://localhost:5173
  delivery-mode: MOCK
  mock-delivery-allowed: true
  rate-limit-enabled: false
```

- [ ] **Step 6: Run focused tests**

Run:

```bash
cd be
./mvnw -Dtest=RbacOttConfigurationTests,MockActivationLinkDeliveryTests test
```

Expected: PASS; three configuration tests and one URL test succeed.

- [ ] **Step 7: Commit Task 2**

```bash
git add be/src/main/java/top/egon/mario/rbac/activation \
  be/src/main/java/top/egon/mario/rbac/dto/response/ActivationDeliveryResponse.java \
  be/src/main/resources/application.yaml \
  be/src/main/resources/application-auto.yaml \
  be/src/test/resources/application.yaml \
  be/src/test/java/top/egon/mario/rbac/activation/config/RbacOttConfigurationTests.java \
  be/src/test/java/top/egon/mario/rbac/activation/delivery/MockActivationLinkDeliveryTests.java
git commit -m "feat(auth): configure mock activation delivery"
```

### Task 3: Implement the persistent Reactive OTT SPI and atomic activation

**Files:**
- Create: `be/src/main/java/top/egon/mario/rbac/po/enums/OneTimeTokenPurpose.java`
- Create: `be/src/main/java/top/egon/mario/rbac/po/OneTimeTokenPo.java`
- Create: `be/src/main/java/top/egon/mario/rbac/repository/OneTimeTokenRepository.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/repository/UserRepository.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/model/StoredActivationToken.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/model/ActivationTokenIssueReason.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/model/CompleteAccountActivationCommand.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/model/AccountActivationResult.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/store/RbacOneTimeTokenStore.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/service/RbacAccountActivationTokenService.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/service/RbacReactiveOneTimeTokenService.java`
- Create: `be/src/test/java/top/egon/mario/rbac/activation/service/RbacReactiveOneTimeTokenServiceTests.java`

**Interfaces:**
- Consumes: `RbacOttProperties`, `Clock`, `SecureRandom`, `PasswordTransportEncryptionService`, `PasswordEncoder`, `RbacAuditService`, `Scheduler blockingScheduler`, and Task 1's locked schema.
- Produces:
  - `RbacAccountActivationTokenService extends ReactiveOneTimeTokenService`.
  - `IssuedActivationToken issueForUser(Long userId, Long actorUserId, ActivationTokenIssueReason reason)`.
  - `AccountActivationResult activate(CompleteAccountActivationCommand command)`.
  - `boolean revokeForUser(Long userId, Long actorUserId, String reason)`.
  - `CompleteAccountActivationCommand(String token, String passwordKeyId, String encryptedPassword, String ip, String userAgent)`.

- [ ] **Step 1: Write failing persistence, SPI, and atomicity tests**

Create `RbacReactiveOneTimeTokenServiceTests` as a `@SpringBootTest` using the existing H2 test profile. Autowire the service, token/user/audit repositories, password encoder, and password transport service; clear `sys_one_time_token` before `sys_user` in `@BeforeEach`.

The first test must prove entropy representation, hashing, and TTL:

```java
@Test
void issueStoresOnlySha256HashAndExpiresAfterTwentyFourHours() {
    UserPo user = pendingUser("mario", "mario@example.com");
    Instant before = clock.instant();

    IssuedActivationToken issued = tokenService.issueForUser(
            user.getId(), 99L, ActivationTokenIssueReason.ISSUED);

    OneTimeTokenPo stored = oneTimeTokenRepository.findAll().getFirst();
    assertThat(Base64.getUrlDecoder().decode(issued.token().getTokenValue())).hasSize(32);
    assertThat(stored.getTokenHash()).hasSize(64)
            .isNotEqualTo(issued.token().getTokenValue());
    assertThat(stored.getUserId()).isEqualTo(user.getId());
    assertThat(stored.getPurpose()).isEqualTo(OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
    assertThat(stored.getExpiresAt()).isBetween(before.plus(Duration.ofHours(24)),
            clock.instant().plus(Duration.ofHours(24)));
}
```

The replacement/SPI test must use the Spring interface, not the concrete class:

```java
@Test
void reactiveSpiReplacesAndConsumesTheSingleCurrentToken() {
    UserPo user = pendingUser("luigi", "luigi@example.com");
    ReactiveOneTimeTokenService springService = tokenService;

    OneTimeToken first = springService.generate(new GenerateOneTimeTokenRequest(user.getUsername(),
            Duration.ofHours(24))).block();
    OneTimeToken second = springService.generate(new GenerateOneTimeTokenRequest(user.getUsername(),
            Duration.ofHours(24))).block();

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(second.getTokenValue()).isNotEqualTo(first.getTokenValue());
    assertThat(springService.consume(OneTimeTokenAuthenticationToken
            .unauthenticated(first.getTokenValue())).block()).isNull();
    assertThat(springService.consume(OneTimeTokenAuthenticationToken
            .unauthenticated(second.getTokenValue())).block()).isNotNull();
    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
}
```

The successful activation test must encrypt through the existing RSA helper and assert all state:

```java
@Test
void activateAtomicallySetsTheFirstPasswordAndConsumesTheToken() {
    UserPo user = pendingUser("peach", "peach@example.com");
    IssuedActivationToken issued = tokenService.issueForUser(
            user.getId(), 99L, ActivationTokenIssueReason.ISSUED);
    EncryptedPassword password = encryptPassword("new-password-123");

    AccountActivationResult result = tokenService.activate(new CompleteAccountActivationCommand(
            issued.token().getTokenValue(), password.passwordKeyId(), password.encryptedPassword(),
            "127.0.0.1", "activation-test"));

    UserPo activated = userRepository.findById(user.getId()).orElseThrow();
    assertThat(result.userId()).isEqualTo(user.getId());
    assertThat(activated.getActivatedAt()).isNotNull();
    assertThat(activated.isPasswordExpired()).isFalse();
    assertThat(passwordEncoder.matches("new-password-123", activated.getPasswordHash())).isTrue();
    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    assertThat(auditLogRepository.findAll())
            .extracting("action")
            .contains("AUTH_ACCOUNT_ACTIVATED");
}
```

Add these exact negative cases:

```java
@Test
void invalidExpiredAndAlreadyConsumedTokensShareOneErrorCode() {
    assertActivationCode("not-a-token", "AUTH_ACTIVATION_TOKEN_INVALID");

    UserPo expiredUser = pendingUser("expired", "expired@example.com");
    String rawExpired = "expired-token";
    oneTimeTokenRepository.save(tokenRow(expiredUser.getId(), rawExpired, clock.instant().minusSeconds(1)));
    assertActivationCode(rawExpired, "AUTH_ACTIVATION_TOKEN_INVALID");

    UserPo consumedUser = pendingUser("consumed", "consumed@example.com");
    IssuedActivationToken consumed = tokenService.issueForUser(
            consumedUser.getId(), 99L, ActivationTokenIssueReason.ISSUED);
    EncryptedPassword password = encryptPassword("new-password-123");
    tokenService.activate(command(consumed.token().getTokenValue(), password));
    assertActivationCode(consumed.token().getTokenValue(), "AUTH_ACTIVATION_TOKEN_INVALID");
}

@Test
void passwordPolicyFailureRollsBackAndLeavesTokenUsable() {
    UserPo user = pendingUser("toad", "toad@example.com");
    IssuedActivationToken issued = tokenService.issueForUser(
            user.getId(), 99L, ActivationTokenIssueReason.ISSUED);

    assertThatThrownBy(() -> tokenService.activate(command(
            issued.token().getTokenValue(), encryptPassword("short"))))
            .extracting("code")
            .isEqualTo("RBAC_USER_PASSWORD_INVALID");
    assertThat(oneTimeTokenRepository.findAll()).hasSize(1);
    assertThat(userRepository.findById(user.getId()).orElseThrow().getActivatedAt()).isNull();

    tokenService.activate(command(issued.token().getTokenValue(),
            encryptPassword("valid-password-123")));
    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
}

@Test
void disabledOrLockedUserCannotCompleteActivationAndKeepsTokenForAdminRecovery() {
    UserPo disabled = pendingUser("disabled", "disabled@example.com");
    IssuedActivationToken disabledToken = tokenService.issueForUser(
            disabled.getId(), 99L, ActivationTokenIssueReason.ISSUED);
    disabled.setStatus(RbacStatus.DISABLED);
    userRepository.saveAndFlush(disabled);

    assertThatThrownBy(() -> tokenService.activate(command(disabledToken.token().getTokenValue(),
            encryptPassword("valid-password-123"))))
            .extracting("code").isEqualTo("AUTH_ACTIVATION_USER_UNAVAILABLE");

    UserPo locked = pendingUser("locked", "locked@example.com");
    IssuedActivationToken lockedToken = tokenService.issueForUser(
            locked.getId(), 99L, ActivationTokenIssueReason.ISSUED);
    locked.setLocked(true);
    userRepository.saveAndFlush(locked);

    assertThatThrownBy(() -> tokenService.activate(command(lockedToken.token().getTokenValue(),
            encryptPassword("valid-password-123"))))
            .extracting("code").isEqualTo("AUTH_ACTIVATION_USER_UNAVAILABLE");
    assertThat(oneTimeTokenRepository.findAll()).hasSize(2);
}
```

Add the concurrent case:

```java
@Test
void concurrentActivationAllowsExactlyOneSuccess() throws Exception {
    UserPo user = pendingUser("bowser", "bowser@example.com");
    IssuedActivationToken issued = tokenService.issueForUser(
            user.getId(), 99L, ActivationTokenIssueReason.ISSUED);
    EncryptedPassword password = encryptPassword("new-password-123");
    CompleteAccountActivationCommand command = command(issued.token().getTokenValue(), password);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
        Callable<Object> attempt = () -> {
            start.await();
            try {
                return tokenService.activate(command);
            } catch (RbacException exception) {
                return exception;
            }
        };
        Future<Object> first = executor.submit(attempt);
        Future<Object> second = executor.submit(attempt);
        start.countDown();
        List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS));

        assertThat(outcomes).filteredOn(AccountActivationResult.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(RbacException.class::isInstance)
                .singleElement()
                .extracting(value -> ((RbacException) value).getCode())
                .isEqualTo("AUTH_ACTIVATION_TOKEN_INVALID");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getActivatedAt()).isNotNull();
        assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    } finally {
        executor.shutdownNow();
    }
}
```

Define every referenced test helper in the same class:

```java
private UserPo pendingUser(String username, String email) {
    UserPo user = new UserPo();
    user.setAccountNo(username);
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode("unusable-placeholder"));
    user.setStatus(RbacStatus.ENABLED);
    user.setLocked(false);
    user.setPasswordExpired(true);
    return userRepository.save(user);
}

private OneTimeTokenPo tokenRow(Long userId, String rawToken, Instant expiresAt) {
    OneTimeTokenPo row = new OneTimeTokenPo();
    row.setUserId(userId);
    row.setPurpose(OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
    row.setTokenHash(tokenStore.hash(rawToken));
    row.setExpiresAt(expiresAt);
    row.setCreatedAt(clock.instant().minusSeconds(60));
    row.setCreatedBy(99L);
    return row;
}

private CompleteAccountActivationCommand command(String rawToken, EncryptedPassword password) {
    return new CompleteAccountActivationCommand(rawToken, password.passwordKeyId(),
            password.encryptedPassword(), "127.0.0.1", "activation-test");
}

private void assertActivationCode(String rawToken, String expectedCode) {
    assertThatThrownBy(() -> tokenService.activate(command(
            rawToken, encryptPassword("valid-password-123"))))
            .extracting("code")
            .isEqualTo(expectedCode);
}

private EncryptedPassword encryptPassword(String password) {
    try {
        var key = passwordTransportEncryptionService.currentKey();
        byte[] publicKeyBytes = Base64.getDecoder().decode(key.publicKey());
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return new EncryptedPassword(
                Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8))),
                key.keyId());
    } catch (GeneralSecurityException exception) {
        throw new IllegalStateException("failed to encrypt activation test password", exception);
    }
}

private record EncryptedPassword(String encryptedPassword, String passwordKeyId) {
}
```

Autowire `RbacOneTimeTokenStore tokenStore` for the helper so the production SHA-256 rule is reused. Import `Cipher`, `OAEPParameterSpec`, `PSource`, `KeyFactory`, `PublicKey`, `GeneralSecurityException`, `MGF1ParameterSpec`, `X509EncodedKeySpec`, `StandardCharsets`, and the `java.util.concurrent` types used above.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd be
./mvnw -Dtest=RbacReactiveOneTimeTokenServiceTests test
```

Expected: compilation fails because the persistence types, store, and token service do not exist.

- [ ] **Step 3: Add the JPA entity, purpose, and locking repositories**

Create the purpose enum:

```java
package top.egon.mario.rbac.po.enums;

public enum OneTimeTokenPurpose {
    ACCOUNT_ACTIVATION
}
```

Create the complete entity:

```java
package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.po.enums.OneTimeTokenPurpose;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sys_one_time_token", uniqueConstraints = {
        @UniqueConstraint(name = "uk_one_time_token_hash", columnNames = "token_hash"),
        @UniqueConstraint(name = "uk_one_time_token_user_purpose", columnNames = {"user_id", "purpose"})
})
public class OneTimeTokenPo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private OneTimeTokenPurpose purpose;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "created_by")
    private Long createdBy;
}
```

Create the token repository:

```java
package top.egon.mario.rbac.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.rbac.po.OneTimeTokenPo;
import top.egon.mario.rbac.po.enums.OneTimeTokenPurpose;

import java.util.Optional;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeTokenPo, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from OneTimeTokenPo token
            where token.tokenHash = :tokenHash and token.purpose = :purpose
            """)
    Optional<OneTimeTokenPo> findByHashForUpdate(@Param("tokenHash") String tokenHash,
                                                  @Param("purpose") OneTimeTokenPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from OneTimeTokenPo token
            where token.userId = :userId and token.purpose = :purpose
            """)
    Optional<OneTimeTokenPo> findByUserAndPurposeForUpdate(@Param("userId") Long userId,
                                                           @Param("purpose") OneTimeTokenPurpose purpose);
}
```

Add this method to `UserRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select user from UserPo user where user.id = :id and user.deleted = false")
Optional<UserPo> findByIdForUpdate(@Param("id") Long id);
```

- [ ] **Step 4: Implement the token Store with one hashing rule**

Create the package records:

```java
package top.egon.mario.rbac.activation.model;

import org.springframework.security.authentication.ott.OneTimeToken;
import top.egon.mario.rbac.po.OneTimeTokenPo;

public record StoredActivationToken(OneTimeTokenPo row, OneTimeToken token) {
}
```

```java
package top.egon.mario.rbac.activation.model;

public enum ActivationTokenIssueReason {
    ISSUED("AUTH_ACTIVATION_TOKEN_ISSUED"),
    REISSUED("AUTH_ACTIVATION_TOKEN_REISSUED");

    private final String auditAction;

    ActivationTokenIssueReason(String auditAction) {
        this.auditAction = auditAction;
    }

    public String auditAction() {
        return auditAction;
    }
}
```

Implement the Store; all callers must use these methods instead of hashing independently:

```java
package top.egon.mario.rbac.activation.store;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;
import top.egon.mario.rbac.activation.model.StoredActivationToken;
import top.egon.mario.rbac.po.OneTimeTokenPo;
import top.egon.mario.rbac.po.enums.OneTimeTokenPurpose;
import top.egon.mario.rbac.repository.OneTimeTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RbacOneTimeTokenStore {
    private static final int TOKEN_BYTES = 32;
    private final OneTimeTokenRepository tokenRepository;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public Optional<OneTimeTokenPo> lockCurrentForUser(Long userId) {
        return tokenRepository.findByUserAndPurposeForUpdate(
                userId, OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
    }

    public IssuedActivationToken replace(OneTimeTokenPo current, Long userId, String username,
                                          Long actorUserId, Duration ttl) {
        if (current != null) {
            tokenRepository.delete(current);
        }
        String rawToken = randomToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        OneTimeTokenPo row = new OneTimeTokenPo();
        row.setUserId(userId);
        row.setPurpose(OneTimeTokenPurpose.ACCOUNT_ACTIVATION);
        row.setTokenHash(hash(rawToken));
        row.setExpiresAt(expiresAt);
        row.setCreatedAt(now);
        row.setCreatedBy(actorUserId);
        tokenRepository.saveAndFlush(row);
        return new IssuedActivationToken(userId,
                new DefaultOneTimeToken(rawToken, username, expiresAt));
    }

    public Optional<StoredActivationToken> findForUpdate(String rawToken) {
        return tokenRepository.findByHashForUpdate(hash(rawToken), OneTimeTokenPurpose.ACCOUNT_ACTIVATION)
                .map(row -> new StoredActivationToken(row,
                        new DefaultOneTimeToken(rawToken, String.valueOf(row.getUserId()), row.getExpiresAt())));
    }

    public void delete(OneTimeTokenPo row) {
        tokenRepository.delete(row);
    }

    public boolean revoke(Long userId) {
        Optional<OneTimeTokenPo> current = lockCurrentForUser(userId);
        current.ifPresent(tokenRepository::delete);
        return current.isPresent();
    }

    public String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
```

- [ ] **Step 5: Define the extended Spring Security contract and commands**

Create:

```java
package top.egon.mario.rbac.activation.model;

public record CompleteAccountActivationCommand(
        String token,
        String passwordKeyId,
        String encryptedPassword,
        String ip,
        String userAgent
) {
}
```

```java
package top.egon.mario.rbac.activation.model;

public record AccountActivationResult(Long userId, String username) {
}
```

```java
package top.egon.mario.rbac.activation.service;

import org.springframework.security.authentication.ott.reactive.ReactiveOneTimeTokenService;
import top.egon.mario.rbac.activation.model.AccountActivationResult;
import top.egon.mario.rbac.activation.model.ActivationTokenIssueReason;
import top.egon.mario.rbac.activation.model.CompleteAccountActivationCommand;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;

public interface RbacAccountActivationTokenService extends ReactiveOneTimeTokenService {
    IssuedActivationToken issueForUser(Long userId, Long actorUserId, ActivationTokenIssueReason reason);
    AccountActivationResult activate(CompleteAccountActivationCommand command);
    boolean revokeForUser(Long userId, Long actorUserId, String reason);
}
```

- [ ] **Step 6: Implement the reactive adapter and the atomic transaction**

`RbacReactiveOneTimeTokenService` must inject `TransactionOperations` so the synchronous business methods and the reactive SPI share an explicit transaction boundary even when invoked from a Reactor scheduler. Implement these core methods exactly:

```java
@Override
public Mono<OneTimeToken> generate(GenerateOneTimeTokenRequest request) {
    return Mono.fromCallable(() -> {
        UserPo user = userRepository.findByUsernameAndDeletedFalse(request.getUsername())
                .orElseThrow(this::invalidToken);
        Duration ttl = request.getExpiresIn() == null
                ? properties.activationTokenTtl() : request.getExpiresIn();
        return requireTransactionResult(transactionOperations.execute(status ->
                issueLocked(user.getId(), null, ActivationTokenIssueReason.ISSUED, ttl).token()));
    }).subscribeOn(blockingScheduler);
}

@Override
public Mono<OneTimeToken> consume(OneTimeTokenAuthenticationToken authenticationToken) {
    return Mono.fromCallable(() -> transactionOperations.execute(status ->
                    consumeLocked(authenticationToken.getTokenValue())))
            .flatMap(Mono::justOrEmpty)
            .subscribeOn(blockingScheduler);
}

@Override
public IssuedActivationToken issueForUser(Long userId, Long actorUserId,
                                           ActivationTokenIssueReason reason) {
    return requireTransactionResult(transactionOperations.execute(status ->
            issueLocked(userId, actorUserId, reason, properties.activationTokenTtl())));
}

@Override
public AccountActivationResult activate(CompleteAccountActivationCommand command) {
    return requireTransactionResult(transactionOperations.execute(status -> activateLocked(command)));
}

@Override
public boolean revokeForUser(Long userId, Long actorUserId, String reason) {
    return Boolean.TRUE.equals(transactionOperations.execute(status -> {
        boolean revoked = tokenStore.revoke(userId);
        if (revoked) {
            auditService.log(actorUserId, "AUTH_ACTIVATION_TOKEN_REVOKED", "USER", userId,
                    null, reason, null, null);
        }
        return revoked;
    }));
}
```

Use these private transaction bodies:

```java
private IssuedActivationToken issueLocked(Long userId, Long actorUserId,
                                           ActivationTokenIssueReason reason, Duration ttl) {
    OneTimeTokenPo current = tokenStore.lockCurrentForUser(userId).orElse(null);
    UserPo user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new RbacException("RBAC_USER_NOT_FOUND", "user not found"));
    if (user.getActivatedAt() != null) {
        throw new RbacException("AUTH_USER_ALREADY_ACTIVATED", "user account is already activated");
    }
    ensureActivationUserAvailable(user);
    IssuedActivationToken issued = tokenStore.replace(current, user.getId(), user.getUsername(),
            actorUserId, ttl);
    auditService.log(actorUserId, reason.auditAction(), "USER", user.getId(), null,
            "{\"expiresAt\":\"" + issued.token().getExpiresAt() + "\"}", null, null);
    return issued;
}

private OneTimeToken consumeLocked(String rawToken) {
    StoredActivationToken stored = tokenStore.findForUpdate(rawToken).orElse(null);
    if (stored == null || !stored.row().getExpiresAt().isAfter(clock.instant())) {
        return null;
    }
    UserPo user = userRepository.findByIdForUpdate(stored.row().getUserId()).orElse(null);
    if (user == null) {
        return null;
    }
    tokenStore.delete(stored.row());
    return new DefaultOneTimeToken(rawToken, user.getUsername(), stored.row().getExpiresAt());
}

private AccountActivationResult activateLocked(CompleteAccountActivationCommand command) {
    StoredActivationToken stored = tokenStore.findForUpdate(command.token())
            .orElseThrow(this::invalidToken);
    if (!stored.row().getExpiresAt().isAfter(clock.instant())) {
        throw invalidToken();
    }
    UserPo user = userRepository.findByIdForUpdate(stored.row().getUserId())
            .orElseThrow(this::invalidToken);
    if (user.getActivatedAt() != null) {
        throw invalidToken();
    }
    ensureActivationUserAvailable(user);
    String password = passwordTransportEncryptionService.decryptPassword(
            command.passwordKeyId(), command.encryptedPassword());
    validatePassword(password);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setPasswordExpired(false);
    user.setActivatedAt(clock.instant());
    userRepository.save(user);
    tokenStore.delete(stored.row());
    auditService.log(user.getId(), "AUTH_ACCOUNT_ACTIVATED", "USER", user.getId(),
            null, user.getUsername(), command.ip(), command.userAgent());
    return new AccountActivationResult(user.getId(), user.getUsername());
}

private void ensureActivationUserAvailable(UserPo user) {
    if (user.getStatus() != RbacStatus.ENABLED || user.isLocked() || user.isDeleted()) {
        throw new RbacException("AUTH_ACTIVATION_USER_UNAVAILABLE", "user cannot be activated");
    }
}

private void validatePassword(String password) {
    if (password.length() < 8 || password.length() > 128) {
        throw new RbacException("RBAC_USER_PASSWORD_INVALID",
                "password length must be between 8 and 128");
    }
}

private RbacException invalidToken() {
    return new RbacException("AUTH_ACTIVATION_TOKEN_INVALID",
            "activation token is invalid or expired");
}

private <T> T requireTransactionResult(T value) {
    if (value == null) {
        throw new IllegalStateException("activation transaction returned no result");
    }
    return value;
}
```

Constructor fields are exactly: `UserRepository`, `RbacOneTimeTokenStore`, `PasswordTransportEncryptionService`, `PasswordEncoder`, `RbacAuditService`, `RbacOttProperties`, `TransactionOperations`, `Scheduler blockingScheduler`, and `Clock`. Annotate the class `@Service`, `@RequiredArgsConstructor`, and do not log raw tokens, encrypted password bodies, or URLs.

- [ ] **Step 7: Run the focused test repeatedly to exercise concurrency**

Run:

```bash
cd be
./mvnw -Dtest=RbacReactiveOneTimeTokenServiceTests test
./mvnw -Dtest=RbacReactiveOneTimeTokenServiceTests#concurrentActivationAllowsExactlyOneSuccess test
```

Expected: both commands exit 0; the concurrency method has one success and one uniform invalid-token failure.

- [ ] **Step 8: Commit Task 3**

```bash
git add be/src/main/java/top/egon/mario/rbac/po/OneTimeTokenPo.java \
  be/src/main/java/top/egon/mario/rbac/po/enums/OneTimeTokenPurpose.java \
  be/src/main/java/top/egon/mario/rbac/repository/OneTimeTokenRepository.java \
  be/src/main/java/top/egon/mario/rbac/repository/UserRepository.java \
  be/src/main/java/top/egon/mario/rbac/activation/model \
  be/src/main/java/top/egon/mario/rbac/activation/store/RbacOneTimeTokenStore.java \
  be/src/main/java/top/egon/mario/rbac/activation/service/RbacAccountActivationTokenService.java \
  be/src/main/java/top/egon/mario/rbac/activation/service/RbacReactiveOneTimeTokenService.java \
  be/src/test/java/top/egon/mario/rbac/activation/service/RbacReactiveOneTimeTokenServiceTests.java
git commit -m "feat(auth): persist and consume activation ott"
```

### Task 4: Expose the public activation endpoint with rolling Redis rate limiting

**Files:**
- Create: `be/src/main/java/top/egon/mario/rbac/dto/request/CompleteAccountActivationRequest.java`
- Create: `be/src/main/java/top/egon/mario/rbac/activation/service/ActivationFailureRateLimiter.java`
- Create: `be/src/main/java/top/egon/mario/rbac/application/RbacAccountActivationApplication.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/web/AuthController.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicy.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java`
- Modify: `be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java`
- Create: `be/src/test/java/top/egon/mario/rbac/activation/service/ActivationFailureRateLimiterTests.java`
- Create: `be/src/test/java/top/egon/mario/rbac/application/RbacAccountActivationApplicationTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicyTests.java`
- Modify: `be/src/test/java/top/egon/mario/config/RbacSecurityConfigCsrfTests.java`
- Modify: `be/src/test/java/top/egon/mario/config/GlobalExceptionHandlerTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/web/AuthControllerBrowserCookieTests.java`

**Interfaces:**
- Consumes: Task 3 `RbacAccountActivationTokenService#activate`, `RbacOttProperties#rateLimitEnabled`, current CSRF/browser-header behavior, and Redis `StringRedisTemplate`.
- Produces: `RbacAccountActivationApplication#complete(CompleteAccountActivationRequest, String ip, String userAgent): void` and public POST `/api/auth/activation/complete` returning `ApiResponse<Void>`.

- [ ] **Step 1: Write failing limiter, application, endpoint, and status tests**

Create `ActivationFailureRateLimiterTests` with a fixed clock and mocked `StringRedisTemplate`:

```java
class ActivationFailureRateLimiterTests {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void allowsNineteenFailuresAndRejectsTheTwentyFirstRequest() {
        ActivationFailureRateLimiter limiter = limiter(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(19L, 20L);

        assertThatCode(() -> limiter.assertAllowed("127.0.0.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.assertAllowed("127.0.0.1"))
                .extracting("code")
                .isEqualTo("AUTH_ACTIVATION_RATE_LIMITED");
    }

    @Test
    void recordsFailureWithTheTenMinuteRollingWindowExpiry() {
        ActivationFailureRateLimiter limiter = limiter(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        limiter.recordFailure("127.0.0.1");

        verify(redisTemplate).execute(any(RedisScript.class), anyList(),
                argThat(arguments -> Arrays.asList(arguments).contains("600000")));
    }

    @Test
    void disabledLimiterDoesNotCallRedis() {
        ActivationFailureRateLimiter limiter = limiter(false);
        limiter.assertAllowed("127.0.0.1");
        limiter.recordFailure("127.0.0.1");
        verifyNoInteractions(redisTemplate);
    }

    private ActivationFailureRateLimiter limiter(boolean enabled) {
        RbacOttProperties properties = new RbacOttProperties(true, Duration.ofHours(24),
                URI.create("http://localhost:5173"), ActivationDeliveryMode.MOCK, true, enabled);
        return new ActivationFailureRateLimiter(redisTemplate, properties, clock);
    }
}
```

In `RbacAccountActivationApplicationTests`, mock the token service and limiter. Use this counted-error matrix:

```java
@ParameterizedTest
@ValueSource(strings = {
        "AUTH_ACTIVATION_TOKEN_INVALID",
        "AUTH_ACTIVATION_USER_UNAVAILABLE",
        "AUTH_PASSWORD_KEY_INVALID",
        "AUTH_PASSWORD_DECRYPT_FAILED",
        "RBAC_USER_PASSWORD_INVALID"
})
void recordsOnlySecurityRelevantActivationFailures(String code) {
    CompleteAccountActivationRequest request = request();
    doThrow(new RbacException(code, "rejected")).when(tokenService).activate(any());

    assertThatThrownBy(() -> application.complete(request, "127.0.0.1", "test"))
            .isInstanceOf(RbacException.class);

    then(rateLimiter).should().assertAllowed("127.0.0.1");
    then(rateLimiter).should().recordFailure("127.0.0.1");
}
```

Add a success test that verifies `recordFailure` is not called and the command contains the token, password key id, encrypted password, IP, and User-Agent.

```java
@Test
void successfulActivationDoesNotIncrementFailureCount() {
    CompleteAccountActivationRequest request = request();
    given(tokenService.activate(any())).willReturn(new AccountActivationResult(7L, "mario"));

    application.complete(request, "127.0.0.1", "test-agent");

    ArgumentCaptor<CompleteAccountActivationCommand> command =
            ArgumentCaptor.forClass(CompleteAccountActivationCommand.class);
    then(tokenService).should().activate(command.capture());
    assertThat(command.getValue()).isEqualTo(new CompleteAccountActivationCommand(
            "raw-token", "key-1", "encrypted-password", "127.0.0.1", "test-agent"));
    then(rateLimiter).should(never()).recordFailure(any());
}

private CompleteAccountActivationRequest request() {
    return new CompleteAccountActivationRequest("raw-token", "key-1", "encrypted-password");
}
```

Extend `RbacPublicApiPolicyTests` with:

```java
assertThat(RbacPublicApiPolicy.isAllowedPublicRule(
        "POST", "/api/auth/activation/complete")).isTrue();
assertThat(RbacPublicApiPolicy.isAllowedPublicRule(
        "GET", "/api/auth/activation/complete")).isFalse();
```

Extend `RbacSecurityConfigCsrfTests` with these requests using an invalid activation body:

```java
@Test
void browserActivationWithoutCsrfIsForbidden() {
    webTestClient.post().uri("/api/auth/activation/complete")
            .header("X-Client-Type", "browser")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidActivationBody())
            .exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.code").isEqualTo("AUTH_CSRF_INVALID");
}

@Test
void nonBrowserActivationIsPublicAndReachesValidation() {
    webTestClient.post().uri("/api/auth/activation/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidActivationBody())
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
}

private String invalidActivationBody() {
    return """
            {"token":"","passwordKeyId":"","encryptedPassword":""}
            """;
}
```

Extend `GlobalExceptionHandlerTests`:

```java
@Test
void mapsOnlyActivationRateLimitToTooManyRequests() {
    var response = handler.handleRbacException(new RbacException(
                    "AUTH_ACTIVATION_RATE_LIMITED", "too many activation failures"))
            .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-rate"))
            .block();
    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getBody().code()).isEqualTo("AUTH_ACTIVATION_RATE_LIMITED");
}
```

In `AuthControllerBrowserCookieTests`, add `RbacAccountActivationApplication accountActivationApplication` to setup and the controller constructor, then add:

```java
@Test
void browserActivationReturnsNoTokensAndWritesNoAuthCookies() {
    CompleteAccountActivationRequest request = new CompleteAccountActivationRequest(
            "raw-token", "key-1", "encrypted-password");
    MockServerWebExchange exchange = exchange("/api/auth/activation/complete", true);

    StepVerifier.create(controller.completeActivation(request, exchange))
            .assertNext(response -> {
                assertThat(response.data()).isNull();
                assertNoAuthCookies(exchange);
            })
            .verifyComplete();
    verify(accountActivationApplication).complete(eq(request), nullable(String.class), eq("controller-test"));
}
```

- [ ] **Step 2: Run the focused tests to verify the red state**

Run:

```bash
cd be
./mvnw -Dtest=ActivationFailureRateLimiterTests,RbacAccountActivationApplicationTests,RbacPublicApiPolicyTests,RbacSecurityConfigCsrfTests,GlobalExceptionHandlerTests,AuthControllerBrowserCookieTests test
```

Expected: compilation or assertion failures for the missing limiter/application/endpoint and 429 mapping.

- [ ] **Step 3: Add the validated public request**

Create:

```java
package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompleteAccountActivationRequest(
        @NotBlank String token,
        @NotBlank String passwordKeyId,
        @NotBlank String encryptedPassword
) {
}
```

- [ ] **Step 4: Implement a true rolling-window limiter with Redis sorted sets**

Create `ActivationFailureRateLimiter` with these constants and scripts:

```java
private static final String KEY_PREFIX = "rbac:activation:failures:";
private static final Duration WINDOW = Duration.ofMinutes(10);
private static final long FAILURE_LIMIT = 20L;

private static final DefaultRedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>("""
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
        local count = redis.call('ZCARD', KEYS[1])
        if count == 0 then redis.call('DEL', KEYS[1]) end
        return count
        """, Long.class);

private static final DefaultRedisScript<Long> RECORD_SCRIPT = new DefaultRedisScript<>("""
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
        redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
        redis.call('PEXPIRE', KEYS[1], ARGV[4])
        return redis.call('ZCARD', KEYS[1])
        """, Long.class);
```

Implement the public methods:

```java
public void assertAllowed(String ip) {
    if (!properties.rateLimitEnabled()) {
        return;
    }
    long now = clock.millis();
    Long count = redisTemplate.execute(COUNT_SCRIPT, List.of(key(ip)),
            String.valueOf(now - WINDOW.toMillis()));
    if (count != null && count >= FAILURE_LIMIT) {
        throw new RbacException("AUTH_ACTIVATION_RATE_LIMITED",
                "too many activation failures; try again later");
    }
}

public void recordFailure(String ip) {
    if (!properties.rateLimitEnabled()) {
        return;
    }
    long now = clock.millis();
    redisTemplate.execute(RECORD_SCRIPT, List.of(key(ip)),
            String.valueOf(now - WINDOW.toMillis()),
            String.valueOf(now),
            now + ":" + UUID.randomUUID(),
            String.valueOf(WINDOW.toMillis()));
}
```

Build the Redis key from `SHA-256(ip == null || ip.isBlank() ? "unknown" : ip)` in lowercase hex so the raw IP is not embedded in Redis keys. Do not catch Redis failures here; an unavailable limiter must not silently downgrade brute-force protection.

Use this exact helper and annotate the class `@Component` / `@RequiredArgsConstructor` with fields `StringRedisTemplate redisTemplate`, `RbacOttProperties properties`, and `Clock clock`:

```java
private String key(String ip) {
    String source = ip == null || ip.isBlank() ? "unknown" : ip;
    try {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
}
```

- [ ] **Step 5: Implement activation application orchestration**

Create `RbacAccountActivationApplication` with `@Service`, `@RequiredArgsConstructor`, and these methods:

```java
private static final Set<String> COUNTED_FAILURE_CODES = Set.of(
        "AUTH_ACTIVATION_TOKEN_INVALID",
        "AUTH_ACTIVATION_USER_UNAVAILABLE",
        "AUTH_PASSWORD_KEY_INVALID",
        "AUTH_PASSWORD_DECRYPT_FAILED",
        "RBAC_USER_PASSWORD_INVALID"
);

public void complete(CompleteAccountActivationRequest request, String ip, String userAgent) {
    failureRateLimiter.assertAllowed(ip);
    try {
        tokenService.activate(new CompleteAccountActivationCommand(
                request.token(), request.passwordKeyId(), request.encryptedPassword(), ip, userAgent));
    } catch (RbacException exception) {
        if (COUNTED_FAILURE_CODES.contains(exception.getCode())) {
            failureRateLimiter.recordFailure(ip);
        }
        throw exception;
    }
}
```

Inject only `RbacAccountActivationTokenService` and `ActivationFailureRateLimiter` in this task. Admin orchestration is added to this same application facade in Task 5.

- [ ] **Step 6: Add the endpoint and public/security policy**

Inject `RbacAccountActivationApplication` into `AuthController`, then add:

```java
@RbacApi(appCode = "rbac", code = "api:rbac:auth:activation:complete",
        name = "RBAC 完成账号激活", publicFlag = true)
@PostMapping("/activation/complete")
public Mono<ApiResponse<Void>> completeActivation(
        @Valid @RequestBody CompleteAccountActivationRequest request,
        ServerWebExchange exchange) {
    return blockingVoid(() -> accountActivationApplication.complete(
            request, clientIp(exchange), userAgent(exchange)));
}
```

Move `"/api/auth/activation/complete"` into a dedicated constant to keep the wildcard-free policy explicit:

```java
public static final String[] PUBLIC_ACTIVATION_ENDPOINTS = {
        "/api/auth/activation/complete"
};
```

Add it to `PUBLIC_POST_PATHS`, and add this matcher immediately after the existing public auth POST matcher in `RbacSecurityConfig`:

```java
.pathMatchers(HttpMethod.POST, RbacPublicApiPolicy.PUBLIC_ACTIVATION_ENDPOINTS).permitAll()
```

Do not add a GET matcher and do not bypass `requiresCsrfProtection`; browser requests continue to acquire and send the current XSRF token through `requestJson`.

- [ ] **Step 7: Map the one rate-limit error to HTTP 429**

Change only the response construction inside `handleRbacException`:

```java
return ResponseEntity.status(rbacStatus(ex))
        .body(ApiResponse.fail(ex.getCode(), ex.getDetailMessage(), traceId));
```

Add:

```java
private HttpStatus rbacStatus(RbacException exception) {
    return "AUTH_ACTIVATION_RATE_LIMITED".equals(exception.getCode())
            ? HttpStatus.TOO_MANY_REQUESTS
            : HttpStatus.BAD_REQUEST;
}
```

This preserves all existing RBAC exception status behavior.

- [ ] **Step 8: Run focused tests**

Run:

```bash
cd be
./mvnw -Dtest=ActivationFailureRateLimiterTests,RbacAccountActivationApplicationTests,RbacPublicApiPolicyTests,RbacSecurityConfigCsrfTests,GlobalExceptionHandlerTests,AuthControllerBrowserCookieTests test
```

Expected: PASS; the browser activation call needs CSRF, the non-browser public call reaches validation, the controller writes no auth cookies, and rate-limit errors are 429.

- [ ] **Step 9: Commit Task 4**

```bash
git add be/src/main/java/top/egon/mario/rbac/dto/request/CompleteAccountActivationRequest.java \
  be/src/main/java/top/egon/mario/rbac/activation/service/ActivationFailureRateLimiter.java \
  be/src/main/java/top/egon/mario/rbac/application/RbacAccountActivationApplication.java \
  be/src/main/java/top/egon/mario/rbac/web/AuthController.java \
  be/src/main/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicy.java \
  be/src/main/java/top/egon/mario/rbac/config/RbacSecurityConfig.java \
  be/src/main/java/top/egon/mario/config/GlobalExceptionHandler.java \
  be/src/test/java/top/egon/mario/rbac/activation/service/ActivationFailureRateLimiterTests.java \
  be/src/test/java/top/egon/mario/rbac/application/RbacAccountActivationApplicationTests.java \
  be/src/test/java/top/egon/mario/rbac/service/security/RbacPublicApiPolicyTests.java \
  be/src/test/java/top/egon/mario/config/RbacSecurityConfigCsrfTests.java \
  be/src/test/java/top/egon/mario/config/GlobalExceptionHandlerTests.java \
  be/src/test/java/top/egon/mario/rbac/web/AuthControllerBrowserCookieTests.java
git commit -m "feat(auth): expose rate limited account activation"
```

### Task 5: Convert admin user creation to pending activation and add reissue/revocation

**Files:**
- Modify: `be/src/main/java/top/egon/mario/rbac/dto/request/CreateUserRequest.java`
- Create: `be/src/main/java/top/egon/mario/rbac/dto/response/AdminUserCreateResponse.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/RbacUserService.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/impl/RbacUserServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/application/RbacAccountActivationApplication.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/web/AdminUserController.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/resource/RbacAdminConsoleResourceProvider.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/service/RbacUserServiceValidationTests.java`
- Create: `be/src/test/java/top/egon/mario/rbac/application/RbacAdminAccountActivationTests.java`
- Modify: `be/src/test/java/top/egon/mario/rbac/service/resource/RbacAdminConsoleResourceProviderTests.java`

**Interfaces:**
- Consumes: Task 2 `ActivationLinkDelivery`, Task 3 issue/revoke operations, existing role assignment and audit behavior, and Spring `TransactionOperations`.
- Produces:
  - `RbacUserService#createPendingUser(CreateUserRequest, Long): UserResponse`.
  - `RbacAccountActivationApplication#createPendingUser(CreateUserRequest, Long): AdminUserCreateResponse`.
  - `RbacAccountActivationApplication#reissue(Long, Long): ActivationDeliveryResponse`.
  - Admin POST `/api/admin/users/{id}/activation-token`.
  - RBAC button `btn:system:user:resendActivation`.

- [ ] **Step 1: Write failing admin contract and lifecycle tests**

Update `RbacUserServiceValidationTests` so the blank request must report `accountNo`, `username`, and `email`, and must not mention `initialPassword`.

Create `RbacAdminAccountActivationTests` as a `@SpringBootTest` with real H2 repositories/service/application and `@MockitoBean ActivationLinkDelivery`. Clear token relations before users. Cover these cases:

Spy the real token service so one test can force an issuance failure without replacing the implementation for the remaining integration tests:

```java
@MockitoSpyBean(reset = MockReset.AFTER)
private RbacAccountActivationTokenService tokenService;
```

```java
@Test
void adminCreateCommitsPendingUserRolesAndTokenBeforeDelivery() {
    RolePo role = roleRepository.save(enabledRole("RBAC_VIEWER"));
    CreateUserRequest request = createRequest("mario", "mario@example.com", Set.of(role.getId()));
    given(delivery.deliver(any())).willAnswer(invocation -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        IssuedActivationToken issued = invocation.getArgument(0);
        return new ActivationDeliveryResponse(ActivationDeliveryMode.MOCK,
                issued.token().getExpiresAt(), "http://localhost:5173/activate#token="
                + issued.token().getTokenValue());
    });

    AdminUserCreateResponse response = application.createPendingUser(request, 99L);

    UserPo saved = userRepository.findById(response.user().getId()).orElseThrow();
    assertThat(saved.getEmail()).isEqualTo("mario@example.com");
    assertThat(saved.getStatus()).isEqualTo(RbacStatus.ENABLED);
    assertThat(saved.isLocked()).isFalse();
    assertThat(saved.isPasswordExpired()).isTrue();
    assertThat(saved.getActivatedAt()).isNull();
    assertThat(passwordEncoder.matches("known-admin-password", saved.getPasswordHash())).isFalse();
    assertThat(oneTimeTokenRepository.findAll()).hasSize(1);
    assertThat(userRoleRepository.findByUserId(saved.getId()))
            .extracting("roleId").containsExactly(role.getId());
    assertThat(response.user().getActivationStatus()).isEqualTo(ActivationStatus.PENDING_ACTIVATION);
    assertThat(response.activationDelivery().mockActivationUrl()).contains("/activate#token=");
}

@Test
void tokenIssuanceFailureRollsBackUserRolesAuditAndDelivery() {
    RolePo role = roleRepository.save(enabledRole("RBAC_VIEWER"));
    doThrow(new RbacException("AUTH_ACTIVATION_TOKEN_ISSUE_FAILED", "forced failure"))
            .when(tokenService).issueForUser(anyLong(), eq(99L),
                    eq(ActivationTokenIssueReason.ISSUED));

    assertThatThrownBy(() -> application.createPendingUser(
            createRequest("rollback", "rollback@example.com", Set.of(role.getId())), 99L))
            .extracting("code").isEqualTo("AUTH_ACTIVATION_TOKEN_ISSUE_FAILED");

    assertThat(userRepository.findByAccountNoAndDeletedFalse("rollback")).isEmpty();
    assertThat(userRoleRepository.findAll()).isEmpty();
    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    assertThat(auditLogRepository.findAll()).isEmpty();
    then(delivery).should(never()).deliver(any());
}
```

Add the duplicate, reissue, and reset tests:

```java
@Test
void duplicateEmailCreatesNeitherPendingUserNorToken() {
    UserPo existing = new UserPo();
    existing.setAccountNo("existing");
    existing.setUsername("existing");
    existing.setEmail("taken@example.com");
    existing.setPasswordHash(passwordEncoder.encode("existing-password"));
    existing.setStatus(RbacStatus.ENABLED);
    existing.setActivatedAt(Instant.now());
    userRepository.save(existing);

    assertThatThrownBy(() -> application.createPendingUser(
            createRequest("duplicate", "taken@example.com", Set.of()), 99L))
            .extracting("code").isEqualTo("RBAC_USER_EMAIL_DUPLICATED");
    assertThat(userRepository.findAll()).hasSize(1);
    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
}

@Test
void reissueReplacesTheOldRawTokenAndAuditsWithoutTokenContent() {
    AdminUserCreateResponse created = application.createPendingUser(
            createRequest("luigi", "luigi@example.com", Set.of()), 99L);
    String firstRaw = rawToken(created.activationDelivery());

    ActivationDeliveryResponse reissued = application.reissue(created.user().getId(), 99L);
    String secondRaw = rawToken(reissued);

    assertThat(secondRaw).isNotEqualTo(firstRaw);
    assertThat(oneTimeTokenRepository.findAll()).singleElement()
            .extracting(OneTimeTokenPo::getTokenHash)
            .isEqualTo(tokenStore.hash(secondRaw));
    assertThat(auditLogRepository.findAll()).extracting("action")
            .contains("AUTH_ACTIVATION_TOKEN_ISSUED", "AUTH_ACTIVATION_TOKEN_REISSUED");
    assertThat(auditLogRepository.findAll()).allSatisfy(audit -> {
        assertThat(String.valueOf(audit.getBeforeJson())).doesNotContain(firstRaw, secondRaw);
        assertThat(String.valueOf(audit.getAfterJson())).doesNotContain(firstRaw, secondRaw);
    });
}

@Test
void activatedUserCannotReissueAndCanStillUseAdminPasswordReset() {
    UserPo user = activatedUser("peach", "peach@example.com", "old-password-123");

    assertThatThrownBy(() -> application.reissue(user.getId(), 99L))
            .extracting("code").isEqualTo("AUTH_USER_ALREADY_ACTIVATED");
    userService.resetPassword(user.getId(), "new-password-123");
    assertThat(passwordEncoder.matches("new-password-123",
            userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
}

@Test
void pendingUserCannotBypassActivationWithAdminPasswordReset() {
    AdminUserCreateResponse created = application.createPendingUser(
            createRequest("toad", "toad@example.com", Set.of()), 99L);
    String before = userRepository.findById(created.user().getId()).orElseThrow().getPasswordHash();

    assertThatThrownBy(() -> userService.resetPassword(created.user().getId(), "new-password-123"))
            .extracting("code").isEqualTo("AUTH_USER_NOT_ACTIVATED");
    assertThat(userRepository.findById(created.user().getId()).orElseThrow().getPasswordHash())
            .isEqualTo(before);
    assertThat(oneTimeTokenRepository.findAll()).hasSize(1);
}
```

Add lifecycle revocation as separate fresh-user tests so each operation starts with one current token:

```java
@Test
void pendingEmailChangeRevokesWithoutIssuingAReplacement() {
    AdminUserCreateResponse created = application.createPendingUser(
            createRequest("daisy", "daisy@example.com", Set.of()), 99L);
    UpdateUserRequest update = new UpdateUserRequest();
    update.setEmail("new-daisy@example.com");

    userService.updateUser(created.user().getId(), update);

    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    assertThat(auditLogRepository.findAll()).extracting("action")
            .contains("AUTH_ACTIVATION_TOKEN_REVOKED");
}

@Test
void disablingThenReenablingPendingUserDoesNotCreateAToken() {
    AdminUserCreateResponse created = application.createPendingUser(
            createRequest("wario", "wario@example.com", Set.of()), 99L);

    userService.updateStatus(created.user().getId(),
            top.egon.mario.rbac.dto.enums.RbacStatus.DISABLED);
    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
    userService.updateStatus(created.user().getId(),
            top.egon.mario.rbac.dto.enums.RbacStatus.ENABLED);
    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
}

@Test
void lockingPendingUserRevokesItsToken() {
    AdminUserCreateResponse created = application.createPendingUser(
            createRequest("rosalina", "rosalina@example.com", Set.of()), 99L);
    UpdateUserRequest update = new UpdateUserRequest();
    update.setEmail("rosalina@example.com");
    update.setLocked(true);

    userService.updateUser(created.user().getId(), update);

    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
}

@Test
void deletingPendingUserRevokesItsToken() {
    AdminUserCreateResponse created = application.createPendingUser(
            createRequest("yoshi", "yoshi@example.com", Set.of()), 99L);

    userService.deleteUser(created.user().getId(), 99L);

    assertThat(oneTimeTokenRepository.findAll()).isEmpty();
}
```

Initialize the delivery stub in `@BeforeEach` and define all helpers used above:

```java
@BeforeEach
void setUp() {
    auditLogRepository.deleteAll();
    oneTimeTokenRepository.deleteAll();
    userRoleRepository.deleteAll();
    roleRepository.deleteAll();
    userRepository.deleteAll();
    given(delivery.deliver(any())).willAnswer(invocation -> {
        IssuedActivationToken issued = invocation.getArgument(0);
        return new ActivationDeliveryResponse(ActivationDeliveryMode.MOCK,
                issued.token().getExpiresAt(), "http://localhost:5173/activate#token="
                + issued.token().getTokenValue());
    });
}

private CreateUserRequest createRequest(String account, String email, Set<Long> roleIds) {
    CreateUserRequest request = new CreateUserRequest();
    request.setAccountNo(account);
    request.setUsername(account);
    request.setNickname(account);
    request.setEmail(email);
    request.setRoleIds(roleIds);
    return request;
}

private RolePo enabledRole(String code) {
    RolePo role = new RolePo();
    role.setRoleCode(code);
    role.setRoleName(code);
    role.setStatus(RbacStatus.ENABLED);
    return role;
}

private UserPo activatedUser(String account, String email, String password) {
    UserPo user = new UserPo();
    user.setAccountNo(account);
    user.setUsername(account);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setStatus(RbacStatus.ENABLED);
    user.setActivatedAt(Instant.now());
    return userRepository.save(user);
}

private String rawToken(ActivationDeliveryResponse response) {
    return URI.create(response.mockActivationUrl()).getFragment().substring("token=".length());
}
```

Autowire `RbacOneTimeTokenStore tokenStore`; do not add a repository method that accepts raw tokens.

Extend the resource-provider test to contain `btn:system:user:resendActivation` and verify its button action is `resendActivation`.

- [ ] **Step 2: Run the focused tests to verify they fail**

Run:

```bash
cd be
./mvnw -Dtest=RbacUserServiceValidationTests,RbacAdminAccountActivationTests,RbacAdminConsoleResourceProviderTests test
```

Expected: FAIL because the create DTO still accepts a password/status, the application has no admin methods, and the RBAC button is absent.

- [ ] **Step 3: Upgrade the admin request and response contract**

Replace `CreateUserRequest` with this complete contract:

```java
package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Management request for creating a pending user account.
 */
@Getter
@Setter
public class CreateUserRequest {
    @NotBlank
    private String accountNo;
    @NotBlank
    private String username;
    private String nickname;
    @NotBlank
    @Email
    private String email;
    private String mobile;
    private String avatarUrl;
    private String remark;
    private Set<Long> roleIds = Set.of();
}
```

Create:

```java
package top.egon.mario.rbac.dto.response;

public record AdminUserCreateResponse(
        UserResponse user,
        ActivationDeliveryResponse activationDelivery
) {
}
```

Rename the service interface operation:

```java
UserResponse createPendingUser(@Valid @NotNull CreateUserRequest request, Long actorUserId);
```

- [ ] **Step 4: Create pending users with a random placeholder and fixed status**

Inject the Task 2 `SecureRandom` and Task 3 `RbacAccountActivationTokenService` into `RbacUserServiceImpl`. Rename the implementation method to `createPendingUser`, retain existing account/username/contact validation and role assignment, then set exact state:

```java
UserPo user = rbacDtoConverter.toUserPo(request);
user.setAccountNo(accountNo);
user.setUsername(username);
user.setEmail(request.getEmail().trim());
user.setMobile(trimToNull(request.getMobile()));
byte[] placeholderBytes = new byte[32];
secureRandom.nextBytes(placeholderBytes);
String placeholder = Base64.getUrlEncoder().withoutPadding().encodeToString(placeholderBytes);
user.setPasswordHash(passwordEncoder.encode(placeholder));
user.setStatus(RbacStatus.ENABLED);
user.setLocked(false);
user.setPasswordExpired(true);
user.setActivatedAt(null);
UserPo savedUser = userRepository.save(user);
replaceUserRoles(savedUser.getId(), request.getRoleIds(), actorUserId);
auditService.log(actorUserId, "RBAC_USER_CREATE_PENDING", "USER", savedUser.getId(),
        null, savedUser.getUsername(), null, null);
return rbacDtoConverter.toUserResponse(savedUser);
```

Never return or log `placeholder`.

- [ ] **Step 5: Enforce pending-user revocation and reset rules in the existing service**

Add this private helper:

```java
private void revokePendingActivation(UserPo user, Long actorUserId, String reason) {
    if (user.getActivatedAt() == null) {
        accountActivationTokenService.revokeForUser(user.getId(), actorUserId, reason);
    }
}
```

Apply exact rules:

```java
// updateUser: revoke before user mutation so every path locks Token before User
boolean pending = user.getActivatedAt() == null;
String oldEmail = user.getEmail();
String newEmail = trimToNull(request.getEmail());
boolean disabling = request.getStatus() != null
        && rbacDtoConverter.toPoRbacStatus(request.getStatus()) != RbacStatus.ENABLED;
boolean locking = Boolean.TRUE.equals(request.getLocked());
if (pending && (!Objects.equals(oldEmail, newEmail) || disabling || locking)) {
    revokePendingActivation(user, null, "pending user profile or availability changed");
}
user.setNickname(request.getNickname());
user.setEmail(newEmail);
user.setMobile(trimToNull(request.getMobile()));
user.setAvatarUrl(request.getAvatarUrl());
user.setRemark(request.getRemark());
if (request.getStatus() != null) {
    user.setStatus(rbacDtoConverter.toPoRbacStatus(request.getStatus()));
}
if (request.getLocked() != null) {
    user.setLocked(request.getLocked());
}
if (request.getPasswordExpired() != null) {
    user.setPasswordExpired(request.getPasswordExpired());
}
UserPo savedUser = userRepository.save(user);
```

```java
// resetPassword, before encoding
if (user.getActivatedAt() == null) {
    throw new RbacException("AUTH_USER_NOT_ACTIVATED",
            "pending user password must be set through account activation");
}
```

```java
// updateStatus, before changing the user row
if (status != top.egon.mario.rbac.dto.enums.RbacStatus.ENABLED) {
    revokePendingActivation(user, null, "pending user disabled");
}
user.setStatus(rbacDtoConverter.toPoRbacStatus(status));
userRepository.save(user);
```

```java
// deleteUser, before changing the user row
revokePendingActivation(user, actorUserId, "pending user deleted");
user.setDeleted(true);
userRepository.save(user);
```

Do not issue on email update or re-enable; only the explicit reissue endpoint creates a new link.

- [ ] **Step 6: Add create/reissue orchestration with delivery after commit**

Inject `RbacUserService`, `ActivationLinkDelivery`, and `TransactionOperations` into `RbacAccountActivationApplication`, retaining the Task 4 dependencies. Add:

```java
public AdminUserCreateResponse createPendingUser(CreateUserRequest request, Long actorUserId) {
    PendingActivation pending = requireTransactionResult(transactionOperations.execute(status -> {
        UserResponse user = userService.createPendingUser(request, actorUserId);
        IssuedActivationToken issued = tokenService.issueForUser(
                user.getId(), actorUserId, ActivationTokenIssueReason.ISSUED);
        return new PendingActivation(user, issued);
    }));
    return new AdminUserCreateResponse(pending.user(), delivery.deliver(pending.issuedToken()));
}

public ActivationDeliveryResponse reissue(Long userId, Long actorUserId) {
    IssuedActivationToken issued = requireTransactionResult(transactionOperations.execute(status ->
            tokenService.issueForUser(userId, actorUserId, ActivationTokenIssueReason.REISSUED)));
    return delivery.deliver(issued);
}

private <T> T requireTransactionResult(T value) {
    if (value == null) {
        throw new IllegalStateException("account activation transaction returned no result");
    }
    return value;
}

private record PendingActivation(UserResponse user, IssuedActivationToken issuedToken) {
}
```

The explicit outer transaction guarantees user, roles, and token are one commit; calling `delivery.deliver` only after `execute` returns guarantees delivery occurs after commit. A future mail failure therefore leaves a recoverable pending account and current token.

- [ ] **Step 7: Upgrade admin endpoints and seed the permission button**

Replace the create controller method with:

```java
@PostMapping
public Mono<ApiResponse<AdminUserCreateResponse>> create(
        @Valid @RequestBody CreateUserRequest request,
        @AuthenticationPrincipal RbacPrincipal principal) {
    return blocking(() -> accountActivationApplication.createPendingUser(
            request, actorId(principal)));
}
```

Add:

```java
@PostMapping("/{id}/activation-token")
public Mono<ApiResponse<ActivationDeliveryResponse>> reissueActivationToken(
        @PathVariable @Min(1) Long id,
        @AuthenticationPrincipal RbacPrincipal principal) {
    return blocking(() -> accountActivationApplication.reissue(id, actorId(principal)));
}
```

Inject `RbacAccountActivationApplication` into the controller. Existing `api:rbac:admin:*` remains the API authority; do not add a public rule.

Insert this provider seed after reset password and shift the old status/delete sort numbers to 7/8:

```java
resources.add(button("btn:system:user:resendActivation", "重新生成激活链接",
        "menu:system:users", "resendActivation", 6));
```

- [ ] **Step 8: Run focused admin tests**

Run:

```bash
cd be
./mvnw -Dtest=RbacUserServiceValidationTests,RbacAdminAccountActivationTests,RbacAdminConsoleResourceProviderTests test
```

Expected: PASS; create/reissue responses contain Mock delivery only at the admin boundary, pending lifecycle changes revoke tokens, and activated reset-password behavior passes.

- [ ] **Step 9: Commit Task 5**

```bash
git add be/src/main/java/top/egon/mario/rbac/dto/request/CreateUserRequest.java \
  be/src/main/java/top/egon/mario/rbac/dto/response/AdminUserCreateResponse.java \
  be/src/main/java/top/egon/mario/rbac/service/RbacUserService.java \
  be/src/main/java/top/egon/mario/rbac/service/impl/RbacUserServiceImpl.java \
  be/src/main/java/top/egon/mario/rbac/application/RbacAccountActivationApplication.java \
  be/src/main/java/top/egon/mario/rbac/web/AdminUserController.java \
  be/src/main/java/top/egon/mario/rbac/service/resource/RbacAdminConsoleResourceProvider.java \
  be/src/test/java/top/egon/mario/rbac/service/RbacUserServiceValidationTests.java \
  be/src/test/java/top/egon/mario/rbac/application/RbacAdminAccountActivationTests.java \
  be/src/test/java/top/egon/mario/rbac/service/resource/RbacAdminConsoleResourceProviderTests.java
git commit -m "feat(rbac): create and reissue pending accounts"
```

### Task 6: Upgrade frontend RBAC contracts and service calls

**Files:**
- Modify: `fe/src/modules/rbac/rbacTypes.ts`
- Modify: `fe/src/modules/rbac/rbacService.ts`
- Modify: `fe/src/modules/rbac/rbacService.test.ts`
- Modify: `fe/src/modules/rbac/rbacPermissionCodes.ts`
- Modify: `fe/src/modules/rbac/users/UserEditorDrawer.tsx`

**Interfaces:**
- Consumes: Task 5 backend JSON contracts.
- Produces:
  - `ActivationStatus = 'PENDING_ACTIVATION' | 'ACTIVATED'`.
  - `ActivationDeliveryResponse` and `AdminUserCreateResponse`.
  - `createUser(request): Promise<AdminUserCreateResponse>`.
  - `reissueUserActivation(id): Promise<ActivationDeliveryResponse>`.
  - `rbacButtonCodes.user.resendActivation`.

- [ ] **Step 1: Write failing request-contract tests**

Extend the imports in `rbacService.test.ts` and add:

```ts
test('sends the pending-user create contract without password or status', async () => {
    const {requestJson} = await import('../../services/request')

    void createUser({
        accountNo: 'mario',
        username: 'mario',
        email: 'mario@example.com',
        roleIds: [1, 2],
    })

    expect(requestJson).toHaveBeenCalledWith('/api/admin/users', {
        method: 'POST',
        body: {
            accountNo: 'mario',
            username: 'mario',
            email: 'mario@example.com',
            roleIds: [1, 2],
        },
    })
    expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('initialPassword')
    expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('status')
})

test('posts the explicit activation reissue endpoint without a request body', async () => {
    const {requestJson} = await import('../../services/request')

    void reissueUserActivation(42)

    expect(requestJson).toHaveBeenCalledWith('/api/admin/users/42/activation-token', {
        method: 'POST',
    })
})
```

- [ ] **Step 2: Run the service test to verify it fails**

Run:

```bash
cd fe
bun run test -- src/modules/rbac/rbacService.test.ts
```

Expected: TypeScript or assertion failure because create still requires `initialPassword` and reissue is absent.

- [ ] **Step 3: Define exact frontend contracts**

Add to `rbacTypes.ts`:

```ts
export type ActivationStatus = 'PENDING_ACTIVATION' | 'ACTIVATED'
export type ActivationDeliveryMode = 'MOCK'

export type ActivationDeliveryResponse = {
    mode: ActivationDeliveryMode
    expiresAt: string
    mockActivationUrl?: string
}

export type AdminUserCreateResponse = {
    user: UserResponse
    activationDelivery: ActivationDeliveryResponse
}
```

Add to `UserResponse`:

```ts
activatedAt?: string
activationStatus: ActivationStatus
```

Replace the create/update types with:

```ts
export type CreateUserRequest = {
    accountNo: string
    username: string
    nickname?: string
    email: string
    mobile?: string
    avatarUrl?: string
    remark?: string
    roleIds?: number[]
}

export type UpdateUserRequest = Partial<Omit<CreateUserRequest, 'accountNo' | 'username' | 'roleIds'>> & {
    status?: 'ENABLED' | 'DISABLED'
    locked?: boolean
    passwordExpired?: boolean
}
```

- [ ] **Step 4: Upgrade service return types and add reissue**

Import the two response types, then change/add:

```ts
export function createUser(request: CreateUserRequest) {
    return requestJson<AdminUserCreateResponse>('/api/admin/users', {
        method: 'POST',
        body: request,
    })
}

export function reissueUserActivation(id: number) {
    return requestJson<ActivationDeliveryResponse>(`/api/admin/users/${id}/activation-token`, {
        method: 'POST',
    })
}
```

Add the permission constant next to `resetPassword`:

```ts
resendActivation: 'btn:system:user:resendActivation',
```

Keep the existing editor compiling for this contract-only commit by widening its private form model; Task 7 immediately removes the transient password field from both model and UI:

```ts
type UserFormValues = CreateUserRequest & {
    initialPassword?: string
    status?: 'ENABLED' | 'DISABLED'
    locked?: boolean
    passwordExpired?: boolean
}
```

- [ ] **Step 5: Run tests and typecheck**

Run:

```bash
cd fe
bun run test -- src/modules/rbac/rbacService.test.ts
bun run typecheck
```

Expected: service tests pass and typecheck exits 0. The private `initialPassword?` compatibility field exists only in `UserEditorDrawer` until Task 7 removes the old control; it is not part of `CreateUserRequest` or `rbacService`.

- [ ] **Step 6: Commit Task 6**

```bash
git add fe/src/modules/rbac/rbacTypes.ts \
  fe/src/modules/rbac/rbacService.ts \
  fe/src/modules/rbac/rbacService.test.ts \
  fe/src/modules/rbac/rbacPermissionCodes.ts \
  fe/src/modules/rbac/users/UserEditorDrawer.tsx
git commit -m "feat(rbac): add activation api contracts"
```

### Task 7: Update the admin create form and add the ephemeral activation-link modal

**Files:**
- Modify: `fe/src/modules/rbac/users/UserEditorDrawer.tsx`
- Create: `fe/src/modules/rbac/users/UserEditorDrawer.test.tsx`
- Create: `fe/src/modules/rbac/users/ActivationLinkModal.tsx`
- Create: `fe/src/modules/rbac/users/ActivationLinkModal.test.tsx`

**Interfaces:**
- Consumes: Task 6 `CreateUserRequest`, `UpdateUserRequest`, and `ActivationDeliveryResponse`.
- Produces: a create form with required valid email and no initial-password/status inputs, plus `ActivationLinkModal({title, value, onClose})` that keeps the URL only in parent component memory.

- [ ] **Step 1: Write failing form-mode tests**

Create `UserEditorDrawer.test.tsx`:

```tsx
import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {UserEditorDrawer} from './UserEditorDrawer'
import type {UserResponse} from '../rbacTypes'

describe('UserEditorDrawer', () => {
    test('new-user mode requires email and omits password and status', async () => {
        const user = userEvent.setup()
        render(<App><UserEditorDrawer open onClose={vi.fn()}
            onSubmit={vi.fn().mockResolvedValue(undefined)}/></App>)

        expect(screen.queryByLabelText('初始密码')).toBeNull()
        expect(screen.queryByLabelText('状态')).toBeNull()
        await user.type(screen.getByLabelText('账号'), 'mario')
        await user.type(screen.getByLabelText('用户名'), 'mario')
        await user.click(screen.getByRole('button', {name: '保存'}))
        expect(await screen.findByText('请输入邮箱')).toBeTruthy()
    })

    test('edit mode keeps status and allows an existing empty email', async () => {
        const onSubmit = vi.fn().mockResolvedValue(undefined)
        const user = userEvent.setup()
        const value: UserResponse = {
            id: 7,
            accountNo: 'mario',
            username: 'mario',
            status: 'ENABLED',
            locked: false,
            passwordExpired: false,
            activationStatus: 'ACTIVATED',
        }
        render(<App><UserEditorDrawer open value={value} onClose={vi.fn()} onSubmit={onSubmit}/></App>)

        expect(screen.getByLabelText('状态')).toBeTruthy()
        await user.click(screen.getByRole('button', {name: '保存'}))
        await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
        expect(screen.queryByText('请输入邮箱')).toBeNull()
    })
})
```

- [ ] **Step 2: Write the failing modal test**

Create `ActivationLinkModal.test.tsx`:

```tsx
import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {ActivationLinkModal} from './ActivationLinkModal'

describe('ActivationLinkModal', () => {
    beforeEach(() => {
        Object.defineProperty(navigator, 'clipboard', {
            configurable: true,
            value: {writeText: vi.fn().mockResolvedValue(undefined)},
        })
    })

    test('shows expiry, copies the mock URL, and delegates close', async () => {
        const onClose = vi.fn()
        const user = userEvent.setup()
        const url = 'http://localhost:5173/activate#token=raw-token'
        render(
            <App>
                <ActivationLinkModal
                    onClose={onClose}
                    title="账号已创建"
                    value={{mode: 'MOCK', expiresAt: '2026-07-23T00:00:00Z', mockActivationUrl: url}}
                />
            </App>,
        )

        expect(screen.getByDisplayValue(url)).toBeTruthy()
        expect(screen.getByText(/2026/)).toBeTruthy()
        await user.click(screen.getByRole('button', {name: '复制激活链接'}))
        expect(navigator.clipboard.writeText).toHaveBeenCalledWith(url)
        await user.click(screen.getByRole('button', {name: '关闭'}))
        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
```

- [ ] **Step 3: Run the tests to verify they fail**

Run:

```bash
cd fe
bun run test -- src/modules/rbac/users/UserEditorDrawer.test.tsx src/modules/rbac/users/ActivationLinkModal.test.tsx
```

Expected: FAIL because the old create fields are visible and `ActivationLinkModal` does not exist.

- [ ] **Step 4: Make the form contract mode-specific**

Define form values without leaking create-only fields into update:

```ts
type UserFormValues = CreateUserRequest & {
    status?: 'ENABLED' | 'DISABLED'
    locked?: boolean
    passwordExpired?: boolean
}
```

Delete the entire initial-password `Form.Item`. Make email rules conditional and status edit-only:

```tsx
<Form.Item
    label="邮箱"
    name="email"
    rules={[
        {required: !editing, message: '请输入邮箱'},
        {type: 'email', message: '请输入有效邮箱'},
    ]}
>
    <Input/>
</Form.Item>
{editing && (
    <Form.Item label="状态" name="status">
        <Select options={RBAC_STATUS_OPTIONS}/>
    </Form.Item>
)}
```

Keep account/username disabled only while editing, and retain the existing edit-only locked/password-expired switches.

- [ ] **Step 5: Implement the focused modal**

Create:

```tsx
import {App, Button, Input, Modal, Space, Typography} from 'antd'
import {DateTimeText} from '../../../components/DateTimeText'
import type {ActivationDeliveryResponse} from '../rbacTypes'

type ActivationLinkModalProps = {
    title: string
    value: ActivationDeliveryResponse | null
    onClose: () => void
}

export function ActivationLinkModal({title, value, onClose}: ActivationLinkModalProps) {
    const {message} = App.useApp()
    const url = value?.mockActivationUrl

    async function copyLink() {
        if (!url) return
        await navigator.clipboard.writeText(url)
        message.success('激活链接已复制')
    }

    return (
        <Modal
            destroyOnHidden
            footer={<Button onClick={onClose}>关闭</Button>}
            onCancel={onClose}
            open={Boolean(value)}
            title={title}
        >
            {value && (
                <Space direction="vertical" size="middle" style={{width: '100%'}}>
                    <Typography.Text>
                        链接失效时间：<DateTimeText value={value.expiresAt}/>
                    </Typography.Text>
                    {url && <Input.TextArea readOnly autoSize value={url}/>}
                    <Button disabled={!url} onClick={() => void copyLink()} type="primary">
                        复制激活链接
                    </Button>
                </Space>
            )}
        </Modal>
    )
}
```

Do not add storage calls, URL query parameters, console output, or module-level caching. The parent will clear `value` on close.

- [ ] **Step 6: Run focused tests and typecheck**

Run:

```bash
cd fe
bun run test -- src/modules/rbac/users/UserEditorDrawer.test.tsx src/modules/rbac/users/ActivationLinkModal.test.tsx
bun run typecheck
```

Expected: both component tests pass. Any remaining type errors must be limited to `UserListPage`'s old create response handling and are resolved in Task 8.

- [ ] **Step 7: Commit Task 7**

```bash
git add fe/src/modules/rbac/users/UserEditorDrawer.tsx \
  fe/src/modules/rbac/users/UserEditorDrawer.test.tsx \
  fe/src/modules/rbac/users/ActivationLinkModal.tsx \
  fe/src/modules/rbac/users/ActivationLinkModal.test.tsx
git commit -m "feat(rbac): add pending user create ui"
```

### Task 8: Show activation state and mutually exclusive reset/reissue actions

**Files:**
- Modify: `fe/src/modules/rbac/users/UserListPage.tsx`
- Create: `fe/src/modules/rbac/users/UserListPage.test.tsx`

**Interfaces:**
- Consumes: Task 6 service/permission contracts and Task 7 `ActivationLinkModal`.
- Produces: activation status column, `activationActionsFor(user, access)` test seam, create/reissue modal flow, and strict permission-aware action visibility.

- [ ] **Step 1: Write failing activation-action tests**

Create:

```tsx
import {describe, expect, test} from 'vitest'
import {activationActionsFor, activationStatusLabel} from './UserListPage'
import type {UserResponse} from '../rbacTypes'

const baseUser: UserResponse = {
    id: 7,
    accountNo: 'mario',
    username: 'mario',
    status: 'ENABLED',
    locked: false,
    passwordExpired: true,
    activationStatus: 'PENDING_ACTIVATION',
}

describe('UserListPage activation actions', () => {
    test('pending users can reissue but cannot reset when both permissions exist', () => {
        expect(activationActionsFor(baseUser, {resetPassword: true, resendActivation: true}))
            .toEqual({resetPassword: false, resendActivation: true})
        expect(activationStatusLabel(baseUser)).toBe('待激活')
    })

    test('activated users can reset but cannot reissue', () => {
        const activated = {...baseUser, activationStatus: 'ACTIVATED' as const, passwordExpired: false}
        expect(activationActionsFor(activated, {resetPassword: true, resendActivation: true}))
            .toEqual({resetPassword: true, resendActivation: false})
        expect(activationStatusLabel(activated)).toBe('已激活')
    })

    test('resend remains hidden without its button permission', () => {
        expect(activationActionsFor(baseUser, {resetPassword: true, resendActivation: false}))
            .toEqual({resetPassword: false, resendActivation: false})
    })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd fe
bun run test -- src/modules/rbac/users/UserListPage.test.tsx
```

Expected: FAIL because the two exported activation helpers do not exist.

- [ ] **Step 3: Add the single source of truth for activation actions**

Add these exports near `isEnabled` and use them inside `renderActions`:

```ts
type ActivationActionAccess = {
    resetPassword: boolean
    resendActivation: boolean
}

export function activationActionsFor(user: UserResponse, access: ActivationActionAccess) {
    const pending = user.activationStatus === 'PENDING_ACTIVATION'
    return {
        resetPassword: access.resetPassword && !pending,
        resendActivation: access.resendActivation && pending,
    }
}

export function activationStatusLabel(user: UserResponse) {
    return user.activationStatus === 'PENDING_ACTIVATION' ? '待激活' : '已激活'
}
```

Read the new permission:

```ts
const canResendActivation = canUseRbacButton(auth, rbacButtonCodes.user.resendActivation)
```

At the start of `renderActions`:

```ts
const activationActions = activationActionsFor(record, {
    resetPassword: canResetPassword,
    resendActivation: canResendActivation,
})
```

Replace the reset condition with `activationActions.resetPassword`. Add the resend action:

```tsx
if (activationActions.resendActivation) {
    actions.push(
        <Button icon={<LinkOutlined/>} key="resend-activation" size="small"
                onClick={() => void resendActivation(record)}>
            重新生成激活链接
        </Button>,
    )
}
```

- [ ] **Step 4: Add the status column and ephemeral modal state**

Import `Tag`, `ActivationLinkModal`, `reissueUserActivation`, and `ActivationDeliveryResponse`. Add the local-only wrapper and state:

```ts
type ActivationModalState = {
    title: string
    delivery: ActivationDeliveryResponse
}

const [activationModal, setActivationModal] = useState<ActivationModalState | null>(null)
```

Add a column after status:

```tsx
{
    title: '激活状态',
    dataIndex: 'activationStatus',
    width: 110,
    render: (_, record) => (
        <Tag color={record.activationStatus === 'PENDING_ACTIVATION' ? 'gold' : 'green'}>
            {activationStatusLabel(record)}
        </Tag>
    ),
},
```

In the create branch, capture the upgraded response:

```ts
const created = await createUser(request as CreateUserRequest)
setActivationModal({title: '账号已创建', delivery: created.activationDelivery})
```

Leave edit behavior unchanged. Add reissue:

```ts
async function resendActivation(user: UserResponse) {
    setSaving(true)
    try {
        const delivery = await reissueUserActivation(user.id)
        setActivationModal({title: '新激活链接已生成', delivery})
        message.success('已生成新的激活链接')
    } finally {
        setSaving(false)
    }
}
```

Render once near the drawers:

```tsx
<ActivationLinkModal
    onClose={() => setActivationModal(null)}
    title={activationModal?.title ?? '激活链接'}
    value={activationModal?.delivery ?? null}
/>
```

Closing sets the only URL-bearing state to null. Do not place the modal wrapper or delivery object in `usePageData`, auth state, URL state, or browser storage.

- [ ] **Step 5: Run focused tests, all RBAC frontend tests, and typecheck**

Run:

```bash
cd fe
bun run test -- src/modules/rbac/rbacService.test.ts src/modules/rbac/users/UserEditorDrawer.test.tsx src/modules/rbac/users/ActivationLinkModal.test.tsx src/modules/rbac/users/UserListPage.test.tsx
bun run typecheck
```

Expected: all listed tests pass and TypeScript exits 0.

- [ ] **Step 6: Commit Task 8**

```bash
git add fe/src/modules/rbac/users/UserListPage.tsx \
  fe/src/modules/rbac/users/UserListPage.test.tsx
git commit -m "feat(rbac): manage user activation links"
```

### Task 9: Add the public activation page and login success handoff

**Files:**
- Modify: `fe/src/modules/auth/authService.ts`
- Modify: `fe/src/modules/auth/authService.test.ts`
- Create: `fe/src/modules/auth/pages/ActivationPage.tsx`
- Create: `fe/src/modules/auth/pages/ActivationPage.test.tsx`
- Modify: `fe/src/modules/auth/pages/LoginPage.tsx`
- Create: `fe/src/modules/auth/pages/LoginPage.test.tsx`
- Modify: `fe/src/app/routes.tsx`
- Modify: `fe/src/app/routes.test.ts`

**Interfaces:**
- Consumes: existing `encryptPasswordForTransport`, `requestJson`, `AuthLayout`, and Task 4 POST endpoint.
- Produces: `completeAccountActivation(token: string, password: string): Promise<void>`, public `/activate`, fragment-clearing memory-only token handling, and `/login?activated=1` banner.

- [ ] **Step 1: Write the failing encrypted service test**

Import `completeAccountActivation` and add:

```ts
test('encrypts the activation password and never sends plaintext', async () => {
    const {requestJson} = await import('../../services/request')

    await completeAccountActivation('raw-token', 'new-password-123')

    expect(requestJson).toHaveBeenCalledWith('/api/auth/activation/complete', {
        method: 'POST',
        auth: false,
        body: {
            token: 'raw-token',
            encryptedPassword: 'encrypted:new-password-123',
            passwordKeyId: 'key-1',
        },
    })
    expect(vi.mocked(requestJson).mock.calls[0]?.[1]?.body).not.toHaveProperty('password')
})
```

- [ ] **Step 2: Write failing page and route tests**

Create `ActivationPage.test.tsx` with this setup and test tree:

```tsx
import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import {MemoryRouter, Route, Routes} from 'react-router'
import {completeAccountActivation} from '../authService'
import {ActivationPage} from './ActivationPage'

vi.mock('../authService', () => ({completeAccountActivation: vi.fn()}))

beforeEach(() => {
    vi.mocked(completeAccountActivation).mockResolvedValue(undefined)
})

afterEach(() => {
    window.history.replaceState({}, '', '/')
    vi.clearAllMocks()
    vi.restoreAllMocks()
})

function pageTree() {
    return (
        <App>
            <MemoryRouter initialEntries={['/activate']}>
                <Routes>
                    <Route path="/activate" element={<ActivationPage/>}/>
                    <Route path="/login" element={<div>login-destination</div>}/>
                </Routes>
            </MemoryRouter>
        </App>
    )
}
```

Add these cases:

```tsx
test('captures then clears the fragment without using browser storage', async () => {
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem')
    window.history.replaceState({}, '', '/activate#token=raw-token')

    render(pageTree())

    await waitFor(() => expect(window.location.hash).toBe(''))
    expect(screen.getByRole('heading', {name: '激活账号'})).toBeTruthy()
    expect(storageSpy).not.toHaveBeenCalled()
})

test('validates confirmation and redirects after encrypted activation', async () => {
    window.history.replaceState({}, '', '/activate#token=raw-token')
    const user = userEvent.setup()
    render(pageTree())

    await user.type(screen.getByLabelText('新密码'), 'new-password-123')
    await user.type(screen.getByLabelText('确认密码'), 'new-password-123')
    await user.click(screen.getByRole('button', {name: '完成激活'}))

    await waitFor(() => expect(completeAccountActivation)
        .toHaveBeenCalledWith('raw-token', 'new-password-123'))
    expect(await screen.findByText('login-destination')).toBeTruthy()
})

test('uses one uniform message for a rejected activation link', async () => {
    vi.mocked(completeAccountActivation).mockRejectedValueOnce(new Error('backend detail'))
    window.history.replaceState({}, '', '/activate#token=raw-token')
    const user = userEvent.setup()
    render(pageTree())
    await user.type(screen.getByLabelText('新密码'), 'new-password-123')
    await user.type(screen.getByLabelText('确认密码'), 'new-password-123')
    await user.click(screen.getByRole('button', {name: '完成激活'}))

    expect(await screen.findByText('激活链接无效或已过期，请联系管理员重新发送')).toBeTruthy()
    expect(screen.queryByText('backend detail')).toBeNull()
})

test('shows the uniform invalid-link message when the fragment is missing', () => {
    window.history.replaceState({}, '', '/activate')
    render(pageTree())
    expect(screen.getByText('激活链接无效或已过期，请联系管理员重新发送')).toBeTruthy()
    expect(screen.getByRole('button', {name: '完成激活'}).hasAttribute('disabled')).toBe(true)
})

test('rejects mismatched passwords before calling the backend', async () => {
    window.history.replaceState({}, '', '/activate#token=raw-token')
    const user = userEvent.setup()
    render(pageTree())
    await user.type(screen.getByLabelText('新密码'), 'new-password-123')
    await user.type(screen.getByLabelText('确认密码'), 'different-password')
    await user.click(screen.getByRole('button', {name: '完成激活'}))
    expect(await screen.findByText('两次输入的密码不一致')).toBeTruthy()
    expect(completeAccountActivation).not.toHaveBeenCalled()
})
```

Create the complete `LoginPage.test.tsx`:

```tsx
import {render, screen} from '@testing-library/react'
import {App} from 'antd'
import {MemoryRouter} from 'react-router'
import {describe, expect, test, vi} from 'vitest'
import {LoginPage} from './LoginPage'

vi.mock('../authStore', () => ({
    useAuth: () => ({
        bootstrapping: false,
        authenticated: false,
        roleCodes: [],
        menus: [],
        buttonCodes: [],
        permissionCodes: [],
        login: vi.fn(),
        register: vi.fn(),
        logout: vi.fn(),
        reload: vi.fn(),
        hasButton: vi.fn(() => false),
        hasAnyButton: vi.fn(() => false),
        hasPermission: vi.fn(() => false),
    }),
}))

describe('LoginPage', () => {
    test('shows the activation success handoff from the query flag', () => {
        render(
            <App>
                <MemoryRouter initialEntries={['/login?activated=1']}>
                    <LoginPage/>
                </MemoryRouter>
            </App>,
        )

        expect(screen.getByText('账号激活成功，请使用新密码登录')).toBeTruthy()
    })
})
```

Extend `routes.test.ts`:

```ts
test('registers account activation as a public auth-layout route', () => {
    expect(routesSource).toContain("path: '/activate'")
    expect(routesSource).toContain('<ActivationPage/>')
    const activationIndex = routesSource.indexOf("path: '/activate'")
    const protectedIndex = routesSource.indexOf("path: '/'")
    expect(activationIndex).toBeGreaterThan(-1)
    expect(activationIndex).toBeLessThan(protectedIndex)
})
```

- [ ] **Step 3: Run the tests to verify they fail**

Run:

```bash
cd fe
bun run test -- src/modules/auth/authService.test.ts src/modules/auth/pages/ActivationPage.test.tsx src/modules/auth/pages/LoginPage.test.tsx src/app/routes.test.ts
```

Expected: FAIL because the service, page, route, and success banner are absent.

- [ ] **Step 4: Add the encrypted activation request with stale-key retry**

Generalize the private helper without changing login/register behavior:

```ts
async function requestWithPasswordKeyRetry<T>(action: () => Promise<T>): Promise<T> {
    try {
        return await action()
    } catch (error) {
        if (!(error instanceof ApiRequestError) || error.code !== PASSWORD_KEY_INVALID_CODE) {
            throw error
        }
        clearPasswordKeyCache()
        return action()
    }
}
```

Add:

```ts
export async function completeAccountActivation(token: string, password: string) {
    return requestWithPasswordKeyRetry(async () => {
        const encryptedPassword = await encryptPasswordForTransport(password)
        return requestJson<void>('/api/auth/activation/complete', {
            method: 'POST',
            auth: false,
            body: {token, ...encryptedPassword},
        })
    })
}
```

The current `requestJson` continues to add `X-Client-Type` and the existing CSRF header for unsafe browser calls even when `auth: false` disables authentication refresh.

- [ ] **Step 5: Implement the memory-only activation page**

Create:

```tsx
import {Alert, Button, Card, Form, Input, Typography} from 'antd'
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {VisualBackdrop} from '../../../components/VisualBackdrop'
import {voidify} from '../../../utils/async'
import {completeAccountActivation} from '../authService'

type ActivationForm = {
    password: string
    confirmPassword: string
}

const INVALID_LINK_MESSAGE = '激活链接无效或已过期，请联系管理员重新发送'

export function ActivationPage() {
    const navigate = useNavigate()
    const [token, setToken] = useState(() => {
        const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
        return params.get('token') ?? ''
    })
    const [error, setError] = useState(token ? '' : INVALID_LINK_MESSAGE)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        if (window.location.hash) {
            window.history.replaceState(window.history.state, '',
                `${window.location.pathname}${window.location.search}`)
        }
    }, [])

    async function handleFinish(values: ActivationForm) {
        if (!token) {
            setError(INVALID_LINK_MESSAGE)
            return
        }
        if (values.password !== values.confirmPassword) {
            setError('两次输入的密码不一致')
            return
        }
        setSubmitting(true)
        setError('')
        try {
            await completeAccountActivation(token, values.password)
            setToken('')
            void navigate('/login?activated=1', {replace: true})
        } catch {
            setError(INVALID_LINK_MESSAGE)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="auth-page">
            <VisualBackdrop particleCount={24} variant="auth"/>
            <section className="auth-hero" aria-label="CyberMario 账号激活">
                <Card className="auth-card">
                    <Typography.Title level={2}>激活账号</Typography.Title>
                    <Typography.Paragraph type="secondary">
                        设置首个登录密码，完成后返回登录页。
                    </Typography.Paragraph>
                    {error && <Alert showIcon className="auth-alert" message={error} type="error"/>}
                    <Form<ActivationForm> layout="vertical" onFinish={voidify(handleFinish)} requiredMark={false}>
                        <Form.Item label="新密码" name="password"
                                   rules={[{required: true, message: '请输入新密码'},
                                       {min: 8, max: 128, message: '密码长度必须为 8–128 位'}]}>
                            <Input.Password autoComplete="new-password"/>
                        </Form.Item>
                        <Form.Item label="确认密码" name="confirmPassword"
                                   rules={[{required: true, message: '请再次输入新密码'}]}>
                            <Input.Password autoComplete="new-password"/>
                        </Form.Item>
                        <Button block disabled={!token} htmlType="submit" loading={submitting} type="primary">
                            完成激活
                        </Button>
                    </Form>
                </Card>
            </section>
        </div>
    )
}
```

The token exists only in React state. Refreshing after the Fragment is cleared intentionally loses it and requires an administrator to reissue.

- [ ] **Step 6: Register the public route and login banner**

Import `ActivationPage` in `routes.tsx` and insert before the protected root route:

```tsx
{
    path: '/activate',
    element: (
        <AuthLayout>
            <ActivationPage/>
        </AuthLayout>
    ),
},
```

In `LoginPage`, derive the flag from the existing `location`:

```ts
const activated = new URLSearchParams(location.search).get('activated') === '1'
```

Render above the error alert:

```tsx
{activated && (
    <Alert showIcon className="auth-alert"
           message="账号激活成功，请使用新密码登录" type="success"/>
)}
```

Do not call auth-store login/register methods during activation.

- [ ] **Step 7: Run focused auth tests, typecheck, and build**

Run:

```bash
cd fe
bun run test -- src/modules/auth/authService.test.ts src/modules/auth/pages/ActivationPage.test.tsx src/modules/auth/pages/LoginPage.test.tsx src/app/routes.test.ts
bun run typecheck
bun run build
```

Expected: all tests pass, typecheck exits 0, and Vite reports a successful production build.

- [ ] **Step 8: Commit Task 9**

```bash
git add fe/src/modules/auth/authService.ts \
  fe/src/modules/auth/authService.test.ts \
  fe/src/modules/auth/pages/ActivationPage.tsx \
  fe/src/modules/auth/pages/ActivationPage.test.tsx \
  fe/src/modules/auth/pages/LoginPage.tsx \
  fe/src/modules/auth/pages/LoginPage.test.tsx \
  fe/src/app/routes.tsx \
  fe/src/app/routes.test.ts
git commit -m "feat(auth): add account activation page"
```

### Task 10: Run security, compatibility, and full regression verification

**Files:**
- Verify only: all files changed in Tasks 1–9.

**Interfaces:**
- Consumes: all prior task deliverables.
- Produces: evidence that the approved OTT scope is complete without starting the application.

- [ ] **Step 1: Run all focused backend activation and RBAC regression tests**

Run:

```bash
cd be
./mvnw -Dtest=RbacAccountActivationSchemaMigrationTests,RbacOttConfigurationTests,MockActivationLinkDeliveryTests,RbacReactiveOneTimeTokenServiceTests,ActivationFailureRateLimiterTests,RbacAccountActivationApplicationTests,RbacAdminAccountActivationTests,RbacAuthApplicationTests,RbacAdminBootstrapTests,RbacDtoConverterTests,RbacUserServiceValidationTests,RbacAdminConsoleResourceProviderTests,RbacPublicApiPolicyTests,RbacSecurityConfigCsrfTests,GlobalExceptionHandlerTests,AuthControllerBrowserCookieTests test
```

Expected: exit 0 with zero failures and zero errors.

- [ ] **Step 2: Run all focused frontend activation/RBAC tests**

Run:

```bash
cd fe
bun run test -- src/modules/rbac/rbacService.test.ts src/modules/rbac/users/UserEditorDrawer.test.tsx src/modules/rbac/users/ActivationLinkModal.test.tsx src/modules/rbac/users/UserListPage.test.tsx src/modules/auth/authService.test.ts src/modules/auth/pages/ActivationPage.test.tsx src/modules/auth/pages/LoginPage.test.tsx src/app/routes.test.ts
```

Expected: exit 0 and all listed test files pass.

- [ ] **Step 3: Run complete backend tests**

Run:

```bash
cd be
./mvnw test
```

Expected: Maven `BUILD SUCCESS` with no test failures. If an unrelated external-integration test requires unavailable credentials or services, record the exact test and error; do not weaken or delete it.

- [ ] **Step 4: Run complete frontend quality gates**

Run:

```bash
cd fe
bun run test
bun run lint
bun run typecheck
bun run build
```

Expected: all four commands exit 0; Vite produces `dist` successfully.

- [ ] **Step 5: Perform the security and scope scans**

Run:

```bash
cd ..
find be/src/main/resources/db/migration -maxdepth 1 -name 'V53__*' -print
rg -n -i "passkey|webauthn" \
  be/src/main/java/top/egon/mario/rbac \
  fe/src/modules/auth \
  fe/src/modules/rbac
rg -n "mockActivationUrl|tokenValue|getTokenValue" \
  be/src/main/java/top/egon/mario/rbac
rg -n "localStorage|sessionStorage|document\.cookie" \
  fe/src/modules/auth/pages/ActivationPage.tsx \
  fe/src/modules/rbac/users/ActivationLinkModal.tsx
git status --short
git diff --check
```

Expected:

- `find` prints exactly `be/src/main/resources/db/migration/V53__add_rbac_account_activation_ott.sql`.
- Passkey/WebAuthn scan returns no matches in changed RBAC/auth scope.
- Backend raw-token scan finds only in-memory construction, delivery response, and Spring token access; no logging/audit statement contains the raw value or complete URL.
- Browser-storage scan returns no matches.
- `git diff --check` prints nothing.
- `git status --short` contains no unexpected files; all implementation tasks are already committed.

- [ ] **Step 6: Review acceptance criteria against live behavior**

Confirm from test output and code inspection:

```text
[x] Admin create requires email and accepts neither initialPassword nor status in the typed contract.
[x] New admin-created users are ENABLED, pending, password-expired, and receive one hashed 24h OTT.
[x] Create and reissue responses alone expose the Mock Fragment URL.
[x] Reissue invalidates the previous token.
[x] Pending users cannot login or use admin password reset.
[x] Email change, disable, lock, and delete revoke a pending token; re-enable does not issue one.
[x] Public registration and bootstrap users are activated immediately.
[x] Activation is transactionally atomic and concurrent consumption has one winner.
[x] Browser activation observes CSRF, rolling Redis failures are limited, and the 21st attempt is 429.
[x] Activation success creates no JWT, refresh token, auth cookie, browser storage, or authenticated session.
[x] Frontend clears the Fragment, encrypts the first password, and redirects to /login?activated=1.
[x] No Passkey/WebAuthn implementation or dependency was introduced.
```

Do not start the backend or frontend server. If every check passes, implementation is ready for the user's runtime acceptance test.
