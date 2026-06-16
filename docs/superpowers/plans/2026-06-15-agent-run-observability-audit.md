# Agent Run Observability Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a SUPER_ADMIN-only Agent observability audit surface that stores full plaintext Agent run details in
PostgreSQL and shows them as a recent-runs list plus event timeline detail page.

**Architecture:** Add a unified `agent_run_audit` summary table and `agent_run_event_audit` timeline table next to the
existing model/conversation/MCP audit tables. Create a focused `AgentRunAuditService` used by `ReactAgentChatService`, a
model interceptor, and a tool interceptor through `RunnableConfig.metadata` so one run links user input, every ReAct
model round, tool/MCP calls, final output, errors, and cancellation. Keep existing `AgentConversationAuditService`,
`ModelAuditService`, and `McpToolCallLogService` for backward compatibility and usage dashboards.

**Tech Stack:** Spring Boot WebFlux, Spring Data JPA, Flyway, PostgreSQL `TEXT`, Spring AI Alibaba `ReactAgent`
model/tool interceptors, Reactor, JUnit 5, Mockito, AssertJ, React 19, Ant Design 6, TypeScript, Vitest.

---

## File Structure

Backend persistence and API:

- Create `be/src/main/resources/db/migration/V16__create_agent_run_audit.sql`: one Flyway migration for the two new
  audit tables and indexes.
- Create `be/src/main/java/top/egon/mario/agent/observability/po/AgentRunAuditPo.java`: JPA entity for one Agent run.
- Create `be/src/main/java/top/egon/mario/agent/observability/po/AgentRunEventAuditPo.java`: JPA entity for one run
  timeline event.
- Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunAuditStatus.java`: run status enum.
- Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventType.java`: event type enum.
- Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventStatus.java`: event status enum.
- Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunToolType.java`: local/MCP/unknown tool
  type enum.
- Create `be/src/main/java/top/egon/mario/agent/observability/repository/AgentRunAuditRepository.java`: JPA repository
  and specifications for run list filtering.
- Create `be/src/main/java/top/egon/mario/agent/observability/repository/AgentRunEventAuditRepository.java`: JPA
  repository for event detail lookup.
- Create `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunAuditContext.java`: context object
  stored in `RunnableConfig.metadata`.
- Create `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunAuditStart.java`: run-start input
  object.
- Create `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunEventRecord.java`: event input
  object.
- Create `be/src/main/java/top/egon/mario/agent/observability/service/AgentRunAuditService.java`: service interface for
  run lifecycle, events, and queries.
- Create `be/src/main/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImpl.java`: transactional
  persistence service with best-effort event writes.
- Create `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptor.java`:
  captures full model prompt/messages/tools/options/response per ReAct round.
- Create `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptor.java`:
  captures full tool input/output/status/duration per tool call.
- Create `be/src/main/java/top/egon/mario/agent/observability/web/AdminAgentRunAuditController.java`: SUPER_ADMIN query
  endpoints.
- Create DTO files under `be/src/main/java/top/egon/mario/agent/observability/dto/request` and `dto/response` for
  list/detail APIs.
- Modify `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`: create the run, put context in
  `RunnableConfig.metadata`, finish/fail/cancel the run, and write final output events.
- Modify `be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java`: inject the observability
  interceptors into `ReactAgent.builder()`.
- Modify `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`: inject `AgentRunAuditService` into
  `ReactAgentChatService`.
- Modify `be/src/main/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProvider.java`: add the new
  SUPER_ADMIN-only menu/API resources.

Frontend:

- Modify `fe/src/modules/agent/agentTypes.ts`: add run-audit request/response/event types.
- Modify `fe/src/modules/agent/agentService.ts`: add `getAgentRunAudits` and `getAgentRunAuditEvents`.
- Create `fe/src/modules/agent/AgentRunAuditPage.tsx`: recent run list, filters, and detail drawer timeline.
- Modify `fe/src/app/routes.tsx`: add `/agent/run-audits` route.
- Add tests in `fe/src/modules/agent/agentService.test.ts` for new request builders.

Tests:

- Create backend tests for migration shape, service persistence/querying, model interceptor, tool interceptor,
  chat-service lifecycle integration, RBAC resources, and controller authorization.
- Extend frontend tests for service URL construction and optional page rendering if the existing test setup can render
  AntD reliably.

Validation commands:

- Backend targeted tests use
  `mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=<ClassName> test`.
- Frontend service tests use `npm test -- agentService.test.ts`.
- Frontend type/build validation uses `npm run typecheck`.

---

### Task 1: Flyway Migration And Enum Model

**Files:**

- Create: `be/src/main/resources/db/migration/V16__create_agent_run_audit.sql`
- Create: `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunAuditStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventType.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunToolType.java`
- Test: `be/src/test/java/top/egon/mario/agent/observability/AgentRunAuditSchemaMigrationTests.java`

- [ ] **Step 1: Write the failing migration test**

Create `be/src/test/java/top/egon/mario/agent/observability/AgentRunAuditSchemaMigrationTests.java`:

```java
package top.egon.mario.agent.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunAuditSchemaMigrationTests {

    @Test
    void migrationCreatesRunAndEventAuditTablesWithTextPayloadColumns() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V16__create_agent_run_audit.sql"));

        assertThat(sql).contains("CREATE TABLE agent_run_audit");
        assertThat(sql).contains("CREATE TABLE agent_run_event_audit");
        assertThat(sql).contains("effective_config_json TEXT");
        assertThat(sql).contains("user_message TEXT");
        assertThat(sql).contains("final_message TEXT");
        assertThat(sql).contains("final_thinking TEXT");
        assertThat(sql).contains("prompt_text TEXT");
        assertThat(sql).contains("request_messages_json TEXT");
        assertThat(sql).contains("tool_arguments TEXT");
        assertThat(sql).contains("tool_result TEXT");
        assertThat(sql).contains("CONSTRAINT fk_agent_run_event_run");
        assertThat(sql).contains("idx_agent_run_audit_status_created");
        assertThat(sql).contains("idx_agent_run_event_run_seq");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentRunAuditSchemaMigrationTests test
```

Expected: FAIL because `V16__create_agent_run_audit.sql` does not exist yet.

- [ ] **Step 3: Add the Flyway migration**

Create `be/src/main/resources/db/migration/V16__create_agent_run_audit.sql`:

```sql
CREATE TABLE agent_run_audit
(
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    request_id            VARCHAR(64),
    trace_id              VARCHAR(64),
    thread_id             VARCHAR(128)             NOT NULL,
    user_id               BIGINT,
    username              VARCHAR(128),
    preset_id             BIGINT,
    runtime_fingerprint   VARCHAR(128),
    effective_config_json TEXT,
    user_message          TEXT,
    final_message         TEXT,
    final_thinking        TEXT,
    status                VARCHAR(32)              NOT NULL,
    model_call_count      INTEGER                  NOT NULL DEFAULT 0,
    tool_call_count       INTEGER                  NOT NULL DEFAULT 0,
    mcp_tool_call_count   INTEGER                  NOT NULL DEFAULT 0,
    started_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at           TIMESTAMP WITH TIME ZONE,
    duration_ms           BIGINT,
    error_code            VARCHAR(256),
    error_message         TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agent_run_audit_user_created ON agent_run_audit (user_id, created_at);
CREATE INDEX idx_agent_run_audit_thread ON agent_run_audit (thread_id);
CREATE INDEX idx_agent_run_audit_trace ON agent_run_audit (trace_id);
CREATE INDEX idx_agent_run_audit_request ON agent_run_audit (request_id);
CREATE INDEX idx_agent_run_audit_status_created ON agent_run_audit (status, created_at);

CREATE TABLE agent_run_event_audit
(
    id                   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    run_id               BIGINT                   NOT NULL,
    request_id           VARCHAR(64),
    trace_id             VARCHAR(64),
    thread_id            VARCHAR(128),
    seq_no               INTEGER                  NOT NULL,
    event_type           VARCHAR(32)              NOT NULL,
    react_round          INTEGER,
    tool_call_id         VARCHAR(128),
    tool_name            VARCHAR(192),
    tool_type            VARCHAR(32),
    mcp_server_code      VARCHAR(64),
    status               VARCHAR(32)              NOT NULL,
    started_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at          TIMESTAMP WITH TIME ZONE,
    duration_ms          BIGINT,
    model_provider       VARCHAR(32),
    model_name           VARCHAR(128),
    prompt_text          TEXT,
    request_messages_json TEXT,
    request_options_json TEXT,
    available_tools_json TEXT,
    response_text        TEXT,
    tool_arguments       TEXT,
    tool_result          TEXT,
    metadata_json        TEXT,
    error_code           VARCHAR(256),
    error_message        TEXT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_agent_run_event_run FOREIGN KEY (run_id) REFERENCES agent_run_audit (id)
);

CREATE INDEX idx_agent_run_event_run_seq ON agent_run_event_audit (run_id, seq_no);
CREATE INDEX idx_agent_run_event_trace ON agent_run_event_audit (trace_id);
CREATE INDEX idx_agent_run_event_tool_created ON agent_run_event_audit (tool_name, created_at);
```

- [ ] **Step 4: Add enums**

Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunAuditStatus.java`:

```java
package top.egon.mario.agent.observability.po.enums;

public enum AgentRunAuditStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventType.java`:

```java
package top.egon.mario.agent.observability.po.enums;

