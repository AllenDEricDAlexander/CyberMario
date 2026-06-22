# Agent Chat Final Snapshot History Design

**Date:** 2026-06-22

**Goal:** Fix Agent Chat, Agent Debug, and RAG Chat session history so every persisted turn is recoverable as a final snapshot. History must not duplicate assistant text when a model streams cumulative chunks, failed model calls must keep the user's input and show a recoverable assistant error, and final thinking content should restore into the existing thinking block.

**Current Background:** The chat workspaces now load persisted memory messages when a session is selected. The remaining bug is in the persistence and stream failure boundary. `ReactAgentChatService` and `RagChatServiceImpl` currently collect stream chunks and write memory only after a successful completion. If a provider emits cumulative chunks, joining chunks corrupts the persisted assistant message. If the model call fails, the session can exist without the user's message or a persisted assistant error. RAG also does not have the same structured error event path as Agent Chat.

---

## 1. User-Confirmed Direction

- Store only final complete records for each turn.
- Do not store stream-process deltas, cumulative chunks, intermediate function-call events, or replay events.
- Add fields to `agent_memory_message` instead of creating a stream event table or a turn table.
- Keep history reads based on `agent_memory_message`, grouped by `turn_no`.
- Use one new Flyway migration for the database change. Do not modify existing migrations.

---

## 2. Scope

### 2.1 In Scope

- Agent Chat (`/chat`), Agent Debug (`/agent/debug`), and RAG Chat (`/rag/chat`).
- Persist the user message before invoking the model.
- Persist final assistant `THINK`, `MESSAGE`, `ERROR`, and RAG/source metadata after the model reaches a terminal state.
- Deduplicate cumulative stream chunks before persisting the final assistant text.
- Make Agent and RAG stream failures visible to the frontend as structured error chunks/events.
- Ensure failed requests stop the frontend loading state and restore correctly from history.
- Preserve existing history mapping behavior for user messages, assistant messages, thinking blocks, errors, and sources.
- Add focused backend and frontend tests for final snapshot persistence, cumulative chunk merging, stream errors, and history mapping.

### 2.2 Out of Scope

- Persisting stream-process events.
- Replaying a stream from persisted events.
- Adding a dedicated turn table.
- Automatically repairing already-corrupted historical assistant text.
- Starting the project or opening a browser.
- Broad refactors outside the chat memory persistence and stream handling path.

---

## 3. Data Model

Add the following nullable/defaulted columns to `agent_memory_message` with exactly one new Flyway migration:

| Column | Type | Purpose |
|---|---|---|
| `message_status` | `VARCHAR(32)` | Final row state: `SUCCEEDED`, `FAILED`, or `CANCELLED`. Existing rows default to `SUCCEEDED`. |
| `error_code` | `VARCHAR(256)` | Exception class or business error code for failed assistant rows. |
| `error_message` | `TEXT` | Original backend error message for failed assistant rows. |
| `metadata_json` | `TEXT` | Final structured metadata such as tool/function summaries, model metadata, or RAG details that do not belong in visible content. |

The existing columns remain the primary chat-history model:

| Column | Role |
|---|---|
| `session_id` | Conversation identity. |
| `turn_no` | Groups one user request and its assistant final records. |
| `seq_no` | Stable display order. |
| `role` | `USER`, `ASSISTANT`, or future/system roles. |
| `message_type` | Final display type: `MESSAGE`, `THINK`, `ERROR`, `RAG_SOURCES`. |
| `content` | Final visible content. |
| `source_refs_json` | Existing RAG source references. |
| `trace_id` / `request_id` | Diagnostics and correlation. |

`message_status` is a row status, not a separate turn status. A failed turn will usually have a `USER MESSAGE` row with `message_status=SUCCEEDED` because the user input was accepted and persisted, followed by an `ASSISTANT ERROR` row with `message_status=FAILED`.

Tool/function-call details are not stored as stream events. If a final, stable summary is available, store it in `metadata_json`. Only add `TOOL_CALL` / `TOOL_RESULT` message types if the implementation can reliably produce final user-facing records; otherwise keep the enum unchanged.

---

## 4. Final Snapshot Rules

Each request produces one final snapshot group under a single `turn_no`.

### 4.1 Request Start

1. Resolve or create the memory session.
2. Allocate the next `turn_no` once.
3. Immediately append the user message:

| Field | Value |
|---|---|
| `role` | `USER` |
| `message_type` | `MESSAGE` |
| `message_status` | `SUCCEEDED` |
| `content` | User input |
| `turn_no` | Allocated turn number |
| `trace_id` / `request_id` | Current request values |

This makes the user's input recoverable even when the model call fails.

### 4.2 Stream Processing

During streaming, keep chunks only in request-local memory. Do not write stream chunks to `agent_memory_message`.

