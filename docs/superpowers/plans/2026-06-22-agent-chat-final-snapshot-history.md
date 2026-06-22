# Agent Chat Final Snapshot History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist each Agent Chat, Agent Debug, and RAG Chat turn as a final recoverable snapshot, with no duplicated assistant text, durable failed-turn user input, and visible stream errors.

**Architecture:** Add final-snapshot metadata fields to `agent_memory_message`, then keep stream chunks request-local and persist only terminal rows. Agent and RAG services will share a small text accumulator that supports both delta chunks and cumulative-full-text chunks. Frontend mapping remains history-driven, while RAG realtime deltas and error events become consistent with Agent Chat.

**Tech Stack:** Java 21, Spring Boot/WebFlux, Reactor, Spring AI Alibaba, JPA/Hibernate, Flyway SQL migrations, JUnit 5, Mockito, AssertJ, React, TypeScript, Vitest, Bun.

---

## Source Spec

Read before executing: `docs/superpowers/specs/2026-06-22-agent-chat-final-snapshot-history-design.md`.

Do not modify existing Flyway migrations. The next migration version is `V23` because `be/src/main/resources/db/migration` currently ends at `V22__add_clocktower_board_valid.sql`.

Do not start the project. Do not open a browser. Keep changes scoped to the files listed in this plan.

---

## File Structure

### Backend Schema and Memory Model

- Create `be/src/main/resources/db/migration/V23__extend_agent_memory_message_final_snapshot.sql`
  - Adds final snapshot columns to `agent_memory_message`.
- Create `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryMessageStatus.java`
  - Defines `SUCCEEDED`, `FAILED`, `CANCELLED`.
- Modify `be/src/main/java/top/egon/mario/agent/memory/po/AgentMemoryMessagePo.java`
  - Adds JPA fields for `messageStatus`, `errorCode`, `errorMessage`, `metadataJson`.
- Modify `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryMessageRecord.java`
  - Carries the new final snapshot fields while keeping the old constructor shape available.
- Modify `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentMemoryMessageResponse.java`
  - Exposes new fields to frontend history loading.
- Modify `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryMessageServiceImpl.java`
  - Copies new fields into persisted rows and defaults missing status to `SUCCEEDED`.

### Backend Stream Merge and Final Snapshot Persistence

- Create `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulator.java`
  - Owns the delta/cumulative merge rule used by Agent and RAG.
- Create `be/src/test/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulatorTests.java`
  - Proves merge behavior independent of the service code.
- Modify `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
  - Writes user row before model stream.
  - Accumulates final message/think content with `AgentMemoryTextAccumulator`.
  - Writes final assistant rows on success.
  - Writes `ERROR` row and emits error chunk on failure.
- Modify `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`
  - Covers user-first persistence, cumulative output persistence, thinking output persistence, and failure persistence.
- Modify `be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java`
  - Writes user row before retrieval/model work.
  - Accumulates final answer with `AgentMemoryTextAccumulator`.
  - Emits structured `error` events and persists `ERROR` rows on retrieval/model failure.
- Modify `be/src/test/java/top/egon/mario/rag/service/RagChatMemoryServiceTests.java`
  - Covers cumulative RAG deltas, no-context success, and failure snapshot persistence.

### Frontend Types and Rendering

- Modify `fe/src/modules/agent/agentTypes.ts`
  - Adds `AgentMemoryMessageStatus` and new optional history fields.
- Modify `fe/src/modules/rag/ragTypes.ts`
  - Adds `sessionId?: string` to RAG error events.
- Modify `fe/src/modules/chat/chatMessageStream.ts`
  - Exports `mergeStreamText` for RAG use.
- Modify `fe/src/modules/chat/chatMessageStream.test.ts`
  - Keeps delta/cumulative merge coverage on the exported helper.
- Modify `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`
  - Uses the shared merge helper for RAG deltas.
  - Reads persisted error fallback fields.
  - Keeps old persisted rows compatible.
- Modify `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`
  - Covers RAG cumulative delta, RAG error `sessionId`, missing `messageStatus`, and persisted error fallback fields.

---

## Task 1: Add Final Snapshot Columns and Memory Message Model Fields

**Files:**
- Create: `be/src/main/resources/db/migration/V23__extend_agent_memory_message_final_snapshot.sql`
- Create: `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryMessageStatus.java`
- Modify: `be/src/main/java/top/egon/mario/agent/memory/po/AgentMemoryMessagePo.java`
- Modify: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryMessageRecord.java`
- Modify: `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentMemoryMessageResponse.java`
- Modify: `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryMessageServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`

- [ ] **Step 1: Add the failing backend test assertion for final snapshot fields**

In `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`, update the existing `debugChatResolvesSpecAndWritesSuccessfulAudit` memory persistence verification so it expects the user and assistant rows to expose successful message status.

Replace the `verify(support.memoryMessageService).appendAll(...)` block in that test with this block:

```java
verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
        records.size() == 2
                && records.get(0).role() == AgentMemoryMessageRole.USER
                && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                && records.get(1).role() == AgentMemoryMessageRole.ASSISTANT
                && records.get(1).messageType() == AgentMemoryMessageType.MESSAGE
                && records.get(1).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                && records.get(1).errorCode() == null
                && records.get(1).errorMessage() == null
                && records.get(1).metadataJson() == null
                && records.get(1).content().equals("答案")));
```

Add this import near the existing memory enum imports:

```java
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
```

- [ ] **Step 2: Run the targeted backend test to verify it fails**

Run:

```bash
cd be && ./mvnw test -Dtest=ReactAgentChatServiceTests#debugChatResolvesSpecAndWritesSuccessfulAudit
```

Expected: FAIL during test compilation with an error that `AgentMemoryMessageStatus` or `messageStatus()` cannot be resolved.

