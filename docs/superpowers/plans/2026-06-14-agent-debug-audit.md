# Agent Debug Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Agent debug presets, dynamic non-model runtime overrides, RBAC-gated debug/audit pages, and
super-admin-only conversation audit.

**Architecture:** Add a focused `top.egon.mario.agent` preset/runtime/audit layer around the existing
`ReactAgentChatService`. A saved preset plus per-request overrides is merged into an `AgentRuntimeSpec`, used by
`AgentRuntimeFactory` to build an isolated `ReactAgent`, while `AgentConversationAuditService` persists request and
message history. Frontend adds an Agent debug page and a super-admin-only audit page using the existing Ant Design,
request, menu, and route patterns.

**Tech Stack:** Java 21, Spring Boot WebFlux, Spring AI Alibaba ReactAgent, JPA, Flyway, RBAC resource provider, React,
TypeScript, Ant Design, Vitest.

---

### Task 1: Persist Presets And Conversation Audit

**Files:**

- Create: `be/src/main/resources/db/migration/V13__create_agent_debug_preset_and_conversation_audit.sql`
- Create: `be/src/main/java/top/egon/mario/agent/po/AgentChatPresetPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/po/AgentConversationAuditPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/po/AgentConversationMessageAuditPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/po/enums/AgentConversationStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/po/enums/AgentConversationRole.java`
- Create: `be/src/main/java/top/egon/mario/agent/po/enums/AgentConversationMessageType.java`
- Create: `be/src/main/java/top/egon/mario/agent/repository/AgentChatPresetRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/repository/AgentConversationAuditRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/repository/AgentConversationMessageAuditRepository.java`
- Test: `be/src/test/java/top/egon/mario/agent/service/impl/AgentConversationAuditServiceTests.java`

- [ ] **Step 1: Add migration and JPA entities**

Create the three tables from the design doc with indexes for preset visibility, thread lookup, trace lookup, status
lookup, and ordered message lookup. Use the same JPA style as `ModelAuditPo` and current repositories.

- [ ] **Step 2: Add repository contracts**

Add Spring Data repositories with lookup methods for non-deleted presets and ordered audit messages.

- [ ] **Step 3: Add audit persistence tests**

Cover creating an audit record, appending user/assistant/think messages, marking success, marking failure, and
truncating long error messages.

### Task 2: Add Preset DTOs And Service

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/dto/request/AgentDebugChatRequest.java`
- Create: `be/src/main/java/top/egon/mario/agent/dto/request/AgentPresetRequest.java`
- Create: `be/src/main/java/top/egon/mario/agent/dto/request/AgentPresetStatusRequest.java`
- Create: `be/src/main/java/top/egon/mario/agent/dto/request/AgentConversationAuditQuery.java`
- Create: `be/src/main/java/top/egon/mario/agent/dto/response/AgentPresetResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/dto/response/AgentConversationAuditResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/dto/response/AgentConversationMessageAuditResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/model/AgentModelConfig.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/model/AgentToolConfig.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/model/AgentOptions.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/model/AgentPresetConfig.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/model/AgentRuntimeSpec.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/model/AgentRuntimeDefaults.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/AgentPresetService.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/impl/AgentPresetServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/agent/service/impl/AgentPresetServiceTests.java`

- [ ] **Step 1: Add DTO and model records**

Use records for request/response/config objects and bean validation annotations for user input.

- [ ] **Step 2: Add default config**

Move the current hard-coded model, prompt, model options, and agent options into `AgentRuntimeDefaults`.

- [ ] **Step 3: Implement preset service**

Support page/list, detail, create, update, status change, delete, and runtime spec resolution. Only the preset creator
can update, enable/disable, or delete a preset; `SUPER_ADMIN` can still read through RBAC but does not bypass ownership
for writes unless explicitly added later.

- [ ] **Step 4: Add service tests**

Verify config merging, ownership checks, model config rejection, tool validation, and soft delete behavior.

### Task 3: Build Runtime Agent Factory And Debug Chat Flow

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/service/AgentRuntimeFactory.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/AgentConversationAuditService.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/impl/AgentConversationAuditServiceImpl.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/ChatAgentService.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
- Modify: `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`
- Test: `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/config/AgentConfigurationTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java`

- [ ] **Step 1: Implement runtime factory**

Create `ReactAgent` instances from `AgentRuntimeSpec`, filtering `ToolCallback` by enabled tool names and resolving
`ChatModel` through `MarioModelFactory`.

- [ ] **Step 2: Refactor default chat**

Keep existing `/demo/chat/stream` behavior by resolving the default spec internally.

- [ ] **Step 3: Add debug chat**

Add `debugChat(AgentDebugChatRequest, RbacPrincipal)` that resolves spec from preset/overrides and writes conversation
audit for success, failure, and cancellation.

- [ ] **Step 4: Add focused tests**

Verify default compatibility, debug spec usage, Arxiv user context cleanup, audit calls, and runtime factory model/tool
behavior.

### Task 4: Add Controllers And RBAC Resources

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/web/AgentDebugController.java`
- Create: `be/src/main/java/top/egon/mario/agent/web/AgentPresetController.java`
- Create: `be/src/main/java/top/egon/mario/agent/web/AdminAgentConversationAuditController.java`
- Create: `be/src/main/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProvider.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/resource/ChatRbacResourceProvider.java`
- Modify: `be/src/main/java/top/egon/mario/rbac/service/bootstrap/RbacRolePresetCatalog.java`
- Test: `be/src/test/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProviderTests.java`
- Test: `be/src/test/java/top/egon/mario/web/ChatControllerTests.java`

