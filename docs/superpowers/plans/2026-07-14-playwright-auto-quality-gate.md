# Playwright Auto Quality Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a root-level Playwright quality project that runs real registration and login regression tests locally and in CI against the Spring `auto` profile, pre-provisioned PostgreSQL/pgvector, and Redis.

**Architecture:** A Bun-based `quality/` runner validates and resets approved Auto infrastructure, then lets Playwright manage dedicated backend and frontend processes. The backend `auto` profile reuses the production PostgreSQL Flyway chain and refuses unsafe database, schema, Redis, or Cookie targets. Auth Page Objects and fixtures keep scenarios independent, while one stable CI `Quality Gate` aggregates backend, frontend, and browser checks.

**Tech Stack:** Java 21, Spring Boot 3.5, PostgreSQL/pgvector, Redis, Bun 1.3.14, TypeScript, Playwright 1.61.1, GitHub Actions

---

## Scope And Execution Constraints

- Execute in an isolated worktree created through `superpowers:using-git-worktrees`.
- Do not modify any existing Flyway migration.
- Do not provision PostgreSQL, Redis, or MQ from Playwright or CI.
- Do not start or leave project processes running outside the explicit Playwright verification commands.
- Preserve unrelated staged and unstaged files in the main worktree.
- Commit every task separately with the exact commit boundary shown below.
- The initial browser suite is Auth-only. Do not add speculative tests for other domains.

## File Responsibility Map

**Backend Auto boundary**

- Create `be/src/main/resources/application-auto.yaml`: Auto PostgreSQL, Redis, schema, ports, JWT, admin bootstrap, and browser-Cookie overrides.
- Create `be/src/main/java/top/egon/mario/config/AutoEnvironmentGuard.java`: fail-closed startup validation for destructive Auto targets.
- Create `be/src/main/resources/META-INF/spring.factories`: register the guard before DataSource and Flyway initialization.
- Create `be/src/test/java/top/egon/mario/config/AutoEnvironmentGuardTests.java`: pure unit coverage for every guard invariant.

**Quality infrastructure**

- Create `quality/package.json`, `quality/bun.lock`, and `quality/tsconfig.json`: independent Bun/TypeScript toolchain.
- Create `quality/.env.example` and `quality/.gitignore`: local configuration contract and output exclusions.
- Create `quality/support/autoEnvironment.ts`: parse, validate, and redact the Auto environment.
- Create `quality/support/autoState.ts`: reset/drop the PostgreSQL schema and flush the approved Redis database.
- Create `quality/support/laneLock.ts`: reject concurrent local processes targeting one Auto lane.
- Create `quality/scripts/run-quality.ts`: prepare state, launch Playwright, forward signals, and clean state in `finally`.
- Create `quality/scripts/start-server.ts`: start one managed application process while teeing output to a process log.

**Playwright Auth suite**

- Create `quality/playwright.config.ts`: Chromium, reporters, diagnostics, and backend/frontend `webServer` definitions.
- Create `quality/models/api.ts` and `quality/models/test-identity.ts`: response envelope and safe unique identity model.
- Create `quality/pages/RegisterPage.ts`, `quality/pages/LoginPage.ts`, and `quality/pages/AdminShell.ts`: accessible user-level page operations only.
- Create `quality/fixtures/auth.fixture.ts`: identity factory and UI-only registration setup.
- Create `quality/tests/auth/register.spec.ts`, `quality/tests/auth/login.spec.ts`, and `quality/tests/auth/session.spec.ts`: independently reported Auth behavior.

**Gate and documentation**

- Create `.github/workflows/quality-gate.yml`: serialized Auto lane and stable aggregate gate.
- Create `quality/README.md`: prerequisites, commands, diagnostics, and cleanup rules.
- Modify `README.md`: link the repository-level quality workflow.

## Preflight Baseline

Before Task 1, create the implementation worktree and record the unchanged
baseline:

```bash
git status --short --branch
cd be
./mvnw -Dmaven.build.cache.enabled=false test
cd ../fe
bun install --frozen-lockfile
bun run lint
bun run typecheck
bun run test
bun run build
```

Expected: the worktree is clean and all existing checks pass. If an existing
check fails before implementation, record the exact command and failure; do
not expand this plan to repair unrelated baseline debt without user approval.

### Task 1: Add The Fail-Closed Backend Auto Profile

**Files:**
- Create: `be/src/test/java/top/egon/mario/config/AutoEnvironmentGuardTests.java`
- Create: `be/src/main/java/top/egon/mario/config/AutoEnvironmentGuard.java`
- Create: `be/src/main/resources/application-auto.yaml`
- Create: `be/src/main/resources/META-INF/spring.factories`

- [ ] **Step 1: Write the failing guard tests**

Create `AutoEnvironmentGuardTests.java`:

```java
package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoEnvironmentGuardTests {

    @Test
    void registersTheGuardAsAnEnvironmentPostProcessor() throws IOException {
        var properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("META-INF/spring.factories"));

        assertThat(properties.getProperty("org.springframework.boot.env.EnvironmentPostProcessor"))
                .contains("top.egon.mario.config.AutoEnvironmentGuard");
    }

    @Test
    void acceptsDedicatedAutoTargets() {
        assertThatCode(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "auto_local",
                15,
                false
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPostgreSqlDatasource() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:h2:mem:auto",
                "auto_local",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void rejectsDatabaseWithoutAutoBoundary() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario",
                "auto_local",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auto database");
    }

    @Test
    void rejectsPublicOrUnscopedSchema() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "public",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auto schema");

        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "quality",
                15,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auto schema");
    }

    @Test
    void rejectsDevelopmentRedisDatabase() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "auto_local",
                1,
                false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis database");
    }

    @Test
    void rejectsSecureCookieOnHttpAutoOrigin() {
        assertThatThrownBy(() -> AutoEnvironmentGuard.validate(
                "jdbc:postgresql://localhost:5432/cyber_mario_auto",
                "auto_local",
                15,
                true
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secure");
    }
}
```

- [ ] **Step 2: Run the focused test and verify the missing guard fails**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=AutoEnvironmentGuardTests test
```

Expected: FAIL during test compilation because `AutoEnvironmentGuard` does not exist.

- [ ] **Step 3: Implement the minimal startup guard**

Create `AutoEnvironmentGuard.java`:

```java
package top.egon.mario.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rejects unsafe Auto targets before DataSource and Flyway initialization.
 */
public class AutoEnvironmentGuard implements EnvironmentPostProcessor, Ordered {