- [ ] **Step 3: Create the Flyway migration**

Create `be/src/main/resources/db/migration/V23__extend_agent_memory_message_final_snapshot.sql` with exactly:

```sql
ALTER TABLE agent_memory_message
    ADD COLUMN message_status VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED',
    ADD COLUMN error_code VARCHAR(256),
    ADD COLUMN error_message TEXT,
    ADD COLUMN metadata_json TEXT;
```

- [ ] **Step 4: Create the message status enum**

Create `be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryMessageStatus.java`:

```java
package top.egon.mario.agent.memory.po.enums;

public enum AgentMemoryMessageStatus {
    SUCCEEDED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 5: Extend the JPA entity**

In `be/src/main/java/top/egon/mario/agent/memory/po/AgentMemoryMessagePo.java`, add this import:

```java
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
```

Add these fields between `requestId` and `createdAt`:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "message_status", nullable = false, length = 32)
    private AgentMemoryMessageStatus messageStatus = AgentMemoryMessageStatus.SUCCEEDED;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
```

- [ ] **Step 6: Extend the service record with a compatibility constructor and factories**

Replace the full content of `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryMessageRecord.java` with:

```java
package top.egon.mario.agent.memory.service.model;

import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;

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
        String requestId,
        AgentMemoryMessageStatus messageStatus,
        String errorCode,
        String errorMessage,
        String metadataJson
) {

    public AgentMemoryMessageRecord(String sessionId, Long userId, AgentMemoryEntryType entryType,
                                    int turnNo, AgentMemoryMessageRole role,
                                    AgentMemoryMessageType messageType, String content,
                                    String sourceRefsJson, String traceId, String requestId) {
        this(sessionId, userId, entryType, turnNo, role, messageType, content, sourceRefsJson,
                traceId, requestId, AgentMemoryMessageStatus.SUCCEEDED, null, null, null);
    }

    public static AgentMemoryMessageRecord failed(String sessionId, Long userId, AgentMemoryEntryType entryType,
                                                  int turnNo, AgentMemoryMessageRole role,
                                                  AgentMemoryMessageType messageType, String content,
                                                  String traceId, String requestId,
                                                  String errorCode, String errorMessage) {
        return new AgentMemoryMessageRecord(sessionId, userId, entryType, turnNo, role, messageType,
                content, null, traceId, requestId, AgentMemoryMessageStatus.FAILED,
                errorCode, errorMessage, null);
    }
}
```

- [ ] **Step 7: Extend the response DTO**

In `be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentMemoryMessageResponse.java`, add this import:

```java
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
```

Replace the record declaration with:

```java
public record AgentMemoryMessageResponse(
        Long id,
        String sessionId,
        AgentMemoryEntryType entryType,
        int seqNo,
        int turnNo,
        AgentMemoryMessageRole role,
        AgentMemoryMessageType messageType,
        String content,
        Integer contentChars,
        String sourceRefsJson,
        String traceId,
        String requestId,
        AgentMemoryMessageStatus messageStatus,
        String errorCode,
        String errorMessage,
        String metadataJson,
        Instant createdAt
) {
```

Replace the `from` factory call body with:

```java
        return new AgentMemoryMessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getEntryType(),
                message.getSeqNo(),
                message.getTurnNo(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                message.getContentChars(),
                message.getSourceRefsJson(),
                message.getTraceId(),
                message.getRequestId(),
                message.getMessageStatus(),
                message.getErrorCode(),
                message.getErrorMessage(),
                message.getMetadataJson(),
                message.getCreatedAt()
        );
```

- [ ] **Step 8: Persist final snapshot fields in the message service**

In `be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryMessageServiceImpl.java`, add this import:

```java
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
```

Inside `appendAll`, after `message.setRequestId(record.requestId());`, add:

```java
            message.setMessageStatus(record.messageStatus() == null
                    ? AgentMemoryMessageStatus.SUCCEEDED
                    : record.messageStatus());
            message.setErrorCode(record.errorCode());
            message.setErrorMessage(record.errorMessage());
            message.setMetadataJson(record.metadataJson());
```

- [ ] **Step 9: Run the targeted backend test**

Run:

```bash
cd be && ./mvnw test -Dtest=ReactAgentChatServiceTests#debugChatResolvesSpecAndWritesSuccessfulAudit
```

Expected: PASS.

- [ ] **Step 10: Commit Task 1**

Run:

```bash
git add \
  be/src/main/resources/db/migration/V23__extend_agent_memory_message_final_snapshot.sql \
  be/src/main/java/top/egon/mario/agent/memory/po/enums/AgentMemoryMessageStatus.java \
  be/src/main/java/top/egon/mario/agent/memory/po/AgentMemoryMessagePo.java \
  be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryMessageRecord.java \
  be/src/main/java/top/egon/mario/agent/memory/dto/response/AgentMemoryMessageResponse.java \
  be/src/main/java/top/egon/mario/agent/memory/service/impl/AgentMemoryMessageServiceImpl.java \
  be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java
git commit -m "feat(backend): extend memory message snapshots"
```

---

## Task 2: Add a Shared Backend Stream Text Accumulator

**Files:**
- Create: `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulator.java`
- Create: `be/src/test/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulatorTests.java`

- [ ] **Step 1: Write the accumulator tests**

Create `be/src/test/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulatorTests.java`:

```java
package top.egon.mario.agent.memory.service.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemoryTextAccumulatorTests {

    @Test
    void appendsDeltaChunks() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("你");
        accumulator.accept("好");

        assertThat(accumulator.content()).isEqualTo("你好");
        assertThat(accumulator.normalizedContent()).isEqualTo("你好");
    }

    @Test
    void replacesCumulativeChunks() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("你");
        accumulator.accept("你好");
        accumulator.accept("你好，Mario");

        assertThat(accumulator.content()).isEqualTo("你好，Mario");
    }

    @Test
    void ignoresDuplicateSuffixChunks() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("你好");
        accumulator.accept("好");

        assertThat(accumulator.content()).isEqualTo("你好");
    }

    @Test
    void normalizesBlankContentToNull() {
        AgentMemoryTextAccumulator accumulator = new AgentMemoryTextAccumulator();

        accumulator.accept("");
        accumulator.accept("   ");

        assertThat(accumulator.content()).isEqualTo("");
        assertThat(accumulator.normalizedContent()).isNull();
    }
}
```

- [ ] **Step 2: Run the accumulator test to verify it fails**

Run:

```bash
cd be && ./mvnw test -Dtest=AgentMemoryTextAccumulatorTests
```

Expected: FAIL during test compilation because `AgentMemoryTextAccumulator` does not exist.

- [ ] **Step 3: Implement the accumulator**

Create `be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulator.java`:

```java
package top.egon.mario.agent.memory.service.model;

import org.springframework.util.StringUtils;

/**
 * Accumulates model stream text that may arrive as deltas or cumulative full text.
 */
public class AgentMemoryTextAccumulator {

    private String content = "";

    public void accept(String chunk) {
        content = merge(content, chunk);
    }

    public String content() {
        return content;
    }

    public String normalizedContent() {
        return StringUtils.hasText(content) ? content : null;
    }

    public static String merge(String currentText, String chunkText) {
        String current = currentText == null ? "" : currentText;
        String chunk = chunkText == null ? "" : chunkText;
        if (chunk.isEmpty()) {
            return current;
        }
        if (current.isEmpty() || chunk.startsWith(current)) {
            return chunk;
        }
        if (current.endsWith(chunk)) {
            return current;
        }
        return current + chunk;
    }
}
```

- [ ] **Step 4: Run the accumulator test**

Run:

```bash
cd be && ./mvnw test -Dtest=AgentMemoryTextAccumulatorTests
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
git add \
  be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulator.java \
  be/src/test/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulatorTests.java
git commit -m "feat(backend): add memory stream text accumulator"
```

---

## Task 3: Persist Agent Chat Final Snapshots and Error Rows

**Files:**
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
- Modify: `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`

- [ ] **Step 1: Add Agent service tests for cumulative content and failed-turn persistence**

In `be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java`, add these imports:

```java
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.Message;
import static org.mockito.Mockito.inOrder;
```

Add these tests after `chatConvertsAgentFailureToErrorChunk`:

```java
    @Test
    void chatPersistsOnlyFinalCumulativeAssistantMessage() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(agent.stream(eq("你好"), any(RunnableConfig.class)))
                .willReturn(Flux.just(messageOutput("你"), messageOutput("你好"), messageOutput("你好，Mario")));

        StepVerifier.create(support.chatService.chat("你好", "thread-1", null))
                .expectNext(new ChatResponse("thread-1", "你", "message"))
                .expectNext(new ChatResponse("thread-1", "你好", "message"))
                .expectNext(new ChatResponse("thread-1", "你好，Mario", "message"))
                .verifyComplete();

        InOrder inOrder = inOrder(support.memoryMessageService);
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("你好")));
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("你好，Mario")));
    }

    @Test
    void chatPersistsOnlyFinalCumulativeThinkingMessage() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        given(agent.stream(eq("分析一下"), any(RunnableConfig.class)))
                .willReturn(Flux.just(
                        directOutput(new AssistantMessage("分析"), "THINKING"),
                        directOutput(new AssistantMessage("分析问题"), "THINKING"),
                        messageOutput("最终回答")));

        StepVerifier.create(support.chatService.chat("分析一下", "thread-1", null))
                .expectNext(new ChatResponse("thread-1", "分析", "think"))
                .expectNext(new ChatResponse("thread-1", "分析问题", "think"))
                .expectNext(new ChatResponse("thread-1", "最终回答", "message"))
                .verifyComplete();

        verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 2
                        && records.get(0).messageType() == AgentMemoryMessageType.THINK
                        && records.get(0).content().equals("分析问题")
                        && records.get(1).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(1).content().equals("最终回答")));
    }

    @Test
    void chatPersistsUserAndAssistantErrorWhenAgentFails() throws Exception {
        ReactAgent agent = mock(ReactAgent.class);
        TestSupport support = new TestSupport(agent);
        IllegalArgumentException failure = new IllegalArgumentException("[InvalidParameter] url error");
        given(agent.stream(eq("查这个链接"), any(RunnableConfig.class))).willReturn(Flux.error(failure));

        StepVerifier.create(support.chatService.chat("查这个链接", "thread-1", null))
                .assertNext(response -> {
                    assertThat(response.threadId()).isEqualTo("thread-1");
                    assertThat(response.type()).isEqualTo("error");
                    assertThat(response.message()).contains("模型调用失败");
                    assertThat(response.message()).contains("url error");
                })
                .verifyComplete();

        InOrder inOrder = inOrder(support.memoryMessageService);
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("查这个链接")));
        inOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.ERROR
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.FAILED
                        && records.get(0).content().contains("模型调用失败")
                        && records.get(0).errorCode().equals(IllegalArgumentException.class.getName())
                        && records.get(0).errorMessage().equals("[InvalidParameter] url error")));
        verify(support.memoryExtractionService, never()).extractAfterTurn(any());
    }
```

In the existing `debugChatResolvesSpecAndWritesSuccessfulAudit` test, replace the Task 1 memory persistence verification with this user-first sequence:

```java
        InOrder successfulMemoryOrder = inOrder(support.memoryMessageService);
        successfulMemoryOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("你好")));
        successfulMemoryOrder.verify(support.memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("答案")));
```

