# Agent Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build recoverable, user-private Agent Memory for Agent Chat, Agent Debug, and RAG Chat, with session short-term memory, user-level Markdown long-term memory, memory management pages, and RBAC isolation.

**Architecture:** Add Agent-domain memory persistence as the product source of truth, while using Spring AI Alibaba `PostgresSaver` for ReactAgent checkpoint recovery. Chat services resolve a user-owned memory session before each stream, assemble the recent 10-turn context and user Markdown memory, persist normalized messages after the stream, and trigger long-term extraction asynchronously. RBAC grants API/menu/button access, but every memory service still enforces `principal.userId()` ownership.

**Tech Stack:** Spring Boot WebFlux, Spring Data JPA, Flyway, PostgreSQL, Spring AI Alibaba `ReactAgent` / `PostgresSaver` / `MessagesModelHook`, Reactor, JUnit 5, Mockito, AssertJ, React 19, TypeScript, Ant Design 6, Vitest.

---

## Scope Check

The design spans backend persistence, chat stream integration, RAG prompt assembly, RBAC resources, and frontend pages. These pieces are strongly dependent: chat and frontend need the session API; long-term extraction needs persisted messages; RBAC must protect the same APIs exposed to the pages. Keep this as one phased plan so each phase lands working, testable software without creating mismatched API contracts.

---

## File Structure

Backend memory package:

- Create `be/src/main/resources/db/migration/V17__create_agent_memory_schema.sql`: one Flyway migration for all Agent memory tables and indexes.
- Create `be/src/main/java/top/egon/mario/agent/memory/po/*`: JPA entities for sessions, messages, long-term memory, versions, and extraction audit.
- Create `be/src/main/java/top/egon/mario/agent/memory/po/enums/*`: memory entry/status/scope/role/type enums.
- Create `be/src/main/java/top/egon/mario/agent/memory/repository/*`: JPA repositories and query helpers.
- Create `be/src/main/java/top/egon/mario/agent/memory/dto/request/*`: session create/update/query and extraction query requests.
- Create `be/src/main/java/top/egon/mario/agent/memory/dto/response/*`: session/message/long-term/version/extraction responses.
- Create `be/src/main/java/top/egon/mario/agent/memory/service/*`: service interfaces and simple model records.
- Create `be/src/main/java/top/egon/mario/agent/memory/service/impl/*`: transactional service implementations.
- Create `be/src/main/java/top/egon/mario/agent/memory/checkpoint/*`: `PostgresSaver` factory for ReactAgent checkpoint recovery.
- Create `be/src/main/java/top/egon/mario/agent/memory/hook/*`: Agent message-window hook and long-term prompt hook.
- Create `be/src/main/java/top/egon/mario/agent/memory/web/AgentMemoryController.java`: current-user memory APIs.
- Create `be/src/main/java/top/egon/mario/agent/memory/resource/AgentMemoryPermissionCatalog.java`: permission constants.
- Create `be/src/main/java/top/egon/mario/agent/memory/resource/AgentMemoryRbacResourceProvider.java`: RBAC resource seed provider.

Backend integration points:

- Modify `be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java`: inject memory hooks and a persistent checkpointer.
- Modify `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`: resolve sessions, write messages, trigger extraction, include memory metadata.
- Modify `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`: inject memory services into `ReactAgentChatService`.
- Modify `be/src/main/java/top/egon/mario/agent/service/ChatAgentService.java`: accept richer chat requests.
- Modify `be/src/main/java/top/egon/mario/agent/dto/request/AgentDebugChatRequest.java`: add `sessionId`, `memoryEnabled`, and `longTermExtractionEnabled`.
- Modify `be/src/main/java/top/egon/mario/pojo/request/ChatRequest.java`: add `sessionId` and `memoryEnabled`.
- Modify `be/src/main/java/top/egon/mario/web/ChatController.java`: pass the full request to chat service.
- Modify `be/src/main/java/top/egon/mario/agent/web/AgentDebugController.java`: continue delegating full debug request.
- Modify `be/src/main/java/top/egon/mario/rag/dto/request/RagChatRequest.java`: add memory switches.
- Modify `be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java`: resolve RAG memory session, inject recent turns, persist messages, trigger extraction.

Frontend:

- Modify `fe/src/modules/agent/agentTypes.ts`: memory DTOs and debug chat fields.
- Modify `fe/src/modules/agent/agentService.ts`: memory API client functions.
- Create `fe/src/modules/agent/memoryPermissionCodes.ts`: memory button codes.
- Create `fe/src/modules/agent/memorySessionControls.tsx`: shared session selector and memory switches.
- Create `fe/src/modules/agent/AgentMemoryPage.tsx`: read-only long-term memory, versions, extraction records, active/released sessions.
- Create `fe/src/modules/agent/AgentMemoryArchivePage.tsx`: archived sessions, restore/delete actions.
- Modify `fe/src/modules/chat/chatTypes.ts` and `fe/src/modules/chat/chatService.ts`: send `sessionId` and `memoryEnabled`.
- Modify `fe/src/modules/chat/pages/ChatPage.tsx`: use backend session identity and memory controls.
- Modify `fe/src/modules/agent/AgentDebugPage.tsx`: use memory controls.
- Modify `fe/src/modules/rag/ragTypes.ts`, `fe/src/modules/rag/ragService.ts`, `fe/src/modules/rag/RagChatPage.tsx`: send memory switches and display session id.
- Modify `fe/src/layouts/AdminLayout/menu.tsx` and `fe/src/app/routes.tsx`: add memory routes.

Tests:

- Backend tests under `be/src/test/java/top/egon/mario/agent/memory/*`.
- Existing backend integration tests in `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`, `be/src/test/java/top/egon/mario/web/ChatControllerTests.java`, and RAG tests.
- Frontend tests in `fe/src/modules/agent/agentService.test.ts`, `fe/src/modules/rag/ragService.test.ts`, and `fe/src/layouts/AdminLayout/menu.test.tsx`.

---

## Shared Contracts

Use these names consistently across tasks:

```java
public enum AgentMemoryEntryType {
    AGENT_CHAT,
    AGENT_DEBUG,
    RAG_CHAT,
    BUTLER_AGENT
}

public enum AgentMemorySessionStatus {
    ACTIVE,
    RELEASED,
    ARCHIVED,
    DELETED
}

public enum AgentLongTermMemoryScopeType {
    USER_AGENT,
    USER_BUTLER
}

public enum AgentMemoryMessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

public enum AgentMemoryMessageType {
    MESSAGE,
    THINK,
    ERROR,
    RAG_SOURCES
}

public enum AgentMemoryExtractionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}
```

Default constants:

```java
public final class AgentMemoryDefaults {
    public static final int SHORT_TERM_WINDOW_TURNS = 10;
    public static final int LONG_TERM_MARKDOWN_MAX_CHARS = 20_000;
    public static final String DEFAULT_USER_MEMORY_MARKDOWN = """
            # User Memory

            ## Preferences

            ## Stable Facts

            ## Working Style

            ## Project And Tooling Notes

            ## RAG-Derived Notes

            ## Do Not Forget

            ## Source Index
            """;

    private AgentMemoryDefaults() {
    }
}
```

Validation commands are listed concretely inside each task. Backend tests use `cd /Users/mario/SelfProject/CyberMario/be` followed by Maven with `-Dmaven.build.cache.enabled=false -Djava.version=21`. Frontend tests use `cd /Users/mario/SelfProject/CyberMario/fe` followed by the exact Vitest or build command shown in the task.

---

### Task 1: Database Schema, Enums, PO, And Repositories