public enum AgentRunEventType {
    RUN_STARTED,
    USER_MESSAGE,
    MODEL_REQUEST,
    MODEL_RESPONSE,
    TOOL_REQUEST,
    TOOL_RESPONSE,
    ASSISTANT_THINK,
    ASSISTANT_MESSAGE,
    RUN_COMPLETED,
    RUN_FAILED,
    RUN_CANCELLED
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventStatus.java`:

```java
package top.egon.mario.agent.observability.po.enums;

public enum AgentRunEventStatus {
    STARTED,
    SUCCESS,
    FAILED,
    CANCELLED
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunToolType.java`:

```java
package top.egon.mario.agent.observability.po.enums;

public enum AgentRunToolType {
    LOCAL,
    MCP,
    UNKNOWN
}
```

- [ ] **Step 5: Run the migration test**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentRunAuditSchemaMigrationTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/resources/db/migration/V16__create_agent_run_audit.sql \
  be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunAuditStatus.java \
  be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventType.java \
  be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunEventStatus.java \
  be/src/main/java/top/egon/mario/agent/observability/po/enums/AgentRunToolType.java \
  be/src/test/java/top/egon/mario/agent/observability/AgentRunAuditSchemaMigrationTests.java
git commit -m "feat(agent): add agent run audit schema"
```

### Task 2: JPA Entities, Repositories, And Service Persistence

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/observability/po/AgentRunAuditPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/po/AgentRunEventAuditPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/repository/AgentRunAuditRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/repository/AgentRunEventAuditRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunAuditContext.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunAuditStart.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunEventRecord.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/service/AgentRunAuditService.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImplTests.java`

- [ ] **Step 1: Write the failing service test**

Create `be/src/test/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImplTests.java`:

```java
package top.egon.mario.agent.observability.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;
import top.egon.mario.agent.observability.po.AgentRunEventAuditPo;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.repository.AgentRunAuditRepository;
import top.egon.mario.agent.observability.repository.AgentRunEventAuditRepository;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunAuditServiceImplTests {

    @Test
    void startEventAndCompletePersistPlaintextAuditData() {
        AgentRunAuditRepository runRepository = mock(AgentRunAuditRepository.class);
        AgentRunEventAuditRepository eventRepository = mock(AgentRunEventAuditRepository.class);
        AtomicReference<AgentRunAuditPo> savedRun = new AtomicReference<>();
        List<AgentRunEventAuditPo> savedEvents = new ArrayList<>();
        when(runRepository.save(any(AgentRunAuditPo.class))).thenAnswer(invocation -> {
            AgentRunAuditPo po = invocation.getArgument(0);
            if (po.getId() == null) {
                po.setId(100L);
            }
            savedRun.set(po);
            return po;
        });
        when(runRepository.findById(100L)).thenAnswer(invocation -> Optional.of(savedRun.get()));
        when(eventRepository.save(any(AgentRunEventAuditPo.class))).thenAnswer(invocation -> {
            AgentRunEventAuditPo po = invocation.getArgument(0);
            if (po.getId() == null) {
                po.setId((long) savedEvents.size() + 1);
            }
            savedEvents.add(po);
            return po;
        });
        AgentRunAuditServiceImpl service = new AgentRunAuditServiceImpl(runRepository, eventRepository, new ObjectMapper());

        AgentRunAuditContext context = service.start(new AgentRunAuditStart(
                "request-1",
                "trace-1",
                8L,
                "luigi",
                "thread-1",
                9L,
                "fingerprint-1",
                "{\"systemPrompt\":\"full prompt\"}",
                "用户明文输入",
                Instant.parse("2026-06-15T01:00:00Z")
        ));
        service.record(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_REQUEST)
                .reactRound(1)
                .status(AgentRunEventStatus.SUCCESS)
                .promptText("system prompt\nuser prompt")
                .requestMessagesJson("[{\"role\":\"user\",\"content\":\"用户明文输入\"}]")
                .availableToolsJson("[\"searchWikipedia\"]")
                .startedAt(Instant.parse("2026-06-15T01:00:01Z"))
                .finishedAt(Instant.parse("2026-06-15T01:00:02Z"))
                .durationMs(1000L)
                .build());
        service.record(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_RESPONSE)
                .reactRound(1)
                .toolName("searchWikipedia")
                .toolCallId("call-1")
                .toolType(AgentRunToolType.LOCAL)
                .status(AgentRunEventStatus.SUCCESS)
                .toolResult("完整工具返回")
                .startedAt(Instant.parse("2026-06-15T01:00:02Z"))
                .finishedAt(Instant.parse("2026-06-15T01:00:03Z"))
                .durationMs(1000L)
                .build());
        service.complete(context, "最终回答", "思考内容", Instant.parse("2026-06-15T01:00:04Z"));

        AgentRunAuditPo run = savedRun.get();
        assertThat(run.getStatus()).isEqualTo(AgentRunAuditStatus.SUCCESS);
        assertThat(run.getUserMessage()).isEqualTo("用户明文输入");
        assertThat(run.getFinalMessage()).isEqualTo("最终回答");
        assertThat(run.getFinalThinking()).isEqualTo("思考内容");
        assertThat(run.getModelCallCount()).isEqualTo(1);
        assertThat(run.getToolCallCount()).isEqualTo(1);
        assertThat(run.getMcpToolCallCount()).isZero();
        assertThat(savedEvents).extracting(AgentRunEventAuditPo::getSeqNo).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(savedEvents).extracting(AgentRunEventAuditPo::getEventType)
                .containsExactly(AgentRunEventType.RUN_STARTED, AgentRunEventType.USER_MESSAGE,
                        AgentRunEventType.MODEL_REQUEST, AgentRunEventType.TOOL_RESPONSE,
                        AgentRunEventType.ASSISTANT_THINK, AgentRunEventType.ASSISTANT_MESSAGE,
                        AgentRunEventType.RUN_COMPLETED);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentRunAuditServiceImplTests test
```

Expected: FAIL because the observability classes do not exist.

- [ ] **Step 3: Add entities and repositories**

Create `be/src/main/java/top/egon/mario/agent/observability/po/AgentRunAuditPo.java`:

```java
package top.egon.mario.agent.observability.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_run_audit")
public class AgentRunAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "thread_id", nullable = false, length = 128)
    private String threadId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "preset_id")
    private Long presetId;

    @Column(name = "runtime_fingerprint", length = 128)
    private String runtimeFingerprint;

    @Column(name = "effective_config_json")
    private String effectiveConfigJson;

    @Column(name = "user_message")
    private String userMessage;

    @Column(name = "final_message")
    private String finalMessage;

    @Column(name = "final_thinking")
    private String finalThinking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentRunAuditStatus status = AgentRunAuditStatus.RUNNING;

    @Column(name = "model_call_count", nullable = false)
    private int modelCallCount;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount;

    @Column(name = "mcp_tool_call_count", nullable = false)
    private int mcpToolCallCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/po/AgentRunEventAuditPo.java`:

```java
package top.egon.mario.agent.observability.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_run_event_audit")
public class AgentRunEventAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AgentRunEventType eventType;

    @Column(name = "react_round")
    private Integer reactRound;

    @Column(name = "tool_call_id", length = 128)
    private String toolCallId;

    @Column(name = "tool_name", length = 192)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", length = 32)
    private AgentRunToolType toolType;

    @Column(name = "mcp_server_code", length = 64)
    private String mcpServerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentRunEventStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_provider", length = 32)
    private ModelProviderType modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_text")
    private String promptText;

    @Column(name = "request_messages_json")
    private String requestMessagesJson;

    @Column(name = "request_options_json")
    private String requestOptionsJson;

    @Column(name = "available_tools_json")
    private String availableToolsJson;

    @Column(name = "response_text")
    private String responseText;

    @Column(name = "tool_arguments")
    private String toolArguments;

    @Column(name = "tool_result")
    private String toolResult;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/repository/AgentRunAuditRepository.java`:

```java
package top.egon.mario.agent.observability.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;

