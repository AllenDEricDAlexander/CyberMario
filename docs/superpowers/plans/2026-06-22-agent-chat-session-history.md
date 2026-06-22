# Agent Chat Session History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore persisted Agent Memory conversation history when a user selects an existing chat session in Agent Chat, Agent Debug, or RAG Chat.

**Architecture:** Reuse the existing `/api/agent/memory/sessions/{sessionId}/messages` endpoint through `getAgentMemoryMessages(sessionId)`. Add one shared mapper that converts persisted `AgentMemoryMessageResponse[]` into `ChatWorkspaceMessage[]`, then call it from the three chat pages when conversations change. Keep backend, database, RBAC, and `useXChatWorkspace` unchanged.

**Tech Stack:** React 19, TypeScript 6, Ant Design 6, Ant Design X, Vitest, Bun, existing CyberMario frontend services.

---

## Scope Check

The approved spec is a single frontend bug fix. It does not span independent subsystems. Do not split this into backend, database, or RBAC work. Do not start the project.

Existing unrelated workspace changes are present in Clocktower backend files. Do not stage, edit, revert, or commit those files. Every commit below uses explicit pathspecs.

## File Structure

- Modify `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`
  - Owns unit coverage for mapping persisted memory rows into workspace messages.
- Modify `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`
  - Owns shared mapping helpers for session rows, workspace bubble rows, stream updates, and the new memory history restore mapper.
- Modify `fe/src/modules/chat/pages/ChatPage.tsx`
  - Loads `AGENT_CHAT` history when a sidebar conversation is selected.
- Modify `fe/src/modules/agent/AgentDebugPage.tsx`
  - Loads `AGENT_DEBUG` history when a sidebar conversation is selected.
- Modify `fe/src/modules/rag/RagChatPage.tsx`
  - Loads `RAG_CHAT` history when a sidebar conversation is selected and keeps source drawer cleanup behavior.

## Task 1: Add Shared Memory History Mapper

**Files:**
- Modify: `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts:1-217`
- Modify: `fe/src/components/chat-workspace/chatWorkspaceMappers.ts:1-98`

- [ ] **Step 1: Update mapper test imports**

In `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`, replace the existing agent type import and mapper import with:

```ts
import type {AgentMemoryMessageResponse, AgentMemorySessionResponse} from '../../modules/agent/agentTypes'
```

```ts
import {
    applyAgentChunkToMessage,
    applyRagEventToMessage,
    mapMemoryMessagesToWorkspaceMessages,
    mapSessionToConversation,
    mapWorkspaceMessageToBubbleItem,
} from './chatWorkspaceMappers'
```

- [ ] **Step 2: Add a memory message fixture helper**

In `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`, after the existing `source` constant, add:

```ts
function memoryMessage(overrides: Partial<AgentMemoryMessageResponse>): AgentMemoryMessageResponse {
    return {
        id: 1,
        sessionId: 'session-1',
        entryType: 'AGENT_CHAT',
        seqNo: 1,
        turnNo: 1,
        role: 'USER',
        messageType: 'MESSAGE',
        content: 'Hello',
        ...overrides,
    }
}
```

- [ ] **Step 3: Add failing mapper tests**

In `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`, inside `describe('chat workspace mappers', () => {` after the existing `maps assistant messages to ai bubble items with workspace extra info` test, add:

```ts
    test('maps persisted memory messages to workspace messages in seq order', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 2,
                seqNo: 2,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Hi there',
                traceId: 'trace-1',
                requestId: 'request-1',
            }),
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'USER',
                content: 'Hello',
            }),
        ])

        expect(messages).toMatchObject([
            {
                id: 'memory-1',
                role: 'user',
                content: 'Hello',
                status: 'success',
            },
            {
                id: 'memory-2',
                role: 'assistant',
                content: 'Hi there',
                status: 'success',
                traceId: 'trace-1',
                requestId: 'request-1',
            },
        ])
    })

    test('merges persisted thinking and RAG sources into the assistant turn', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'USER',
                content: 'Find the source',
            }),
            memoryMessage({
                id: 2,
                seqNo: 2,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'THINK',
                content: 'Searching notes',
            }),
            memoryMessage({
                id: 3,
                seqNo: 3,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Here is the answer',
            }),
            memoryMessage({
                id: 4,
                seqNo: 4,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'RAG_SOURCES',
                sourceRefsJson: JSON.stringify({sources: [source]}),
            }),
        ])

        expect(messages).toHaveLength(2)
        expect(messages[1]).toMatchObject({
            id: 'memory-3',
            role: 'assistant',
            content: 'Here is the answer',
            thinkContent: 'Searching notes',
            sources: [source],
            status: 'success',
        })
    })

    test('ignores invalid persisted RAG source JSON without dropping the assistant message', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Answer without parsed sources',
            }),
            memoryMessage({
                id: 2,
                seqNo: 2,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'RAG_SOURCES',
                sourceRefsJson: '{invalid',
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            id: 'memory-1',
            role: 'assistant',
            content: 'Answer without parsed sources',
            status: 'success',
        })
    })

    test('maps persisted errors onto the assistant turn', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'ERROR',
                content: '模型调用失败',
                traceId: 'trace-error',
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            id: 'memory-1',
            role: 'assistant',
            content: '模型调用失败',
            error: '模型调用失败',
            status: 'error',
            traceId: 'trace-error',
        })
    })

    test('returns no workspace messages for empty persisted memory history', () => {
        expect(mapMemoryMessagesToWorkspaceMessages([])).toEqual([])
    })
```

- [ ] **Step 4: Run mapper tests and confirm they fail**

Run:

```bash
cd fe && bun run test src/components/chat-workspace/chatWorkspaceMappers.test.ts
```

Expected: FAIL because `mapMemoryMessagesToWorkspaceMessages` is not exported from `chatWorkspaceMappers.ts`.

- [ ] **Step 5: Update mapper imports**

In `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`, replace the current type imports with:

```ts
import type {AgentMemoryMessageResponse, AgentMemorySessionResponse} from '../../modules/agent/agentTypes'
import type {RagStreamEvent, SourceReferenceResponse} from '../../modules/rag/ragTypes'
```

- [ ] **Step 6: Implement `mapMemoryMessagesToWorkspaceMessages`**

In `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`, add this function after `mapWorkspaceMessageToBubbleItem` and before `applyAgentChunkToMessage`:

```ts
export function mapMemoryMessagesToWorkspaceMessages(
    messages: AgentMemoryMessageResponse[]
): ChatWorkspaceMessage[] {
    const workspaceMessages: ChatWorkspaceMessage[] = []
    const assistantMessagesByTurnNo = new Map<number, ChatWorkspaceMessage>()
    const sortedMessages = [...messages].sort((left, right) => left.seqNo - right.seqNo)

    for (const memoryMessage of sortedMessages) {
        if (memoryMessage.messageType === 'MESSAGE') {
            if (memoryMessage.role === 'ASSISTANT') {
                const assistantMessage = findOrCreateAssistantMessage(
                    memoryMessage,
                    workspaceMessages,
                    assistantMessagesByTurnNo,
                )
                assistantMessage.id = memoryMessageId(memoryMessage)
                assistantMessage.content = memoryMessage.content ?? ''
                applyMemoryMessageMetadata(assistantMessage, memoryMessage)
                assistantMessage.status = assistantMessage.status === 'error' ? 'error' : 'success'
                continue
            }

            const workspaceMessage: ChatWorkspaceMessage = {
                id: memoryMessageId(memoryMessage),
                role: mapMemoryMessageRole(memoryMessage.role),
                content: memoryMessage.content ?? '',
                status: 'success',
            }
            applyMemoryMessageMetadata(workspaceMessage, memoryMessage)
            workspaceMessages.push(workspaceMessage)
            continue
        }

        if (memoryMessage.messageType === 'THINK') {
            const assistantMessage = findOrCreateAssistantMessage(
                memoryMessage,
                workspaceMessages,
                assistantMessagesByTurnNo,
            )
            assistantMessage.thinkContent = appendHistoryText(
                assistantMessage.thinkContent,
                memoryMessage.content ?? '',
            )
            continue
        }

        if (memoryMessage.messageType === 'RAG_SOURCES') {
            const sources = parseSourceRefs(memoryMessage.sourceRefsJson)
            if (sources && sources.length > 0) {
                const assistantMessage = findOrCreateAssistantMessage(
                    memoryMessage,
                    workspaceMessages,
                    assistantMessagesByTurnNo,
                )
                assistantMessage.sources = sources
            }
            continue
        }

        if (memoryMessage.messageType === 'ERROR') {
            const assistantMessage = findOrCreateAssistantMessage(
                memoryMessage,
                workspaceMessages,
                assistantMessagesByTurnNo,
            )
            const errorMessage = memoryMessage.content?.trim() || 'Request failed.'
            assistantMessage.error = errorMessage
            assistantMessage.status = 'error'
            applyMemoryMessageMetadata(assistantMessage, memoryMessage)
            if (!assistantMessage.content.trim()) {
                assistantMessage.content = errorMessage
            }
        }
    }

    return workspaceMessages
}
```