**Files:**
- Create: `be/src/main/resources/db/migration/V17__create_agent_memory_schema.sql`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryEntryType.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemorySessionStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentLongTermMemoryScopeType.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentLongTermMemoryStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryMessageRole.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryMessageType.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryExtractionStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/AgentMemorySessionPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/AgentMemoryMessagePo.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/AgentLongTermMemoryPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/AgentLongTermMemoryVersionPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/AgentMemoryExtractionAuditPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/repository/AgentMemorySessionRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/repository/AgentMemoryMessageRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/repository/AgentLongTermMemoryRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/repository/AgentLongTermMemoryVersionRepository.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/repository/AgentMemoryExtractionAuditRepository.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemorySchemaMigrationTests.java`

- [ ] **Step 1: Write the failing migration test**

Create `be/src/test/java/top/egon/mario/agent/memory/AgentMemorySchemaMigrationTests.java`:

```java
package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemorySchemaMigrationTests {

    @Test
    void migrationCreatesAllMemoryTablesWithSessionAndUserIndexes() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V17__create_agent_memory_schema.sql"));

        assertThat(sql).contains("CREATE TABLE agent_memory_session");
        assertThat(sql).contains("CREATE TABLE agent_memory_message");
        assertThat(sql).contains("CREATE TABLE agent_long_term_memory");
        assertThat(sql).contains("CREATE TABLE agent_long_term_memory_version");
        assertThat(sql).contains("CREATE TABLE agent_memory_extraction_audit");
        assertThat(sql).contains("CONSTRAINT uk_agent_memory_session_id UNIQUE (session_id)");
        assertThat(sql).contains("idx_agent_memory_session_user_status");
        assertThat(sql).contains("idx_agent_memory_message_session_seq");
        assertThat(sql).contains("idx_agent_long_term_memory_user_scope");
        assertThat(sql).contains("idx_agent_memory_extraction_user_created");
        assertThat(sql).contains("long_term_extraction_enabled BOOLEAN                  NOT NULL DEFAULT TRUE");
        assertThat(sql).contains("short_term_window_turns      INTEGER                  NOT NULL DEFAULT 10");
    }
}
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentMemorySchemaMigrationTests test
```

Expected: FAIL because `V17__create_agent_memory_schema.sql` is missing.

- [ ] **Step 3: Add the Flyway migration**

Create `be/src/main/resources/db/migration/V17__create_agent_memory_schema.sql`:

```sql
CREATE TABLE agent_memory_session
(
    id                           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    session_id                   VARCHAR(128)             NOT NULL,
    entry_type                   VARCHAR(32)              NOT NULL,
    title                        VARCHAR(256),
    user_id                      BIGINT                   NOT NULL,
    username                     VARCHAR(128),
    status                       VARCHAR(32)              NOT NULL,
    memory_enabled               BOOLEAN                  NOT NULL DEFAULT TRUE,
    long_term_extraction_enabled BOOLEAN                  NOT NULL DEFAULT TRUE,
    short_term_window_turns      INTEGER                  NOT NULL DEFAULT 10,
    last_active_at               TIMESTAMP WITH TIME ZONE,
    released_at                  TIMESTAMP WITH TIME ZONE,
    archived_at                  TIMESTAMP WITH TIME ZONE,
    deleted_at                   TIMESTAMP WITH TIME ZONE,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    version                      BIGINT                   NOT NULL DEFAULT 0,
    deleted                      BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_memory_session_id UNIQUE (session_id)
);

CREATE INDEX idx_agent_memory_session_user_status ON agent_memory_session (user_id, status, updated_at);
CREATE INDEX idx_agent_memory_session_entry_user ON agent_memory_session (entry_type, user_id, updated_at);
CREATE INDEX idx_agent_memory_session_deleted ON agent_memory_session (deleted);

CREATE TABLE agent_memory_message
(
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    session_id       VARCHAR(128)             NOT NULL,
    user_id          BIGINT                   NOT NULL,
    entry_type       VARCHAR(32)              NOT NULL,
    seq_no           INTEGER                  NOT NULL,
    turn_no          INTEGER                  NOT NULL,
    role             VARCHAR(32)              NOT NULL,
    message_type     VARCHAR(32)              NOT NULL,
    content          TEXT,
    content_chars    INTEGER,
    source_refs_json TEXT,
    trace_id         VARCHAR(64),
    request_id       VARCHAR(64),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted          BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_agent_memory_message_session_seq ON agent_memory_message (session_id, seq_no);
CREATE INDEX idx_agent_memory_message_user_created ON agent_memory_message (user_id, created_at);
CREATE INDEX idx_agent_memory_message_entry_created ON agent_memory_message (entry_type, created_at);
CREATE INDEX idx_agent_memory_message_deleted ON agent_memory_message (deleted);

CREATE TABLE agent_long_term_memory
(
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id           BIGINT                   NOT NULL,
    username          VARCHAR(128),
    scope_type        VARCHAR(32)              NOT NULL,
    content_markdown  TEXT                     NOT NULL,
    content_chars     INTEGER                  NOT NULL,
    active_version_id BIGINT,
    status            VARCHAR(32)              NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT                   NOT NULL DEFAULT 0,
    deleted           BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_agent_long_term_memory_user_scope
    ON agent_long_term_memory (user_id, scope_type)
    WHERE deleted = FALSE;
CREATE INDEX idx_agent_long_term_memory_status ON agent_long_term_memory (status);

CREATE TABLE agent_long_term_memory_version
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    memory_id          BIGINT                   NOT NULL,
    version_no         INTEGER                  NOT NULL,
    content_markdown   TEXT                     NOT NULL,
    content_chars      INTEGER                  NOT NULL,
    change_summary     TEXT,
    source_session_ids TEXT,
    source_message_ids TEXT,
    request_id         VARCHAR(64),
    trace_id           VARCHAR(64),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agent_long_term_memory_version_memory ON agent_long_term_memory_version (memory_id, version_no);

CREATE TABLE agent_memory_extraction_audit
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id            BIGINT                   NOT NULL,
    session_id         VARCHAR(128),
    entry_type         VARCHAR(32),
    source_message_ids TEXT,
    status             VARCHAR(32)              NOT NULL,
    extracted_markdown TEXT,
    merged_version_id  BIGINT,
    error_code         VARCHAR(256),
    error_message      TEXT,
    request_id         VARCHAR(64),
    trace_id           VARCHAR(64),
    started_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at        TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agent_memory_extraction_user_created ON agent_memory_extraction_audit (user_id, created_at);
CREATE INDEX idx_agent_memory_extraction_session ON agent_memory_extraction_audit (session_id);
CREATE INDEX idx_agent_memory_extraction_status ON agent_memory_extraction_audit (status);
```

- [ ] **Step 4: Add enums**

Create the enum files exactly as listed in Shared Contracts, plus `AgentLongTermMemoryStatus`:

```java
package top.egon.mario.agent.memory.po.enums;

public enum AgentLongTermMemoryStatus {
    ACTIVE,
    DELETED
}
```

- [ ] **Step 5: Add JPA entities**

Create `AgentMemorySessionPo` with Lombok and JPA annotations:

```java
package top.egon.mario.agent.memory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_memory_session")
public class AgentMemorySessionPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private AgentMemoryEntryType entryType;

    @Column(name = "title", length = 256)
    private String title;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentMemorySessionStatus status = AgentMemorySessionStatus.ACTIVE;

    @Column(name = "memory_enabled", nullable = false)
    private boolean memoryEnabled = true;

    @Column(name = "long_term_extraction_enabled", nullable = false)
    private boolean longTermExtractionEnabled = true;

    @Column(name = "short_term_window_turns", nullable = false)
    private int shortTermWindowTurns = 10;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
```

Create `AgentMemoryMessagePo`:

```java
package top.egon.mario.agent.memory.po;

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
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_memory_message")
public class AgentMemoryMessagePo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private AgentMemoryEntryType entryType;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "turn_no", nullable = false)
    private int turnNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private AgentMemoryMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private AgentMemoryMessageType messageType;

    @Column(name = "content")
    private String content;

    @Column(name = "content_chars")
    private Integer contentChars;

    @Column(name = "source_refs_json")
    private String sourceRefsJson;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
```

Create `AgentLongTermMemoryPo`:

```java
package top.egon.mario.agent.memory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_long_term_memory")
public class AgentLongTermMemoryPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private AgentLongTermMemoryScopeType scopeType;

    @Column(name = "content_markdown", nullable = false)
    private String contentMarkdown;

    @Column(name = "content_chars", nullable = false)
    private int contentChars;

    @Column(name = "active_version_id")
    private Long activeVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentLongTermMemoryStatus status = AgentLongTermMemoryStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
```

Create `AgentLongTermMemoryVersionPo`:

```java
package top.egon.mario.agent.memory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_long_term_memory_version")
public class AgentLongTermMemoryVersionPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_id", nullable = false)
    private Long memoryId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "content_markdown", nullable = false)
    private String contentMarkdown;

    @Column(name = "content_chars", nullable = false)
    private int contentChars;

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "source_session_ids")
    private String sourceSessionIds;

    @Column(name = "source_message_ids")
    private String sourceMessageIds;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

Create `AgentMemoryExtractionAuditPo`:

```java
package top.egon.mario.agent.memory.po;

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
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryExtractionStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "agent_memory_extraction_audit")
public class AgentMemoryExtractionAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 32)
    private AgentMemoryEntryType entryType;

    @Column(name = "source_message_ids")
    private String sourceMessageIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentMemoryExtractionStatus status;

    @Column(name = "extracted_markdown")
    private String extractedMarkdown;

    @Column(name = "merged_version_id")
    private Long mergedVersionId;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

- [ ] **Step 6: Add repositories**

Create repository interfaces:

```java
package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;

import java.util.List;
import java.util.Optional;

public interface AgentMemorySessionRepository extends JpaRepository<AgentMemorySessionPo, Long>,
        JpaSpecificationExecutor<AgentMemorySessionPo> {

    Optional<AgentMemorySessionPo> findBySessionIdAndUserIdAndDeletedFalse(String sessionId, Long userId);

    List<AgentMemorySessionPo> findByUserIdAndEntryTypeAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
            Long userId, AgentMemoryEntryType entryType, AgentMemorySessionStatus status);
}
```

```java
package top.egon.mario.agent.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;

