import {describe, expect, test} from 'vitest'
import type {ReactNode} from 'react'
import type {ChatResponse} from '../../modules/chat/chatTypes'
import type {AgentMemoryMessageResponse, AgentMemorySessionResponse} from '../../modules/agent/agentTypes'
import type {RagStreamEvent, SourceReferenceResponse} from '../../modules/rag/ragTypes'
import {
    applyAgentChunkToMessage,
    applyRagEventToMessage,
    mapMemoryMessagesToWorkspaceMessages,
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

    test('restores same-turn user question on persisted assistant messages', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'USER',
                content: 'What did the scheduler decide?',
            }),
            memoryMessage({
                id: 2,
                seqNo: 2,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'THINK',
                content: 'Checking prior runs',
            }),
            memoryMessage({
                id: 3,
                seqNo: 3,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'It selected the retry window.',
                traceId: 'trace-question',
                sourceRefsJson: JSON.stringify({sources: [source]}),
            }),
            memoryMessage({
                id: 4,
                seqNo: 4,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'ERROR',
                content: '',
                requestId: 'request-question',
            }),
        ])

        expect(messages).toHaveLength(2)
        expect(messages[1]).toMatchObject({
            id: 'memory-3',
            role: 'assistant',
            content: 'It selected the retry window.',
            question: 'What did the scheduler decide?',
            thinkContent: 'Checking prior runs',
            sources: [source],
            traceId: 'trace-question',
            requestId: 'request-question',
            status: 'error',
        })
    })

    test('skips blank persisted system messages', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                role: 'SYSTEM',
                content: '   ',
            }),
        ])

        expect(messages).toEqual([])
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

    test('preserves metadata from persisted assistant turn side rows', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Answer with later metadata',
            }),
            memoryMessage({
                id: 2,
                seqNo: 2,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'THINK',
                content: 'Thinking with trace',
                traceId: 'trace-merged',
                requestId: 'request-merged',
            }),
            memoryMessage({
                id: 3,
                seqNo: 3,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'RAG_SOURCES',
                sourceRefsJson: JSON.stringify({sources: [source]}),
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            id: 'memory-1',
            role: 'assistant',
            content: 'Answer with later metadata',
            thinkContent: 'Thinking with trace',
            sources: [source],
            traceId: 'trace-merged',
            requestId: 'request-merged',
            status: 'success',
        })
    })

    test('restores persisted RAG sources from assistant message rows', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Answer with inline sources',
                sourceRefsJson: JSON.stringify({sources: [source]}),
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            id: 'memory-1',
            role: 'assistant',
            content: 'Answer with inline sources',
            sources: [source],
            status: 'success',
        })
    })

    test('normalizes compact persisted RAG source refs from assistant message rows', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Answer with compact source refs',
                sourceRefsJson: JSON.stringify([
                    {
                        sourceId: 'source-compact',
                        knowledgeBaseId: 9,
                        documentId: 10,
                        chunkId: 11,
                    },
                ]),
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            id: 'memory-1',
            role: 'assistant',
            content: 'Answer with compact source refs',
            sources: [
                {
                    sourceId: 'source-compact',
                    knowledgeBaseId: 9,
                    knowledgeBaseName: 'Knowledge Base 9',
                    documentId: 10,
                    documentName: 'Document 10',
                    chunkId: 11,
                    chunkIndex: 0,
                    score: 0,
                    content: '',
                    metadata: {},
                },
            ],
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

    test('filters invalid persisted RAG source items without dropping the assistant message', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Answer without valid source items',
            }),
            memoryMessage({
                id: 2,
                seqNo: 2,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'RAG_SOURCES',
                sourceRefsJson: JSON.stringify({
                    sources: [
                        {
                            sourceId: 'source-1',
                            score: '0.88',
                        },
                    ],
                }),
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            id: 'memory-1',
            role: 'assistant',
            content: 'Answer without valid source items',
            status: 'success',
        })
        expect(messages[0].sources).toBeUndefined()
    })

    test('preserves RAG metadata when persisted sources are filtered out', () => {
        const messages = mapMemoryMessagesToWorkspaceMessages([
            memoryMessage({
                id: 1,
                seqNo: 1,
                turnNo: 1,
                role: 'ASSISTANT',
                content: 'Answer with metadata only RAG row',
            }),
            memoryMessage({
                id: 2,
                seqNo: 2,
                turnNo: 1,
                role: 'ASSISTANT',
                messageType: 'RAG_SOURCES',
                sourceRefsJson: JSON.stringify({
                    sources: [
                        {
                            sourceId: 'source-invalid',
                            score: '0.42',
                        },
                    ],
                }),
                traceId: 'trace-rag-meta',
                requestId: 'request-rag-meta',
            }),
        ])

        expect(messages).toHaveLength(1)
        expect(messages[0]).toMatchObject({
            id: 'memory-1',
            role: 'assistant',
            content: 'Answer with metadata only RAG row',
            traceId: 'trace-rag-meta',
            requestId: 'request-rag-meta',
            status: 'success',
        })
        expect(messages[0].sources).toBeUndefined()
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