- [ ] **Step 7: Add mapper helper functions**

In `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`, add these helper functions after `toChatMessage` and before `assertNever`:

```ts
function findOrCreateAssistantMessage(
    memoryMessage: AgentMemoryMessageResponse,
    workspaceMessages: ChatWorkspaceMessage[],
    assistantMessagesByTurnNo: Map<number, ChatWorkspaceMessage>,
): ChatWorkspaceMessage {
    const existingMessage = assistantMessagesByTurnNo.get(memoryMessage.turnNo)
    if (existingMessage) {
        return existingMessage
    }

    const assistantMessage: ChatWorkspaceMessage = {
        id: memoryMessageId(memoryMessage),
        role: 'assistant',
        content: '',
        status: memoryMessage.messageType === 'ERROR' ? 'error' : 'success',
    }
    applyMemoryMessageMetadata(assistantMessage, memoryMessage)
    assistantMessagesByTurnNo.set(memoryMessage.turnNo, assistantMessage)
    workspaceMessages.push(assistantMessage)
    return assistantMessage
}

function mapMemoryMessageRole(role: AgentMemoryMessageResponse['role']): ChatWorkspaceMessage['role'] {
    switch (role) {
        case 'ASSISTANT':
            return 'assistant'
        case 'SYSTEM':
            return 'system'
        case 'USER':
        default:
            return 'user'
    }
}

function memoryMessageId(message: AgentMemoryMessageResponse): string {
    return `memory-${message.id}`
}

function appendHistoryText(currentText: string | undefined, nextText: string): string | undefined {
    if (!nextText.trim()) {
        return currentText
    }

    return currentText?.trim() ? `${currentText}\n${nextText}` : nextText
}

function applyMemoryMessageMetadata(
    message: ChatWorkspaceMessage,
    memoryMessage: AgentMemoryMessageResponse,
) {
    if (memoryMessage.traceId) {
        message.traceId = memoryMessage.traceId
    }
    if (memoryMessage.requestId) {
        message.requestId = memoryMessage.requestId
    }
}

function parseSourceRefs(sourceRefsJson: string | undefined): SourceReferenceResponse[] | undefined {
    if (!sourceRefsJson?.trim()) {
        return undefined
    }

    try {
        const parsed: unknown = JSON.parse(sourceRefsJson)
        if (Array.isArray(parsed)) {
            return parsed as SourceReferenceResponse[]
        }
        if (isRecord(parsed) && Array.isArray(parsed.sources)) {
            return parsed.sources as SourceReferenceResponse[]
        }
    } catch {
        return undefined
    }

    return undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}
```

- [ ] **Step 8: Run mapper tests and confirm they pass**

Run:

```bash
cd fe && bun run test src/components/chat-workspace/chatWorkspaceMappers.test.ts
```

Expected: PASS for all tests in `chatWorkspaceMappers.test.ts`.

- [ ] **Step 9: Commit Task 1**

Run:

```bash
git add fe/src/components/chat-workspace/chatWorkspaceMappers.ts fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts
git commit -m "fix(frontend): map persisted chat history" -- fe/src/components/chat-workspace/chatWorkspaceMappers.ts fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts
```