import java.util.List;

public interface AgentMemoryMessageRepository extends JpaRepository<AgentMemoryMessagePo, Long> {

    List<AgentMemoryMessagePo> findBySessionIdAndDeletedFalseOrderBySeqNoAsc(String sessionId);

    List<AgentMemoryMessagePo> findTop40BySessionIdAndDeletedFalseOrderBySeqNoDesc(String sessionId);
}
```

Create long-term and extraction repositories with these methods:

```java
Optional<AgentLongTermMemoryPo> findByUserIdAndScopeTypeAndDeletedFalse(Long userId, AgentLongTermMemoryScopeType scopeType);
List<AgentLongTermMemoryVersionPo> findByMemoryIdOrderByVersionNoDesc(Long memoryId);
List<AgentMemoryExtractionAuditPo> findByUserIdOrderByCreatedAtDesc(Long userId);
```

- [ ] **Step 7: Run the schema test**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentMemorySchemaMigrationTests test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add be/src/main/resources/db/migration/V17__create_agent_memory_schema.sql \
  be/src/main/java/top/egon/mario/agent/memory/po \
  be/src/main/java/top/egon/mario/agent/memory/repository \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemorySchemaMigrationTests.java
git commit -m "feat(agent-memory): add persistence schema"
```

---

### Task 2: Session And Message Services

