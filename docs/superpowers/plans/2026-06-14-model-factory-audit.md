# Model Factory Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable model factory that returns provider-backed Spring AI `ChatModel` instances from
upstream-supplied model/options and records model call audit data.

**Architecture:** Add a small `top.egon.mario.agent.model` package with a factory, provider adapter registry, DashScope
adapter, audited `ChatModel` wrapper, and AI model call audit persistence. The factory does not select models; upstream
callers provide provider, model, options, and call context.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring AI 1.1.2, Spring AI Alibaba DashScope, JPA, Flyway, JUnit 5, Mockito.

---

### Task 1: Model Factory Contract Tests

**Files:**

- Create: `be/src/test/java/top/egon/mario/agent/model/DefaultMarioModelFactoryTests.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/MarioModelFactory.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/DefaultMarioModelFactory.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelProviderAdapter.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelProviderType.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelScenario.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelOptions.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelCallContext.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelRequest.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelResolveResult.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelFactoryException.java`

- [ ] **Step 1: Write failing factory tests**

Add tests that assert the factory chooses an adapter by provider, requires provider/model, rejects unknown providers,
and returns an audited `ChatModel`.

- [ ] **Step 2: Run factory test and verify red**

Run: `mvn -q -f be/pom.xml -Dtest=DefaultMarioModelFactoryTests test`
Expected: compile failure because the model factory types do not exist.

- [ ] **Step 3: Implement minimal contract**

Implement records, enums, exception, adapter interface, and factory.

- [ ] **Step 4: Run factory test and verify green**

Run: `mvn -q -f be/pom.xml -Dtest=DefaultMarioModelFactoryTests test`
Expected: tests pass.

### Task 2: DashScope Adapter Tests

**Files:**

- Create: `be/src/test/java/top/egon/mario/agent/model/DashScopeModelProviderAdapterTests.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/DashScopeModelProviderAdapter.java`

- [ ] **Step 1: Write failing DashScope adapter tests**

Add tests that verify generic options map to `DashScopeChatOptions`, provider-specific options map into `extraBody`, and
the created model uses DashScope defaults.

- [ ] **Step 2: Run adapter test and verify red**

Run: `mvn -q -f be/pom.xml -Dtest=DashScopeModelProviderAdapterTests test`
Expected: compile failure because the adapter does not exist.

- [ ] **Step 3: Implement DashScope adapter**

Use existing `DashScopeApi`, `DashScopeChatModel`, and `DashScopeChatOptions`.

- [ ] **Step 4: Run adapter test and verify green**

Run: `mvn -q -f be/pom.xml -Dtest=DashScopeModelProviderAdapterTests test`
Expected: tests pass.

### Task 3: Audited ChatModel Tests

**Files:**

- Create: `be/src/test/java/top/egon/mario/agent/model/AuditedChatModelTests.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/AuditedChatModel.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelAuditService.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelAuditEvent.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelAuditStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/TokenUsageSource.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelTokenUsage.java`

- [ ] **Step 1: Write failing audited model tests**

Add tests for synchronous success, synchronous failure, streaming success, and streaming failure. Verify status, token
usage, prompt/completion lengths, and duration are passed to `ModelAuditService`.

- [ ] **Step 2: Run audited model test and verify red**

Run: `mvn -q -f be/pom.xml -Dtest=AuditedChatModelTests test`
Expected: compile failure because audited model types do not exist.

- [ ] **Step 3: Implement audited model**

Wrap `ChatModel.call(Prompt)` and `ChatModel.stream(Prompt)`, extracting `ChatResponseMetadata.getUsage()` when present.

- [ ] **Step 4: Run audited model test and verify green**

Run: `mvn -q -f be/pom.xml -Dtest=AuditedChatModelTests test`
Expected: tests pass.

### Task 4: Audit Persistence Tests

**Files:**

- Create: `be/src/test/java/top/egon/mario/agent/model/DefaultModelAuditServiceTests.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/DefaultModelAuditService.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelAuditPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/model/ModelAuditRepository.java`
- Create: `be/src/main/resources/db/migration/V8__create_ai_model_call_audit.sql`

- [ ] **Step 1: Write failing persistence tests**

Add tests that verify `ModelAuditEvent` is converted to `ModelAuditPo`, options JSON is persisted, and long error
messages are truncated.

- [ ] **Step 2: Run persistence test and verify red**

Run: `mvn -q -f be/pom.xml -Dtest=DefaultModelAuditServiceTests test`
Expected: compile failure because persistence types do not exist.

- [ ] **Step 3: Implement audit persistence**

Add JPA entity, repository, service, and Flyway migration.

- [ ] **Step 4: Run persistence test and verify green**

Run: `mvn -q -f be/pom.xml -Dtest=DefaultModelAuditServiceTests test`
Expected: tests pass.

### Task 5: Spring Wiring and Verification

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/model/ModelFactoryConfiguration.java`
- Modify: `be/src/test/java/top/egon/mario/common/api/LoggingSourceConventionsTests.java`

- [ ] **Step 1: Add configuration wiring**

Register `MarioModelFactory` and enable repository/entity scanning through normal Spring component scanning. Keep
existing services unchanged.

- [ ] **Step 2: Update source convention test if needed**

Include new logging source classes that intentionally use `LogUtil`.

- [ ] **Step 3: Run focused tests**

Run:
`mvn -q -f be/pom.xml -Dtest=DefaultMarioModelFactoryTests,DashScopeModelProviderAdapterTests,AuditedChatModelTests,DefaultModelAuditServiceTests test`
Expected: all focused tests pass.

- [ ] **Step 4: Run compile verification**

Run: `mvn -q -f be/pom.xml -DskipTests compile`
Expected: compile succeeds.

- [ ] **Step 5: Check working tree**

Run: `git status --short`
Expected: only model factory, audit migration, plan, and focused test files changed.