    private static final Pattern AUTO_DATABASE =
            Pattern.compile("(^|[_-])auto([_-]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_SCHEMA = Pattern.compile("^auto_[a-z0-9_]+$");

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        if (!environment.acceptsProfiles(Profiles.of("auto"))) {
            return;
        }
        validate(
                environment.getProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.flyway.default-schema"),
                environment.getProperty("spring.data.redis.database", Integer.class, 1),
                environment.getProperty("mario.security.browser-cookie.secure", Boolean.class, true)
        );
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void validate(String jdbcUrl, String schema, int redisDatabase, boolean secureCookie) {
        String databaseName = databaseName(jdbcUrl);
        if (!AUTO_DATABASE.matcher(databaseName).find()) {
            throw new IllegalStateException("Auto profile requires a dedicated Auto database");
        }
        if (!StringUtils.hasText(schema)
                || !AUTO_SCHEMA.matcher(schema.toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalStateException("Auto profile requires an auto_* Auto schema");
        }
        if (redisDatabase <= 1) {
            throw new IllegalStateException("Auto profile requires a Redis database outside the development lane");
        }
        if (secureCookie) {
            throw new IllegalStateException("Auto HTTP origin cannot use Secure browser cookies");
        }
    }

    private static String databaseName(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Auto profile requires a PostgreSQL datasource");
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || path.length() == 1) {
                throw new IllegalStateException("Auto PostgreSQL URL must include a database name");
            }
            return path.substring(1).split("/", 2)[0];
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Auto PostgreSQL URL is invalid", exception);
        }
    }
}
```

- [ ] **Step 4: Register the early guard**

Create `be/src/main/resources/META-INF/spring.factories`:

```properties
org.springframework.boot.env.EnvironmentPostProcessor=top.egon.mario.config.AutoEnvironmentGuard
```

- [ ] **Step 5: Add the production-like Auto Spring configuration**

Create `application-auto.yaml`:

```yaml
spring:
  config:
    activate:
      on-profile: auto
  datasource:
    url: ${AUTO_DB_URL:jdbc:postgresql://localhost:5432/cyber_mario_auto}
    username: ${AUTO_DB_USERNAME:postgres}
    password: ${AUTO_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: CyberMarioAutoHikariPool
      maximum-pool-size: ${AUTO_DB_POOL_MAX_SIZE:8}
      minimum-idle: ${AUTO_DB_POOL_MIN_IDLE:1}
      connection-init-sql: SET search_path TO ${AUTO_DB_SCHEMA:auto_local}, public
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: ${AUTO_DB_SCHEMA:auto_local}
  flyway:
    enabled: true
    locations: classpath:db/migration,classpath:db/postgresql
    default-schema: ${AUTO_DB_SCHEMA:auto_local}
    schemas: ${AUTO_DB_SCHEMA:auto_local}
    create-schemas: true
    validate-on-migrate: true
    clean-disabled: true
  data:
    redis:
      host: ${AUTO_REDIS_HOST:localhost}
      port: ${AUTO_REDIS_PORT:6379}
      password: ${AUTO_REDIS_PASSWORD:}
      database: ${AUTO_REDIS_DATABASE:15}
      timeout: ${AUTO_REDIS_TIMEOUT:3s}

server:
  port: ${AUTO_BACKEND_PORT:28081}

management:
  health:
    redis:
      enabled: true

mario:
  security:
    jwt:
      issuer: CyberMarioAuto
      secret: ${AUTO_JWT_SECRET}
      access-token-ttl: PT30M
      refresh-token-ttl: PT2H
    browser-cookie:
      enabled: true
      secure: false
  rbac:
    resource-sync:
      enabled: true
    bootstrap:
      admin:
        enabled: true
        password: ${AUTO_ADMIN_PASSWORD}
        require-password-change: false
  agent:
    memory:
      checkpointer:
        enabled: false
```

The PostgreSQL checkpointer is disabled because it opens a separate connection
that does not inherit the Auto Hikari search path, and Auth scenarios do not
use it. Do not add a SQLite dependency or a second Flyway migration tree.

- [ ] **Step 6: Run focused and configuration regression tests**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false -Dtest=AutoEnvironmentGuardTests,RbacSecurityConfigPasswordEncoderTests test
```

Expected: PASS. The regular test profile remains H2-based and the `auto` guard is exercised only through its pure validation method.

- [ ] **Step 7: Commit Task 1**

```bash
git add be/src/main/resources/application-auto.yaml be/src/main/resources/META-INF/spring.factories be/src/main/java/top/egon/mario/config/AutoEnvironmentGuard.java be/src/test/java/top/egon/mario/config/AutoEnvironmentGuardTests.java
git commit -m "test: add guarded auto environment profile"
```

### Task 2: Bootstrap The Quality Project And Environment Contract

**Files:**
- Create: `quality/package.json`
- Create: `quality/bun.lock`
- Create: `quality/tsconfig.json`
- Create: `quality/.env.example`
- Create: `quality/.gitignore`
- Create: `quality/support/autoEnvironment.test.ts`
- Create: `quality/support/autoEnvironment.ts`

- [ ] **Step 1: Create the Bun project manifest and compiler configuration**

Create `quality/package.json`:

```json
{
  "name": "@cybermario/quality",
  "private": true,
  "version": "0.0.0",
  "packageManager": "bun@1.3.14",
  "type": "module",
  "scripts": {
    "test": "bun test support scripts models",
    "typecheck": "bun run --bun tsc --noEmit",
    "test:auth": "bun run scripts/run-quality.ts auth",
    "test:auth:headed": "bun run scripts/run-quality.ts auth --headed",
    "test:auth:ui": "bun run scripts/run-quality.ts auth --ui",
    "test:auth:debug": "bun run scripts/run-quality.ts auth --debug",
    "report": "bunx playwright show-report playwright-report"
  },
  "dependencies": {
    "pg": "8.22.0",
    "redis": "6.1.0"
  },
  "devDependencies": {
    "@playwright/test": "1.61.1",
    "@types/bun": "1.3.14",
    "@types/pg": "8.20.0",
    "typescript": "~6.0.2"
  }
}
```

Create `quality/tsconfig.json`:

```json
{
  "compilerOptions": {
    "allowImportingTsExtensions": true,
    "forceConsistentCasingInFileNames": true,
    "module": "Preserve",
    "moduleResolution": "Bundler",
    "noEmit": true,
    "noFallthroughCasesInSwitch": true,
    "noImplicitOverride": true,
    "noUncheckedIndexedAccess": true,
    "strict": true,
    "target": "ES2023",
    "types": ["bun", "node"]
  },
  "include": ["**/*.ts"]
}
```

Create `quality/.gitignore`:

```gitignore
.env
.runtime/
artifacts/
node_modules/
playwright-report/
test-results/
```

Create `quality/.env.example`:

```dotenv
AUTO_CLEANUP_ALLOWED=true
AUTO_DB_URL=jdbc:postgresql://localhost:5432/cyber_mario_auto
AUTO_DB_USERNAME=postgres
AUTO_DB_PASSWORD=replace-me
AUTO_DB_SCHEMA=auto_local
AUTO_REDIS_HOST=localhost
AUTO_REDIS_PORT=6379
AUTO_REDIS_PASSWORD=
AUTO_REDIS_DATABASE=15
AUTO_JWT_SECRET=replace-with-an-auto-only-secret-at-least-32-bytes
AUTO_ADMIN_PASSWORD=replace-with-an-auto-only-admin-password
AUTO_BACKEND_PORT=28081
AUTO_FRONTEND_PORT=5174
```

- [ ] **Step 2: Install dependencies and generate the committed lockfile**

Run:

```bash
cd quality
bun install
```

Expected: `bun.lock` is created and the install exits 0.

- [ ] **Step 3: Write failing environment-contract tests**

Create `support/autoEnvironment.test.ts`:

```typescript
import {describe, expect, test} from 'bun:test'
import {loadAutoEnvironment, safeEnvironmentSummary} from './autoEnvironment'

const safeSource = {
    AUTO_CLEANUP_ALLOWED: 'true',
    AUTO_DB_URL: 'jdbc:postgresql://localhost:5432/cyber_mario_auto',
    AUTO_DB_USERNAME: 'postgres',
    AUTO_DB_PASSWORD: 'database-secret',
    AUTO_DB_SCHEMA: 'auto_local',
    AUTO_REDIS_HOST: 'localhost',
    AUTO_REDIS_PORT: '6379',
    AUTO_REDIS_PASSWORD: 'redis-secret',
    AUTO_REDIS_DATABASE: '15',
    AUTO_JWT_SECRET: 'auto-jwt-secret-that-is-at-least-32-bytes',
    AUTO_ADMIN_PASSWORD: 'AutoAdmin#2026',
    AUTO_BACKEND_PORT: '28081',
    AUTO_FRONTEND_PORT: '5174',
}

describe('loadAutoEnvironment', () => {
    test('accepts an explicit Auto lane', () => {
        const environment = loadAutoEnvironment(safeSource)

        expect(environment.postgres.database).toBe('cyber_mario_auto')
        expect(environment.postgres.schema).toBe('auto_local')
        expect(environment.redis.database).toBe(15)
        expect(environment.backendPort).toBe(28081)
        expect(environment.frontendPort).toBe(5174)
        expect(environment.runId).toMatch(/^quality-[a-z0-9-]+$/)
    })

    test('rejects disabled destructive cleanup', () => {
        expect(() => loadAutoEnvironment({...safeSource, AUTO_CLEANUP_ALLOWED: 'false'}))
            .toThrow('AUTO_CLEANUP_ALLOWED=true')
    })

    test('rejects a non-Auto database', () => {
        expect(() => loadAutoEnvironment({
            ...safeSource,
            AUTO_DB_URL: 'jdbc:postgresql://localhost:5432/cyber_mario',
        })).toThrow('Auto database')
    })

    test('rejects public schema and the development Redis lane', () => {
        expect(() => loadAutoEnvironment({...safeSource, AUTO_DB_SCHEMA: 'public'}))
            .toThrow('auto_* schema')
        expect(() => loadAutoEnvironment({...safeSource, AUTO_REDIS_DATABASE: '1'}))
            .toThrow('Auto Redis database')
    })

    test('redacts every secret from diagnostics', () => {
        const summary = JSON.stringify(safeEnvironmentSummary(loadAutoEnvironment(safeSource)))

        expect(summary).not.toContain('database-secret')
        expect(summary).not.toContain('redis-secret')
        expect(summary).not.toContain('auto-jwt-secret')
        expect(summary).not.toContain('AutoAdmin#2026')
    })
})
```

- [ ] **Step 4: Run the unit test and verify the missing module fails**

Run:

```bash
cd quality
bun test support/autoEnvironment.test.ts
```

Expected: FAIL because `support/autoEnvironment.ts` does not exist.

- [ ] **Step 5: Implement strict environment parsing and redaction**

Create `support/autoEnvironment.ts`:

```typescript
import {randomUUID} from 'node:crypto'

const AUTO_DATABASE = /(^|[_-])auto([_-]|$)/i
const AUTO_SCHEMA = /^auto_[a-z0-9_]+$/

export type AutoEnvironment = {
    runId: string
    ci: boolean
    backendPort: number
    frontendPort: number
    postgres: {
        jdbcUrl: string
        database: string
        username: string
        password: string
        schema: string
    }
    redis: {
        host: string
        port: number
        password: string
        database: number
    }
    jwtSecret: string
    adminPassword: string
}

export function loadAutoEnvironment(source: NodeJS.ProcessEnv = process.env): AutoEnvironment {
    if (source.AUTO_CLEANUP_ALLOWED !== 'true') {
        throw new Error('Auto runner requires AUTO_CLEANUP_ALLOWED=true')
    }

    const jdbcUrl = required(source, 'AUTO_DB_URL')
    if (!jdbcUrl.startsWith('jdbc:postgresql://')) {
        throw new Error('AUTO_DB_URL must be a PostgreSQL JDBC URL')
    }
    const databaseUrl = new URL(jdbcUrl.substring('jdbc:'.length))
    const database = databaseUrl.pathname.replace(/^\//, '')
    if (!AUTO_DATABASE.test(database)) {
        throw new Error('AUTO_DB_URL must target a dedicated Auto database')
    }

    const schema = required(source, 'AUTO_DB_SCHEMA').toLowerCase()
    if (!AUTO_SCHEMA.test(schema)) {
        throw new Error('AUTO_DB_SCHEMA must be an auto_* schema')
    }

    const redisDatabase = integer(source.AUTO_REDIS_DATABASE, 'AUTO_REDIS_DATABASE')
    if (redisDatabase <= 1) {
        throw new Error('AUTO_REDIS_DATABASE must use a dedicated Auto Redis database')
    }

    const jwtSecret = required(source, 'AUTO_JWT_SECRET')
    if (jwtSecret.length < 32) {
        throw new Error('AUTO_JWT_SECRET must contain at least 32 characters')
    }

    return {
        runId: source.QUALITY_RUN_ID || createRunId(),
        ci: source.CI === 'true',
        backendPort: integer(source.AUTO_BACKEND_PORT || '28081', 'AUTO_BACKEND_PORT'),
        frontendPort: integer(source.AUTO_FRONTEND_PORT || '5174', 'AUTO_FRONTEND_PORT'),
        postgres: {
            jdbcUrl,
            database,
            username: required(source, 'AUTO_DB_USERNAME'),
            password: required(source, 'AUTO_DB_PASSWORD'),
            schema,
        },
        redis: {
            host: required(source, 'AUTO_REDIS_HOST'),
            port: integer(source.AUTO_REDIS_PORT || '6379', 'AUTO_REDIS_PORT'),
            password: source.AUTO_REDIS_PASSWORD || '',
            database: redisDatabase,
        },
        jwtSecret,
        adminPassword: required(source, 'AUTO_ADMIN_PASSWORD'),
    }
}

export function safeEnvironmentSummary(environment: AutoEnvironment) {
    return {
        runId: environment.runId,
        ci: environment.ci,
        backendPort: environment.backendPort,
        frontendPort: environment.frontendPort,
        postgres: {
            database: environment.postgres.database,
            schema: environment.postgres.schema,
        },
        redis: {
            host: environment.redis.host,
            port: environment.redis.port,
            database: environment.redis.database,
        },
    }
}

function required(source: NodeJS.ProcessEnv, key: string) {
    const value = source[key]?.trim()
    if (!value) {
        throw new Error(`Missing required environment variable: ${key}`)
    }
    return value
}

function integer(value: string | undefined, key: string) {
    const parsed = Number(value)
    if (!Number.isInteger(parsed) || parsed <= 0) {
        throw new Error(`${key} must be a positive integer`)
    }
    return parsed
}

function createRunId() {
    return `quality-${Date.now().toString(36)}-${randomUUID().slice(0, 8)}`
}
```

- [ ] **Step 6: Run unit tests and typecheck**

Run:

```bash
cd quality
bun test support/autoEnvironment.test.ts
bun run typecheck
```

Expected: both commands PASS.

- [ ] **Step 7: Commit Task 2**

```bash
git add quality/package.json quality/bun.lock quality/tsconfig.json quality/.env.example quality/.gitignore quality/support/autoEnvironment.ts quality/support/autoEnvironment.test.ts
git commit -m "test: bootstrap Playwright quality project"
```

### Task 3: Implement Auto State Reset, Lane Locking, And The Quality Runner

**Files:**
- Create: `quality/support/autoState.test.ts`
- Create: `quality/support/autoState.ts`
- Create: `quality/support/laneLock.test.ts`
- Create: `quality/support/laneLock.ts`
- Create: `quality/scripts/run-quality.test.ts`
- Create: `quality/scripts/run-quality.ts`

- [ ] **Step 1: Write failing lifecycle and argument tests**

Create `support/autoState.test.ts`:

```typescript
import {describe, expect, test} from 'bun:test'
import {cleanupAutoState, prepareAutoState, type AutoStateAdapter} from './autoState'

describe('Auto state lifecycle', () => {
    test('prepares PostgreSQL before Redis', async () => {
        const calls: string[] = []
        const adapter: AutoStateAdapter = {
            resetPostgres: async () => {
                calls.push('postgres:reset')
            },
            dropPostgres: async () => {
                calls.push('postgres:drop')
            },
            flushRedis: async () => {
                calls.push('redis:flush')
            },
        }

        await prepareAutoState(adapter)

        expect(calls).toEqual(['postgres:reset', 'redis:flush'])
    })

    test('attempts Redis cleanup when PostgreSQL cleanup fails', async () => {
        const calls: string[] = []
        const adapter: AutoStateAdapter = {
            resetPostgres: async () => undefined,
            dropPostgres: async () => {
                calls.push('postgres:drop')
                throw new Error('postgres cleanup failed')
            },
            flushRedis: async () => {
                calls.push('redis:flush')
            },
        }

        await expect(cleanupAutoState(adapter)).rejects.toThrow('Auto state cleanup failed')
        expect(calls).toEqual(['postgres:drop', 'redis:flush'])
    })
})
```

Create `support/laneLock.test.ts`:

```typescript
import {expect, test} from 'bun:test'
import {mkdtemp, rm, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import path from 'node:path'
import {acquireLaneLock} from './laneLock'

test('rejects a second owner of the same Auto lane', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'cybermario-quality-'))
    const first = await acquireLaneLock(directory, 'auto_local-redis-15')

    try {
        await expect(acquireLaneLock(directory, 'auto_local-redis-15'))
            .rejects.toThrow('already in use')
    } finally {
        await first.release()
        await rm(directory, {recursive: true, force: true})
    }
})

test('reclaims a lock left by a dead process', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'cybermario-quality-'))
    const lane = 'auto_local-redis-15'
    await writeFile(path.join(directory, `${lane}.lock`), '999999')

    const lock = await acquireLaneLock(directory, lane)

    await lock.release()
    await rm(directory, {recursive: true, force: true})
})
```

Create `scripts/run-quality.test.ts`:

```typescript
import {expect, test} from 'bun:test'
import {playwrightArguments} from './run-quality'

test('limits the initial runner to the Auth suite and forwards Playwright flags', () => {
    expect(playwrightArguments(['auth', '--headed']))
        .toEqual(['test', 'tests/auth', '--headed'])
    expect(playwrightArguments(['auth', 'tests/auth/register.spec.ts']))
        .toEqual(['test', 'tests/auth/register.spec.ts'])
    expect(() => playwrightArguments(['regression']))
        .toThrow('Only the auth suite is available')
})
```

- [ ] **Step 2: Run the focused tests and verify missing modules fail**

Run:

```bash
cd quality
bun test support/autoState.test.ts support/laneLock.test.ts scripts/run-quality.test.ts
```

Expected: FAIL because the lifecycle modules do not exist.

- [ ] **Step 3: Implement real PostgreSQL and Redis state adapters**

Create `support/autoState.ts`:

```typescript
import {Client, type ClientConfig} from 'pg'
import {createClient} from 'redis'
import type {AutoEnvironment} from './autoEnvironment'

export type AutoStateAdapter = {
    resetPostgres: () => Promise<void>
    dropPostgres: () => Promise<void>
    flushRedis: () => Promise<void>
}

export async function prepareAutoState(adapter: AutoStateAdapter) {
    await adapter.resetPostgres()
    await adapter.flushRedis()
}

export async function cleanupAutoState(adapter: AutoStateAdapter) {
    const results = await Promise.allSettled([
        adapter.dropPostgres(),
        adapter.flushRedis(),
    ])
    const failures = results
        .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
        .map((result) => result.reason)
    if (failures.length > 0) {
        throw new AggregateError(failures, 'Auto state cleanup failed')
    }
}

export function realAutoStateAdapter(environment: AutoEnvironment): AutoStateAdapter {
    return {
        resetPostgres: () => withPostgres(environment, async (client) => {
            const schema = quoteIdentifier(environment.postgres.schema)
            await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`)
            await client.query(`CREATE SCHEMA ${schema}`)
        }),
        dropPostgres: () => withPostgres(environment, async (client) => {
            const schema = quoteIdentifier(environment.postgres.schema)
            await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`)
        }),
        flushRedis: () => flushRedis(environment),
    }
}

async function withPostgres(
    environment: AutoEnvironment,
    action: (client: Client) => Promise<void>,
) {
    const client = new Client(postgresClientConfig(environment))
    await client.connect()
    try {
        const result = await client.query<{current_database: string}>('SELECT current_database()')
        if (result.rows[0]?.current_database !== environment.postgres.database) {
            throw new Error('Connected PostgreSQL database does not match the validated Auto target')
        }
        await action(client)
    } finally {
        await client.end()
    }
}

function postgresClientConfig(environment: AutoEnvironment): ClientConfig {
    const url = new URL(environment.postgres.jdbcUrl.substring('jdbc:'.length))
    return {
        host: url.hostname,
        port: Number(url.port || '5432'),
        database: environment.postgres.database,
        user: environment.postgres.username,
        password: environment.postgres.password,
        ssl: url.searchParams.get('sslmode') === 'require'
            ? {rejectUnauthorized: false}
            : undefined,
    }
}

async function flushRedis(environment: AutoEnvironment) {
    const client = createClient({
        socket: {
            host: environment.redis.host,
            port: environment.redis.port,
        },
        password: environment.redis.password || undefined,
        database: environment.redis.database,
    })
    client.on('error', () => undefined)
    await client.connect()
    try {
        await client.flushDb()
    } finally {
        await client.quit()
    }
}

function quoteIdentifier(identifier: string) {
    return `"${identifier.replaceAll('"', '""')}"`
}
```

- [ ] **Step 4: Implement the exclusive local lane lock**

Create `support/laneLock.ts`:

```typescript
import {mkdir, open, readFile, rm, type FileHandle} from 'node:fs/promises'
import path from 'node:path'

export type LaneLock = {
    release: () => Promise<void>
}

export async function acquireLaneLock(directory: string, lane: string): Promise<LaneLock> {
    await mkdir(directory, {recursive: true})
    const lockPath = path.join(directory, `${lane}.lock`)
    let handle: FileHandle
    try {
        handle = await open(lockPath, 'wx')
    } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
            throw error
        }
        if (await hasLiveOwner(lockPath)) {
            throw new Error(`Auto lane ${lane} is already in use`)
        }
        await rm(lockPath, {force: true})
        try {
            handle = await open(lockPath, 'wx')
        } catch (retryError) {
            if ((retryError as NodeJS.ErrnoException).code === 'EEXIST') {
                throw new Error(`Auto lane ${lane} is already in use`)
            }
            throw retryError
        }
    }
    await handle.writeFile(String(process.pid))

    return {
        release: async () => {
            await handle.close()
            await rm(lockPath, {force: true})
        },
    }
}