Add this helper near `messageOutput`:

```java
    private NodeOutput directOutput(Message message, String outputType) {
        return new DirectMessageOutput(message, outputType);
    }
```

Add this nested class near `TestSupport`:

```java
    private static final class DirectMessageOutput extends NodeOutput {

        private final Message message;

        private final String outputType;

        private DirectMessageOutput(Message message, String outputType) {
            super("node", "agent", new OverAllState(Map.of()));
            this.message = message;
            this.outputType = outputType;
        }

        public Message message() {
            return message;
        }

        public String getOutputType() {
            return outputType;
        }
    }
```

- [ ] **Step 2: Run Agent tests to verify the new behavior fails**

Run:

```bash
cd be && ./mvnw test -Dtest=ReactAgentChatServiceTests
```

Expected: FAIL because `ReactAgentChatService` still writes user and assistant together on success, does not persist failed memory rows, and still joins cumulative chunks.

- [ ] **Step 3: Import the accumulator and status enum in `ReactAgentChatService`**

In `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`, add imports:

```java
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.service.model.AgentMemoryTextAccumulator;
```

- [ ] **Step 4: Replace chunk lists with accumulators and allocate the turn before streaming**

In `executeChat`, replace:

```java
            List<String> messageChunks = new ArrayList<>();
            List<String> thinkChunks = new ArrayList<>();
```

with:

```java
            int turnNo = memoryMessageService.nextTurnNo(memorySession.getSessionId());
            persistUserMemory(memorySession, message, turnNo, requestId, traceId);
            AgentMemoryTextAccumulator messageContent = new AgentMemoryTextAccumulator();
            AgentMemoryTextAccumulator thinkContent = new AgentMemoryTextAccumulator();
```

Remove the unused `java.util.ArrayList` import if no other code uses it after this task.

- [ ] **Step 5: Update stream collection and finalization call sites**

Inside the reactive chain in `executeChat`, replace:

```java
                    .doOnNext(response -> collectAuditChunk(response, messageChunks, thinkChunks))
                    .doFinally(signalType -> {
                        finishAudit(signalType, auditId.get(), messageChunks, thinkChunks, null);
                        finishRunAudit(signalType, runAuditContext.get(), messageChunks, thinkChunks, null);
                        finishMemory(signalType, memorySession, message, messageChunks, thinkChunks, requestId, traceId);
                    })
                    .onErrorResume(error -> {
                        failAudit(auditId.get(), error);
                        failRunAudit(runAuditContext.get(), error);
                        return Flux.just(new ChatResponse(conversationThreadId, errorMessage(error), "error"));
                    });
```

with:

```java
                    .doOnNext(response -> collectAuditChunk(response, messageContent, thinkContent))
                    .doFinally(signalType -> {
                        finishAudit(signalType, auditId.get(), messageContent, thinkContent, null);
                        finishRunAudit(signalType, runAuditContext.get(), messageContent, thinkContent, null);
                        finishAssistantMemory(signalType, memorySession, turnNo, messageContent, thinkContent,
                                requestId, traceId);
                    })
                    .onErrorResume(error -> {
                        String userFacingError = errorMessage(error);
                        failAudit(auditId.get(), error);
                        failRunAudit(runAuditContext.get(), error);
                        failAssistantMemory(memorySession, turnNo, userFacingError, error, requestId, traceId);
                        return Flux.just(new ChatResponse(conversationThreadId, userFacingError, "error"));
                    });
```

- [ ] **Step 6: Replace helper methods in `ReactAgentChatService`**

Replace `collectAuditChunk`, `finishAudit`, `finishRunAudit`, `finishMemory`, and `normalizeContent` with these methods:

```java
    private void collectAuditChunk(ChatResponse response, AgentMemoryTextAccumulator messageContent,
                                   AgentMemoryTextAccumulator thinkContent) {
        if (response == null || response.message() == null) {
            return;
        }
        if ("think".equals(response.type())) {
            thinkContent.accept(response.message());
            return;
        }
        if ("message".equals(response.type())) {
            messageContent.accept(response.message());
        }
    }

    private void finishAudit(SignalType signalType, Long auditId, AgentMemoryTextAccumulator messageContent,
                             AgentMemoryTextAccumulator thinkContent, Throwable error) {
        if (auditId == null || error != null) {
            return;
        }
        if (signalType == SignalType.CANCEL) {
            auditService.cancel(auditId, Instant.now());
            return;
        }
        if (signalType == SignalType.ON_COMPLETE) {
            List<AgentConversationMessageRecord> messages = new ArrayList<>();
            String finalThinkContent = thinkContent.normalizedContent();
            if (StringUtils.hasText(finalThinkContent)) {
                messages.add(new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT,
                        AgentConversationMessageType.THINK, finalThinkContent));
            }
            String finalMessageContent = messageContent.normalizedContent();
            if (StringUtils.hasText(finalMessageContent)) {
                messages.add(new AgentConversationMessageRecord(AgentConversationRole.ASSISTANT,
                        AgentConversationMessageType.MESSAGE, finalMessageContent));
            }
            auditService.complete(auditId, messages, Instant.now());
        }
    }

    private void finishRunAudit(SignalType signalType, AgentRunAuditContext context,
                                AgentMemoryTextAccumulator messageContent,
                                AgentMemoryTextAccumulator thinkContent, Throwable error) {
        if (context == null || error != null) {
            return;
        }
        if (signalType == SignalType.CANCEL) {
            runAuditService.cancel(context, Instant.now());
            return;
        }
        if (signalType == SignalType.ON_COMPLETE) {
            runAuditService.complete(context, messageContent.normalizedContent(),
                    thinkContent.normalizedContent(), Instant.now());
        }
    }

    private void persistUserMemory(AgentMemorySessionPo session, String userMessage, int turnNo,
                                   String requestId, String traceId) {
        if (session == null) {
            return;
        }
        memoryMessageService.appendAll(List.of(new AgentMemoryMessageRecord(session.getSessionId(),
                session.getUserId(), session.getEntryType(), turnNo, AgentMemoryMessageRole.USER,
                AgentMemoryMessageType.MESSAGE, userMessage, null, traceId, requestId,
                AgentMemoryMessageStatus.SUCCEEDED, null, null, null)));
    }

    private void finishAssistantMemory(SignalType signalType, AgentMemorySessionPo session, int turnNo,
                                       AgentMemoryTextAccumulator messageContent,
                                       AgentMemoryTextAccumulator thinkContent,
                                       String requestId, String traceId) {
        if (session == null || signalType != SignalType.ON_COMPLETE) {
            return;
        }
        List<AgentMemoryMessageRecord> records = new ArrayList<>();
        String finalThinkContent = thinkContent.normalizedContent();
        if (StringUtils.hasText(finalThinkContent)) {
            records.add(new AgentMemoryMessageRecord(session.getSessionId(), session.getUserId(),
                    session.getEntryType(), turnNo, AgentMemoryMessageRole.ASSISTANT,
                    AgentMemoryMessageType.THINK, finalThinkContent, null, traceId, requestId,
                    AgentMemoryMessageStatus.SUCCEEDED, null, null, null));
        }
        String finalMessageContent = messageContent.normalizedContent();
        if (StringUtils.hasText(finalMessageContent)) {
            records.add(new AgentMemoryMessageRecord(session.getSessionId(), session.getUserId(),
                    session.getEntryType(), turnNo, AgentMemoryMessageRole.ASSISTANT,
                    AgentMemoryMessageType.MESSAGE, finalMessageContent, null, traceId, requestId,
                    AgentMemoryMessageStatus.SUCCEEDED, null, null, null));
        }
        if (records.isEmpty()) {
            return;
        }
        memoryMessageService.appendAll(records);
        if (session.isLongTermExtractionEnabled()) {
            memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(session.getSessionId(),
                    requestId, traceId));
        }
    }

    private void failAssistantMemory(AgentMemorySessionPo session, int turnNo, String userFacingError,
                                     Throwable error, String requestId, String traceId) {
        if (session == null) {
            return;
        }
        memoryMessageService.appendAll(List.of(AgentMemoryMessageRecord.failed(session.getSessionId(),
                session.getUserId(), session.getEntryType(), turnNo, AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.ERROR, userFacingError, traceId, requestId,
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage())));
    }
```

- [ ] **Step 7: Run Agent tests**

Run:

```bash
cd be && ./mvnw test -Dtest=ReactAgentChatServiceTests,AgentMemoryTextAccumulatorTests
```

Expected: PASS.

- [ ] **Step 8: Commit Task 3**

Run:

```bash
git add \
  be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java \
  be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java
git commit -m "fix(backend): persist agent chat final snapshots"
```

---

## Task 4: Persist RAG Final Snapshots and Emit RAG Error Events

**Files:**
- Modify: `be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java`
- Modify: `be/src/test/java/top/egon/mario/rag/service/RagChatMemoryServiceTests.java`

- [ ] **Step 1: Add RAG tests for cumulative answer persistence and failure snapshots**

In `be/src/test/java/top/egon/mario/rag/service/RagChatMemoryServiceTests.java`, add this import:

```java
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
```

Add these tests after `ragChatPersistsNoContextAnswerWithoutUsingMemoryAsKnowledge`:

```java
    @Test
    void ragChatPersistsOnlyFinalCumulativeAnswer() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(true), any()))
                .willReturn(session("rag-session-3", true, true));
        given(memoryContextService.contextFor(any(), any())).willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-3")).willReturn(4);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(source("S1", "source content")));
        given(chatModel.stream(any(Prompt.class)))
                .willReturn(Flux.just(response("你"), response("你好"), response("你好，Mario")));

        StepVerifier.create(service.stream(new RagChatRequest(
                        null, true, true, "继续说", List.of(1L), null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type()))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "你".equals(event.data().get("content")))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "你好".equals(event.data().get("content")))
                .expectNextMatches(event -> "delta".equals(event.type())
                        && "你好，Mario".equals(event.data().get("content")))
                .expectNextMatches(event -> "done".equals(event.type()))
                .verifyComplete();

        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("继续说")));
        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("你好，Mario")
                        && records.get(0).sourceRefsJson().contains("\"sourceId\":\"S1\"")));
    }

    @Test
    void ragChatPersistsErrorAndEmitsErrorEventWhenModelFails() {
        given(memorySessionService.resolveOrCreate(eq(AgentMemoryEntryType.RAG_CHAT), any(), eq(true), eq(false), any()))
                .willReturn(session("rag-session-4", true, false));
        given(memoryContextService.contextFor(any(), any())).willReturn(new AgentMemoryContext("", ""));
        given(memoryMessageService.nextTurnNo("rag-session-4")).willReturn(5);
        given(retrievalService.searchSources(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of(source("S1", "source content")));
        given(chatModel.stream(any(Prompt.class))).willReturn(Flux.error(new IllegalStateException("provider down")));

        StepVerifier.create(service.stream(new RagChatRequest(
                        null, true, false, "继续说", List.of(1L), null, null, true), principal))
                .expectNextMatches(event -> "metadata".equals(event.type())
                        && "rag-session-4".equals(event.data().get("sessionId")))
                .expectNextMatches(event -> "retrieval".equals(event.type()))
                .expectNextMatches(event -> "error".equals(event.type())
                        && String.valueOf(event.data().get("message")).contains("模型调用失败")
                        && IllegalStateException.class.getName().equals(event.data().get("code"))
                        && "rag-session-4".equals(event.data().get("sessionId")))
                .verifyComplete();

        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).content().equals("继续说")));
        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.ERROR
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.FAILED
                        && records.get(0).content().contains("模型调用失败")
                        && records.get(0).errorCode().equals(IllegalStateException.class.getName())
                        && records.get(0).errorMessage().equals("provider down")));
        verify(memoryExtractionService, never()).extractAfterTurn(any());
    }
```