public interface AgentRunAuditRepository extends JpaRepository<AgentRunAuditPo, Long>,
        JpaSpecificationExecutor<AgentRunAuditPo> {
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/repository/AgentRunEventAuditRepository.java`:

```java
package top.egon.mario.agent.observability.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.observability.po.AgentRunEventAuditPo;

import java.util.List;

public interface AgentRunEventAuditRepository extends JpaRepository<AgentRunEventAuditPo, Long> {

    List<AgentRunEventAuditPo> findByRunIdOrderBySeqNoAsc(Long runId);
}
```

- [ ] **Step 4: Add service model records**

Create `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunAuditContext.java`:

```java
package top.egon.mario.agent.observability.service.model;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public record AgentRunAuditContext(
        Long runId,
        String requestId,
        String traceId,
        Long userId,
        String username,
        String threadId,
        Long presetId,
        String runtimeFingerprint,
        AtomicInteger sequence,
        AtomicInteger reactRound
) {

    public static final String METADATA_KEY = "agentRunAuditContext";

    public Map<String, Object> metadata() {
        return Map.of(METADATA_KEY, this,
                "requestId", requestId,
                "traceId", traceId,
                "threadId", threadId,
                "userId", userId == null ? "" : userId);
    }

    public int nextSeq() {
        return sequence.incrementAndGet();
    }

    public int nextReactRound() {
        return reactRound.incrementAndGet();
    }
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunAuditStart.java`:

```java
package top.egon.mario.agent.observability.service.model;

import java.time.Instant;

public record AgentRunAuditStart(
        String requestId,
        String traceId,
        Long userId,
        String username,
        String threadId,
        Long presetId,
        String runtimeFingerprint,
        String effectiveConfigJson,
        String userMessage,
        Instant startedAt
) {
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/service/model/AgentRunEventRecord.java`:

```java
package top.egon.mario.agent.observability.service.model;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;

import java.time.Instant;

public record AgentRunEventRecord(
        AgentRunEventType eventType,
        Integer reactRound,
        String toolCallId,
        String toolName,
        AgentRunToolType toolType,
        String mcpServerCode,
        AgentRunEventStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        ModelProviderType modelProvider,
        String modelName,
        String promptText,
        String requestMessagesJson,
        String requestOptionsJson,
        String availableToolsJson,
        String responseText,
        String toolArguments,
        String toolResult,
        String metadataJson,
        String errorCode,
        String errorMessage
) {

    public static Builder builder(AgentRunEventType eventType) {
        return new Builder(eventType);
    }

    public static final class Builder {
        private final AgentRunEventType eventType;
        private Integer reactRound;
        private String toolCallId;
        private String toolName;
        private AgentRunToolType toolType;
        private String mcpServerCode;
        private AgentRunEventStatus status = AgentRunEventStatus.SUCCESS;
        private Instant startedAt;
        private Instant finishedAt;
        private Long durationMs;
        private ModelProviderType modelProvider;
        private String modelName;
        private String promptText;
        private String requestMessagesJson;
        private String requestOptionsJson;
        private String availableToolsJson;
        private String responseText;
        private String toolArguments;
        private String toolResult;
        private String metadataJson;
        private String errorCode;
        private String errorMessage;

        private Builder(AgentRunEventType eventType) {
            this.eventType = eventType;
        }

        public Builder reactRound(Integer reactRound) { this.reactRound = reactRound; return this; }
        public Builder toolCallId(String toolCallId) { this.toolCallId = toolCallId; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder toolType(AgentRunToolType toolType) { this.toolType = toolType; return this; }
        public Builder mcpServerCode(String mcpServerCode) { this.mcpServerCode = mcpServerCode; return this; }
        public Builder status(AgentRunEventStatus status) { this.status = status; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder finishedAt(Instant finishedAt) { this.finishedAt = finishedAt; return this; }
        public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }
        public Builder modelProvider(ModelProviderType modelProvider) { this.modelProvider = modelProvider; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder promptText(String promptText) { this.promptText = promptText; return this; }
        public Builder requestMessagesJson(String requestMessagesJson) { this.requestMessagesJson = requestMessagesJson; return this; }
        public Builder requestOptionsJson(String requestOptionsJson) { this.requestOptionsJson = requestOptionsJson; return this; }
        public Builder availableToolsJson(String availableToolsJson) { this.availableToolsJson = availableToolsJson; return this; }
        public Builder responseText(String responseText) { this.responseText = responseText; return this; }
        public Builder toolArguments(String toolArguments) { this.toolArguments = toolArguments; return this; }
        public Builder toolResult(String toolResult) { this.toolResult = toolResult; return this; }
        public Builder metadataJson(String metadataJson) { this.metadataJson = metadataJson; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public AgentRunEventRecord build() {
            return new AgentRunEventRecord(eventType, reactRound, toolCallId, toolName, toolType, mcpServerCode,
                    status, startedAt, finishedAt, durationMs, modelProvider, modelName, promptText,
                    requestMessagesJson, requestOptionsJson, availableToolsJson, responseText, toolArguments,
                    toolResult, metadataJson, errorCode, errorMessage);
        }
    }
}
```

- [ ] **Step 5: Add service interface and implementation**

Create `be/src/main/java/top/egon/mario/agent/observability/service/AgentRunAuditService.java`:

```java
package top.egon.mario.agent.observability.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

public interface AgentRunAuditService {

    AgentRunAuditContext start(AgentRunAuditStart start);

    void record(AgentRunAuditContext context, AgentRunEventRecord event);

    void complete(AgentRunAuditContext context, String finalMessage, String finalThinking, Instant finishedAt);

    void fail(AgentRunAuditContext context, String errorCode, String errorMessage, Instant finishedAt);

    void cancel(AgentRunAuditContext context, Instant finishedAt);

    Page<AgentRunAuditResponse> page(AgentRunAuditQuery query, Pageable pageable, RbacPrincipal principal);

    List<AgentRunEventAuditResponse> events(Long runId, RbacPrincipal principal);
}
```

Create DTO records now so the backend service and controller share the same response contract. Task 6 adds matching
frontend types.

Create `be/src/main/java/top/egon/mario/agent/observability/dto/request/AgentRunAuditQuery.java`:

```java
package top.egon.mario.agent.observability.dto.request;

import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;

import java.time.Instant;

public record AgentRunAuditQuery(
        Instant startAt,
        Instant endAt,
        Long userId,
        String username,
        String threadId,
        String requestId,
        String traceId,
        Long presetId,
        String toolName,
        AgentRunAuditStatus status
) {
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/dto/response/AgentRunAuditResponse.java`:

```java
package top.egon.mario.agent.observability.dto.response;

import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;

import java.time.Instant;

public record AgentRunAuditResponse(
        Long id,
        String requestId,
        String traceId,
        String threadId,
        Long userId,
        String username,
        Long presetId,
        String runtimeFingerprint,
        String effectiveConfigJson,
        String userMessage,
        String finalMessage,
        String finalThinking,
        AgentRunAuditStatus status,
        Integer modelCallCount,
        Integer toolCallCount,
        Integer mcpToolCallCount,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/dto/response/AgentRunEventAuditResponse.java`:

```java
package top.egon.mario.agent.observability.dto.response;

import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;

import java.time.Instant;

public record AgentRunEventAuditResponse(
        Long id,
        Long runId,
        String requestId,
        String traceId,
        String threadId,
        Integer seqNo,
        AgentRunEventType eventType,
        Integer reactRound,
        String toolCallId,
        String toolName,
        AgentRunToolType toolType,
        String mcpServerCode,
        AgentRunEventStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        ModelProviderType modelProvider,
        String modelName,
        String promptText,
        String requestMessagesJson,
        String requestOptionsJson,
        String availableToolsJson,
        String responseText,
        String toolArguments,
        String toolResult,
        String metadataJson,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
}
```

Create `be/src/main/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImpl.java`:

```java
package top.egon.mario.agent.observability.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.AgentRunAuditPo;
import top.egon.mario.agent.observability.po.AgentRunEventAuditPo;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.repository.AgentRunAuditRepository;
import top.egon.mario.agent.observability.repository.AgentRunEventAuditRepository;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.agent.service.AgentException;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunAuditServiceImpl implements AgentRunAuditService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final AgentRunAuditRepository runRepository;
    private final AgentRunEventAuditRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AgentRunAuditContext start(AgentRunAuditStart start) {
        Instant startedAt = start.startedAt() == null ? Instant.now() : start.startedAt();
        AgentRunAuditPo run = new AgentRunAuditPo();
        run.setRequestId(start.requestId());
        run.setTraceId(start.traceId());
        run.setUserId(start.userId());
        run.setUsername(start.username());
        run.setThreadId(start.threadId());
        run.setPresetId(start.presetId());
        run.setRuntimeFingerprint(start.runtimeFingerprint());
        run.setEffectiveConfigJson(start.effectiveConfigJson());
        run.setUserMessage(start.userMessage());
        run.setStatus(AgentRunAuditStatus.RUNNING);
        run.setStartedAt(startedAt);
        run.setCreatedAt(Instant.now());
        AgentRunAuditPo saved = runRepository.save(run);
        AgentRunAuditContext context = new AgentRunAuditContext(saved.getId(), start.requestId(), start.traceId(),
                start.userId(), start.username(), start.threadId(), start.presetId(), start.runtimeFingerprint(),
                new AtomicInteger(-1), new AtomicInteger(0));
        record(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_STARTED)
                .status(AgentRunEventStatus.SUCCESS)
                .metadataJson(start.effectiveConfigJson())
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        record(context, AgentRunEventRecord.builder(AgentRunEventType.USER_MESSAGE)
                .status(AgentRunEventStatus.SUCCESS)
                .responseText(start.userMessage())
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        return context;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AgentRunAuditContext context, AgentRunEventRecord event) {
        if (context == null || context.runId() == null || event == null) {
            return;
        }
        AgentRunEventAuditPo po = new AgentRunEventAuditPo();
        po.setRunId(context.runId());
        po.setRequestId(context.requestId());
        po.setTraceId(context.traceId());
        po.setThreadId(context.threadId());
        po.setSeqNo(context.nextSeq());
        po.setEventType(event.eventType());
        po.setReactRound(event.reactRound());
        po.setToolCallId(event.toolCallId());
        po.setToolName(event.toolName());
        po.setToolType(event.toolType());
        po.setMcpServerCode(event.mcpServerCode());
        po.setStatus(event.status() == null ? AgentRunEventStatus.SUCCESS : event.status());
        po.setStartedAt(event.startedAt() == null ? Instant.now() : event.startedAt());
        po.setFinishedAt(event.finishedAt());
        po.setDurationMs(event.durationMs());
        po.setModelProvider(event.modelProvider());
        po.setModelName(event.modelName());
        po.setPromptText(event.promptText());
        po.setRequestMessagesJson(event.requestMessagesJson());
        po.setRequestOptionsJson(event.requestOptionsJson());
        po.setAvailableToolsJson(event.availableToolsJson());
        po.setResponseText(event.responseText());
        po.setToolArguments(event.toolArguments());
        po.setToolResult(event.toolResult());
        po.setMetadataJson(event.metadataJson());
        po.setErrorCode(event.errorCode());
        po.setErrorMessage(event.errorMessage());
        po.setCreatedAt(Instant.now());
        eventRepository.save(po);
        incrementCounters(context.runId(), event);
        LogUtil.info(log).log("agent run audit event saved, runId={}, eventType={}, seqNo={}",
                context.runId(), event.eventType(), po.getSeqNo());
    }

    @Override
    @Transactional
    public void complete(AgentRunAuditContext context, String finalMessage, String finalThinking, Instant finishedAt) {
        finish(context, AgentRunAuditStatus.SUCCESS, finalMessage, finalThinking, null, null, finishedAt);
        if (StringUtils.hasText(finalThinking)) {
            record(context, AgentRunEventRecord.builder(AgentRunEventType.ASSISTANT_THINK)
                    .responseText(finalThinking)
                    .startedAt(finishedAt)
                    .finishedAt(finishedAt)
                    .durationMs(0L)
                    .build());
        }
        if (StringUtils.hasText(finalMessage)) {
            record(context, AgentRunEventRecord.builder(AgentRunEventType.ASSISTANT_MESSAGE)
                    .responseText(finalMessage)
                    .startedAt(finishedAt)
                    .finishedAt(finishedAt)
                    .durationMs(0L)
                    .build());
        }
        record(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_COMPLETED)
                .responseText(finalMessage)
                .startedAt(finishedAt)
                .finishedAt(finishedAt)
                .durationMs(0L)
                .build());
    }

    @Override
    @Transactional
    public void fail(AgentRunAuditContext context, String errorCode, String errorMessage, Instant finishedAt) {
        finish(context, AgentRunAuditStatus.FAILED, null, null, errorCode, errorMessage, finishedAt);
        record(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_FAILED)
                .status(AgentRunEventStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .startedAt(finishedAt)
                .finishedAt(finishedAt)
                .durationMs(0L)
                .build());
    }

    @Override
    @Transactional
    public void cancel(AgentRunAuditContext context, Instant finishedAt) {
        finish(context, AgentRunAuditStatus.CANCELLED, null, null, null, null, finishedAt);
        record(context, AgentRunEventRecord.builder(AgentRunEventType.RUN_CANCELLED)
                .status(AgentRunEventStatus.CANCELLED)
                .startedAt(finishedAt)
                .finishedAt(finishedAt)
                .durationMs(0L)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgentRunAuditResponse> page(AgentRunAuditQuery query, Pageable pageable, RbacPrincipal principal) {
        requireSuperAdmin(principal);
        return runRepository.findAll(specification(query), pageable).map(this::toRunResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRunEventAuditResponse> events(Long runId, RbacPrincipal principal) {
        requireSuperAdmin(principal);
        return eventRepository.findByRunIdOrderBySeqNoAsc(runId).stream().map(this::toEventResponse).toList();
    }

    private void finish(AgentRunAuditContext context, AgentRunAuditStatus status, String finalMessage,
                        String finalThinking, String errorCode, String errorMessage, Instant finishedAt) {
        if (context == null || context.runId() == null) {
            return;
        }
        AgentRunAuditPo run = runRepository.findById(context.runId())
                .orElseThrow(() -> new IllegalStateException("agent run audit not found: " + context.runId()));
        Instant endedAt = finishedAt == null ? Instant.now() : finishedAt;
        run.setStatus(status);
        run.setFinalMessage(finalMessage);
        run.setFinalThinking(finalThinking);
        run.setFinishedAt(endedAt);
        run.setDurationMs(durationMs(run.getStartedAt(), endedAt));
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        runRepository.save(run);
    }

    private void incrementCounters(Long runId, AgentRunEventRecord event) {
        if (event.eventType() != AgentRunEventType.MODEL_RESPONSE && event.eventType() != AgentRunEventType.TOOL_RESPONSE) {
            return;
        }
        runRepository.findById(runId).ifPresent(run -> {
            if (event.eventType() == AgentRunEventType.MODEL_RESPONSE) {
                run.setModelCallCount(run.getModelCallCount() + 1);
            }
            if (event.eventType() == AgentRunEventType.TOOL_RESPONSE) {
                run.setToolCallCount(run.getToolCallCount() + 1);
                if (event.toolType() == AgentRunToolType.MCP) {
                    run.setMcpToolCallCount(run.getMcpToolCallCount() + 1);
                }
            }
            runRepository.save(run);
        });
    }

    private Specification<AgentRunAuditPo> specification(AgentRunAuditQuery query) {
        AgentRunAuditQuery safeQuery = query == null
                ? new AgentRunAuditQuery(null, null, null, null, null, null, null, null, null, null)
                : query;
        return (root, ignored, cb) -> {
            java.util.ArrayList<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (safeQuery.startAt() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), safeQuery.startAt()));
            }
            if (safeQuery.endAt() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), safeQuery.endAt()));
            }
            if (safeQuery.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), safeQuery.userId()));
            }
            if (StringUtils.hasText(safeQuery.username())) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + safeQuery.username().trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(safeQuery.threadId())) {
                predicates.add(cb.equal(root.get("threadId"), safeQuery.threadId().trim()));
            }
            if (StringUtils.hasText(safeQuery.requestId())) {
                predicates.add(cb.equal(root.get("requestId"), safeQuery.requestId().trim()));
            }
            if (StringUtils.hasText(safeQuery.traceId())) {
                predicates.add(cb.equal(root.get("traceId"), safeQuery.traceId().trim()));
            }
            if (safeQuery.presetId() != null) {
                predicates.add(cb.equal(root.get("presetId"), safeQuery.presetId()));
            }
            if (safeQuery.status() != null) {
                predicates.add(cb.equal(root.get("status"), safeQuery.status()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private AgentRunAuditResponse toRunResponse(AgentRunAuditPo po) {
        return new AgentRunAuditResponse(po.getId(), po.getRequestId(), po.getTraceId(), po.getThreadId(),
                po.getUserId(), po.getUsername(), po.getPresetId(), po.getRuntimeFingerprint(),
                po.getEffectiveConfigJson(), po.getUserMessage(), po.getFinalMessage(), po.getFinalThinking(),
                po.getStatus(), po.getModelCallCount(), po.getToolCallCount(), po.getMcpToolCallCount(),
                po.getStartedAt(), po.getFinishedAt(), po.getDurationMs(), po.getErrorCode(), po.getErrorMessage(),
                po.getCreatedAt());
    }

    private AgentRunEventAuditResponse toEventResponse(AgentRunEventAuditPo po) {
        return new AgentRunEventAuditResponse(po.getId(), po.getRunId(), po.getRequestId(), po.getTraceId(),
                po.getThreadId(), po.getSeqNo(), po.getEventType(), po.getReactRound(), po.getToolCallId(),
                po.getToolName(), po.getToolType(), po.getMcpServerCode(), po.getStatus(), po.getStartedAt(),
                po.getFinishedAt(), po.getDurationMs(), po.getModelProvider(), po.getModelName(), po.getPromptText(),
                po.getRequestMessagesJson(), po.getRequestOptionsJson(), po.getAvailableToolsJson(),
                po.getResponseText(), po.getToolArguments(), po.getToolResult(), po.getMetadataJson(),
                po.getErrorCode(), po.getErrorMessage(), po.getCreatedAt());
    }

    private Long durationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private void requireSuperAdmin(RbacPrincipal principal) {
        if (principal == null || principal.roleCodes() == null || !principal.roleCodes().contains(SUPER_ADMIN_ROLE_CODE)) {
            throw new AgentException("AGENT_RUN_AUDIT_FORBIDDEN", "agent run audits are only available to super administrators");
        }
    }
}
```

- [ ] **Step 6: Run the service test and adjust the test stubs if needed**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentRunAuditServiceImplTests test
```

Expected: PASS. If the in-memory repository stubs are awkward because of Spring Data inherited methods, convert them to
Mockito mocks while preserving the assertions for saved run and event rows.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/observability \
  be/src/test/java/top/egon/mario/agent/observability/service/impl/AgentRunAuditServiceImplTests.java
git commit -m "feat(agent): persist agent run audit events"
```

### Task 3: Model And Tool Observability Interceptors

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptor.java`
- Create: `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptor.java`
- Test: `be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptorTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptorTests.java`

- [ ] **Step 1: Write the failing model interceptor test**

Create `be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptorTests.java`:

```java
package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentObservabilityModelInterceptorTests {

    @Test
    void recordsModelRequestAndResponseWithPlaintextPrompt() {
        CapturingRunAuditService auditService = new CapturingRunAuditService();
        AgentObservabilityModelInterceptor interceptor = new AgentObservabilityModelInterceptor(auditService, new com.fasterxml.jackson.databind.ObjectMapper());
        AgentRunAuditContext context = context();
        ModelRequest request = ModelRequest.builder()
                .systemMessage(new SystemMessage("system prompt"))
                .messages(List.of(new UserMessage("user prompt")))
                .tools(List.of("searchWikipedia"))
                .toolDescriptions(Map.of("searchWikipedia", "Search Wikipedia"))
                .options(ToolCallingChatOptions.builder().model("qwen-plus").build())
                .context(Map.of(AgentRunAuditContext.METADATA_KEY, context))
                .build();

        ModelResponse response = interceptor.interceptModel(request,
                ignored -> ModelResponse.of(new AssistantMessage("assistant answer")));

        assertThat(response.getMessage()).isInstanceOf(AssistantMessage.class);
        assertThat(auditService.events).hasSize(2);
        AgentRunEventRecord modelRequest = auditService.events.get(0);
        AgentRunEventRecord modelResponse = auditService.events.get(1);
        assertThat(modelRequest.eventType()).isEqualTo(AgentRunEventType.MODEL_REQUEST);
        assertThat(modelRequest.reactRound()).isEqualTo(1);
        assertThat(modelRequest.promptText()).contains("system prompt").contains("user prompt");
        assertThat(modelRequest.requestMessagesJson()).contains("user prompt");
        assertThat(modelRequest.availableToolsJson()).contains("searchWikipedia");
        assertThat(modelResponse.eventType()).isEqualTo(AgentRunEventType.MODEL_RESPONSE);
        assertThat(modelResponse.reactRound()).isEqualTo(1);
        assertThat(modelResponse.responseText()).contains("assistant answer");
    }

    private AgentRunAuditContext context() {
        return new AgentRunAuditContext(7L, "request-1", "trace-1", 8L, "luigi", "thread-1", 9L,
                "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(0));
    }

    private static final class CapturingRunAuditService implements AgentRunAuditService {
        private final List<AgentRunEventRecord> events = new ArrayList<>();

        @Override
        public void record(AgentRunAuditContext context, AgentRunEventRecord event) {
            events.add(event);
        }

        @Override
        public AgentRunAuditContext start(AgentRunAuditStart start) {
            return null;
        }

        @Override
        public void complete(AgentRunAuditContext context, String finalMessage, String finalThinking, Instant finishedAt) {
        }

        @Override
        public void fail(AgentRunAuditContext context, String errorCode, String errorMessage, Instant finishedAt) {
        }

        @Override
        public void cancel(AgentRunAuditContext context, Instant finishedAt) {
        }

        @Override
        public Page<AgentRunAuditResponse> page(AgentRunAuditQuery query, Pageable pageable, RbacPrincipal principal) {
            return Page.empty();
        }

        @Override
        public List<AgentRunEventAuditResponse> events(Long runId, RbacPrincipal principal) {
            return Collections.emptyList();
        }
    }
}
```

- [ ] **Step 2: Write the failing tool interceptor test**

Create `be/src/test/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptorTests.java`:

```java
package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunAuditStart;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentObservabilityToolInterceptorTests {

    @Test
    void recordsLocalToolRequestAndResponse() {
        CapturingRunAuditService auditService = new CapturingRunAuditService();
        AgentObservabilityToolInterceptor interceptor = new AgentObservabilityToolInterceptor(auditService);
        AgentRunAuditContext context = context();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("searchWikipedia")
                .toolCallId("call-1")
                .arguments("{\"query\":\"Spring AI\"}")
                .context(Map.of(AgentRunAuditContext.METADATA_KEY, context))
                .build();

        ToolCallResponse response = interceptor.interceptToolCall(request,
                ignored -> ToolCallResponse.of("call-1", "searchWikipedia", "tool result"));

        assertThat(response.getResult()).isEqualTo("tool result");
        assertThat(auditService.events).hasSize(2);
        assertThat(auditService.events.get(0).eventType()).isEqualTo(AgentRunEventType.TOOL_REQUEST);
        assertThat(auditService.events.get(0).toolArguments()).contains("Spring AI");
        assertThat(auditService.events.get(1).eventType()).isEqualTo(AgentRunEventType.TOOL_RESPONSE);
        assertThat(auditService.events.get(1).toolType()).isEqualTo(AgentRunToolType.LOCAL);
        assertThat(auditService.events.get(1).toolResult()).isEqualTo("tool result");
    }

    @Test
    void recordsMcpToolMetadataWhenPresent() {
        CapturingRunAuditService auditService = new CapturingRunAuditService();
        AgentObservabilityToolInterceptor interceptor = new AgentObservabilityToolInterceptor(auditService);
        AgentRunAuditContext context = context();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("docs_search")
                .toolCallId("call-2")
                .arguments("{}")
                .context(Map.of(AgentRunAuditContext.METADATA_KEY, context,
                        "toolType", "MCP",
                        "mcpServerCode", "docs"))
                .build();

        interceptor.interceptToolCall(request, ignored -> ToolCallResponse.of("call-2", "docs_search", "ok"));

        AgentRunEventRecord response = auditService.events.get(1);
        assertThat(response.toolType()).isEqualTo(AgentRunToolType.MCP);
        assertThat(response.mcpServerCode()).isEqualTo("docs");
    }

    private AgentRunAuditContext context() {
        return new AgentRunAuditContext(7L, "request-1", "trace-1", 8L, "luigi", "thread-1", 9L,
                "fingerprint-1", new AtomicInteger(-1), new AtomicInteger(0));
    }

    private static final class CapturingRunAuditService implements AgentRunAuditService {
        private final List<AgentRunEventRecord> events = new ArrayList<>();

        @Override
        public void record(AgentRunAuditContext context, AgentRunEventRecord event) {
            events.add(event);
        }

        @Override
        public AgentRunAuditContext start(AgentRunAuditStart start) {
            return null;
        }

        @Override
        public void complete(AgentRunAuditContext context, String finalMessage, String finalThinking, Instant finishedAt) {
        }

        @Override
        public void fail(AgentRunAuditContext context, String errorCode, String errorMessage, Instant finishedAt) {
        }

        @Override
        public void cancel(AgentRunAuditContext context, Instant finishedAt) {
        }

        @Override
        public Page<AgentRunAuditResponse> page(AgentRunAuditQuery query, Pageable pageable, RbacPrincipal principal) {
            return Page.empty();
        }

        @Override
        public List<AgentRunEventAuditResponse> events(Long runId, RbacPrincipal principal) {
            return Collections.emptyList();
        }
    }
}
```

- [ ] **Step 3: Run the interceptor tests to verify they fail**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentObservabilityModelInterceptorTests,AgentObservabilityToolInterceptorTests test
```

Expected: FAIL because interceptor classes do not exist.

- [ ] **Step 4: Add the model interceptor**

Create `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityModelInterceptor.java`:

```java
package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.common.utils.LogUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class AgentObservabilityModelInterceptor extends ModelInterceptor {

    private final AgentRunAuditService auditService;
    private final ObjectMapper objectMapper;

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        AgentRunAuditContext context = context(request);
        if (context == null) {
            return handler.call(request);
        }
        int round = context.nextReactRound();
        Instant startedAt = Instant.now();
        auditService.record(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_REQUEST)
                .reactRound(round)
                .promptText(promptText(request))
                .requestMessagesJson(json(request.getMessages()))
                .requestOptionsJson(json(request.getOptions()))
                .availableToolsJson(json(request.getTools()))
                .metadataJson(json(request.getToolDescriptions()))
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        LogUtil.info(log).log("agent model round started, runId={}, round={}, tools={}",
                context.runId(), round, request.getTools());
        if (log.isDebugEnabled()) {
            LogUtil.debug(log).log("agent model round request, runId={}, round={}, prompt={}, messages={}, options={}",
                    context.runId(), round, promptText(request), json(request.getMessages()), json(request.getOptions()));
        }
        try {
            ModelResponse response = handler.call(request);
            Object message = response.getMessage();
            if (message instanceof Flux<?> flux) {
                return ModelResponse.of(wrapStream(context, round, startedAt, flux));
            }
            recordModelResponse(context, round, startedAt, response, null);
            return response;
        } catch (RuntimeException e) {
            recordModelResponse(context, round, startedAt, null, e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<ChatResponse> wrapStream(AgentRunAuditContext context, int round, Instant startedAt, Flux<?> flux) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        StringBuilder responseText = new StringBuilder();
        return ((Flux<ChatResponse>) flux)
                .doOnNext(response -> responseText.append(responseText(response)))
                .doOnError(failure::set)
                .doFinally(signalType -> {
                    Throwable error = failure.get();
                    AgentRunEventStatus status = signalType == SignalType.CANCEL
                            ? AgentRunEventStatus.CANCELLED
                            : error == null ? AgentRunEventStatus.SUCCESS : AgentRunEventStatus.FAILED;
                    Instant finishedAt = Instant.now();
                    auditService.record(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_RESPONSE)
                            .reactRound(round)
                            .status(status)
                            .responseText(responseText.toString())
                            .startedAt(startedAt)
                            .finishedAt(finishedAt)
                            .durationMs(Duration.between(startedAt, finishedAt).toMillis())
                            .errorCode(error == null ? null : error.getClass().getName())
                            .errorMessage(error == null ? null : error.getMessage())
                            .build());
                    if (log.isDebugEnabled()) {
                        LogUtil.debug(log).log("agent model round response, runId={}, round={}, response={}",
                                context.runId(), round, responseText);
                    }
                });
    }

    private void recordModelResponse(AgentRunAuditContext context, int round, Instant startedAt,
                                     ModelResponse response, RuntimeException error) {
        Instant finishedAt = Instant.now();
        auditService.record(context, AgentRunEventRecord.builder(AgentRunEventType.MODEL_RESPONSE)
                .reactRound(round)
                .status(error == null ? AgentRunEventStatus.SUCCESS : AgentRunEventStatus.FAILED)
                .responseText(responseText(response))
                .metadataJson(json(response == null ? null : response.getChatResponse()))
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs(Duration.between(startedAt, finishedAt).toMillis())
                .errorCode(error == null ? null : error.getClass().getName())
                .errorMessage(error == null ? null : error.getMessage())
                .build());
        LogUtil.info(log).log("agent model round completed, runId={}, round={}, status={}, costMs={}",
                context.runId(), round, error == null ? "SUCCESS" : "FAILED", Duration.between(startedAt, finishedAt).toMillis());
        if (log.isDebugEnabled()) {
            LogUtil.debug(log).log("agent model round response, runId={}, round={}, response={}",
                    context.runId(), round, responseText(response));
        }
    }

    private AgentRunAuditContext context(ModelRequest request) {
        if (request == null || request.getContext() == null) {
            return null;
        }
        Object value = request.getContext().get(AgentRunAuditContext.METADATA_KEY);
        return value instanceof AgentRunAuditContext context ? context : null;
    }

    private String promptText(ModelRequest request) {
        String system = request.getSystemMessage() == null ? "" : request.getSystemMessage().getText();
        String messages = request.getMessages() == null ? "" : request.getMessages().stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n"));
        return (system + "\n" + messages).trim();
    }

    private String responseText(ModelResponse response) {
        if (response == null || response.getMessage() == null) {
            return null;
        }
        Object message = response.getMessage();
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        }
        return String.valueOf(message);
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText() == null ? "" : response.getResult().getOutput().getText();
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    @Override
    public String getName() {
        return "AgentObservabilityModelInterceptor";
    }
}
```

- [ ] **Step 5: Add the tool interceptor**

Create `be/src/main/java/top/egon/mario/agent/observability/interceptor/AgentObservabilityToolInterceptor.java`:

```java
package top.egon.mario.agent.observability.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.agent.observability.service.model.AgentRunAuditContext;
import top.egon.mario.agent.observability.service.model.AgentRunEventRecord;
import top.egon.mario.common.utils.LogUtil;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
@Slf4j
public class AgentObservabilityToolInterceptor extends ToolInterceptor {

    private final AgentRunAuditService auditService;

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        AgentRunAuditContext context = context(request);
        if (context == null) {
            return handler.call(request);
        }
        Instant startedAt = Instant.now();
        AgentRunToolType toolType = toolType(request);
        String mcpServerCode = value(request, "mcpServerCode");
        auditService.record(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_REQUEST)
                .toolCallId(request.getToolCallId())
                .toolName(request.getToolName())
                .toolType(toolType)
                .mcpServerCode(mcpServerCode)
                .toolArguments(request.getArguments())
                .startedAt(startedAt)
                .finishedAt(startedAt)
                .durationMs(0L)
                .build());
        LogUtil.info(log).log("agent tool call started, runId={}, toolName={}, toolType={}",
                context.runId(), request.getToolName(), toolType);
        if (log.isDebugEnabled()) {
            LogUtil.debug(log).log("agent tool call request, runId={}, toolName={}, arguments={}",
                    context.runId(), request.getToolName(), request.getArguments());
        }
        try {
            ToolCallResponse response = handler.call(request);
            recordResponse(context, request, response, toolType, mcpServerCode, startedAt, null);
            return response;
        } catch (RuntimeException e) {
            recordResponse(context, request, null, toolType, mcpServerCode, startedAt, e);
            throw e;
        }
    }

    private void recordResponse(AgentRunAuditContext context, ToolCallRequest request, ToolCallResponse response,
                                AgentRunToolType toolType, String mcpServerCode, Instant startedAt, RuntimeException error) {
        Instant finishedAt = Instant.now();
        auditService.record(context, AgentRunEventRecord.builder(AgentRunEventType.TOOL_RESPONSE)
                .toolCallId(request.getToolCallId())
                .toolName(request.getToolName())
                .toolType(toolType)
                .mcpServerCode(mcpServerCode)
                .status(error == null && (response == null || !response.isError())
                        ? AgentRunEventStatus.SUCCESS : AgentRunEventStatus.FAILED)
                .toolArguments(request.getArguments())
                .toolResult(response == null ? null : response.getResult())
                .metadataJson(response == null ? null : String.valueOf(response.getMetadata()))
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs(Duration.between(startedAt, finishedAt).toMillis())
                .errorCode(error == null ? null : error.getClass().getName())
                .errorMessage(error == null ? null : error.getMessage())
                .build());
        LogUtil.info(log).log("agent tool call completed, runId={}, toolName={}, status={}, costMs={}",
                context.runId(), request.getToolName(), error == null ? "SUCCESS" : "FAILED",
                Duration.between(startedAt, finishedAt).toMillis());
        if (log.isDebugEnabled()) {
            LogUtil.debug(log).log("agent tool call response, runId={}, toolName={}, result={}",
                    context.runId(), request.getToolName(), response == null ? null : response.getResult());
        }
    }

    private AgentRunAuditContext context(ToolCallRequest request) {
        Object value = request.getContext().get(AgentRunAuditContext.METADATA_KEY);
        return value instanceof AgentRunAuditContext context ? context : null;
    }

    private AgentRunToolType toolType(ToolCallRequest request) {
        String value = value(request, "toolType");
        if ("MCP".equalsIgnoreCase(value)) {
            return AgentRunToolType.MCP;
        }
        if ("LOCAL".equalsIgnoreCase(value)) {
            return AgentRunToolType.LOCAL;
        }
        return AgentRunToolType.LOCAL;
    }

    private String value(ToolCallRequest request, String key) {
        Object value = request.getContext().get(key);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public String getName() {
        return "AgentObservabilityToolInterceptor";
    }
}
```

- [ ] **Step 6: Run interceptor tests**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentObservabilityModelInterceptorTests,AgentObservabilityToolInterceptorTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/observability/interceptor \
  be/src/test/java/top/egon/mario/agent/observability/interceptor
git commit -m "feat(agent): capture model and tool audit events"
```

### Task 4: Wire Run Context Into Chat Service And Runtime Factory

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java`
- Modify: `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`
- Modify: `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`
- Modify: `be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java`

- [ ] **Step 1: Add failing chat-service lifecycle assertions**

Modify `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`:

1. Add a mocked field in `TestSupport`:

```java
private final top.egon.mario.agent.observability.service.AgentRunAuditService runAuditService =
        mock(top.egon.mario.agent.observability.service.AgentRunAuditService.class);
```

2. In `TestSupport` constructor, create a context and stub `start`:

```java
top.egon.mario.agent.observability.service.model.AgentRunAuditContext runContext =
        new top.egon.mario.agent.observability.service.model.AgentRunAuditContext(100L, "request-1", "trace-1",
                8L, "luigi", "thread-1", 9L, "fingerprint-1",
                new java.util.concurrent.atomic.AtomicInteger(-1), new java.util.concurrent.atomic.AtomicInteger(0));
given(runAuditService.start(any())).willReturn(runContext);
```

3. Update the constructor call:

```java
this.chatService = new ReactAgentChatService(presetService, runtimeFactory, auditService, runAuditService,
        Schedulers.immediate(), userContext);
```

4. In `debugChatResolvesSpecAndWritesSuccessfulAudit`, add:

```java
verify(support.runAuditService).start(any());
verify(support.runAuditService).complete(any(), eq("答案"), eq(""), any(Instant.class));
```

5. In `debugChatMarksAuditFailedAndReturnsErrorChunkWhenAgentFails`, add:

```java
verify(support.runAuditService).fail(any(), eq(IllegalStateException.class.getName()), eq("boom"), any(Instant.class));
```

- [ ] **Step 2: Add failing runtime factory assertions**

Modify `be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java`:

1. Add fields to `StubAgentBuilder`:

```java
private boolean hasObservabilityModelInterceptor;
private boolean hasObservabilityToolInterceptor;
```

2. In `StubAgentBuilder.build(...)`, inspect the new `request.interceptors()` list:

```java
this.hasObservabilityModelInterceptor = request.interceptors().stream()
        .anyMatch(interceptor -> interceptor.getClass().getSimpleName().equals("AgentObservabilityModelInterceptor"));
this.hasObservabilityToolInterceptor = request.interceptors().stream()
        .anyMatch(interceptor -> interceptor.getClass().getSimpleName().equals("AgentObservabilityToolInterceptor"));
```

3. Add these assertions to `getResolvesModelAndBuildsAgentWithEnabledTools()`:

```java
assertThat(builder.hasObservabilityModelInterceptor).isTrue();
assertThat(builder.hasObservabilityToolInterceptor).isTrue();
```

4. Change factory construction in tests to pass `mock(AgentRunAuditService.class)` and `new ObjectMapper()` after the
   constructor signature is updated.

- [ ] **Step 3: Run the chat/runtime tests to verify they fail**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ReactAgentChatServiceTests,AgentRuntimeFactoryTests test
```

Expected: FAIL because constructors and runtime wiring do not yet include run audit service/interceptors.

- [ ] **Step 4: Modify `ReactAgentChatService`**

In `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`:

1. Add a field:

```java
private final AgentRunAuditService runAuditService;
```

2. Update the constructor:

```java
public ReactAgentChatService(AgentPresetService agentPresetService, AgentRuntimeFactory agentRuntimeFactory,
                             AgentConversationAuditService auditService, AgentRunAuditService runAuditService,
                             Scheduler blockingScheduler, ArxivToolUserContext arxivToolUserContext) {
    this.agentPresetService = agentPresetService;
    this.agentRuntimeFactory = agentRuntimeFactory;
    this.auditService = auditService;
    this.runAuditService = runAuditService;
    this.blockingScheduler = blockingScheduler;
    this.arxivToolUserContext = arxivToolUserContext;
}
```

3. Build run context before `RunnableConfig`:

```java
String requestId = UUID.randomUUID().toString();
AgentRuntimeSpec spec = debugRequest == null
        ? agentPresetService.defaultRuntimeSpec()
        : agentPresetService.resolveRuntimeSpec(debugRequest);
AgentRunAuditContext runAuditContext = runAuditService.start(new AgentRunAuditStart(
        requestId,
        traceId,
        principal == null ? null : principal.userId(),
        principal == null ? null : principal.username(),
        conversationThreadId,
        spec.presetId(),
        spec.fingerprint(),
        agentPresetService.serializeRuntimeSpec(spec),
        message,
        Instant.now()
));
RunnableConfig config = RunnableConfig.builder()
        .threadId(conversationThreadId)
        .addMetadata(AgentRunAuditContext.METADATA_KEY, runAuditContext)
        .addMetadata("requestId", requestId)
        .addMetadata("traceId", traceId)
        .addMetadata("threadId", conversationThreadId)
        .addMetadata("userId", principal == null ? null : principal.userId())
        .build();
```

4. Reuse `requestId` and `spec` instead of creating them again inside the existing defer block.

5. In `finishAudit`, after existing `auditService.complete`, call:

```java
runAuditService.complete(runAuditContext, messageContent, thinkContent, Instant.now());
```

6. In the cancellation branch, call:

```java
runAuditService.cancel(runAuditContext, Instant.now());
```

7. In `failAudit`, call:

```java
runAuditService.fail(runAuditContext, error.getClass().getName(), error.getMessage(), Instant.now());
```

If method signatures become too large, introduce private helper methods `completeRunAudit`, `failRunAudit`, and
`cancelRunAudit` in the same class. Keep behavior identical to the existing conversation audit.

- [ ] **Step 5: Modify `AgentConfiguration`**

In `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`, import `AgentRunAuditService` and update the
bean:

```java
public ChatAgentService chatAgentService(AgentPresetService agentPresetService,
                                         AgentRuntimeFactory agentRuntimeFactory,
                                         AgentConversationAuditService auditService,
                                         AgentRunAuditService runAuditService,
                                         Scheduler blockingScheduler,
                                         ArxivToolUserContext arxivToolUserContext) {
    return new ReactAgentChatService(agentPresetService, agentRuntimeFactory, auditService, runAuditService,
            blockingScheduler, arxivToolUserContext);
}
```

- [ ] **Step 6: Modify `DefaultAgentRuntimeFactory`**

Add dependencies:

```java
private final AgentRunAuditService runAuditService;
private final ObjectMapper objectMapper;
```

Update constructors so Spring injects these dependencies. Add a new field to `AgentBuildRequest`:

```java
List<com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor> interceptors
```

When constructing `AgentBuildRequest`, pass:

```java
List.of(
        new AgentObservabilityModelInterceptor(runAuditService, objectMapper),
        new AgentObservabilityToolInterceptor(runAuditService),
        new ToolMonitorInterceptor()
)
```

In `ReactAgentBuilder.build`, replace the existing `.interceptors(new ToolMonitorInterceptor())` call with:

```java
.interceptors(request.interceptors())
```

- [ ] **Step 7: Run chat/runtime tests**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ReactAgentChatServiceTests,AgentRuntimeFactoryTests test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java \
  be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java \
  be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java \
  be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java \
  be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java
git commit -m "feat(agent): wire agent run audit context"
```

### Task 5: SUPER_ADMIN API And RBAC Resources

**Files:**

- Create: `be/src/main/java/top/egon/mario/agent/observability/web/AdminAgentRunAuditController.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProvider.java`
- Test: `be/src/test/java/top/egon/mario/agent/observability/web/AdminAgentRunAuditControllerTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProviderTests.java`

- [ ] **Step 1: Write failing controller test**

Create `be/src/test/java/top/egon/mario/agent/observability/web/AdminAgentRunAuditControllerTests.java`:

```java
package top.egon.mario.agent.observability.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAgentRunAuditControllerTests {

    @Test
    void pageReturnsRunAudits() {
        AgentRunAuditService service = mock(AgentRunAuditService.class);
        AdminAgentRunAuditController controller = new AdminAgentRunAuditController(service, Schedulers.immediate());
        AgentRunAuditResponse row = new AgentRunAuditResponse(1L, "request-1", "trace-1", "thread-1",
                8L, "luigi", 9L, "fingerprint", "{}", "hello", "answer", "think",
                AgentRunAuditStatus.SUCCESS, 1, 1, 0, Instant.now(), Instant.now(), 1000L,
                null, null, Instant.now());
        when(service.page(any(AgentRunAuditQuery.class), any(Pageable.class), any(RbacPrincipal.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        StepVerifier.create(controller.page(1, 20, null, null, null, null, null, null,
                        null, null, null, AgentRunAuditStatus.SUCCESS,
                        new RbacPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of(), "v1")))
                .assertNext(response -> {
                    assertThat(response.data().records()).hasSize(1);
                    assertThat(response.data().records().getFirst().requestId()).isEqualTo("request-1");
                })
                .verifyComplete();
    }
}
```

- [ ] **Step 2: Add failing RBAC provider assertions**

Modify `be/src/test/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProviderTests.java` to assert
resources include:

```java
assertThat(codes).contains(
        "menu:agent:run-audit",
        "api:agent:run-audit:collection",
        "api:agent:run-audit:*"
);
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AdminAgentRunAuditControllerTests,AgentDebugRbacResourceProviderTests test
```

Expected: FAIL because controller and RBAC resources do not exist.

- [ ] **Step 4: Add controller**

Create `be/src/main/java/top/egon/mario/agent/observability/web/AdminAgentRunAuditController.java`:

```java
package top.egon.mario.agent.observability.web;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agent/run-audits")
@Validated
public class AdminAgentRunAuditController extends top.egon.mario.agent.web.ReactiveAgentSupport {

    private final AgentRunAuditService auditService;
    private final Scheduler blockingScheduler;

    @GetMapping
    public Mono<ApiResponse<PageResult<AgentRunAuditResponse>>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String threadId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Long presetId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) AgentRunAuditStatus status,
            @AuthenticationPrincipal RbacPrincipal principal) {
        AgentRunAuditQuery query = new AgentRunAuditQuery(startAt, endAt, userId, username, threadId,
                requestId, traceId, presetId, toolName, status);
        return blocking(() -> pageResult(auditService.page(query,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()), principal)));
    }

    @GetMapping("/{id}/events")
    public Mono<ApiResponse<List<AgentRunEventAuditResponse>>> events(@PathVariable @Min(1) Long id,
                                                                      @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> auditService.events(id, principal));
    }
}
```

- [ ] **Step 5: Add RBAC resources**

Modify `be/src/main/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProvider.java`:

1. Add `runAuditMenu()`, `runAuditCollectionApi()`, and `runAuditApi()`.
2. Include them in `resources()` after `conversationAuditMenu()`.
3. Do not add these permissions to `CHAT_BASIC`; SUPER_ADMIN bypass should own access.

Use these resource codes and menu path:

```java
"menu:agent:run-audit"
"/agent/run-audits"
"api:agent:run-audit:collection"
"/api/admin/agent/run-audits"
"api:agent:run-audit:*"
"/api/admin/agent/run-audits/**"
```

- [ ] **Step 6: Run controller/RBAC tests**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AdminAgentRunAuditControllerTests,AgentDebugRbacResourceProviderTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/observability/web/AdminAgentRunAuditController.java \
  be/src/main/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProvider.java \
  be/src/test/java/top/egon/mario/agent/observability/web/AdminAgentRunAuditControllerTests.java \
  be/src/test/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProviderTests.java
git commit -m "feat(agent): expose agent run audit APIs"
```

### Task 6: Frontend Service Types And Routes

**Files:**

- Modify: `fe/src/modules/agent/agentTypes.ts`
- Modify: `fe/src/modules/agent/agentService.ts`
- Modify: `fe/src/modules/agent/agentService.test.ts`
- Modify: `fe/src/app/routes.tsx`

- [ ] **Step 1: Add failing frontend service tests**

Modify `fe/src/modules/agent/agentService.test.ts` imports:

```ts
import {
    createAgentPreset,
    deleteAgentPreset,
    getAgentConversationAuditMessages,
    getAgentConversationAudits,
    getAgentPresets,
    getAgentRunAuditEvents,
    getAgentRunAudits,
    streamAgentDebugChat,
    updateAgentPreset,
    updateAgentPresetStatus,
} from './agentService'
```

Add test:

```ts
test('builds agent run audit list and event requests', async () => {
    const {requestJson} = await import('../../services/request')

    void getAgentRunAudits({
        page: 2,
        size: 50,
        username: 'luigi',
        status: 'FAILED',
        toolName: 'searchWikipedia',
    })
    void getAgentRunAuditEvents(99)

    expect(requestJson).toHaveBeenNthCalledWith(
        1,
        '/api/admin/agent/run-audits?page=2&size=50&username=luigi&toolName=searchWikipedia&status=FAILED',
    )
    expect(requestJson).toHaveBeenNthCalledWith(2, '/api/admin/agent/run-audits/99/events')
})
```

- [ ] **Step 2: Run frontend test to verify it fails**

Run:

```bash
cd fe
npm test -- agentService.test.ts
```

Expected: FAIL because `getAgentRunAudits` and `getAgentRunAuditEvents` do not exist.

- [ ] **Step 3: Add TypeScript types**

Append to `fe/src/modules/agent/agentTypes.ts`:

```ts
export type AgentRunAuditStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
export type AgentRunEventType =
    | 'RUN_STARTED'
    | 'USER_MESSAGE'
    | 'MODEL_REQUEST'
    | 'MODEL_RESPONSE'
    | 'TOOL_REQUEST'
    | 'TOOL_RESPONSE'
    | 'ASSISTANT_THINK'
    | 'ASSISTANT_MESSAGE'
    | 'RUN_COMPLETED'
    | 'RUN_FAILED'
    | 'RUN_CANCELLED'
export type AgentRunEventStatus = 'STARTED' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
export type AgentRunToolType = 'LOCAL' | 'MCP' | 'UNKNOWN'

export type AgentRunAuditResponse = {
    id: number
    requestId?: string
    traceId?: string
    threadId: string
    userId?: number
    username?: string
    presetId?: number
    runtimeFingerprint?: string
    effectiveConfigJson?: string
    userMessage?: string
    finalMessage?: string
    finalThinking?: string
    status: AgentRunAuditStatus
    modelCallCount: number
    toolCallCount: number
    mcpToolCallCount: number
    startedAt: string
    finishedAt?: string
    durationMs?: number
    errorCode?: string
    errorMessage?: string
    createdAt?: string
}

export type AgentRunEventAuditResponse = {
    id: number
    runId: number
    requestId?: string
    traceId?: string
    threadId?: string
    seqNo: number
    eventType: AgentRunEventType
    reactRound?: number
    toolCallId?: string
    toolName?: string
    toolType?: AgentRunToolType
    mcpServerCode?: string
    status: AgentRunEventStatus
    startedAt: string
    finishedAt?: string
    durationMs?: number
    modelProvider?: AgentModelProviderType
    modelName?: string
    promptText?: string
    requestMessagesJson?: string
    requestOptionsJson?: string
    availableToolsJson?: string
    responseText?: string
    toolArguments?: string
    toolResult?: string
    metadataJson?: string
    errorCode?: string
    errorMessage?: string
    createdAt?: string
}
```

- [ ] **Step 4: Add service functions**

Append to `fe/src/modules/agent/agentService.ts`:

```ts
export function getAgentRunAudits(params: PageParams & {
    startAt?: string
    endAt?: string
    userId?: number
    username?: string
    threadId?: string
    requestId?: string
    traceId?: string
    presetId?: number
    toolName?: string
    status?: AgentRunAuditStatus
}) {
    return requestJson<AgentPage<AgentRunAuditResponse>>(`/api/admin/agent/run-audits?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        startAt: params.startAt,
        endAt: params.endAt,
        userId: params.userId,
        username: params.username,
        threadId: params.threadId,
        requestId: params.requestId,
        traceId: params.traceId,
        presetId: params.presetId,
        toolName: params.toolName,
        status: params.status,
    })}`)
}

export function getAgentRunAuditEvents(id: number) {
    return requestJson<AgentRunEventAuditResponse[]>(`/api/admin/agent/run-audits/${id}/events`)
}
```