async function hasLiveOwner(lockPath: string) {
    const pid = Number((await readFile(lockPath, 'utf8')).trim())
    if (!Number.isInteger(pid) || pid <= 0) {
        return false
    }
    try {
        process.kill(pid, 0)
        return true
    } catch (error) {
        return (error as NodeJS.ErrnoException).code !== 'ESRCH'
    }
}
```

- [ ] **Step 5: Implement the cleanup-first quality runner**

Create `scripts/run-quality.ts`:

```typescript
import {mkdir, rm} from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {loadAutoEnvironment, safeEnvironmentSummary} from '../support/autoEnvironment'
import {cleanupAutoState, prepareAutoState, realAutoStateAdapter} from '../support/autoState'
import {acquireLaneLock} from '../support/laneLock'

const qualityRoot = fileURLToPath(new URL('..', import.meta.url))

export function playwrightArguments(arguments_: string[]) {
    const [suite, ...flags] = arguments_
    if (suite !== 'auth') {
        throw new Error('Only the auth suite is available in the initial quality gate')
    }
    const hasFileFilter = flags.some((argument) => argument.startsWith('tests/'))
    return ['test', ...(hasFileFilter ? [] : ['tests/auth']), ...flags]
}

export async function run(arguments_: string[] = process.argv.slice(2)) {
    const environment = loadAutoEnvironment()
    const lane = `${environment.postgres.schema}-redis-${environment.redis.database}`
    const lock = await acquireLaneLock(path.join(qualityRoot, '.runtime'), lane)
    const adapter = realAutoStateAdapter(environment)
    let child: ReturnType<typeof Bun.spawn> | undefined
    let result = 1
    let interrupted = false

    const interrupt = () => {
        interrupted = true
        child?.kill()
    }
    process.once('SIGINT', interrupt)
    process.once('SIGTERM', interrupt)

    try {
        await prepareOutputDirectories()
        console.info('Auto environment:', safeEnvironmentSummary(environment))
        await prepareAutoState(adapter)

        child = Bun.spawn(
            ['bunx', 'playwright', ...playwrightArguments(arguments_)],
            {
                cwd: qualityRoot,
                env: {
                    ...process.env,
                    QUALITY_RUN_ID: environment.runId,
                    AUTO_DB_URL: environment.postgres.jdbcUrl,
                    AUTO_DB_USERNAME: environment.postgres.username,
                    AUTO_DB_PASSWORD: environment.postgres.password,
                    AUTO_DB_SCHEMA: environment.postgres.schema,
                    AUTO_REDIS_HOST: environment.redis.host,
                    AUTO_REDIS_PORT: String(environment.redis.port),
                    AUTO_REDIS_PASSWORD: environment.redis.password,
                    AUTO_REDIS_DATABASE: String(environment.redis.database),
                    AUTO_JWT_SECRET: environment.jwtSecret,
                    AUTO_ADMIN_PASSWORD: environment.adminPassword,
                    AUTO_BACKEND_PORT: String(environment.backendPort),
                    AUTO_FRONTEND_PORT: String(environment.frontendPort),
                },
                stdout: 'inherit',
                stderr: 'inherit',
            },
        )
        result = await child.exited
        if (interrupted && result === 0) {
            result = 130
        }
    } catch (error) {
        console.error('Quality runner failed:', error)
        result = 1
    } finally {
        try {
            await cleanupAutoState(adapter)
        } catch (cleanupError) {
            console.error('Quality cleanup failed:', cleanupError)
            result = 1
        }
        await lock.release()
        process.removeListener('SIGINT', interrupt)
        process.removeListener('SIGTERM', interrupt)
    }

    return result
}