**Files:**
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/AgentMemoryDefaults.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/AgentMemoryException.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/AgentMemorySessionService.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/AgentMemoryMessageService.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemorySessionCreate.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemorySessionUpdate.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryMessageRecord.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryTurn.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemorySessionServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryMessageServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemorySessionServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemoryMessageServiceTests.java`

- [ ] **Step 1: Write failing service tests**

Create `AgentMemorySessionServiceTests` with these cases:

```java
package top.egon.mario.agent.memory;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;
import top.egon.mario.agent.memory.repository.AgentMemorySessionRepository;
import top.egon.mario.agent.memory.service.impl.AgentMemorySessionServiceImpl;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionCreate;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentMemorySessionServiceTests {

    private final AgentMemorySessionRepository repository = mock(AgentMemorySessionRepository.class);
    private final AgentMemorySessionServiceImpl service = new AgentMemorySessionServiceImpl(repository);
    private final RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of("CHAT_BASIC"), Set.of(), "v1");

    @Test
    void createsActiveSessionForCurrentUserWithDefaults() {
        given(repository.save(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        AgentMemorySessionPo saved = service.create(new AgentMemorySessionCreate(
                AgentMemoryEntryType.AGENT_CHAT, "Chat", null, null), principal);

        assertThat(saved.getSessionId()).isNotBlank();
        assertThat(saved.getUserId()).isEqualTo(8L);
        assertThat(saved.getUsername()).isEqualTo("luigi");
        assertThat(saved.getStatus()).isEqualTo(AgentMemorySessionStatus.ACTIVE);
        assertThat(saved.isMemoryEnabled()).isTrue();
        assertThat(saved.isLongTermExtractionEnabled()).isTrue();
        assertThat(saved.getShortTermWindowTurns()).isEqualTo(10);
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        given(repository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwned("session-1", principal))
                .hasMessageContaining("memory session not found");
    }

    @Test
    void archivedSessionCannotBeActivatedImplicitlyByRequireActive() {
        AgentMemorySessionPo session = session("session-1", AgentMemorySessionStatus.ARCHIVED);
        given(repository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> service.requireUsableForChat("session-1", principal))
                .hasMessageContaining("memory session is archived");
    }

    @Test
    void archivedSessionCanBeLogicallyDeleted() {
        AgentMemorySessionPo session = session("session-1", AgentMemorySessionStatus.ARCHIVED);
        given(repository.findBySessionIdAndUserIdAndDeletedFalse("session-1", 8L)).willReturn(Optional.of(session));

        service.deleteArchived("session-1", principal);

        assertThat(session.getStatus()).isEqualTo(AgentMemorySessionStatus.DELETED);
        assertThat(session.isDeleted()).isTrue();
        verify(repository).save(session);
    }

    private AgentMemorySessionPo session(String sessionId, AgentMemorySessionStatus status) {
        AgentMemorySessionPo session = new AgentMemorySessionPo();
        session.setSessionId(sessionId);
        session.setUserId(8L);
        session.setEntryType(AgentMemoryEntryType.AGENT_CHAT);
        session.setStatus(status);
        session.setMemoryEnabled(true);
        session.setLongTermExtractionEnabled(true);
        session.setShortTermWindowTurns(10);
        return session;
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentMemorySessionServiceTests test
```

Expected: FAIL because service classes are missing.

- [ ] **Step 3: Add service models and exception**

Create:

```java
public record AgentMemorySessionCreate(
        AgentMemoryEntryType entryType,
        String title,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled
) {
}

public record AgentMemorySessionUpdate(
        String title,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled
) {
}

public record AgentMemoryMessageRecord(
        String sessionId,
        Long userId,
        AgentMemoryEntryType entryType,
        int turnNo,
        AgentMemoryMessageRole role,
        AgentMemoryMessageType messageType,
        String content,
        String sourceRefsJson,
        String traceId,
        String requestId
) {
}

public record AgentMemoryTurn(String userMessage, String assistantMessage) {
}
```

Create `AgentMemoryException`:

```java
package top.egon.mario.agent.memory.service;

public class AgentMemoryException extends RuntimeException {

    private final String code;

    public AgentMemoryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

- [ ] **Step 4: Implement session service**

Create `AgentMemorySessionService`:

```java
public interface AgentMemorySessionService {

    AgentMemorySessionPo create(AgentMemorySessionCreate request, RbacPrincipal principal);

    AgentMemorySessionPo resolveOrCreate(AgentMemoryEntryType entryType, String sessionId,
                                         Boolean memoryEnabled, Boolean longTermExtractionEnabled,
                                         RbacPrincipal principal);

    AgentMemorySessionPo requireOwned(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo requireUsableForChat(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo update(String sessionId, AgentMemorySessionUpdate request, RbacPrincipal principal);

    AgentMemorySessionPo release(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo restore(String sessionId, RbacPrincipal principal);

    AgentMemorySessionPo archive(String sessionId, RbacPrincipal principal);

    void deleteArchived(String sessionId, RbacPrincipal principal);
}
```

Implement `AgentMemorySessionServiceImpl` with these rules:

```java
private static final int DEFAULT_WINDOW_TURNS = 10;

private RbacPrincipal requirePrincipal(RbacPrincipal principal) {
    if (principal == null || principal.userId() == null) {
        throw new AgentMemoryException("AGENT_MEMORY_UNAUTHENTICATED", "memory requires an authenticated user");
    }
    return principal;
}

private AgentMemorySessionPo findOwned(String sessionId, RbacPrincipal principal) {
    RbacPrincipal safePrincipal = requirePrincipal(principal);
    return repository.findBySessionIdAndUserIdAndDeletedFalse(sessionId, safePrincipal.userId())
            .orElseThrow(() -> new AgentMemoryException("AGENT_MEMORY_SESSION_NOT_FOUND", "memory session not found"));
}
```

Use `Instant.now()` for lifecycle timestamps. `release` sets `status=RELEASED` and `releasedAt`. `restore` changes `RELEASED` or `ARCHIVED` to `ACTIVE`. `archive` sets `status=ARCHIVED` and `archivedAt`. `deleteArchived` only accepts `ARCHIVED`; otherwise throw `AGENT_MEMORY_SESSION_DELETE_REQUIRES_ARCHIVE`.

- [ ] **Step 5: Implement message service**

Create `AgentMemoryMessageService`:

```java
public interface AgentMemoryMessageService {

    List<AgentMemoryMessagePo> appendAll(List<AgentMemoryMessageRecord> records);

    List<AgentMemoryMessagePo> messages(String sessionId, RbacPrincipal principal);

    List<AgentMemoryTurn> recentTurns(AgentMemorySessionPo session);

    int nextTurnNo(String sessionId);
}
```

Implement `AgentMemoryMessageServiceImpl` with these exact rules:

- Use `findBySessionIdAndDeletedFalseOrderBySeqNoAsc` to compute next seq number.
- `recentTurns` reads descending top 40 rows, reverses them, groups by `turnNo`, and returns the last `session.shortTermWindowTurns` pairs.
- Inject `AgentMemorySessionRepository` into `AgentMemoryMessageServiceImpl`.
- `messages(sessionId, principal)` first calls `sessionRepository.findBySessionIdAndUserIdAndDeletedFalse(sessionId, principal.userId())`; if missing, throw `AgentMemoryException("AGENT_MEMORY_SESSION_NOT_FOUND", "memory session not found")`; then return `messageRepository.findBySessionIdAndDeletedFalseOrderBySeqNoAsc(sessionId)`.
- `appendAll` saves records in input order and assigns sequential `seqNo` starting at `currentMaxSeq + 1`.

- [ ] **Step 6: Run service tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentMemorySessionServiceTests,AgentMemoryMessageServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/memory/service \
  be/src/main/java/top/egon/mario/agent/memory/service/impl \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemorySessionServiceTests.java \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemoryMessageServiceTests.java
git commit -m "feat(agent-memory): add session and message services"
```

---

### Task 3: Long-Term Markdown Memory And Extraction

**Files:**
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/AgentLongTermMemoryService.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/AgentMemoryExtractionService.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentLongTermMemoryMergeRequest.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryExtractionRequest.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentLongTermMemoryServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryExtractionServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentLongTermMemoryServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemoryExtractionServiceTests.java`

- [ ] **Step 1: Write failing long-term service tests**

Create tests that verify:

```java
@Test
void createsDefaultUserAgentMemoryWhenMissing() {
    given(memoryRepository.findByUserIdAndScopeTypeAndDeletedFalse(8L, AgentLongTermMemoryScopeType.USER_AGENT))
            .willReturn(Optional.empty());
    given(memoryRepository.save(any())).willAnswer(invocation -> {
        AgentLongTermMemoryPo po = invocation.getArgument(0);
        po.setId(100L);
        return po;
    });
    given(versionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    AgentLongTermMemoryPo memory = service.getOrCreateUserAgentMemory(principal);

    assertThat(memory.getUserId()).isEqualTo(8L);
    assertThat(memory.getContentMarkdown()).contains("# User Memory");
    assertThat(memory.getContentChars()).isEqualTo(memory.getContentMarkdown().length());
}

@Test
void rejectsMergedMarkdownOverTwentyThousandCharacters() {
    String tooLong = "a".repeat(20_001);

    assertThatThrownBy(() -> service.merge(new AgentLongTermMemoryMergeRequest(
            8L, "luigi", AgentLongTermMemoryScopeType.USER_AGENT, tooLong,
            "summary", "session-1", "1,2", "request-1", "trace-1")))
            .hasMessageContaining("long-term memory exceeds");
}
```

- [ ] **Step 2: Run tests and verify they fail**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentLongTermMemoryServiceTests test
```

Expected: FAIL because long-term service classes are missing.

- [ ] **Step 3: Implement long-term memory service**

Create interface:

```java
public interface AgentLongTermMemoryService {

    AgentLongTermMemoryPo getOrCreateUserAgentMemory(RbacPrincipal principal);

    AgentLongTermMemoryPo getOrCreate(Long userId, String username, AgentLongTermMemoryScopeType scopeType);

    AgentLongTermMemoryPo merge(AgentLongTermMemoryMergeRequest request);

    List<AgentLongTermMemoryVersionPo> versions(Long userId, AgentLongTermMemoryScopeType scopeType);
}
```

Merge algorithm:

```java
String merged = request.mergedMarkdown() == null ? "" : request.mergedMarkdown().trim();
if (merged.length() > AgentMemoryDefaults.LONG_TERM_MARKDOWN_MAX_CHARS) {
    throw new AgentMemoryException("AGENT_LONG_TERM_MEMORY_TOO_LARGE",
            "long-term memory exceeds 20000 characters");
}
AgentLongTermMemoryPo memory = getOrCreate(request.userId(), request.username(), request.scopeType());
memory.setContentMarkdown(merged);
memory.setContentChars(merged.length());
memory.setUpdatedAt(Instant.now());
AgentLongTermMemoryPo saved = memoryRepository.save(memory);
int versionNo = versionRepository.findByMemoryIdOrderByVersionNoDesc(saved.getId()).stream()
        .mapToInt(AgentLongTermMemoryVersionPo::getVersionNo)
        .max()
        .orElse(0) + 1;
AgentLongTermMemoryVersionPo version = new AgentLongTermMemoryVersionPo();
version.setMemoryId(saved.getId());
version.setVersionNo(versionNo);
version.setContentMarkdown(merged);
version.setContentChars(merged.length());
version.setChangeSummary(request.changeSummary());
version.setSourceSessionIds(request.sourceSessionIds());
version.setSourceMessageIds(request.sourceMessageIds());
version.setRequestId(request.requestId());
version.setTraceId(request.traceId());
version.setCreatedAt(Instant.now());
AgentLongTermMemoryVersionPo savedVersion = versionRepository.save(version);
saved.setActiveVersionId(savedVersion.getId());
return memoryRepository.save(saved);
```

- [ ] **Step 4: Implement extraction service**

Create interface:

```java
public interface AgentMemoryExtractionService {

    void extractAfterTurn(AgentMemoryExtractionRequest request);
}
```

First implementation is deterministic and conservative:

- If `session.status` is `ARCHIVED` or `DELETED`, write `SKIPPED`.
- If `longTermExtractionEnabled=false`, write `SKIPPED`.
- If no user message contains one of these markers, write `SKIPPED`: `记住`, `偏好`, `喜欢`, `以后`, `默认`, `不要`.
- If marker exists, append a bullet to the correct Markdown section with source id:

```text
- 用户表达: 我喜欢中文回答 [source: AGENT_CHAT session=session-1]
```

For RAG sessions, write under `## RAG-Derived Notes`. For Agent sessions, write under `## Preferences` when the marker contains `喜欢` or `偏好`, otherwise `## Do Not Forget`.

This is intentionally simple for the first pass. A later model-based extractor can replace only this service without changing persistence or APIs.

- [ ] **Step 5: Run long-term and extraction tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentLongTermMemoryServiceTests,AgentMemoryExtractionServiceTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/memory/service/AgentLongTermMemoryService.java \
  be/src/main/java/top/egon/mario/agent/memory/service/AgentMemoryExtractionService.java \
  be/src/main/java/top/egon/mario/agent/memory/service/model \
  be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentLongTermMemoryServiceImpl.java \
  be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryExtractionServiceImpl.java \
  be/src/test/java/top/egon/mario/agent/memory/AgentLongTermMemoryServiceTests.java \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemoryExtractionServiceTests.java
git commit -m "feat(agent-memory): add long-term markdown memory"
```

---

### Task 4: Context Assembly, Hooks, And Persistent Checkpointer

**Files:**
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/AgentMemoryContextService.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryContext.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryContextServiceImpl.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/checkpoint/AgentMemoryCheckpointerProvider.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/checkpoint/PostgresSaverProperties.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/hook/AgentMemoryMessagesHook.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemoryContextServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemoryMessagesHookTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemoryCheckpointerProviderTests.java`

- [ ] **Step 1: Write failing context tests**

Test behavior:

```java
@Test
void doesNotAssembleHistoryWhenSessionMemoryDisabled() {
    AgentMemorySessionPo session = new AgentMemorySessionPo();
    session.setMemoryEnabled(false);

    AgentMemoryContext context = service.contextFor(session, principal);

    assertThat(context.shortTermPrompt()).isBlank();
    assertThat(context.longTermPrompt()).isBlank();
}

@Test
void assemblesRecentTurnsAndLongTermMarkdownForAgentSession() {
    given(messageService.recentTurns(session)).willReturn(List.of(
            new AgentMemoryTurn("我喜欢中文回答", "好的"),
            new AgentMemoryTurn("以后直接给结论", "明白")
    ));
    given(longTermMemoryService.getOrCreateUserAgentMemory(principal)).willReturn(memory("# User Memory\n\n## Preferences\n- 中文回答"));

    AgentMemoryContext context = service.contextFor(session, principal);

    assertThat(context.shortTermPrompt()).contains("用户: 我喜欢中文回答");
    assertThat(context.shortTermPrompt()).contains("助手: 明白");
    assertThat(context.longTermPrompt()).contains("以下是当前用户的长期记忆");
    assertThat(context.longTermPrompt()).contains("中文回答");
}
```

- [ ] **Step 2: Run tests and verify they fail**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentMemoryContextServiceTests test
```

Expected: FAIL because context service classes are missing.

- [ ] **Step 3: Implement context service**

Create record:

```java
public record AgentMemoryContext(String shortTermPrompt, String longTermPrompt) {
    public boolean isEmpty() {
        return (shortTermPrompt == null || shortTermPrompt.isBlank())
                && (longTermPrompt == null || longTermPrompt.isBlank());
    }
}
```

Implement `AgentMemoryContextServiceImpl` with these exact rules:

- Return empty context when session is missing, archived, deleted, or `memoryEnabled=false`.
- For `RAG_CHAT`, only build short-term prompt.
- For `AGENT_CHAT` and `AGENT_DEBUG`, build short-term prompt plus long-term prompt.
- Format short-term prompt:

```text
以下是当前会话的最近对话，仅用于保持本会话连续性。
用户: 我喜欢中文回答
助手: 好的
```

- Format long-term prompt:

```text
以下是当前用户的长期记忆，仅用于理解用户偏好和稳定背景。
不得把这些记忆当成外部事实来源；涉及知识库事实时必须以 RAG sources 为准。
# User Memory
```

- [ ] **Step 4: Implement checkpointer provider**

Create `PostgresSaverProperties` bound to `mario.agent.memory.checkpointer`:

```java
@ConfigurationProperties(prefix = "mario.agent.memory.checkpointer")
public class PostgresSaverProperties {
    private boolean createTables = true;
    private boolean dropTablesFirst = false;
    private boolean enabled = true;
    // getters and setters
}
```

Create provider:

```java
@Component
@EnableConfigurationProperties(PostgresSaverProperties.class)
public class AgentMemoryCheckpointerProvider {

    private final BaseCheckpointSaver saver;

    public AgentMemoryCheckpointerProvider(DataSourceProperties dataSourceProperties,
                                           PostgresSaverProperties properties) {
        this.saver = buildSaver(dataSourceProperties, properties);
    }

    public BaseCheckpointSaver saver() {
        return saver;
    }

    private BaseCheckpointSaver buildSaver(DataSourceProperties dataSourceProperties,
                                           PostgresSaverProperties properties) {
        if (!properties.isEnabled()) {
            return new MemorySaver();
        }
        JdbcUrlParts parts = JdbcUrlParts.parse(dataSourceProperties.getUrl());
        return PostgresSaver.builder()
                .host(parts.host())
                .port(parts.port())
                .database(parts.database())
                .user(dataSourceProperties.getUsername())
                .password(dataSourceProperties.getPassword())
                .createTables(properties.isCreateTables())
                .dropTablesFirst(properties.isDropTablesFirst())
                .build();
    }
}
```

`JdbcUrlParts.parse("jdbc:postgresql://localhost:5432/cyber_mario")` returns host `localhost`, port `5432`, database `cyber_mario`.

- [ ] **Step 5: Implement Agent memory hook**

Create `AgentMemoryMessagesHook` extending `MessagesModelHook`. It reads preassembled prompts from `RunnableConfig.metadata("agentMemoryLongTermPrompt")` and `RunnableConfig.metadata("agentMemoryShortTermPrompt")`.

Hook behavior:

```java
List<Message> updated = new ArrayList<>();
updated.addAll(systemMessagesFrom(longTermPrompt, shortTermPrompt));
updated.addAll(trimToRecentTurns(previousMessages, 10));
return new AgentCommand(updated, UpdatePolicy.REPLACE);
```

When both prompts are blank, return `new AgentCommand(previousMessages)`.

- [ ] **Step 6: Wire factory**

Modify `DefaultAgentRuntimeFactory`:

- Inject `ObjectProvider<AgentMemoryCheckpointerProvider>`.
- Inject `ObjectProvider<AgentMemoryMessagesHook>`.
- Add the memory hook before `LoggingHook`.
- Replace `.saver(new MemorySaver())` with `.saver(request.checkpointSaver())`.

Extend `AgentBuildRequest` with:

```java
BaseCheckpointSaver checkpointSaver,
List<Hook> hooks
```

Keep all existing interceptors and `ToolMonitorInterceptor`.

- [ ] **Step 7: Run tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentMemoryContextServiceTests,AgentMemoryMessagesHookTests,AgentMemoryCheckpointerProviderTests,ReactAgentChatServiceTests test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/memory/service/AgentMemoryContextService.java \
  be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryContext.java \
  be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryContextServiceImpl.java \
  be/src/main/java/top/egon/mario/agent/memory/checkpoint \
  be/src/main/java/top/egon/mario/agent/memory/hook \
  be/src/main/java/top/egon/mario/agent/service/impl/DefaultAgentRuntimeFactory.java \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemoryContextServiceTests.java \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemoryMessagesHookTests.java \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemoryCheckpointerProviderTests.java
git commit -m "feat(agent-memory): add context assembly and checkpointing"
```

---

### Task 5: Agent Chat And Agent Debug Integration

**Files:**
- Modify: `be/src/main/java/top/egon/mario/pojo/request/ChatRequest.java`
- Modify: `be/src/main/java/top/egon/mario/agent/dto/request/AgentDebugChatRequest.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/ChatAgentService.java`
- Modify: `be/src/main/java/top/egon/mario/web/ChatController.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
- Modify: `be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java`
- Test: `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`
- Test: `be/src/test/java/top/egon/mario/web/ChatControllerTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/web/AgentDebugControllerTests.java`

- [ ] **Step 1: Update request records**

Change `ChatRequest`:

```java
public record ChatRequest(
        @NotBlank String message,
        String threadId,
        String sessionId,
        Boolean memoryEnabled
) {
}
```

Change `AgentDebugChatRequest`:

```java
public record AgentDebugChatRequest(
        @NotBlank String message,
        String threadId,
        String sessionId,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled,
        Long presetId,
        AgentPresetConfig overrides
) {
}
```

- [ ] **Step 2: Write failing controller tests**

In `ChatControllerTests`, verify service receives the full request:

```java
given(chatAgentService.chat(any(ChatRequest.class), any()))
        .willReturn(Flux.just(new ChatResponse("session-1", "answer", "message")));

webTestClient.post()
        .uri("/demo/chat/stream")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_NDJSON)
        .bodyValue("""
                {
                  "message": "你好",
                  "sessionId": "session-1",
                  "memoryEnabled": true
                }
                """)
        .exchange()
        .expectStatus().isOk();

verify(chatAgentService).chat(argThat(request ->
        "session-1".equals(request.sessionId()) && Boolean.TRUE.equals(request.memoryEnabled())), any());
```

- [ ] **Step 3: Update service interface**

Change `ChatAgentService`:

```java
Flux<ChatResponse> chat(ChatRequest request, RbacPrincipal principal);

Flux<ChatResponse> debugChat(AgentDebugChatRequest request, RbacPrincipal principal);
```

Remove the old `chat(String message, String threadId, RbacPrincipal principal)` method after all callers are updated.

- [ ] **Step 4: Integrate memory in `ReactAgentChatService`**

Constructor additions:

```java
private final AgentMemorySessionService memorySessionService;
private final AgentMemoryMessageService memoryMessageService;
private final AgentMemoryContextService memoryContextService;
private final AgentMemoryExtractionService memoryExtractionService;
```

Before runtime creation:

```java
AgentMemoryEntryType entryType = debugRequest == null ? AgentMemoryEntryType.AGENT_CHAT : AgentMemoryEntryType.AGENT_DEBUG;
String requestedSessionId = debugRequest == null ? request.sessionId() : debugRequest.sessionId();
Boolean memoryEnabled = debugRequest == null ? request.memoryEnabled() : debugRequest.memoryEnabled();
Boolean extractionEnabled = debugRequest == null ? Boolean.TRUE : debugRequest.longTermExtractionEnabled();
AgentMemorySessionPo memorySession = memorySessionService.resolveOrCreate(
        entryType, requestedSessionId, memoryEnabled, extractionEnabled, principal);
String conversationThreadId = memorySession.getSessionId();
AgentMemoryContext memoryContext = memoryContextService.contextFor(memorySession, principal);
```

Add metadata:

```java
RunnableConfig.Builder builder = RunnableConfig.builder().threadId(conversationThreadId);
builder.addMetadata("agentMemorySessionId", conversationThreadId);
builder.addMetadata("agentMemoryEntryType", entryType.name());
builder.addMetadata("agentMemoryShortTermPrompt", memoryContext.shortTermPrompt());
builder.addMetadata("agentMemoryLongTermPrompt", memoryContext.longTermPrompt());
```

After successful stream completion, append:

```java
memoryMessageService.appendAll(List.of(
        new AgentMemoryMessageRecord(conversationThreadId, principal.userId(), entryType, turnNo,
                AgentMemoryMessageRole.USER, AgentMemoryMessageType.MESSAGE, message, null, traceId, requestId),
        new AgentMemoryMessageRecord(conversationThreadId, principal.userId(), entryType, turnNo,
                AgentMemoryMessageRole.ASSISTANT, AgentMemoryMessageType.MESSAGE, messageContent, null, traceId, requestId)
));
memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(conversationThreadId, requestId, traceId));
```

When thinking content exists, append it as `ASSISTANT / THINK` in the same `turnNo`.

- [ ] **Step 5: Preserve compatibility**

For existing frontend callers that still send only `threadId`, treat `threadId` as `sessionId` when `sessionId` is blank:

```java
String effectiveSessionId = StringUtils.hasText(request.sessionId()) ? request.sessionId() : request.threadId();
```

The response `ChatResponse.threadId` should return the memory session id.

- [ ] **Step 6: Run Agent tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=ReactAgentChatServiceTests,ChatControllerTests,AgentDebugControllerTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/pojo/request/ChatRequest.java \
  be/src/main/java/top/egon/mario/agent/dto/request/AgentDebugChatRequest.java \
  be/src/main/java/top/egon/mario/agent/service/ChatAgentService.java \
  be/src/main/java/top/egon/mario/web/ChatController.java \
  be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java \
  be/src/main/java/top/egon/mario/agent/config/AgentConfiguration.java \
  be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java \
  be/src/test/java/top/egon/mario/web/ChatControllerTests.java \
  be/src/test/java/top/egon/mario/agent/web/AgentDebugControllerTests.java
git commit -m "feat(agent-memory): connect agent chat sessions"
```

---

### Task 6: RAG Chat Session Memory Integration

**Files:**
- Modify: `be/src/main/java/top/egon/mario/rag/dto/request/RagChatRequest.java`
- Modify: `be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/rag/service/RagChatMemoryServiceTests.java`

- [ ] **Step 1: Update RAG request**

Change record:

```java
public record RagChatRequest(
        String sessionId,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled,
        @NotBlank String question,
        List<Long> knowledgeBaseIds,
        @Valid RetrievalOptions retrievalOptions,
        @Valid ModelOptions modelOptions,
        Boolean withSources
) {
}
```

- [ ] **Step 2: Write failing RAG memory tests**

Create `RagChatMemoryServiceTests`:

```java
@Test
void ragChatAddsOnlySessionShortTermMemoryBeforeSources() {
    given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(true), any()))
            .willReturn(session("rag-session-1"));
    given(memoryContextService.contextFor(any(), any()))
            .willReturn(new AgentMemoryContext("用户: 上一个问题\n助手: 上一个回答", ""));
    given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(List.of(source("S1", "source content")));
    given(chatModel.stream(any(Prompt.class))).willReturn(Flux.empty());

    StepVerifier.create(service.stream(new RagChatRequest(
            null, true, true, "继续说", List.of(1L), null, null, true), principal))
            .expectNextMatches(event -> "metadata".equals(event.type())
                    && "rag-session-1".equals(event.data().get("sessionId")))
            .expectNextMatches(event -> "retrieval".equals(event.type()))
            .expectNextMatches(event -> "done".equals(event.type()))
            .verifyComplete();

    verify(memoryMessageService).appendAll(argThat(records ->
            records.stream().anyMatch(record -> record.entryType() == AgentMemoryEntryType.RAG_CHAT
                    && record.messageType() == AgentMemoryMessageType.MESSAGE)));
}
```

- [ ] **Step 3: Run test and verify it fails**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=RagChatMemoryServiceTests test
```

Expected: FAIL because RAG memory dependencies are not wired.

- [ ] **Step 4: Integrate memory in `RagChatServiceImpl`**

Add constructor dependencies:

```java
private final AgentMemorySessionService memorySessionService;
private final AgentMemoryMessageService memoryMessageService;
private final AgentMemoryContextService memoryContextService;
private final AgentMemoryExtractionService memoryExtractionService;
```

At stream start:

```java
AgentMemorySessionPo session = memorySessionService.resolveOrCreate(
        AgentMemoryEntryType.RAG_CHAT,
        request.sessionId(),
        request.memoryEnabled(),
        request.longTermExtractionEnabled(),
        principal);
AgentMemoryContext memoryContext = memoryContextService.contextFor(session, principal);
```

Metadata event:

```java
event("metadata", Map.of(
        "messageId", messageId,
        "traceId", traceId,
        "sessionId", session.getSessionId(),
        "memoryEnabled", session.isMemoryEnabled(),
        "longTermExtractionEnabled", session.isLongTermExtractionEnabled()
))
```

Prompt assembly:

```java
new UserMessage(userPrompt(request.question(), sources, memoryContext.shortTermPrompt()))
```

The prompt text must label memory separately from sources:

```text
当前 RAG 会话最近对话：
用户: 上一个问题是什么
助手: 上一个问题是部署流程

知识库上下文：
[来源 S1] 部署流程需要先运行测试，再执行发布脚本

用户问题：
继续说
```

When `sources.isEmpty()`, keep the existing no-context answer and persist the user/assistant turn. Do not use memory to answer factual content.

- [ ] **Step 5: Trigger extraction only through the switch**

After successful RAG stream:

```java
if (session.isLongTermExtractionEnabled()) {
    memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(session.getSessionId(), requestId, traceId));
}
```

Persist sources metadata as `sourceRefsJson` on the assistant message. Do not persist raw source content as long-term memory.

- [ ] **Step 6: Run RAG tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=RagChatMemoryServiceTests test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add be/src/main/java/top/egon/mario/rag/dto/request/RagChatRequest.java \
  be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java \
  be/src/test/java/top/egon/mario/rag/service/RagChatMemoryServiceTests.java
git commit -m "feat(agent-memory): add rag session memory"
```

---

### Task 7: Memory APIs And RBAC Resources

**Files:**
- Create: `be/src/main/java/top/egon/mario/agent/memory/dto/request/AgentMemorySessionRequest.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/dto/request/AgentMemorySessionQuery.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentMemorySessionResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentMemoryMessageResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentLongTermMemoryResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentLongTermMemoryVersionResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentMemoryExtractionAuditResponse.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/web/AgentMemoryController.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/resource/AgentMemoryPermissionCatalog.java`
- Create: `be/src/main/java/top/egon/mario/agent/memory/resource/AgentMemoryRbacResourceProvider.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/web/AgentMemoryControllerTests.java`
- Test: `be/src/test/java/top/egon/mario/agent/memory/AgentMemoryRbacResourceProviderTests.java`

- [ ] **Step 1: Write failing RBAC provider test**

Create:

```java
@Test
void chatBasicGetsAgentMemoryPageAndSelfMemoryApis() {
    AgentMemoryRbacResourceProvider provider = new AgentMemoryRbacResourceProvider();

    List<String> permissions = provider.rolePresets().stream()
            .filter(role -> "CHAT_BASIC".equals(role.roleCode()))
            .flatMap(role -> role.permissionCodes().stream())
            .toList();

    assertThat(permissions).contains(
            "menu:agent:memory",
            "menu:agent:memory-archive",
            "api:agent:memory:session:collection",
            "api:agent:memory:session:*",
            "api:agent:memory:long-term:read",
            "api:agent:memory:extraction:read",
            "btn:agent:memory:archive",
            "btn:agent:memory:delete"
    );
}
```

- [ ] **Step 2: Implement permission catalog**

Create constants:

```java
public final class AgentMemoryPermissionCatalog {
    public static final String APP_CODE = "agent";
    public static final String MENU_MEMORY = "menu:agent:memory";
    public static final String MENU_MEMORY_ARCHIVE = "menu:agent:memory-archive";
    public static final String BTN_SWITCH = "btn:agent:memory:switch";
    public static final String BTN_ARCHIVE = "btn:agent:memory:archive";
    public static final String BTN_RESTORE = "btn:agent:memory:restore";
    public static final String BTN_DELETE = "btn:agent:memory:delete";
    public static final String BTN_RELEASE = "btn:agent:memory:release";
    public static final String API_SESSION_COLLECTION = "api:agent:memory:session:collection";
    public static final String API_SESSION_ALL = "api:agent:memory:session:*";
    public static final String API_MESSAGE_READ = "api:agent:memory:message:read";
    public static final String API_LONG_TERM_READ = "api:agent:memory:long-term:read";
    public static final String API_LONG_TERM_VERSION = "api:agent:memory:long-term:version";
    public static final String API_EXTRACTION_READ = "api:agent:memory:extraction:read";
    private AgentMemoryPermissionCatalog() {
    }
}
```

- [ ] **Step 3: Implement RBAC provider**

Implement `AgentMemoryRbacResourceProvider.resources()` with `RbacResourceSeed.menu`, `RbacResourceSeed.button`, and `RbacResourceSeed.api`, following the constructor argument order used in `AgentDebugRbacResourceProvider`.

Routes:

```java
new RbacMenuSeed("agentMemory", "/agent/memory", null, null, "ProfileOutlined", false, true, null)
new RbacMenuSeed("agentMemoryArchive", "/agent/memory/archive", null, null, "InboxOutlined", false, true, null)
```

API matchers:

```java
new RbacApiSeed("ANY", "/api/agent/memory/sessions", ApiMatcherType.EXACT, false, ApiRiskLevel.MEDIUM)
new RbacApiSeed("ANY", "/api/agent/memory/sessions/**", ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM)
new RbacApiSeed("GET", "/api/agent/memory/long-term", ApiMatcherType.EXACT, false, ApiRiskLevel.MEDIUM)
new RbacApiSeed("ANY", "/api/agent/memory/long-term/versions/**", ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM)
new RbacApiSeed("GET", "/api/agent/memory/extractions", ApiMatcherType.EXACT, false, ApiRiskLevel.MEDIUM)
```

- [ ] **Step 4: Implement controller**

Expose:

```java
@GetMapping("/sessions")
Page<AgentMemorySessionResponse> sessions(AgentMemorySessionQuery query, Pageable pageable, RbacPrincipal principal)

@PostMapping("/sessions")
AgentMemorySessionResponse create(@Valid @RequestBody AgentMemorySessionRequest request, RbacPrincipal principal)

@PatchMapping("/sessions/{sessionId}")
AgentMemorySessionResponse update(String sessionId, AgentMemorySessionRequest request, RbacPrincipal principal)

@PostMapping("/sessions/{sessionId}/release")
AgentMemorySessionResponse release(String sessionId, RbacPrincipal principal)

@PostMapping("/sessions/{sessionId}/restore")
AgentMemorySessionResponse restore(String sessionId, RbacPrincipal principal)

@PostMapping("/sessions/{sessionId}/archive")
AgentMemorySessionResponse archive(String sessionId, RbacPrincipal principal)

@DeleteMapping("/sessions/{sessionId}")
void deleteArchived(String sessionId, RbacPrincipal principal)

@GetMapping("/sessions/{sessionId}/messages")
List<AgentMemoryMessageResponse> messages(String sessionId, RbacPrincipal principal)

@GetMapping("/long-term")
AgentLongTermMemoryResponse longTerm(RbacPrincipal principal)

@GetMapping("/long-term/versions")
List<AgentLongTermMemoryVersionResponse> versions(RbacPrincipal principal)

@GetMapping("/extractions")
List<AgentMemoryExtractionAuditResponse> extractions(RbacPrincipal principal)
```

Controller must never accept `userId` as request input. Use `@AuthenticationPrincipal RbacPrincipal principal`.

- [ ] **Step 5: Run API and RBAC tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 -Dtest=AgentMemoryControllerTests,AgentMemoryRbacResourceProviderTests test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add be/src/main/java/top/egon/mario/agent/memory/dto \
  be/src/main/java/top/egon/mario/agent/memory/web \
  be/src/main/java/top/egon/mario/agent/memory/resource \
  be/src/test/java/top/egon/mario/agent/memory/web/AgentMemoryControllerTests.java \
  be/src/test/java/top/egon/mario/agent/memory/AgentMemoryRbacResourceProviderTests.java
git commit -m "feat(agent-memory): expose user memory APIs"
```

---

### Task 8: Frontend API Clients, Types, Menu, And Routes

**Files:**
- Modify: `fe/src/modules/agent/agentTypes.ts`
- Modify: `fe/src/modules/agent/agentService.ts`
- Create: `fe/src/modules/agent/memoryPermissionCodes.ts`
- Modify: `fe/src/modules/rag/ragTypes.ts`
- Modify: `fe/src/modules/rag/ragService.ts`
- Modify: `fe/src/modules/chat/chatTypes.ts`
- Modify: `fe/src/modules/chat/chatService.ts`
- Modify: `fe/src/layouts/AdminLayout/menu.tsx`
- Modify: `fe/src/layouts/AdminLayout/menu.test.tsx`
- Modify: `fe/src/app/routes.tsx`
- Test: `fe/src/modules/agent/agentService.test.ts`
- Test: `fe/src/modules/rag/ragService.test.ts`

- [ ] **Step 1: Add frontend memory types**

In `agentTypes.ts`:

```ts
export type AgentMemoryEntryType = 'AGENT_CHAT' | 'AGENT_DEBUG' | 'RAG_CHAT' | 'BUTLER_AGENT'
export type AgentMemorySessionStatus = 'ACTIVE' | 'RELEASED' | 'ARCHIVED' | 'DELETED'

export type AgentMemorySessionResponse = {
    sessionId: string
    entryType: AgentMemoryEntryType
    title?: string
    status: AgentMemorySessionStatus
    memoryEnabled: boolean
    longTermExtractionEnabled: boolean
    shortTermWindowTurns: number
    lastActiveAt?: string
    createdAt?: string
    updatedAt?: string
}

export type AgentMemorySessionRequest = {
    entryType: AgentMemoryEntryType
    title?: string
    memoryEnabled?: boolean
    longTermExtractionEnabled?: boolean
}

export type AgentLongTermMemoryResponse = {
    contentMarkdown: string
    contentChars: number
    status: string
    updatedAt?: string
}
```

Extend `AgentDebugChatRequest`:

```ts
sessionId?: string
memoryEnabled?: boolean
longTermExtractionEnabled?: boolean
```

- [ ] **Step 2: Add frontend API clients**

In `agentService.ts` add:

```ts
export function getAgentMemorySessions(params: {
    page?: number
    size?: number
    entryType?: AgentMemoryEntryType
    status?: AgentMemorySessionStatus
    archived?: boolean
}) {
    return requestJson<AgentPage<AgentMemorySessionResponse>>(`/api/agent/memory/sessions?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        entryType: params.entryType,
        status: params.status,
        archived: params.archived,
    })}`)
}

export function createAgentMemorySession(request: AgentMemorySessionRequest) {
    return requestJson<AgentMemorySessionResponse>('/api/agent/memory/sessions', {method: 'POST', body: request})
}

export function updateAgentMemorySession(sessionId: string, request: Partial<AgentMemorySessionRequest>) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}`, {method: 'PATCH', body: request})
}

export function releaseAgentMemorySession(sessionId: string) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}/release`, {method: 'POST'})
}