Also update the type imports at the top of `agentService.ts` to include `AgentRunAuditResponse`, `AgentRunAuditStatus`,
and `AgentRunEventAuditResponse`.

- [ ] **Step 5: Add route**

Modify `fe/src/app/routes.tsx` under agent routes:

```tsx
{path: 'agent/run-audits', lazy: () => import('../modules/agent/AgentRunAuditPage')},
```

The component file is created in Task 7. This route will typecheck only after Task 7.

- [ ] **Step 6: Run service test**

Run:

```bash
cd fe
npm test -- agentService.test.ts
```

Expected: PASS for service tests. Typecheck may still fail until the page component exists.

- [ ] **Step 7: Commit**

```bash
git add fe/src/modules/agent/agentTypes.ts \
  fe/src/modules/agent/agentService.ts \
  fe/src/modules/agent/agentService.test.ts \
  fe/src/app/routes.tsx
git commit -m "feat(agent): add run audit frontend service"
```

### Task 7: Frontend List And Detail Page

**Files:**

- Create: `fe/src/modules/agent/AgentRunAuditPage.tsx`
- Optional Test: `fe/src/modules/agent/AgentRunAuditPage.test.tsx`

- [ ] **Step 1: Create page component**

Create `fe/src/modules/agent/AgentRunAuditPage.tsx`:

```tsx
import {EyeOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag, Timeline, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {usePageData} from '../../hooks/usePageData'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {getAgentRunAuditEvents, getAgentRunAudits} from './agentService'
import type {
    AgentRunAuditResponse,
    AgentRunAuditStatus,
    AgentRunEventAuditResponse,
    AgentRunEventStatus,
} from './agentTypes'

type RunAuditQueryForm = {
    userId?: number
    username?: string
    threadId?: string
    requestId?: string
    traceId?: string
    presetId?: number
    toolName?: string
    status?: AgentRunAuditStatus
}

function AgentRunAuditPage() {
    const [form] = Form.useForm<RunAuditQueryForm>()
    const [filters, setFilters] = useState<RunAuditQueryForm>({})
    const [drawerOpen, setDrawerOpen] = useState(false)
    const [selected, setSelected] = useState<AgentRunAuditResponse | null>(null)
    const [events, setEvents] = useState<AgentRunEventAuditResponse[]>([])
    const [detailLoading, setDetailLoading] = useState(false)
    const [detailError, setDetailError] = useState('')

    const loadRuns = useCallback(
        (request: { page: number; size: number }) => getAgentRunAudits({...request, ...filters}),
        [filters],
    )
    const {loading, records, page, size, total, load} = usePageData<AgentRunAuditResponse>(loadRuns)

    const columns: ColumnsType<AgentRunAuditResponse> = [
        {title: 'ID', dataIndex: 'id', width: 80},
        {title: '用户', dataIndex: 'username', width: 130, render: valueOrDash},
        {title: '用户 ID', dataIndex: 'userId', width: 100, render: valueOrDash},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: AgentRunAuditStatus) => <Tag color={statusColor(value)}>{value}</Tag>,
        },
        {
            title: '用户消息',
            dataIndex: 'userMessage',
            width: 360,
            render: (value?: string) => <Typography.Text ellipsis={{tooltip: value}}>{value || '-'}</Typography.Text>,
        },
        {
            title: '线程',
            dataIndex: 'threadId',
            width: 220,
            render: (value: string) => <Typography.Text copyable ellipsis={{tooltip: value}}>{value}</Typography.Text>,
        },
        {title: '模型', dataIndex: 'modelCallCount', width: 90},
        {
            title: 'Tool',
            key: 'toolCount',
            width: 120,
            render: (_, record) => `${record.toolCallCount} / MCP ${record.mcpToolCallCount}`,
        },
        {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 100,
            render: (value?: number) => value === undefined || value === null ? '-' : `${value}ms`,
        },
        {title: '错误', dataIndex: 'errorMessage', width: 220, render: valueOrDash},
        {title: '开始时间', dataIndex: 'startedAt', width: 190, render: valueOrDash},
        {
            title: '操作',
            key: 'action',
            fixed: 'right',
            width: 100,
            render: (_, record) => (
                <Button icon={<EyeOutlined/>} onClick={() => void openDetail(record)} size="small">详情</Button>
            ),
        },
    ]

    async function search() {
        setFilters(await form.validateFields())
        await load(1, size)
    }

    async function openDetail(record: AgentRunAuditResponse) {
        setSelected(record)
        setEvents([])
        setDetailError('')
        setDrawerOpen(true)
        setDetailLoading(true)
        try {
            setEvents(await getAgentRunAuditEvents(record.id))
        } catch (requestError) {
            setDetailError(resolveErrorMessage(requestError))
        } finally {
            setDetailLoading(false)
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>刷新</Button>}
                description="按最近运行查看 Agent 的模型调用、ReAct 轮次、工具调用、MCP 调用和完整明文上下文，仅 SUPER_ADMIN 可访问。"
                title="Agent 运行审计"
            />
            <Card className="dashboard-filter-card">
                <Form form={form} layout="vertical">
                    <Space wrap>
                        <Form.Item label="用户 ID" name="userId"><InputNumber min={1}/></Form.Item>
                        <Form.Item label="用户名" name="username"><Input allowClear/></Form.Item>
                        <Form.Item label="线程 ID" name="threadId"><Input allowClear style={{width: 220}}/></Form.Item>
                        <Form.Item label="请求 ID" name="requestId"><Input allowClear style={{width: 220}}/></Form.Item>
                        <Form.Item label="Trace ID" name="traceId"><Input allowClear style={{width: 220}}/></Form.Item>
                        <Form.Item label="预设 ID" name="presetId"><InputNumber min={1}/></Form.Item>
                        <Form.Item label="Tool" name="toolName"><Input allowClear/></Form.Item>
                        <Form.Item label="状态" name="status">
                            <Select allowClear style={{width: 160}} options={[
                                {label: 'RUNNING', value: 'RUNNING'},
                                {label: 'SUCCESS', value: 'SUCCESS'},
                                {label: 'FAILED', value: 'FAILED'},
                                {label: 'CANCELLED', value: 'CANCELLED'},
                            ]}/>
                        </Form.Item>
                        <Form.Item label=" ">
                            <Button icon={<SearchOutlined/>} onClick={voidify(search)} type="primary">查询</Button>
                        </Form.Item>
                    </Space>
                </Form>
            </Card>
            <Table<AgentRunAuditResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1900}}
                style={{marginTop: 16}}
            />
            <Drawer onClose={() => setDrawerOpen(false)} open={drawerOpen} title="Agent 运行详情" width={1040}>
                {selected && (
                    <Space direction="vertical" size={16} style={{width: '100%'}}>
                        <Space wrap>
                            <Tag>ID={selected.id}</Tag>
                            <Tag color={statusColor(selected.status)}>{selected.status}</Tag>
                            <Tag>thread=<Typography.Text copyable>{selected.threadId}</Typography.Text></Tag>
                            {selected.requestId && <Tag>request=<Typography.Text copyable>{selected.requestId}</Typography.Text></Tag>}
                            {selected.traceId && <Tag>trace=<Typography.Text copyable>{selected.traceId}</Typography.Text></Tag>}
                        </Space>
                        <Card size="small" title="运行摘要">
                            <Typography.Paragraph copyable style={{whiteSpace: 'pre-wrap'}}>
                                {selected.userMessage || '-'}
                            </Typography.Paragraph>
                            <Typography.Text strong>最终输出</Typography.Text>
                            <Typography.Paragraph copyable style={{whiteSpace: 'pre-wrap'}}>
                                {selected.finalMessage || '-'}
                            </Typography.Paragraph>
                            <Typography.Text strong>Thinking</Typography.Text>
                            <Typography.Paragraph copyable style={{whiteSpace: 'pre-wrap'}}>
                                {selected.finalThinking || '-'}
                            </Typography.Paragraph>
                        </Card>
                        <Card size="small" title="运行配置">
                            <Typography.Paragraph copyable style={{whiteSpace: 'pre-wrap'}}>
                                {formatJson(selected.effectiveConfigJson)}
                            </Typography.Paragraph>
                        </Card>
                        {detailError && <Typography.Text type="danger">{detailError}</Typography.Text>}
                        <Timeline
                            pending={detailLoading ? '加载事件...' : false}
                            items={events.map(event => ({
                                color: eventColor(event.status),
                                children: <EventDetail event={event}/>,
                            }))}
                        />
                    </Space>
                )}
            </Drawer>
        </>
    )
}

function EventDetail({event}: { event: AgentRunEventAuditResponse }) {
    return (
        <Card size="small" title={`${event.seqNo}. ${event.eventType}`}>
            <Space wrap>
                <Tag color={eventColor(event.status)}>{event.status}</Tag>
                {event.reactRound !== undefined && <Tag>round={event.reactRound}</Tag>}
                {event.toolName && <Tag>tool={event.toolName}</Tag>}
                {event.toolType && <Tag>{event.toolType}</Tag>}
                {event.durationMs !== undefined && <Tag>{event.durationMs}ms</Tag>}
            </Space>
            <Table
                columns={[
                    {title: '字段', dataIndex: 'name', width: 180},
                    {
                        title: '内容',
                        dataIndex: 'value',
                        render: (value?: string) => (
                            <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                                {value || '-'}
                            </Typography.Paragraph>
                        ),
                    },
                ]}
                dataSource={eventRows(event)}
                pagination={false}
                rowKey="name"
                size="small"
                style={{marginTop: 12}}
            />
        </Card>
    )
}

function eventRows(event: AgentRunEventAuditResponse) {
    return [
        {name: 'promptText', value: event.promptText},
        {name: 'requestMessagesJson', value: formatJson(event.requestMessagesJson)},
        {name: 'requestOptionsJson', value: formatJson(event.requestOptionsJson)},
        {name: 'availableToolsJson', value: formatJson(event.availableToolsJson)},
        {name: 'responseText', value: event.responseText},
        {name: 'toolArguments', value: event.toolArguments},
        {name: 'toolResult', value: event.toolResult},
        {name: 'metadataJson', value: formatJson(event.metadataJson)},
        {name: 'error', value: [event.errorCode, event.errorMessage].filter(Boolean).join('\n')},
    ].filter(row => row.value)
}

function valueOrDash(value?: string | number | null) {
    return value ?? '-'
}

function statusColor(status: AgentRunAuditStatus) {
    if (status === 'SUCCESS') return 'success'
    if (status === 'FAILED') return 'error'
    if (status === 'CANCELLED') return 'default'
    return 'processing'
}

function eventColor(status: AgentRunEventStatus) {
    if (status === 'SUCCESS') return 'green'
    if (status === 'FAILED') return 'red'
    if (status === 'CANCELLED') return 'gray'
    return 'blue'
}

function formatJson(value?: string) {
    if (!value) return '-'
    try {
        return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
        return value
    }
}

export const Component = AgentRunAuditPage
```