async function prepareOutputDirectories() {
    await Promise.all([
        rm(path.join(qualityRoot, 'artifacts'), {recursive: true, force: true}),
        rm(path.join(qualityRoot, 'playwright-report'), {recursive: true, force: true}),
        rm(path.join(qualityRoot, 'test-results'), {recursive: true, force: true}),
    ])
    await mkdir(path.join(qualityRoot, 'artifacts', 'process-logs'), {recursive: true})
}

if (import.meta.main) {
    process.exitCode = await run()
}
```

- [ ] **Step 6: Run lifecycle tests and typecheck**

Run:

```bash
cd quality
bun test support/autoState.test.ts support/laneLock.test.ts scripts/run-quality.test.ts
bun run typecheck
```

Expected: PASS. No external PostgreSQL or Redis connection is made by these unit tests.

- [ ] **Step 7: Commit Task 3**

```bash
git add quality/support/autoState.ts quality/support/autoState.test.ts quality/support/laneLock.ts quality/support/laneLock.test.ts quality/scripts/run-quality.ts quality/scripts/run-quality.test.ts
git commit -m "test: add auto environment lifecycle runner"
```

### Task 4: Add Playwright Process Management And Diagnostics

**Files:**
- Create: `quality/scripts/start-server.test.ts`
- Create: `quality/scripts/start-server.ts`
- Create: `quality/playwright.config.ts`

- [ ] **Step 1: Write the failing managed-server definition test**

Create `scripts/start-server.test.ts`:

```typescript
import {expect, test} from 'bun:test'
import path from 'node:path'
import {serverDefinition, serverEnvironment} from './start-server'