- [ ] **Step 1: Add WebFlux controllers**

Expose debug streaming, preset CRUD, and admin audit query APIs using existing `ApiResponse`, `PageResult`,
`TraceContext`, and `blockingScheduler` patterns.

- [ ] **Step 2: Add RBAC resources**

Declare `/agent/debug`, `/agent/conversation-audits`, debug stream, preset APIs, and admin audit APIs under app code
`agent`.

- [ ] **Step 3: Extend `CHAT_BASIC`**

Grant existing chat users debug menu, debug stream, and preset APIs. Do not grant conversation audit APIs.

- [ ] **Step 4: Add controller/resource tests**

Verify request validation, endpoint delegation, and role preset permissions.

### Task 5: Add Frontend Agent Module

**Files:**

- Create: `fe/src/modules/agent/agentTypes.ts`
- Create: `fe/src/modules/agent/agentService.ts`
- Create: `fe/src/modules/agent/AgentDebugPage.tsx`
- Create: `fe/src/modules/agent/AgentConversationAuditPage.tsx`
- Create: `fe/src/modules/agent/agentService.test.ts`
- Modify: `fe/src/app/routes.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`
- Modify: `fe/src/modules/chat/chatService.ts`
- Test: `fe/src/layouts/AdminLayout/menu.test.tsx`

- [ ] **Step 1: Add agent service and types**

Add request helpers for preset CRUD, debug streaming, and audit query APIs.

- [ ] **Step 2: Add debug page**

Build an Ant Design workbench with preset selector, parameter form, tool checkboxes, save/update actions, and a chat
composer that streams through `/api/agent/debug/chat/stream`.

- [ ] **Step 3: Add audit page**

Build a super-admin-only audit list with detail drawer for message history.

- [ ] **Step 4: Wire routes and menus**

Add `/agent/debug` and `/agent/conversation-audits`; keep audit hidden for non-`SUPER_ADMIN`.

- [ ] **Step 5: Add frontend tests**

Verify API request bodies and menu authorization behavior.

### Task 6: Verification

**Files:**

- Modify only files touched by prior tasks if verification exposes compile or test issues.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
mvn -q -f be/pom.xml -Dtest=AgentPresetServiceTests,AgentRuntimeFactoryTests,ReactAgentChatServiceTests,AgentConversationAuditServiceTests,AgentDebugRbacResourceProviderTests,ChatControllerTests test
```

Expected: all focused backend tests pass.

- [ ] **Step 2: Run backend compile**

Run:

```bash
mvn -q -f be/pom.xml -DskipTests compile
```

Expected: compile succeeds.

- [ ] **Step 3: Run focused frontend tests**

Run:

```bash
npm test -- --run agentService menu
```

Expected: focused frontend tests pass.

- [ ] **Step 4: Run frontend build**

Run:

```bash
npm run build
```

Expected: frontend build succeeds.

- [ ] **Step 5: Review working tree**

Run:

```bash
git status --short
```

Expected: changes are limited to the agent debug/audit feature plus the already-existing unrelated workspace changes.