In the existing `ragChatAddsOnlySessionShortTermMemoryBeforeSources` test, replace the `verify(memoryMessageService).appendAll(...)` block with these two verifications:

```java
        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).entryType() == AgentMemoryEntryType.RAG_CHAT
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("继续说")));
        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).entryType() == AgentMemoryEntryType.RAG_CHAT
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("回答")
                        && records.get(0).sourceRefsJson().contains("\"sourceId\":\"S1\"")));
```

In the existing `ragChatPersistsNoContextAnswerWithoutUsingMemoryAsKnowledge` test, replace the `verify(memoryMessageService).appendAll(...)` block with these two verifications:

```java
        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.USER
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).content().equals("没有来源的问题")));
        verify(memoryMessageService).appendAll(org.mockito.ArgumentMatchers.argThat(records ->
                records.size() == 1
                        && records.get(0).role() == AgentMemoryMessageRole.ASSISTANT
                        && records.get(0).messageType() == AgentMemoryMessageType.MESSAGE
                        && records.get(0).messageStatus() == AgentMemoryMessageStatus.SUCCEEDED
                        && records.get(0).content().equals("知识库中没有找到明确依据。")
                        && "[]".equals(records.get(0).sourceRefsJson())));
```

- [ ] **Step 2: Run RAG tests to verify the new behavior fails**

Run:

```bash
cd be && ./mvnw test -Dtest=RagChatMemoryServiceTests
```

Expected: FAIL because RAG still appends user and assistant together on success, joins cumulative deltas, and has no error event/persisted `ERROR` row.

- [ ] **Step 3: Import status and accumulator in `RagChatServiceImpl`**

In `be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java`, add imports:

```java
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
import top.egon.mario.agent.memory.service.model.AgentMemoryTextAccumulator;
```

- [ ] **Step 4: Update the stream method to create turn and persist user first**

Inside `stream`, after `AgentMemoryContext memoryContext = memoryContextService.contextFor(session, principal);`, add:

```java
            int turnNo = memoryMessageService.nextTurnNo(session.getSessionId());
            persistUserMemory(session, request, turnNo, traceId, messageId);
```

Replace no-context completion:

```java
                return header.concatWithValues(
                        event("delta", Map.of("content", noContextAnswer)),
                        event("done", Map.of("finishReason", "NO_CONTEXT"))
                ).doOnComplete(() -> finishMemory(session, request, noContextAnswer, sources, traceId, messageId));
```

with:

```java
                return header.concatWithValues(
                        event("delta", Map.of("content", noContextAnswer)),
                        event("done", Map.of("finishReason", "NO_CONTEXT"))
                ).doOnComplete(() -> finishAssistantMemory(session, turnNo, noContextAnswer,
                        sources, traceId, messageId));
```

Replace:

```java
            List<String> assistantDeltas = new ArrayList<>();
            Flux<RagStreamEvent> deltas = chatModel.stream(prompt)
                    .map(response -> response.getResult().getOutput().getText())
                    .filter(content -> content != null && !content.isBlank())
                    .doOnNext(assistantDeltas::add)
                    .map(content -> event("delta", Map.of("content", content)));
            return header.concatWith(deltas).concatWithValues(event("done", Map.of("finishReason", "STOP")))
                    .doOnComplete(() -> finishMemory(session, request, String.join("", assistantDeltas),
                            sources, traceId, messageId));
```

with:

```java
            AgentMemoryTextAccumulator assistantContent = new AgentMemoryTextAccumulator();
            Flux<RagStreamEvent> deltas = chatModel.stream(prompt)
                    .map(response -> response.getResult().getOutput().getText())
                    .filter(content -> content != null && !content.isBlank())
                    .doOnNext(assistantContent::accept)
                    .map(content -> event("delta", Map.of("content", content)));
            return header.concatWith(deltas).concatWithValues(event("done", Map.of("finishReason", "STOP")))
                    .doOnComplete(() -> finishAssistantMemory(session, turnNo,
                            assistantContent.normalizedContent(), sources, traceId, messageId))
                    .onErrorResume(error -> {
                        String userFacingError = errorMessage(error);
                        failAssistantMemory(session, turnNo, userFacingError, error, traceId, messageId);
                        return Flux.just(event("error", Map.of(
                                "code", error.getClass().getName(),
                                "message", userFacingError,
                                "traceId", traceId,
                                "sessionId", session.getSessionId())));
                    });
```

Remove the unused `java.util.ArrayList` import after this change.

- [ ] **Step 5: Replace RAG memory helper methods**

Replace `finishMemory` with these helper methods:

```java
    private void persistUserMemory(AgentMemorySessionPo session, RagChatRequest request,
                                   int turnNo, String traceId, String requestId) {
        memoryMessageService.appendAll(List.of(new AgentMemoryMessageRecord(
                session.getSessionId(),
                session.getUserId(),
                AgentMemoryEntryType.RAG_CHAT,
                turnNo,
                AgentMemoryMessageRole.USER,
                AgentMemoryMessageType.MESSAGE,
                request.question(),
                null,
                traceId,
                requestId,
                AgentMemoryMessageStatus.SUCCEEDED,
                null,
                null,
                null)));
    }

    private void finishAssistantMemory(AgentMemorySessionPo session, int turnNo, String assistantContent,
                                       List<SourceReferenceResponse> sources, String traceId, String requestId) {
        memoryMessageService.appendAll(List.of(
                new AgentMemoryMessageRecord(
                        session.getSessionId(),
                        session.getUserId(),
                        AgentMemoryEntryType.RAG_CHAT,
                        turnNo,
                        AgentMemoryMessageRole.ASSISTANT,
                        AgentMemoryMessageType.MESSAGE,
                        assistantContent,
                        sourceRefsJson(sources),
                        traceId,
                        requestId,
                        AgentMemoryMessageStatus.SUCCEEDED,
                        null,
                        null,
                        null)
        ));
        if (session.isLongTermExtractionEnabled()) {
            memoryExtractionService.extractAfterTurn(new AgentMemoryExtractionRequest(
                    session.getSessionId(), requestId, traceId));
        }
    }

    private void failAssistantMemory(AgentMemorySessionPo session, int turnNo, String userFacingError,
                                     Throwable error, String traceId, String requestId) {
        memoryMessageService.appendAll(List.of(AgentMemoryMessageRecord.failed(
                session.getSessionId(),
                session.getUserId(),
                AgentMemoryEntryType.RAG_CHAT,
                turnNo,
                AgentMemoryMessageRole.ASSISTANT,
                AgentMemoryMessageType.ERROR,
                userFacingError,
                traceId,
                requestId,
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage())));
    }

    private String errorMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return "模型调用失败：" + (message == null || message.isBlank() ? "请检查模型配置后重试" : message);
    }
```

- [ ] **Step 6: Run RAG tests**

Run:

```bash
cd be && ./mvnw test -Dtest=RagChatMemoryServiceTests,AgentMemoryTextAccumulatorTests
```

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

Run:

```bash
git add \
  be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java \
  be/src/test/java/top/egon/mario/rag/service/RagChatMemoryServiceTests.java
git commit -m "fix(backend): persist rag chat final snapshots"
```

---

## Task 5: Update Frontend Types, RAG Merge, and History Error Compatibility

**Files:**
- Modify: `fe/src/modules/agent/agentTypes.ts`
- Modify: `fe/src/modules/rag/ragTypes.ts`
- Modify: `fe/src/modules/chat/chatMessageStream.ts`
- Modify: `fe/src/modules/chat/chatMessageStream.test.ts`
- Modify: `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`
- Modify: `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`

- [ ] **Step 1: Add frontend failing tests for RAG cumulative merge and persisted error fields**

In `fe/src/modules/chat/chatMessageStream.test.ts`, update the import:

```ts
import {appendChatChunk, mergeStreamText} from './chatMessageStream'
```

Add this test before the closing `})`:

```ts
    test('exports text merge behavior for RAG streams', () => {
        expect(mergeStreamText('', '你')).toBe('你')
        expect(mergeStreamText('你', '你好')).toBe('你好')
        expect(mergeStreamText('你好', '好')).toBe('你好')
        expect(mergeStreamText('你', '好')).toBe('你好')
    })
```

In `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`, add these tests after `applies RAG retrieval then delta events to sources and streamed content`:

```ts
    test('replaces cumulative RAG deltas instead of duplicating content', () => {
        const first = applyRagEventToMessage({...workspaceMessage, content: ''}, {
            type: 'delta',
            data: {content: '你'},
        })
        const second = applyRagEventToMessage(first, {
            type: 'delta',
            data: {content: '你好'},
        })
        const third = applyRagEventToMessage(second, {
            type: 'delta',
            data: {content: '你好，Mario'},
        })

        expect(third.content).toBe('你好，Mario')
        expect(third.status).toBe('updating')
    })
```

Add this test after `maps persisted errors onto the assistant turn`:

```ts
    test('uses persisted error metadata when error content is blank', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'ERROR',
                content: '',
                errorCode: 'java.lang.IllegalStateException',
                errorMessage: 'provider down',
                messageStatus: 'FAILED',
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            role: 'assistant',
            content: 'provider down',
            error: 'provider down',
            status: 'error',
            errorCode: 'java.lang.IllegalStateException',
            errorMessage: 'provider down',
        })
    })
```

Update the existing `maps RAG error events to failed messages with trace fallback` error event data:

```ts
            data: {
                code: 'RAG_FAILED',
                message: '检索失败',
                sessionId: 'rag-session-1',
            },
```

And add `sessionId` to the expectation:

```ts
            sessionId: 'rag-session-1',
```

- [ ] **Step 2: Run frontend tests to verify they fail**

Run:

```bash
cd fe && bun test chatMessageStream.test.ts chatWorkspaceMappers.test.ts
```

Expected: FAIL because `mergeStreamText` is not exported, RAG delta still concatenates cumulative chunks, and persisted error metadata is not mapped.

- [ ] **Step 3: Extend frontend memory and RAG event types**

In `fe/src/modules/agent/agentTypes.ts`, add:

```ts
export type AgentMemoryMessageStatus = 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
```

Place it near the existing memory enum types.

In `AgentMemoryMessageResponse`, add optional fields after `requestId?: string`:

```ts
    messageStatus?: AgentMemoryMessageStatus
    errorCode?: string
    errorMessage?: string
    metadataJson?: string
```

In `fe/src/modules/rag/ragTypes.ts`, replace the error variant with:

```ts
    | { type: 'error'; data: { code: string; message: string; traceId?: string; sessionId?: string } }
```

- [ ] **Step 4: Export the shared frontend merge helper**

In `fe/src/modules/chat/chatMessageStream.ts`, change:

```ts
function mergeStreamText(currentText: string, chunkText: string) {
```

to:

```ts
export function mergeStreamText(currentText: string, chunkText: string) {
```