Expected: commit includes only the mapper and mapper test files.

## Task 2: Load History In Agent Chat Page

**Files:**
- Modify: `fe/src/modules/chat/pages/ChatPage.tsx:1-270`

- [ ] **Step 1: Import history mapper and memory message service**

In `fe/src/modules/chat/pages/ChatPage.tsx`, add `mapMemoryMessagesToWorkspaceMessages` to the chat workspace import block:

```ts
    mapMemoryMessagesToWorkspaceMessages,
    mapSessionToConversation,
```

Add `getAgentMemoryMessages` to the agent service import block:

```ts
    createAgentMemorySession,
    getAgentMemoryMessages,
    getAgentMemorySessions,
```

- [ ] **Step 2: Add a history load guard ref**

In `ChatPage`, after `updateAssistantMessageRef`, add:

```ts
    const historyRequestKeyRef = useRef('')
```

- [ ] **Step 3: Prevent pending history from overwriting a new send**

In `handleSend`, after `setError('')`, add:

```ts
        historyRequestKeyRef.current = ''
```

The resulting block should be:

```ts
        setInput('')
        setError('')
        historyRequestKeyRef.current = ''
        request({
            message: nextMessage,
            conversationKey: sessionId || undefined,
            entryType: 'AGENT_CHAT',
        })
```

- [ ] **Step 4: Clear pending history during new conversation and archive**

In `handleNewConversation`, immediately after `abort()`, add:

```ts
        historyRequestKeyRef.current = ''
```

In `archiveCurrentSession`, after the empty `conversationKey` guard and before `try`, add:

```ts
        historyRequestKeyRef.current = ''
```

- [ ] **Step 5: Replace conversation change with history loading**

Replace the existing `handleConversationChange` function with:

```ts
    function handleConversationChange(conversationKey: string) {
        void loadConversationHistory(conversationKey)
    }

    async function loadConversationHistory(conversationKey: string) {
        abort()
        setSessionId(conversationKey)
        historyRequestKeyRef.current = conversationKey
        const session = sessions.find((item) => item.sessionId === conversationKey)
        if (session) {
            setMemoryEnabled(session.memoryEnabled)
        }
        setMessages(initialMessages)
        setInput('')
        setError('')

        try {
            const historyMessages = mapMemoryMessagesToWorkspaceMessages(
                await getAgentMemoryMessages(conversationKey)
            )
            if (historyRequestKeyRef.current === conversationKey) {
                setMessages(historyMessages.length > 0 ? historyMessages : initialMessages)
            }
        } catch (requestError) {
            if (historyRequestKeyRef.current === conversationKey) {
                setMessages(initialMessages)
            }
            reportGlobalError(requestError)
        }
    }
```

- [ ] **Step 6: Run targeted test and typecheck**

Run:

```bash
cd fe && bun run test src/components/chat-workspace/chatWorkspaceMappers.test.ts
cd fe && bun run typecheck
```

Expected: mapper tests PASS and typecheck PASS.

- [ ] **Step 7: Commit Task 2**

Run:

```bash
git add fe/src/modules/chat/pages/ChatPage.tsx
git commit -m "fix(frontend): restore agent chat history" -- fe/src/modules/chat/pages/ChatPage.tsx
```

Expected: commit includes only `ChatPage.tsx`.

## Task 3: Load History In Agent Debug Page

**Files:**
- Modify: `fe/src/modules/agent/AgentDebugPage.tsx:1-430`

- [ ] **Step 1: Import history mapper and memory message service**

In `fe/src/modules/agent/AgentDebugPage.tsx`, add `mapMemoryMessagesToWorkspaceMessages` to the chat workspace import block:

```ts
    mapMemoryMessagesToWorkspaceMessages,
    mapSessionToConversation,
```

Add `getAgentMemoryMessages` to the agent service import block:

```ts
    deleteAgentPreset,
    getAgentMemoryMessages,
    getAgentMemorySessions,
```

- [ ] **Step 2: Add a history load guard ref**

In `AgentDebugPage`, after `updateAssistantMessageRef`, add:

```ts
    const historyRequestKeyRef = useRef('')
```

- [ ] **Step 3: Prevent pending history from overwriting a new send**

In `handleSend`, after `setError('')`, add:

```ts
        historyRequestKeyRef.current = ''
```

The resulting block should be:

```ts
        setInput('')
        setError('')
        historyRequestKeyRef.current = ''
        request({
            message: nextMessage,
            conversationKey: sessionId || undefined,
            entryType: 'AGENT_DEBUG',
        })
```

- [ ] **Step 4: Clear pending history during new conversation and archive**

In `newConversation`, immediately after `abort()`, add:

```ts
        historyRequestKeyRef.current = ''
```

In `archiveCurrentSession`, immediately after `abort()`, add:

```ts
        historyRequestKeyRef.current = ''
```

- [ ] **Step 5: Replace conversation change with history loading**

Replace the existing `handleConversationChange` function with:

```ts
    function handleConversationChange(conversationKey: string) {
        void loadConversationHistory(conversationKey)
    }

    async function loadConversationHistory(conversationKey: string) {
        abort()
        setSessionId(conversationKey)
        historyRequestKeyRef.current = conversationKey
        const session = sessions.find((item) => item.sessionId === conversationKey)
        if (session) {
            setMemoryEnabled(session.memoryEnabled)
            setLongTermExtractionEnabled(session.longTermExtractionEnabled)
        }
        setMessages(initialMessages)
        setInput('')
        setError('')

        try {
            const historyMessages = mapMemoryMessagesToWorkspaceMessages(
                await getAgentMemoryMessages(conversationKey)
            )
            if (historyRequestKeyRef.current === conversationKey) {
                setMessages(historyMessages.length > 0 ? historyMessages : initialMessages)
            }
        } catch (requestError) {
            if (historyRequestKeyRef.current === conversationKey) {
                setMessages(initialMessages)
            }
            reportGlobalError(requestError)
        }
    }
```

- [ ] **Step 6: Run targeted test and typecheck**

Run:

```bash
cd fe && bun run test src/components/chat-workspace/chatWorkspaceMappers.test.ts
cd fe && bun run typecheck
```

Expected: mapper tests PASS and typecheck PASS.

- [ ] **Step 7: Commit Task 3**

Run:

```bash
git add fe/src/modules/agent/AgentDebugPage.tsx
git commit -m "fix(frontend): restore agent debug history" -- fe/src/modules/agent/AgentDebugPage.tsx
```

Expected: commit includes only `AgentDebugPage.tsx`.

## Task 4: Load History In RAG Chat Page

**Files:**
- Modify: `fe/src/modules/rag/RagChatPage.tsx:1-380`

- [ ] **Step 1: Import history mapper and memory message service**

In `fe/src/modules/rag/RagChatPage.tsx`, add `mapMemoryMessagesToWorkspaceMessages` to the chat workspace import block:

```ts
    mapMemoryMessagesToWorkspaceMessages,
    mapSessionToConversation,
```

Add `getAgentMemoryMessages` to the agent service import block:

```ts
    createAgentMemorySession,
    getAgentMemoryMessages,
    getAgentMemorySessions,
```

- [ ] **Step 2: Add a history load guard ref**

In `RagChatPage`, after `updateAssistantMessageRef`, add:

```ts
    const historyRequestKeyRef = useRef('')
```

- [ ] **Step 3: Prevent pending history from overwriting a new send**

In `handleSend`, after `setError('')`, add:

```ts
        historyRequestKeyRef.current = ''
```

The resulting block should be:

```ts
        setInput('')
        setError('')
        historyRequestKeyRef.current = ''
        request({
            message: nextMessage,
            conversationKey: sessionId || undefined,
            entryType: 'RAG_CHAT',
        })
```

- [ ] **Step 4: Clear pending history during new conversation and archive**

In `handleNewConversation`, immediately after `abort()`, add:

```ts
        historyRequestKeyRef.current = ''
```

In `archiveCurrentSession`, immediately after `abort()`, add:

```ts
        historyRequestKeyRef.current = ''
```

- [ ] **Step 5: Replace conversation change with history loading**