export function restoreAgentMemorySession(sessionId: string) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}/restore`, {method: 'POST'})
}

export function archiveAgentMemorySession(sessionId: string) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}/archive`, {method: 'POST'})
}

export function deleteAgentMemorySession(sessionId: string) {
    return requestJson<void>(`/api/agent/memory/sessions/${sessionId}`, {method: 'DELETE'})
}

export function getAgentLongTermMemory() {
    return requestJson<AgentLongTermMemoryResponse>('/api/agent/memory/long-term')
}
```

- [ ] **Step 3: Update RAG and chat request types**

`RagChatRequest` adds:

```ts
memoryEnabled?: boolean
longTermExtractionEnabled?: boolean
```

`RagStreamEvent` metadata includes:

```ts
sessionId?: string
memoryEnabled?: boolean
longTermExtractionEnabled?: boolean
```

`ChatRequest` in `chatTypes.ts` includes:

```ts
sessionId?: string
memoryEnabled?: boolean
```

- [ ] **Step 4: Add menu and routes**

`menu.tsx`:

- Add `ProfileOutlined` and `InboxOutlined` imports.
- Add paths `/agent/memory` and `/agent/memory/archive` to `menuPathByKey`.
- Add two children under Agent 管理:

```tsx
{
    key: '/agent/memory',
    icon: <ProfileOutlined/>,
    label: '记忆管理',
},
{
    key: '/agent/memory/archive',
    icon: <InboxOutlined/>,
    label: '归档会话',
},
```

`routes.tsx`:

```tsx
{path: 'agent/memory', lazy: () => import('../modules/agent/AgentMemoryPage')},
{path: 'agent/memory/archive', lazy: () => import('../modules/agent/AgentMemoryArchivePage')},
```

- [ ] **Step 5: Add client tests**

Extend `agentService.test.ts`:

```ts
test('builds memory session requests', async () => {
    const {requestJson} = await import('../../services/request')

    void getAgentMemorySessions({page: 2, size: 30, entryType: 'AGENT_CHAT'})
    void createAgentMemorySession({entryType: 'AGENT_CHAT', title: 'Chat'})
    void updateAgentMemorySession('session-1', {memoryEnabled: false})
    void releaseAgentMemorySession('session-1')
    void restoreAgentMemorySession('session-1')
    void archiveAgentMemorySession('session-1')
    void deleteAgentMemorySession('session-1')
    void getAgentLongTermMemory()

    expect(requestJson).toHaveBeenNthCalledWith(1, '/api/agent/memory/sessions?page=2&size=30&entryType=AGENT_CHAT')
    expect(requestJson).toHaveBeenNthCalledWith(2, '/api/agent/memory/sessions', {method: 'POST', body: {entryType: 'AGENT_CHAT', title: 'Chat'}})
    expect(requestJson).toHaveBeenNthCalledWith(3, '/api/agent/memory/sessions/session-1', {method: 'PATCH', body: {memoryEnabled: false}})
    expect(requestJson).toHaveBeenNthCalledWith(4, '/api/agent/memory/sessions/session-1/release', {method: 'POST'})
    expect(requestJson).toHaveBeenNthCalledWith(5, '/api/agent/memory/sessions/session-1/restore', {method: 'POST'})
    expect(requestJson).toHaveBeenNthCalledWith(6, '/api/agent/memory/sessions/session-1/archive', {method: 'POST'})
    expect(requestJson).toHaveBeenNthCalledWith(7, '/api/agent/memory/sessions/session-1', {method: 'DELETE'})
    expect(requestJson).toHaveBeenNthCalledWith(8, '/api/agent/memory/long-term')
})
```

- [ ] **Step 6: Run frontend service and menu tests**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- agentService.test.ts ragService.test.ts menu.test.tsx
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add fe/src/modules/agent/agentTypes.ts \
  fe/src/modules/agent/agentService.ts \
  fe/src/modules/agent/memoryPermissionCodes.ts \
  fe/src/modules/rag/ragTypes.ts \
  fe/src/modules/rag/ragService.ts \
  fe/src/modules/chat/chatTypes.ts \
  fe/src/modules/chat/chatService.ts \
  fe/src/layouts/AdminLayout/menu.tsx \
  fe/src/layouts/AdminLayout/menu.test.tsx \
  fe/src/app/routes.tsx \
  fe/src/modules/agent/agentService.test.ts \
  fe/src/modules/rag/ragService.test.ts
git commit -m "feat(agent-memory): add frontend memory contracts"
```