- [ ] **Step 5: Use the merge helper and error metadata in chat workspace mappers**

In `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`, change the import:

```ts
import {appendChatChunk} from '../../modules/chat/chatMessageStream'
```

to:

```ts
import {appendChatChunk, mergeStreamText} from '../../modules/chat/chatMessageStream'
```

In the `ERROR` memory message block, replace:

```ts
            const errorMessage = memoryMessage.content?.trim() || 'Request failed.'
```

with:

```ts
            const errorMessage = memoryMessage.content?.trim() ||
                memoryMessage.errorMessage?.trim() ||
                'Request failed.'
```

After `assistantMessage.error = errorMessage`, add:

```ts
            if (memoryMessage.errorCode) {
                assistantMessage.errorCode = memoryMessage.errorCode
            }
            if (memoryMessage.errorMessage) {
                assistantMessage.errorMessage = memoryMessage.errorMessage
            }
            if (memoryMessage.metadataJson) {
                assistantMessage.metadataJson = memoryMessage.metadataJson
            }
```

In `applyRagEventToMessage`, replace the `delta` case return body with:

```ts
            return {
                ...message,
                content: mergeStreamText(message.content, event.data.content),
                status: 'updating',
            }
```

In the `error` case, replace the return body with:

```ts
            return {
                ...message,
                content: event.data.message,
                error: event.data.message,
                errorCode: event.data.code,
                status: 'error',
                traceId: event.data.traceId ?? message.traceId,
                sessionId: event.data.sessionId ?? message.sessionId,
            }
```

- [ ] **Step 6: Run frontend tests**

Run:

```bash
cd fe && bun test chatMessageStream.test.ts chatWorkspaceMappers.test.ts
```

Expected: PASS.

- [ ] **Step 7: Commit Task 5**

Run:

```bash
git add \
  fe/src/modules/agent/agentTypes.ts \
  fe/src/modules/rag/ragTypes.ts \
  fe/src/modules/chat/chatMessageStream.ts \
  fe/src/modules/chat/chatMessageStream.test.ts \
  fe/src/components/chat-workspace/chatWorkspaceMappers.ts \
  fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts
git commit -m "fix(frontend): merge rag streams and map memory errors"
```

---

## Task 6: Run Full Targeted Validation and Fix Integration Breaks

**Files:**
- Modify only files already touched by Tasks 1-5 if validation exposes integration issues.
- Do not touch unrelated Clocktower files or existing Flyway migrations.

- [ ] **Step 1: Run backend targeted tests**

Run:

```bash
cd be && ./mvnw test -Dtest=ReactAgentChatServiceTests,RagChatMemoryServiceTests,AgentMemoryTextAccumulatorTests
```

Expected: PASS.

- [ ] **Step 2: Run frontend targeted tests**

Run:

```bash
cd fe && bun test chatMessageStream.test.ts chatWorkspaceMappers.test.ts
```

Expected: PASS.

- [ ] **Step 3: Run frontend typecheck**

Run:

```bash
cd fe && bun run typecheck
```

Expected: PASS.

- [ ] **Step 4: Run broader frontend test suite**

Run:

```bash
cd fe && bun run test
```

Expected: PASS.

- [ ] **Step 5: Inspect git status**

Run:

```bash
git status --short
```

Expected: only files intentionally changed by this plan are listed, or no files are listed if every task has already been committed.

- [ ] **Step 6: Commit validation fixes if Step 1-4 required changes**

If Step 1-4 required any code fixes, commit only those scoped fixes:

```bash
git add \
  be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java \
  be/src/main/java/top/egon/mario/rag/service/impl/RagChatServiceImpl.java \
  be/src/main/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulator.java \
  be/src/test/java/top/egon/mario/agent/ReactAgentChatServiceTests.java \
  be/src/test/java/top/egon/mario/rag/service/RagChatMemoryServiceTests.java \
  be/src/test/java/top/egon/mario/agent/memory/service/model/AgentMemoryTextAccumulatorTests.java \
  fe/src/modules/agent/agentTypes.ts \
  fe/src/modules/rag/ragTypes.ts \
  fe/src/modules/chat/chatMessageStream.ts \
  fe/src/modules/chat/chatMessageStream.test.ts \
  fe/src/components/chat-workspace/chatWorkspaceMappers.ts \
  fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts
git commit -m "fix: resolve final snapshot integration issues"
```

If Step 1-4 pass without new changes, do not create an empty commit.

---

## Self-Review Checklist

- Spec coverage:
  - Database fields: Task 1.
  - User row before model call: Tasks 3 and 4.
  - Final-only assistant snapshots: Tasks 3 and 4.
  - Delta and cumulative merge: Tasks 2, 3, 4, and 5.
  - Agent error chunk and persisted error row: Task 3.
  - RAG error event and persisted error row: Task 4.
  - Frontend loading/error state compatibility: Task 5.
  - History mapping of `THINK`, `ERROR`, and old rows: Task 5, plus existing mapper tests.
  - No stream event table and no old migration edits: File Structure and Task 1.
- Placeholder scan:
  - No task depends on unfilled values.
  - Every new class, enum, SQL migration, helper, and test method is named explicitly.
  - Every validation step includes an exact command and expected result.
- Type consistency:
  - Backend uses `AgentMemoryMessageStatus`.
  - Java record uses `messageStatus`, `errorCode`, `errorMessage`, `metadataJson`.
  - Frontend uses `messageStatus`, `errorCode`, `errorMessage`, `metadataJson`.
  - RAG error event uses `code`, `message`, `traceId`, and `sessionId`.

---

## Execution Handoff

Plan complete. Execute with one of these approaches:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, and commit each task separately.
2. **Inline Execution** - execute tasks in this session with `superpowers:executing-plans`, using the checkboxes as checkpoints.