test('builds dedicated backend and frontend commands', () => {
    const root = path.resolve('/workspace/CyberMario/quality')
    const backend = serverDefinition('backend', root, {
        ...process.env,
        AUTO_FRONTEND_PORT: '5174',
    })
    const frontend = serverDefinition('frontend', root, {
        ...process.env,
        AUTO_FRONTEND_PORT: '5174',
    })

    expect(backend.command).toEqual([
        './mvnw',
        '-Dmaven.build.cache.enabled=false',
        'spring-boot:run',
    ])
    expect(backend.cwd).toBe(path.resolve(root, '../be'))
    expect(frontend.command).toContain('5174')
    expect(frontend.cwd).toBe(path.resolve(root, '../fe'))

    const frontendEnvironment = serverEnvironment('frontend', {
        PATH: '/bin',
        AUTO_FRONTEND_PORT: '5174',
        AUTO_DB_PASSWORD: 'must-not-reach-vite',
        VITE_BACKEND_TARGET: 'http://127.0.0.1:28081',
    })
    expect(frontendEnvironment.AUTO_DB_PASSWORD).toBeUndefined()
    expect(frontendEnvironment.VITE_BACKEND_TARGET).toBe('http://127.0.0.1:28081')
})
```

- [ ] **Step 2: Run the test and verify the missing module fails**

Run:

```bash
cd quality
bun test scripts/start-server.test.ts
```

Expected: FAIL because `scripts/start-server.ts` does not exist.

- [ ] **Step 3: Implement one process wrapper with durable logs**

Create `scripts/start-server.ts`:

```typescript
import {mkdir} from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'

type ServerTarget = 'backend' | 'frontend'

type ServerDefinition = {
    command: string[]
    cwd: string
}

const qualityRoot = fileURLToPath(new URL('..', import.meta.url))

export function serverDefinition(
    target: ServerTarget,
    root = qualityRoot,
    environment: NodeJS.ProcessEnv = process.env,
): ServerDefinition {
    if (target === 'backend') {
        return {
            command: ['./mvnw', '-Dmaven.build.cache.enabled=false', 'spring-boot:run'],
            cwd: path.resolve(root, '../be'),
        }
    }

    const frontendPort = environment.AUTO_FRONTEND_PORT || '5174'
    return {
        command: [
            'bun',
            'run',
            'dev',
            '--',
            '--host',
            '127.0.0.1',
            '--port',
            frontendPort,
            '--strictPort',
        ],
        cwd: path.resolve(root, '../fe'),
    }
}

export function serverEnvironment(
    target: ServerTarget,
    environment: NodeJS.ProcessEnv = process.env,
) {
    const defined = Object.fromEntries(
        Object.entries(environment)
            .filter((entry): entry is [string, string] => entry[1] !== undefined),
    )
    if (target === 'backend') {
        return defined
    }
    const allowed = new Set([
        'PATH',
        'HOME',
        'CI',
        'NODE_ENV',
        'TMPDIR',
        'TMP',
        'TEMP',
        'AUTO_FRONTEND_PORT',
        'VITE_BACKEND_TARGET',
    ])
    return Object.fromEntries(
        Object.entries(defined).filter(([key]) => allowed.has(key) || key.startsWith('VITE_')),
    )
}

async function start(target: ServerTarget) {
    const definition = serverDefinition(target)
    const logDirectory = path.join(qualityRoot, 'artifacts', 'process-logs')
    await mkdir(logDirectory, {recursive: true})
    const writer = Bun.file(path.join(logDirectory, `${target}.log`)).writer()
    const child = Bun.spawn(definition.command, {
        cwd: definition.cwd,
        env: serverEnvironment(target),
        stdout: 'pipe',
        stderr: 'pipe',
    })

    const forward = async (
        stream: ReadableStream<Uint8Array>,
        output: NodeJS.WriteStream,
    ) => {
        const reader = stream.getReader()
        while (true) {
            const {done, value} = await reader.read()
            if (done) break
            writer.write(value)
            output.write(value)
        }
    }

    const stop = () => child.kill()
    process.once('SIGINT', stop)
    process.once('SIGTERM', stop)
    await Promise.all([
        forward(child.stdout, process.stdout),
        forward(child.stderr, process.stderr),
        child.exited,
    ])
    await writer.end()
    process.removeListener('SIGINT', stop)
    process.removeListener('SIGTERM', stop)
    return child.exitCode
}

if (import.meta.main) {
    const target = process.argv[2]
    if (target !== 'backend' && target !== 'frontend') {
        throw new Error('Server target must be backend or frontend')
    }
    process.exitCode = (await start(target)) ?? 1
}
```

- [ ] **Step 4: Add the Playwright configuration**

Create `playwright.config.ts`:

```typescript
import {defineConfig, devices} from '@playwright/test'
import {serverEnvironment} from './scripts/start-server'
import {loadAutoEnvironment} from './support/autoEnvironment'

const environment = loadAutoEnvironment()
const backendUrl = `http://127.0.0.1:${environment.backendPort}`
const frontendUrl = `http://127.0.0.1:${environment.frontendPort}`
const inheritedEnvironment = Object.fromEntries(
    Object.entries(process.env)
        .filter((entry): entry is [string, string] => entry[1] !== undefined),
)

export default defineConfig({
    testDir: './tests',
    outputDir: 'test-results',
    fullyParallel: false,
    workers: 1,
    retries: 0,
    timeout: 60_000,
    expect: {
        timeout: 10_000,
    },
    reporter: environment.ci
        ? [
            ['list'],
            ['html', {outputFolder: 'playwright-report', open: 'never'}],
            ['junit', {outputFile: 'test-results/junit.xml'}],
        ]
        : [
            ['list'],
            ['html', {outputFolder: 'playwright-report', open: 'never'}],
        ],
    use: {
        ...devices['Desktop Chrome'],
        baseURL: frontendUrl,
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: environment.ci ? 'retain-on-failure' : 'off',
    },
    webServer: [
        {
            command: 'bun run scripts/start-server.ts backend',
            url: `${backendUrl}/actuator/health`,
            timeout: 240_000,
            reuseExistingServer: false,
            stdout: 'pipe',
            stderr: 'pipe',
            env: {
                ...inheritedEnvironment,
                SPRING_PROFILES_ACTIVE: 'auto',
                AUTO_BACKEND_PORT: String(environment.backendPort),
            },
        },
        {
            command: 'bun run scripts/start-server.ts frontend',
            url: `${frontendUrl}/login`,
            timeout: 120_000,
            reuseExistingServer: false,
            stdout: 'pipe',
            stderr: 'pipe',
            env: serverEnvironment('frontend', {
                ...inheritedEnvironment,
                AUTO_FRONTEND_PORT: String(environment.frontendPort),
                VITE_BACKEND_TARGET: backendUrl,
            }),
        },
    ],
})
```

- [ ] **Step 5: Run process tests and typecheck**

Run:

```bash
cd quality
bun test scripts/start-server.test.ts
bun run typecheck
```

Expected: PASS. Do not start the backend or frontend in this step.

- [ ] **Step 6: Commit Task 4**

```bash
git add quality/scripts/start-server.ts quality/scripts/start-server.test.ts quality/playwright.config.ts
git commit -m "test: configure managed Playwright services"
```

### Task 5: Add Independent Registration Regression Scenarios

**Files:**
- Create: `quality/models/api.ts`
- Create: `quality/models/test-identity.test.ts`
- Create: `quality/models/test-identity.ts`
- Create: `quality/pages/RegisterPage.ts`
- Create: `quality/fixtures/auth.fixture.ts`
- Create: `quality/tests/auth/register.spec.ts`

- [ ] **Step 1: Write the failing identity-model unit test**

Create `models/test-identity.test.ts`:

```typescript
import {expect, test} from 'bun:test'
import {createTestIdentity, TEST_PASSWORD} from './test-identity'