Replace the existing `handleConversationChange` function with:

```ts
    function handleConversationChange(conversationKey: string) {
        void loadConversationHistory(conversationKey)
    }

    async function loadConversationHistory(conversationKey: string) {
        abort()
        setSessionId(conversationKey)
        historyRequestKeyRef.current = conversationKey
        const session = sessions.find((item) => item.sessionId === conversationKey)
        if (session) {
            setMemoryEnabled(session.memoryEnabled)
            setLongTermExtractionEnabled(session.longTermExtractionEnabled)
        }
        setMessages(initialMessages)
        setInput('')
        setError('')
        closeSourceDrawer()

        try {
            const historyMessages = mapMemoryMessagesToWorkspaceMessages(
                await getAgentMemoryMessages(conversationKey)
            )
            if (historyRequestKeyRef.current === conversationKey) {
                setMessages(historyMessages.length > 0 ? historyMessages : initialMessages)
            }
        } catch (requestError) {
            if (historyRequestKeyRef.current === conversationKey) {
                setMessages(initialMessages)
            }
            reportGlobalError(requestError)
        }
    }
```

- [ ] **Step 6: Run targeted test and typecheck**

Run:

```bash
cd fe && bun run test src/components/chat-workspace/chatWorkspaceMappers.test.ts
cd fe && bun run typecheck
```

Expected: mapper tests PASS and typecheck PASS.

- [ ] **Step 7: Commit Task 4**

Run:

```bash
git add fe/src/modules/rag/RagChatPage.tsx
git commit -m "fix(frontend): restore rag chat history" -- fe/src/modules/rag/RagChatPage.tsx
```

Expected: commit includes only `RagChatPage.tsx`.

## Task 5: Final Verification

**Files:**
- Verify: `fe/src/components/chat-workspace/chatWorkspaceMappers.ts`
- Verify: `fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts`
- Verify: `fe/src/modules/chat/pages/ChatPage.tsx`
- Verify: `fe/src/modules/agent/AgentDebugPage.tsx`
- Verify: `fe/src/modules/rag/RagChatPage.tsx`

- [ ] **Step 1: Run frontend mapper tests**

Run:

```bash
cd fe && bun run test src/components/chat-workspace/chatWorkspaceMappers.test.ts
```

Expected: PASS.

- [ ] **Step 2: Run frontend service regression tests**

Run:

```bash
cd fe && bun run test src/modules/agent/agentService.test.ts
```

Expected: PASS. This confirms `getAgentMemoryMessages(sessionId)` still targets `/api/agent/memory/sessions/{sessionId}/messages`.

- [ ] **Step 3: Run typecheck**

Run:

```bash
cd fe && bun run typecheck
```

Expected: PASS.

- [ ] **Step 4: Run lint**

Run:

```bash
cd fe && bun run lint
```

Expected: PASS, especially for `@typescript-eslint/no-floating-promises` and `@typescript-eslint/no-misused-promises`.

- [ ] **Step 5: Inspect committed file set**

Run:

```bash
git status --short
git log --oneline -5
```

Expected: the implementation commits touch only:

```text
fe/src/components/chat-workspace/chatWorkspaceMappers.ts
fe/src/components/chat-workspace/chatWorkspaceMappers.test.ts
fe/src/modules/chat/pages/ChatPage.tsx
fe/src/modules/agent/AgentDebugPage.tsx
fe/src/modules/rag/RagChatPage.tsx
```

Existing Clocktower changes may still appear in `git status --short`; leave them untouched.

## Self-Review Notes

- Spec coverage:
  - Shared mapper: Task 1.
  - Agent Chat history load: Task 2.
  - Agent Debug history load: Task 3.
  - RAG Chat history load and source drawer cleanup: Task 4.
  - Empty history fallback, failed load fallback, stale request guard, and validation: Tasks 2-5.
- Placeholder scan: no placeholder steps remain.
- Type consistency:
  - Function name is `mapMemoryMessagesToWorkspaceMessages` in tests, implementation, and page imports.
  - Existing service name remains `getAgentMemoryMessages`.
  - Existing message type remains `ChatWorkspaceMessage`.