---

### Task 9: Frontend Session Controls And Memory Pages

**Files:**
- Create: `fe/src/modules/agent/memorySessionControls.tsx`
- Create: `fe/src/modules/agent/AgentMemoryPage.tsx`
- Create: `fe/src/modules/agent/AgentMemoryArchivePage.tsx`
- Modify: `fe/src/modules/chat/pages/ChatPage.tsx`
- Modify: `fe/src/modules/agent/AgentDebugPage.tsx`
- Modify: `fe/src/modules/rag/RagChatPage.tsx`

- [ ] **Step 1: Create shared session controls**

Create `memorySessionControls.tsx`:

```tsx
import {Button, Select, Space, Switch, Tag} from 'antd'
import {ArchiveOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons'
import type {AgentMemoryEntryType, AgentMemorySessionResponse} from './agentTypes'

type Props = {
    entryType: AgentMemoryEntryType
    sessions: AgentMemorySessionResponse[]
    sessionId?: string
    memoryEnabled: boolean
    longTermExtractionEnabled?: boolean
    showExtractionSwitch?: boolean
    loading?: boolean
    onCreate: () => void
    onSelect: (sessionId: string) => void
    onMemoryChange: (enabled: boolean) => void
    onExtractionChange?: (enabled: boolean) => void
    onArchive?: () => void
    onReload?: () => void
}

export function MemorySessionControls(props: Props) {
    return (
        <Space wrap>
            <Select
                loading={props.loading}
                options={props.sessions.map((item) => ({
                    label: item.title || item.sessionId,
                    value: item.sessionId,
                }))}
                placeholder="选择会话"
                style={{minWidth: 220}}
                value={props.sessionId}
                onChange={props.onSelect}
            />
            <Button icon={<PlusOutlined/>} onClick={props.onCreate}>新会话</Button>
            <Switch checked={props.memoryEnabled} checkedChildren="Memory" unCheckedChildren="Memory"
                    onChange={props.onMemoryChange}/>
            {props.showExtractionSwitch && (
                <Switch checked={props.longTermExtractionEnabled} checkedChildren="长期提取" unCheckedChildren="长期提取"
                        onChange={props.onExtractionChange}/>
            )}
            {props.onArchive && <Button icon={<ArchiveOutlined/>} onClick={props.onArchive}>归档</Button>}
            {props.onReload && <Button icon={<ReloadOutlined/>} onClick={props.onReload}/>}
            {props.sessionId && <Tag>{props.sessionId}</Tag>}
        </Space>
    )
}
```