test('creates bounded unique Auto-only registration values', () => {
    const identity = createTestIdentity('quality-run-1', 'duplicate registration')

    expect(identity.accountNo).toMatch(/^auto_/)
    expect(identity.username).toMatch(/^auto_user_/)
    expect(identity.accountNo.length).toBeLessThanOrEqual(64)
    expect(identity.username.length).toBeLessThanOrEqual(64)
    expect(identity.email).toEndWith('@example.test')
    expect(identity.password).toBe(TEST_PASSWORD)
    expect(identity.confirmPassword).toBe(TEST_PASSWORD)
})
```

- [ ] **Step 2: Run the model test and verify the missing module fails**

Run:

```bash
cd quality
bun test models/test-identity.test.ts
```

Expected: FAIL because `models/test-identity.ts` does not exist.

- [ ] **Step 3: Implement the API and test-identity models**

Create `models/api.ts`:

```typescript
import type {Response} from '@playwright/test'

export type ApiEnvelope<T = unknown> = {
    code: string
    message: string
    data: T
    traceId?: string
    timestamp?: string
}

export async function readApiEnvelope<T = unknown>(response: Response) {
    return await response.json() as ApiEnvelope<T>
}
```

Create `models/test-identity.ts`:

```typescript
import {randomUUID} from 'node:crypto'

export const TEST_PASSWORD = 'AutoGate#2026!'

export type TestIdentity = {
    accountNo: string
    username: string
    nickname: string
    email: string
    password: string
    confirmPassword: string
}

export function createTestIdentity(runId: string, caseId: string): TestIdentity {
    const suffix = sanitize(`${runId}_${caseId}_${randomUUID().slice(0, 8)}`)
    return {
        accountNo: `auto_${suffix}`.slice(0, 64),
        username: `auto_user_${suffix}`.slice(0, 64),
        nickname: `Auto ${sanitize(caseId).slice(0, 32)}`,
        email: `${suffix.slice(0, 80)}@example.test`,
        password: TEST_PASSWORD,
        confirmPassword: TEST_PASSWORD,
    }
}

function sanitize(value: string) {
    return value
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '_')
        .replace(/^_+|_+$/g, '')
}
```

- [ ] **Step 4: Write the three failing registration browser tests**

Create `tests/auth/register.spec.ts`:

```typescript
import {test, expect} from '../../fixtures/auth.fixture'
import {readApiEnvelope} from '../../models/api'
import {RegisterPage} from '../../pages/RegisterPage'

test.describe('registration', () => {
    test('validates required fields and password confirmation before requesting the backend',
        async ({page, identityFactory}) => {
            const requests: string[] = []
            page.on('request', (request) => {
                if (new URL(request.url()).pathname === '/api/auth/register') {
                    requests.push(request.url())
                }
            })
            const registerPage = new RegisterPage(page)
            await registerPage.goto()

            await registerPage.submit()
            await expect(page.getByText('请输入账号')).toBeVisible()
            await expect(page.getByText('请输入用户名')).toBeVisible()

            const identity = identityFactory('client-validation')
            await registerPage.fill(identity, 'different-password')
            await registerPage.submit()
            await expect(page.getByText('两次输入的密码不一致')).toBeVisible()
            expect(requests).toEqual([])
        })

    test('registers a unique user and establishes an HttpOnly browser session',
        async ({page, context, identityFactory}) => {
            const identity = identityFactory('registration-success')
            const registerPage = new RegisterPage(page)
            await registerPage.goto()

            const response = await registerPage.register(identity)
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('0')
            await expect(page).toHaveURL(/\/chat$/)
            await expect(page.getByRole('button', {name: identity.nickname})).toBeVisible()
            const cookies = await context.cookies()
            expect(cookies.find((cookie) => cookie.name === 'CM_ACCESS_TOKEN')?.httpOnly).toBe(true)
            expect(cookies.find((cookie) => cookie.name === 'CM_REFRESH_TOKEN')?.httpOnly).toBe(true)
        })

    test('rejects a duplicate account without creating another session',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('duplicate-account')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const registerPage = new RegisterPage(page)
            await registerPage.goto()
            const response = await registerPage.register(identity)
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('RBAC_USER_ACCOUNT_NO_DUPLICATED')
            await expect(registerPage.errorAlert()).toBeVisible()
            await expect(page).toHaveURL(/\/register$/)
            const cookies = await context.cookies()
            expect(cookies.some((cookie) => cookie.name === 'CM_ACCESS_TOKEN')).toBe(false)
            expect(cookies.some((cookie) => cookie.name === 'CM_REFRESH_TOKEN')).toBe(false)
        })
})
```

- [ ] **Step 5: Run typecheck and verify the missing browser support fails**

Run:

```bash
cd quality
bun run typecheck
```

Expected: FAIL because `fixtures/auth.fixture.ts` and
`pages/RegisterPage.ts` do not exist.

- [ ] **Step 6: Implement the registration Page Object**

Create `pages/RegisterPage.ts`:

```typescript
import type {Page} from '@playwright/test'
import type {TestIdentity} from '../models/test-identity'

export class RegisterPage {
    constructor(private readonly page: Page) {
    }

    async goto() {
        await this.page.goto('/register')
    }

    async fill(identity: TestIdentity, confirmPassword = identity.confirmPassword) {
        await this.page.getByLabel('账号', {exact: true}).fill(identity.accountNo)
        await this.page.getByLabel('用户名', {exact: true}).fill(identity.username)
        await this.page.getByLabel('昵称', {exact: true}).fill(identity.nickname)
        await this.page.getByLabel('密码', {exact: true}).fill(identity.password)
        await this.page.getByLabel('确认密码', {exact: true}).fill(confirmPassword)
        await this.page.getByLabel('邮箱', {exact: true}).fill(identity.email)
    }

    async submit() {
        await this.page.getByRole('button', {name: '注册并进入'}).click()
    }

    async register(identity: TestIdentity) {
        await this.fill(identity)
        const responsePromise = this.page.waitForResponse((response) =>
            response.request().method() === 'POST'
            && new URL(response.url()).pathname === '/api/auth/register')
        await this.submit()
        return await responsePromise
    }

    errorAlert() {
        return this.page.getByRole('alert')
    }
}
```

- [ ] **Step 7: Implement the UI-only registration fixture**

Create `fixtures/auth.fixture.ts`:

```typescript
import {test as base, type Page} from '@playwright/test'
import {readApiEnvelope} from '../models/api'
import {createTestIdentity, type TestIdentity} from '../models/test-identity'
import {RegisterPage} from '../pages/RegisterPage'

type RegistrationResult = {
    code: string
    finalUrl: string
}

type AuthFixtures = {
    requestFailureDiagnostics: void
    identityFactory: (caseId: string) => TestIdentity
    registerThroughUi: (identity: TestIdentity) => Promise<RegistrationResult>
}

export const test = base.extend<AuthFixtures>({
    requestFailureDiagnostics: [async ({page}, use) => {
        attachRequestFailureDiagnostics(page)
        await use()
    }, {auto: true}],
    identityFactory: async ({}, use, testInfo) => {
        const runId = process.env.QUALITY_RUN_ID
        if (!runId) {
            throw new Error('QUALITY_RUN_ID is required; run tests through the quality runner')
        }
        await use((caseId) =>
            createTestIdentity(runId, `${testInfo.file}-${testInfo.title}-${caseId}`))
    },
    registerThroughUi: async ({browser, baseURL}, use) => {
        await use(async (identity) => {
            const context = await browser.newContext({baseURL})
            const page = await context.newPage()
            try {
                attachRequestFailureDiagnostics(page)
                const registerPage = new RegisterPage(page)
                await registerPage.goto()
                const response = await registerPage.register(identity)
                const envelope = await readApiEnvelope(response)
                await page.waitForURL(/\/chat$/)
                return {
                    code: envelope.code,
                    finalUrl: page.url(),
                }
            } finally {
                await context.close()
            }
        })
    },
})

