import {describe, expect, test} from 'vitest'
import type {ReactNode} from 'react'
import type {ChatResponse} from '../../modules/chat/chatTypes'
import type {AgentMemorySessionResponse} from '../../modules/agent/agentTypes'
import type {RagStreamEvent, SourceReferenceResponse} from '../../modules/rag/ragTypes'
import {
    applyAgentChunkToMessage,
    applyRagEventToMessage,
    mapSessionToConversation,
    mapWorkspaceMessageToBubbleItem,
} from './chatWorkspaceMappers'
import type {
    ChatWorkspaceConversation,
    ChatWorkspaceMessage,
    ChatWorkspaceRequest,
    ChatWorkspaceStreamEvent,
} from './chatWorkspaceTypes'

type IsExact<Type, Expected> =
    (<Value>() => Value extends Type ? 1 : 2) extends
    (<Value>() => Value extends Expected ? 1 : 2)
        ? (<Value>() => Value extends Expected ? 1 : 2) extends
        (<Value>() => Value extends Type ? 1 : 2)
            ? true
            : false
        : false
type IsOptional<Type, Key extends keyof Type> = Record<string, never> extends Pick<Type, Key> ? true : false
type IsRequired<Type, Key extends keyof Type> = IsOptional<Type, Key> extends true ? false : true
type IsNever<Type> = [Type] extends [never] ? true : false

const chatWorkspaceTypeContract: {
    labelIsReactNode: IsExact<ChatWorkspaceConversation['label'], ReactNode>
    descriptionIsOptional: IsOptional<ChatWorkspaceConversation, 'description'>
    groupIsOptional: IsOptional<ChatWorkspaceConversation, 'group'>
    entryTypeIsRequired: IsRequired<ChatWorkspaceRequest, 'entryType'>
    streamEventRejectsRawAgentChunk: IsNever<Extract<ChatWorkspaceStreamEvent, ChatResponse>>
    streamEventRejectsRawRagEvent: IsNever<Extract<ChatWorkspaceStreamEvent, RagStreamEvent>>
    streamEventAgentShape: IsExact<
        Extract<ChatWorkspaceStreamEvent, {kind: 'agent'}>,
        {kind: 'agent'; chunk: ChatResponse}
    >
    streamEventRagShape: IsExact<
        Extract<ChatWorkspaceStreamEvent, {kind: 'rag'}>,
        {kind: 'rag'; event: RagStreamEvent}
    >
} = {
    labelIsReactNode: true,
    descriptionIsOptional: true,
    groupIsOptional: true,
    entryTypeIsRequired: true,
    streamEventRejectsRawAgentChunk: true,
    streamEventRejectsRawRagEvent: true,
    streamEventAgentShape: true,
    streamEventRagShape: true,
}

const memorySession: AgentMemorySessionResponse = {
    sessionId: 'session-abcdef123456',
    entryType: 'AGENT_CHAT',
    title: 'Agent research',
    status: 'ACTIVE',
    memoryEnabled: true,
    longTermExtractionEnabled: false,
    shortTermWindowTurns: 8,
    lastActiveAt: '2026-06-17T10:00:00Z',
    updatedAt: '2026-06-18T08:00:00Z',
}

const workspaceMessage: ChatWorkspaceMessage = {
    id: 'message-1',
    role: 'assistant',
    content: 'hello',
    status: 'success',
}

const source: SourceReferenceResponse = {
    sourceId: 'source-1',
    knowledgeBaseId: 1,
    knowledgeBaseName: 'Knowledge',
    documentId: 2,
    documentName: 'Guide.md',
    chunkId: 3,
    chunkIndex: 4,
    score: 0.88,
    content: 'Relevant content',
    metadata: {heading: 'Intro'},
}

describe('chat workspace mappers', () => {
    test('exposes the shared chat workspace UI type contract', () => {
        expect(chatWorkspaceTypeContract).toEqual({
            labelIsReactNode: true,
            descriptionIsOptional: true,
            groupIsOptional: true,
            entryTypeIsRequired: true,
            streamEventRejectsRawAgentChunk: true,
            streamEventRejectsRawRagEvent: true,
            streamEventAgentShape: true,
            streamEventRagShape: true,
        })
    })

    test('maps memory session to conversation entry', () => {
        expect(mapSessionToConversation(memorySession)).toEqual({
            key: 'session-abcdef123456',
            label: 'Agent research',
            description: 'AGENT_CHAT',
            group: 'ACTIVE',
            updatedAt: '2026-06-18T08:00:00Z',
            session: memorySession,
        })
    })

    test('falls back to the first eight session id characters for blank titles', () => {
        const conversation = mapSessionToConversation({
            ...memorySession,
            sessionId: '1234567890abcdef',
            title: '',
            updatedAt: undefined,
        })

        expect(conversation.label).toBe('12345678')
        expect(conversation.updatedAt).toBe('2026-06-17T10:00:00Z')
    })

    test('maps assistant messages to ai bubble items with workspace extra info', () => {
        expect(mapWorkspaceMessageToBubbleItem(workspaceMessage)).toMatchObject({
            key: 'message-1',
            role: 'ai',
            content: 'hello',
            status: 'success',
            extraInfo: {
                workspaceMessage,
            },
        })
    })

    test('keeps agent thinking chunks separate from message content', () => {
        const chunk: ChatResponse = {
            threadId: 'thread-1',
            type: 'think',
            message: '分析问题',
        }

        expect(applyAgentChunkToMessage(workspaceMessage, chunk)).toMatchObject({
            content: 'hello',
            thinkContent: '分析问题',
            status: 'updating',
        })
    })

    test('applies RAG retrieval then delta events to sources and streamed content', () => {
        const retrieved = applyRagEventToMessage(workspaceMessage, {
            type: 'retrieval',
            data: {
                sources: [source],
                topK: 1,
            },
        })
        const updated = applyRagEventToMessage(retrieved, {
            type: 'delta',
            data: {
                content: ' world',
            },
        })

        expect(updated.sources).toEqual([source])
        expect(updated.content).toBe('hello world')
        expect(updated.status).toBe('updating')
    })

    test('marks RAG messages successful when done event arrives', () => {
        const done: RagStreamEvent = {
            type: 'done',
            data: {
                finishReason: 'stop',
            },
        }

        expect(applyRagEventToMessage({...workspaceMessage, status: 'updating'}, done).status).toBe('success')
    })

    test('keeps prior content and status when RAG metadata sets message ids', () => {
        const metadata: RagStreamEvent = {
            type: 'metadata',
            data: {
                messageId: 'rag-message-1',
                traceId: 'trace-1',
                sessionId: 'session-1',
            },
        }

        expect(applyRagEventToMessage(workspaceMessage, metadata)).toMatchObject({
            content: 'hello',
            status: 'success',
            messageId: 'rag-message-1',
            traceId: 'trace-1',
        })
    })

    test('maps RAG error events to failed messages with trace fallback', () => {
        const failed = applyRagEventToMessage({...workspaceMessage, traceId: 'trace-existing'}, {
            type: 'error',
            data: {
                code: 'RAG_FAILED',
                message: '检索失败',
            },
        })

        expect(failed).toMatchObject({
            content: '检索失败',
            error: '检索失败',
            status: 'error',
            traceId: 'trace-existing',
        })
    })
})