Use one shared merge rule for Agent message chunks, Agent thinking chunks, and RAG deltas:

1. If the current text is empty, use the incoming chunk.
2. If the incoming chunk starts with the current text, replace current text with the incoming chunk.
3. If the current text already ends with the incoming chunk, keep current text.
4. Otherwise append the incoming chunk.

This supports both delta streams and cumulative-full-text streams.

### 4.3 Successful Completion

Append only final assistant rows:

| Row | Condition |
|---|---|
| `ASSISTANT THINK` | Final thinking text is non-blank. |
| `ASSISTANT MESSAGE` | Final answer text is non-blank. |
| `ASSISTANT RAG_SOURCES` or `source_refs_json` | RAG has final source metadata. |
| `metadata_json` | Final structured tool/function/model metadata is available. |

All successful assistant rows use `message_status=SUCCEEDED`.

Long-term memory extraction runs only after successful completion and only when the session's extraction switch is enabled.

### 4.4 Failure Completion

When the model, retrieval, prompt construction, or stream read fails after the session has been resolved:

1. Append an assistant error row under the same `turn_no`.
2. Emit a structured error chunk/event to the frontend.
3. Complete the HTTP stream normally after the error chunk/event.
4. Do not run long-term memory extraction for the failed turn.

The persisted assistant error row should use:

| Field | Value |
|---|---|
| `role` | `ASSISTANT` |
| `message_type` | `ERROR` |
| `message_status` | `FAILED` |
| `content` | User-facing error message, such as `模型调用失败：...` |
| `error_code` | Exception class or business code |
| `error_message` | Raw backend error message |
| `turn_no` | Same turn as the user row |

If a stream is cancelled by the user, persist no assistant final message unless the current code path already has a deliberate cancellation snapshot. If a cancellation row is later added, use `message_status=CANCELLED`; do not treat cancellation as a model failure.

---

## 5. Stream API Behavior

### 5.1 Agent Chat and Agent Debug

The existing NDJSON `ChatResponse` format remains:

```json
{"threadId":"session-id","message":"...","type":"message"}
{"threadId":"session-id","message":"...","type":"think"}
{"threadId":"session-id","message":"模型调用失败：...","type":"error"}
```

On failure, the stream must emit the `type="error"` chunk and complete. The frontend already maps Agent error chunks to an assistant error state and `markMessageSucceeded` preserves error status.

### 5.2 RAG Chat

RAG keeps the existing successful event sequence:

```text
metadata -> retrieval -> delta* -> done
```

On failure, RAG emits:

```json
{
  "type": "error",
  "data": {
    "code": "java.lang.IllegalStateException",
    "message": "模型调用失败：...",
    "traceId": "...",
    "sessionId": "..."
  }
}
```

Then the stream completes. The frontend should treat this event as terminal for the current assistant placeholder and stop loading.

RAG frontend delta application should use the same merge rule as Agent chunks instead of unconditional string concatenation.

---

## 6. History Restore Behavior

The history endpoint still returns `AgentMemoryMessageResponse[]` sorted or sortable by `seq_no`. The frontend mapper continues to build workspace messages grouped by `turn_no`.

For each turn:

- `USER MESSAGE` becomes the user bubble.
- `ASSISTANT THINK` becomes `thinkContent` on the assistant bubble and renders through `ChatThinkingBlock`.
- `ASSISTANT MESSAGE` becomes the assistant visible answer.
- `ASSISTANT ERROR` marks the assistant bubble as `error`; if there is no answer content, the error content becomes the visible assistant content.
- RAG sources continue to attach to the assistant bubble through existing source parsing.
- `metadata_json` is retained in the response model only if the frontend needs it for display or diagnostics. It should not break history rendering if absent or invalid.

Rows from older data without `message_status`, `error_code`, `error_message`, or `metadata_json` remain valid. Missing `message_status` is treated as `SUCCEEDED` unless `message_type=ERROR`, which is treated as failed for display.

---

## 7. Backend Design

### 7.1 Memory Message Model

Extend the backend memory message PO, record, response DTO, and repository/service append logic to carry:

- `messageStatus`
- `errorCode`
- `errorMessage`
- `metadataJson`

Keep append behavior compatible with existing call sites by allowing simple successful rows to omit error fields.

### 7.2 Agent Chat Persistence

`ReactAgentChatService` should:

1. Resolve/create session.
2. Allocate `turn_no`.
3. Append the user message immediately.
4. Stream the agent output while accumulating final `think` and `message` content with the shared merge rule.
5. On success, append final assistant rows for thinking and answer.
6. On error, fail audits as today, append an assistant `ERROR` row, emit a `ChatResponse` error chunk, and complete.