export {expect} from '@playwright/test'

function attachRequestFailureDiagnostics(page: Page) {
    page.on('requestfailed', (request) => {
        const url = new URL(request.url())
        console.error('Browser request failed', {
            method: request.method(),
            url: `${url.origin}${url.pathname}`,
            reason: request.failure()?.errorText,
        })
    })
}
```

The fixture returns the registration result so each spec retains the
business-code assertion.

- [ ] **Step 8: Run local static checks**

Run:

```bash
cd quality
bun test models/test-identity.test.ts
bun run typecheck
```

Expected: PASS.

- [ ] **Step 9: Run the registration suite against the configured Auto environment**

Ensure `quality/.env` contains the approved Auto values, then run:

```bash
cd quality
bun run scripts/run-quality.ts auth tests/auth/register.spec.ts
```

Expected: 3 Playwright tests PASS; backend and frontend stop; the Auto schema is dropped; the Auto Redis database is empty.

- [ ] **Step 10: Commit Task 5**

```bash
git add quality/models/api.ts quality/models/test-identity.ts quality/models/test-identity.test.ts quality/pages/RegisterPage.ts quality/fixtures/auth.fixture.ts quality/tests/auth/register.spec.ts
git commit -m "test: cover browser registration flows"
```

### Task 6: Add Login, Session Restoration, And Logout Regression

**Files:**
- Create: `quality/pages/LoginPage.ts`
- Create: `quality/pages/AdminShell.ts`
- Create: `quality/tests/auth/login.spec.ts`
- Create: `quality/tests/auth/session.spec.ts`

- [ ] **Step 1: Write login and session specs before their Page Objects exist**

Create `tests/auth/login.spec.ts`:

```typescript
import {test, expect} from '../../fixtures/auth.fixture'
import {readApiEnvelope} from '../../models/api'
import {LoginPage} from '../../pages/LoginPage'

test.describe('login', () => {
    test('rejects a wrong password without establishing a session',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('wrong-password')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            const response = await loginPage.login(identity.accountNo, 'WrongPassword#2026')
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('AUTH_INVALID_CREDENTIALS')
            await expect(loginPage.errorAlert()).toBeVisible()
            await expect(page).toHaveURL(/\/login$/)
            const cookies = await context.cookies()
            expect(cookies.some((cookie) => cookie.name === 'CM_ACCESS_TOKEN')).toBe(false)
            expect(cookies.some((cookie) => cookie.name === 'CM_REFRESH_TOKEN')).toBe(false)
        })

    test('logs in with the registered account and writes HttpOnly cookies',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('login-success')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            const response = await loginPage.login(identity.accountNo, identity.password)
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('0')
            await expect(page).toHaveURL(/\/chat$/)
            await expect(page.getByRole('button', {name: identity.nickname})).toBeVisible()
            const cookies = await context.cookies()
            expect(cookies.find((cookie) => cookie.name === 'CM_ACCESS_TOKEN')?.httpOnly).toBe(true)
            expect(cookies.find((cookie) => cookie.name === 'CM_REFRESH_TOKEN')?.httpOnly).toBe(true)
        })
})
```

Create `tests/auth/session.spec.ts`:

```typescript
import {test, expect} from '../../fixtures/auth.fixture'
import {readApiEnvelope} from '../../models/api'
import {AdminShell} from '../../pages/AdminShell'
import {LoginPage} from '../../pages/LoginPage'

test.describe('browser session', () => {
    test('restores the authenticated user after reload and protected navigation',
        async ({page, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('session-restore')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            expect((await readApiEnvelope(
                await loginPage.login(identity.accountNo, identity.password))).code).toBe('0')

            const meResponse = page.waitForResponse((response) =>
                response.request().method() === 'GET'
                && new URL(response.url()).pathname === '/api/auth/me')
            await page.reload()
            expect((await readApiEnvelope(await meResponse)).code).toBe('0')
            await expect(page.getByRole('button', {name: identity.nickname})).toBeVisible()

            await page.goto('/account/settings')
            await expect(page).toHaveURL(/\/account\/settings$/)
            await expect(page.getByText('维护当前账号的基础资料和登录密码。')).toBeVisible()
        })

    test('clears cookies and protects routes after logout',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('logout')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            expect((await readApiEnvelope(
                await loginPage.login(identity.accountNo, identity.password))).code).toBe('0')

            const shell = new AdminShell(page)
            const logoutResponse = await shell.logout(identity.nickname)
            expect((await readApiEnvelope(logoutResponse)).code).toBe('0')
            await expect(page).toHaveURL(/\/login$/)

            const cookies = await context.cookies()
            expect(cookies.some((cookie) => cookie.name === 'CM_ACCESS_TOKEN')).toBe(false)
            expect(cookies.some((cookie) => cookie.name === 'CM_REFRESH_TOKEN')).toBe(false)

            await page.goto('/account/settings')
            await expect(page).toHaveURL(/\/login$/)
        })
})
```

- [ ] **Step 2: Run typecheck and verify missing Page Objects fail**

Run:

```bash
cd quality
bun run typecheck
```

Expected: FAIL because `LoginPage` and `AdminShell` do not exist.

- [ ] **Step 3: Implement the login Page Object**

Create `pages/LoginPage.ts`:

```typescript
import type {Page} from '@playwright/test'

export class LoginPage {
    constructor(private readonly page: Page) {
    }

    async goto() {
        await this.page.goto('/login')
    }

    async login(account: string, password: string) {
        await this.page.getByLabel('账号或邮箱', {exact: true}).fill(account)
        await this.page.getByLabel('密码', {exact: true}).fill(password)
        const responsePromise = this.page.waitForResponse((response) =>
            response.request().method() === 'POST'
            && new URL(response.url()).pathname === '/api/auth/login')
        await this.page.getByRole('button', {name: '进入工作台'}).click()
        return await responsePromise
    }

    errorAlert() {
        return this.page.getByRole('alert')
    }
}
```

- [ ] **Step 4: Implement the authenticated-shell Page Object**

Create `pages/AdminShell.ts`:

```typescript
import type {Page} from '@playwright/test'

export class AdminShell {
    constructor(private readonly page: Page) {
    }

    async logout(currentUserName: string) {
        await this.page.getByRole('button', {name: currentUserName}).click()
        const responsePromise = this.page.waitForResponse((response) =>
            response.request().method() === 'POST'
            && new URL(response.url()).pathname === '/api/auth/logout')
        await this.page.getByText('退出登录', {exact: true}).click()
        return await responsePromise
    }
}
```

- [ ] **Step 5: Run static checks**

Run:

```bash
cd quality
bun run test
bun run typecheck
```

Expected: all quality unit tests and typecheck PASS.

- [ ] **Step 6: Run all Auth Playwright scenarios**

Run:

```bash
cd quality
bun run test:auth
```

Expected: 7 Playwright tests PASS across registration, login, session restoration, and logout. Both managed servers stop and the Auto lane is clean afterward.

- [ ] **Step 7: Commit Task 6**

```bash
git add quality/pages/LoginPage.ts quality/pages/AdminShell.ts quality/tests/auth/login.spec.ts quality/tests/auth/session.spec.ts
git commit -m "test: cover login and browser session flows"
```

### Task 7: Add The CI Quality Gate And Operator Documentation

**Files:**
- Create: `.github/workflows/quality-gate.yml`
- Create: `quality/README.md`
- Modify: `README.md`

- [ ] **Step 1: Add the serialized CI Auto gate**

Create `.github/workflows/quality-gate.yml`:

```yaml
name: Quality Gate

on:
  pull_request:
  push:
    branches:
      - main
  workflow_dispatch:
  workflow_call:

permissions:
  contents: read