- [ ] **Step 2: Implement `AgentMemoryPage`**

The page loads:

```ts
const [longTerm, setLongTerm] = useState<AgentLongTermMemoryResponse>()
const [sessions, setSessions] = useState<AgentMemorySessionResponse[]>([])
```

Use `ReactMarkdown` to render `longTerm.contentMarkdown` read-only. Do not include an editable text area. Add filters for entry type and status using `Select`.

- [ ] **Step 3: Implement `AgentMemoryArchivePage`**

Load sessions with:

```ts
getAgentMemorySessions({archived: true, status: 'ARCHIVED', page: 1, size: 100})
```

Rows have `恢复` and `删除` buttons. Delete uses `Popconfirm` and calls `deleteAgentMemorySession`.

- [ ] **Step 4: Integrate Agent Chat page**

In `ChatPage.tsx`:

- Replace `threadId` state with `sessionId`.
- Add `memoryEnabled` state default `true`.
- Load `AGENT_CHAT` sessions on mount.
- `handleNewConversation` calls `createAgentMemorySession({entryType: 'AGENT_CHAT', memoryEnabled})`.
- Submit sends:

```ts
await streamChatResponse({
    message,
    sessionId,
    memoryEnabled,
    signal: abortController.signal,
}, onChunk)
```

When stream chunk contains `threadId`, set `sessionId` to that value for compatibility.