- [ ] **Step 2: Run typecheck**

Run:

```bash
cd fe
npm run typecheck
```

Expected: PASS. If AntD `Timeline` item types differ, replace `pending={detailLoading ? '加载事件...' : false}` with
`pending={detailLoading}` and keep the same UI behavior.

- [ ] **Step 3: Run frontend tests**

Run:

```bash
cd fe
npm test -- agentService.test.ts
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add fe/src/modules/agent/AgentRunAuditPage.tsx
git commit -m "feat(agent): add run audit page"
```

### Task 8: End-To-End Targeted Validation

**Files:**

- No source files should be modified in this task.

- [ ] **Step 1: Run backend targeted test group**

Run:

```bash
mvn -f be/pom.xml -Dmaven.build.cache.enabled=false -Djava.version=21 \
  -Dtest=AgentRunAuditSchemaMigrationTests,AgentRunAuditServiceImplTests,AgentObservabilityModelInterceptorTests,AgentObservabilityToolInterceptorTests,ReactAgentChatServiceTests,AgentRuntimeFactoryTests,AdminAgentRunAuditControllerTests,AgentDebugRbacResourceProviderTests test
```

Expected: PASS. If unrelated MCP test compile drift blocks test compilation before these tests run, report the exact
compile error and rerun the smallest subset that compiles. Do not edit unrelated MCP tests in this feature.