jobs:
  backend-check:
    name: Backend Check
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Run backend tests
        working-directory: be
        run: ./mvnw -Dmaven.build.cache.enabled=false test

  frontend-check:
    name: Frontend Check
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
        with:
          bun-version: 1.3.14
      - name: Install frontend dependencies
        working-directory: fe
        run: bun install --frozen-lockfile
      - name: Lint frontend
        working-directory: fe
        run: bun run lint
      - name: Typecheck frontend
        working-directory: fe
        run: bun run typecheck
      - name: Test frontend
        working-directory: fe
        run: bun run test
      - name: Build frontend
        working-directory: fe
        run: bun run build

  auth-playwright:
    name: Auth Playwright
    needs:
      - backend-check
      - frontend-check
    runs-on: ubuntu-latest
    concurrency:
      group: cybermario-auto-quality
      cancel-in-progress: false
    env:
      CI: 'true'
      AUTO_CLEANUP_ALLOWED: 'true'
      AUTO_DB_URL: ${{ secrets.AUTO_DB_URL }}
      AUTO_DB_USERNAME: ${{ secrets.AUTO_DB_USERNAME }}
      AUTO_DB_PASSWORD: ${{ secrets.AUTO_DB_PASSWORD }}
      AUTO_DB_SCHEMA: ${{ vars.AUTO_DB_SCHEMA }}
      AUTO_REDIS_HOST: ${{ vars.AUTO_REDIS_HOST }}
      AUTO_REDIS_PORT: ${{ vars.AUTO_REDIS_PORT }}
      AUTO_REDIS_PASSWORD: ${{ secrets.AUTO_REDIS_PASSWORD }}
      AUTO_REDIS_DATABASE: ${{ vars.AUTO_REDIS_DATABASE }}
      AUTO_JWT_SECRET: ${{ secrets.AUTO_JWT_SECRET }}
      AUTO_ADMIN_PASSWORD: ${{ secrets.AUTO_ADMIN_PASSWORD }}
      AUTO_BACKEND_PORT: '28081'
      AUTO_FRONTEND_PORT: '5174'
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - uses: oven-sh/setup-bun@v2
        with:
          bun-version: 1.3.14
      - name: Install frontend dependencies
        working-directory: fe
        run: bun install --frozen-lockfile
      - name: Install quality dependencies
        working-directory: quality
        run: bun install --frozen-lockfile
      - name: Install Chromium
        working-directory: quality
        run: bunx playwright install --with-deps chromium
      - name: Test quality infrastructure
        working-directory: quality
        run: bun run test
      - name: Typecheck quality project
        working-directory: quality
        run: bun run typecheck
      - name: Run Auth Playwright gate
        working-directory: quality
        run: bun run test:auth
      - name: Upload Auth diagnostics
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: auth-playwright-${{ github.run_id }}-${{ github.run_attempt }}
          if-no-files-found: ignore
          retention-days: 7
          path: |
            quality/artifacts/process-logs/
            quality/playwright-report/
            quality/test-results/

  quality-gate:
    name: Quality Gate
    if: always()
    needs:
      - backend-check
      - frontend-check
      - auth-playwright
    runs-on: ubuntu-latest
    steps:
      - name: Require every quality check
        shell: bash
        run: |
          test "${{ needs.backend-check.result }}" = "success"
          test "${{ needs.frontend-check.result }}" = "success"
          test "${{ needs.auth-playwright.result }}" = "success"
```

Do not add a deployment job. When production deployment is implemented later,
call this reusable workflow as a prerequisite job, make deployment depend on
that caller job, and deploy the same `github.sha`.

- [ ] **Step 2: Document local and CI operation**

Create `quality/README.md`:

~~~~markdown
# CyberMario Browser Quality

This project runs real browser regression tests against the CyberMario
frontend and a backend using the Spring `auto` profile.

## Prerequisites

- JDK 21
- Bun 1.3.14
- a dedicated PostgreSQL Auto database with `vector`, `hstore`, `uuid-ossp`,
  and `pg_trgm` extension support
- a dedicated local Redis database index greater than 1
- credentials allowed to create and drop only approved `auto_*` schemas

Playwright starts `be` and `fe`. It does not start PostgreSQL, Redis,
MQ, or Docker.

## Local Setup

```bash
cd quality
bun install
cp .env.example .env
bunx playwright install chromium
```

Set only Auto environment values in `.env`. The runner refuses a database
without an `auto` boundary, the `public` schema, Redis database 0 or 1,
and missing cleanup authorization.

## Commands

```bash
bun run test:auth
bun run test:auth:headed
bun run test:auth:ui
bun run test:auth:debug
bun run report
```

Every command uses the same Auth specs. Display and debug modes change only
browser presentation.

## Data Lifecycle

The runner recreates the configured PostgreSQL schema and flushes the
configured Redis database before the suite. It runs the same cleanup after
Playwright stops, including after failures. Cleanup failures fail the command.

Local and CI must use different schema and Redis lanes. Do not point this
project at development or production infrastructure.

## Diagnostics

Failure diagnostics are written to:

- `artifacts/process-logs/`
- `playwright-report/`
- `test-results/`

Reports must never contain passwords, JWTs, Cookie values, database dumps, or
Redis dumps.
~~~~

- [ ] **Step 3: Link the quality project from the root README**

Insert after the existing local frontend/backend development commands in `README.md`:

```markdown
## Browser Quality Gate

The root `quality/` project runs real Playwright registration and login
regression tests against the Spring `auto` profile and pre-provisioned
PostgreSQL/Redis Auto resources.

See [quality/README.md](quality/README.md) for environment safety requirements,
local headed/debug commands, CI usage, diagnostics, and cleanup behavior.
```

- [ ] **Step 4: Validate workflow syntax and all static checks**

Run:

```bash
ruby -e 'require "yaml"; YAML.parse_file(".github/workflows/quality-gate.yml")'
cd quality
bun install --frozen-lockfile
bun run test
bun run typecheck
cd ../fe
bun install --frozen-lockfile
bun run lint
bun run typecheck
bun run test
bun run build
```

Expected: YAML parses and every quality/frontend command PASS.

- [ ] **Step 5: Run the full backend suite**

Run:

```bash
cd be
./mvnw -Dmaven.build.cache.enabled=false test
```

Expected: PASS.

- [ ] **Step 6: Run the complete local Auth gate one final time**

Run:

```bash
cd quality
bun run test:auth
```

Expected: all 7 Auth scenarios PASS, both managed servers stop, diagnostics are available, and Auto PostgreSQL/Redis state is cleaned.

- [ ] **Step 7: Commit Task 7**

```bash
git add .github/workflows/quality-gate.yml quality/README.md README.md
git commit -m "ci: require Auth browser quality gate"
```

## Final Verification Checklist

Run these checks from a clean implementation worktree after Task 7:

- [ ] `git status --short` shows no uncommitted implementation files.
- [ ] `git log --oneline -7` shows one commit for each task.
- [ ] `git diff --check HEAD~7..HEAD` reports no whitespace errors.
- [ ] `cd be && ./mvnw -Dmaven.build.cache.enabled=false test` passes.
- [ ] `cd quality && bun run test && bun run typecheck` passes.
- [ ] `cd fe && bun run lint && bun run typecheck && bun run test && bun run build` passes.
- [ ] `cd quality && bun run test:auth` passes against the approved local Auto lane.
- [ ] Run `AUTO_DB_URL=jdbc:postgresql://localhost:5432/cyber_mario AUTO_DB_SCHEMA=public bun run test:auth` with the remaining approved Auto variables present; expect an immediate safety failure before any connection or cleanup.
- [ ] Confirm `quality/playwright-report/` opens from the completed local run and contains no secret values.
- [ ] Confirm no backend or frontend process remains after Playwright exits.
- [ ] Configure GitHub Actions secrets/variables and run `Quality Gate` manually; expect all four jobs to succeed.
- [ ] Configure any future production deployment job to depend on `quality-gate` and deploy the exact tested SHA before claiming that production release is enforced.

## Required CI Configuration

Before the workflow can pass, configure these repository secrets:

```text
AUTO_DB_URL
AUTO_DB_USERNAME
AUTO_DB_PASSWORD
AUTO_REDIS_PASSWORD
AUTO_JWT_SECRET
AUTO_ADMIN_PASSWORD
```

Configure these repository variables:

```text
AUTO_DB_SCHEMA=auto_ci
AUTO_REDIS_HOST=<auto Redis host>
AUTO_REDIS_PORT=6379
AUTO_REDIS_DATABASE=<dedicated CI Redis database greater than 1>
```

The Auto database principal must be restricted to the Auto database and must
be able to create/drop `auto_ci`, run the existing Flyway migrations, and
use or create the `vector`, `hstore`, `uuid-ossp`, and `pg_trgm` extensions.
The Redis credentials must be restricted to the configured Auto lane and
allowed to execute `FLUSHDB` there. Do not reuse development or production
credentials.