- [ ] **Step 5: Integrate Agent Debug page**

Use `MemorySessionControls` with `entryType="AGENT_DEBUG"`. Send:

```ts
{
    message: text,
    sessionId,
    memoryEnabled,
    longTermExtractionEnabled,
    presetId: values.presetId,
    overrides: toConfig(values),
}
```

- [ ] **Step 6: Integrate RAG Chat page**

Use `MemorySessionControls` with `entryType="RAG_CHAT"` and `showExtractionSwitch`. Send:

```ts
{
    sessionId,
    memoryEnabled,
    longTermExtractionEnabled,
    question,
    knowledgeBaseIds: values.knowledgeBaseIds,
    retrievalOptions: {
        topK: values.topK,
        candidateTopK: values.candidateTopK,
        similarityThreshold: values.similarityThreshold,
        searchMode: values.searchMode,
        rerankEnabled: values.rerankEnabled,
    },
    withSources: true,
}
```

In metadata event handler:

```ts
if (event.data.sessionId) {
    setSessionId(event.data.sessionId)
}
```

Do not show memory as a source card.

- [ ] **Step 7: Build frontend**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm run build
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add fe/src/modules/agent/memorySessionControls.tsx \
  fe/src/modules/agent/AgentMemoryPage.tsx \
  fe/src/modules/agent/AgentMemoryArchivePage.tsx \
  fe/src/modules/chat/pages/ChatPage.tsx \
  fe/src/modules/agent/AgentDebugPage.tsx \
  fe/src/modules/rag/RagChatPage.tsx
git commit -m "feat(agent-memory): add memory management UI"
```

---

### Task 10: Full Verification And Regression Pass

**Files:**
- Modify only files needed to fix failures found by the commands in this task.

- [ ] **Step 1: Run targeted backend tests**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 \
  -Dtest=AgentMemorySchemaMigrationTests,AgentMemorySessionServiceTests,AgentMemoryMessageServiceTests,AgentLongTermMemoryServiceTests,AgentMemoryExtractionServiceTests,AgentMemoryContextServiceTests,AgentMemoryMessagesHookTests,AgentMemoryCheckpointerProviderTests,ReactAgentChatServiceTests,ChatControllerTests,AgentDebugControllerTests,RagChatMemoryServiceTests,AgentMemoryControllerTests,AgentMemoryRbacResourceProviderTests \
  test
```

Expected: PASS.

- [ ] **Step 2: Run backend full test suite**

```bash
cd /Users/mario/SelfProject/CyberMario/be
mvn -Dmaven.build.cache.enabled=false -Djava.version=21 test
```

Expected: PASS. If this command fails, run the targeted command from Step 1 again. Report both outputs in the final implementation summary and fix memory-related failures before proceeding.

- [ ] **Step 3: Run frontend tests**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm test -- agentService.test.ts ragService.test.ts menu.test.tsx
```

Expected: PASS.

- [ ] **Step 4: Run frontend build**

```bash
cd /Users/mario/SelfProject/CyberMario/fe
npm run build
```

Expected: PASS.

- [ ] **Step 5: Check formatting and staged diff**

```bash
cd /Users/mario/SelfProject/CyberMario
git diff --check
git status --short
```

Expected: `git diff --check` exits 0. `git status --short` shows only intended memory feature changes if fixes were made after prior commits.

- [ ] **Step 6: Commit verification fixes**

When Step 5 shows modified files, first print the exact paths:

```bash
git status --short
```

For each path printed by `git status --short`, stage it only when it is part of the Agent Memory implementation or a test/build fix caused by Agent Memory. Use explicit path arguments, for example:

```bash
git add be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryContextServiceImpl.java
git add be/src/test/java/top/egon/mario/agent/memory/AgentMemoryContextServiceTests.java
git commit -m "fix(agent-memory): address verification findings"
```

When Step 5 shows no modified files, do not create an empty commit.

---

## Self-Review Notes

- Spec coverage: Tasks 1-3 cover persistence, lifecycle, long-term Markdown, source/version audit, and extraction. Tasks 4-6 cover persistent short-term memory, Agent/RAG prompt boundaries, 10-turn windows, and RAG extraction switches. Task 7 covers RBAC and user-scoped APIs. Tasks 8-9 cover frontend memory controls, management page, archive page, and no manual Markdown editing. Task 10 covers targeted and full validation.
- Scope boundaries retained: no long-term vector retrieval, no RAG knowledge-base writes, no cross-user reads, no Butler Agent implementation, no user Markdown editor, no physical delete.
- Risk called out for execution: if `PostgresSaver` table creation conflicts with Flyway validation in a specific environment, keep `PostgresSaver.createTables=true` for framework tables and do not modify existing Flyway files; add a new migration only if a stable framework table name is confirmed from local runtime logs.