- [ ] **Step 2: Run frontend validation**

Run:

```bash
cd fe
npm test -- agentService.test.ts
npm run typecheck
```

Expected: both commands PASS.

- [ ] **Step 3: Run git diff checks**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` has no output. `git status --short` only shows files owned by this feature plus any
pre-existing unrelated files. Do not stage `.superpowers/brainstorm/**`.

- [ ] **Step 4: Final commit if validation required changes**

Only if Step 1 or Step 2 required fixes:

```bash
git add be/src/main/java/top/egon/mario/agent/observability \
  be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java \
  be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java \
  be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java \
  be/src/main/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProvider.java \
  be/src/main/resources/db/migration/V16__create_agent_run_audit.sql \
  be/src/test/java/top/egon/mario/agent/observability \
  be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java \
  be/src/test/java/top/egon/mario/agent/service/impl/AgentRuntimeFactoryTests.java \
  be/src/test/java/top/egon/mario/agent/service/resource/AgentDebugRbacResourceProviderTests.java \
  fe/src/modules/agent/agentTypes.ts \
  fe/src/modules/agent/agentService.ts \
  fe/src/modules/agent/agentService.test.ts \
  fe/src/modules/agent/AgentRunAuditPage.tsx \
  fe/src/app/routes.tsx
git commit -m "test(agent): validate run audit observability"
```

Expected: commit succeeds. If the repo is dirty with unrelated files, use `git commit --only -- <owned paths>` or skip
commit and report the owned file list.

---

## Self-Review Checklist

- Spec coverage:
    - Full plaintext DB audit: Tasks 1-4.
    - Debug-level detailed logs and info-level summaries: Task 3.
    - Agent chain only: Tasks 3-4 only wire through `ReactAgentChatService`/ReactAgent interceptors.
    - SUPER_ADMIN-only visibility: Task 5.
    - Frontend list plus detail page without requiring thread/request IDs: Tasks 6-7.
    - Existing audit compatibility: architecture keeps existing services and pages untouched.
- Red-flag scan: Plan contains no incomplete implementation markers and no undefined event names.
- Type consistency:
    - Backend statuses use `RUNNING/SUCCESS/FAILED/CANCELLED`.
    - Event types match the migration, Java enum, and TypeScript union.
    - Frontend service endpoint paths match controller paths.