The audit services can keep their existing completion/failure semantics. The persistence change should not alter audit records except for using the deduplicated final message content.

### 7.3 RAG Chat Persistence

`RagChatServiceImpl` should:

1. Resolve/create session before retrieval/model work.
2. Allocate `turn_no`.
3. Append the user question immediately.
4. Perform retrieval and emit `metadata`/`retrieval`.
5. Stream model deltas while accumulating final answer with the shared merge rule.
6. On success, append final assistant answer and final source metadata.
7. On retrieval or model failure, append an assistant `ERROR` row, emit a RAG `error` event, and complete.

The no-context path is a successful completion and should persist the no-context answer as an assistant `MESSAGE`.

---

## 8. Frontend Design

### 8.1 Shared Merge Helper

Expose or reuse one text merge helper for:

- Agent `ChatResponse` chunks.
- RAG `delta` events.
- Related tests that simulate cumulative full-text streams.

This prevents realtime RAG output and persisted final RAG output from diverging.

### 8.2 Error Visibility

Agent Chat and Agent Debug already route rejected stream promises to `useXChatWorkspace`, which marks the assistant placeholder as `error`. They also handle `type="error"` chunks. Keep both protections.

RAG should handle `type="error"` as terminal assistant state. If the fetch itself rejects before an error event arrives, the existing catch path should still mark the placeholder as failed and clear `isRequesting`.

### 8.3 History Mapping

Update frontend types and mapper as needed for the new fields, while keeping old history compatible. The mapper should not require `message_status` to render successful old messages.

---

## 9. Testing Plan

### 9.1 Backend Tests

Add or update focused tests for:

- Agent Chat stores the user row before model success.
- Agent Chat with cumulative message chunks stores only the final answer.
- Agent Chat with cumulative thinking chunks stores only the final thinking content.
- Agent Chat failure stores the user row and an assistant `ERROR` row.
- Agent Chat failure still emits a `type="error"` chunk and completes.
- RAG Chat stores the user row before retrieval/model success.
- RAG Chat with cumulative deltas stores only the final answer.
- RAG retrieval/model failure stores an assistant `ERROR` row, emits a RAG `error` event, and completes.
- No-context RAG remains a successful assistant `MESSAGE`.

### 9.2 Frontend Tests

Add or update focused tests for:

- Agent chunk merge still supports delta and cumulative streams.
- RAG delta merge supports delta and cumulative streams.
- RAG `error` event sets assistant content/error/status and preserves trace/session metadata.
- History mapper treats missing `messageStatus` as successful for old rows.
- History mapper maps `ERROR` rows to failed assistant bubbles.
- History mapper keeps `THINK` content visible through `thinkContent`.

### 9.3 Validation Commands

Use targeted validation first:

```bash
cd be && ./mvnw test -Dtest=ReactAgentChatServiceTests,RagChatMemoryServiceTests
cd fe && bun test chatMessageStream.test.ts chatWorkspaceMappers.test.ts
```

If targeted commands are not supported by the local runner, run the broader relevant suites:

```bash
cd be && ./mvnw test
cd fe && bun run test
cd fe && bun run typecheck
```

---

## 10. Acceptance Criteria

- Selecting a historical Agent Chat, Agent Debug, or RAG Chat session shows that session's persisted messages.
- A cumulative stream such as `你`, `你好`, `你好，Mario` persists and restores only `你好，Mario`.
- A delta stream such as `你`, `好` persists and restores `你好`.
- Failed model calls keep the user's input in history.
- Failed model calls persist an assistant error row with `message_status=FAILED`, `error_code`, and `error_message`.
- Failed Agent streams emit an Agent error chunk and stop frontend loading.
- Failed RAG streams emit a RAG error event and stop frontend loading.
- Final thinking content restores into the existing thinking block.
- RAG sources remain attached to the assistant message.
- Existing historical rows without the new columns continue to render.
- No stream-process events are written to memory.

---

## 11. Risks and Constraints

- Already-corrupted old assistant text cannot be safely repaired without a separate data cleanup strategy because the original chunk boundaries are gone.
- The current design repeats turn terminal state through final rows instead of adding a turn table. This keeps the change small, but future needs such as resumable turns or stream replay may require a dedicated turn/event model.
- Concurrent API calls to the same session can still race around `nextTurnNo` if callers bypass the frontend's single-request guard. This spec does not introduce a broader session-level lock unless implementation tests reveal a concrete failure in the current service boundary.
- Tool/function metadata is intentionally final-only. Intermediate tool calls remain available through existing observability/audit surfaces, not chat memory history.

---

## 12. Completion Standard

The implementation is complete when the database schema, backend persistence, stream failure handling, frontend stream rendering, history restore mapping, and focused tests all satisfy the acceptance criteria above without changing unrelated modules or existing Flyway migrations.
