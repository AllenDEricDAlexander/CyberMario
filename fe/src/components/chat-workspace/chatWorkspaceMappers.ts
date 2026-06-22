import {appendChatChunk} from '../../modules/chat/chatMessageStream'
import type {ChatMessage, ChatResponse} from '../../modules/chat/chatTypes'
import type {AgentMemoryMessageResponse, AgentMemorySessionResponse} from '../../modules/agent/agentTypes'
import type {ChatWorkspaceBubbleItem, ChatWorkspaceConversation, ChatWorkspaceMessage} from './chatWorkspaceTypes'
import type {RagStreamEvent, SourceReferenceResponse} from '../../modules/rag/ragTypes'

export function mapSessionToConversation(session: AgentMemorySessionResponse): ChatWorkspaceConversation {
    return {
        key: session.sessionId,
        label: session.title?.trim() ? session.title : session.sessionId.slice(0, 8),
        description: session.entryType,
        group: session.status,
        updatedAt: session.updatedAt ?? session.lastActiveAt,
        session,
    }
}

export function mapWorkspaceMessageToBubbleItem(message: ChatWorkspaceMessage): ChatWorkspaceBubbleItem {
    return {
        key: message.id,
        role: message.role === 'assistant' ? 'ai' : message.role,
        content: message.content,
        status: message.status,
        extraInfo: {
            workspaceMessage: message,
        },
    }
}

export function mapMemoryMessagesToWorkspaceMessages(
    messages: AgentMemoryMessageResponse[]
): ChatWorkspaceMessage[] {
    const workspaceMessages: ChatWorkspaceMessage[] = []
    const assistantMessagesByTurnNo = new Map<number, ChatWorkspaceMessage>()
    const userQuestionsByTurnNo = new Map<number, string>()
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
                applySameTurnQuestion(assistantMessage, userQuestionsByTurnNo.get(memoryMessage.turnNo))
                applyMemoryMessageMetadata(assistantMessage, memoryMessage)
                applyMemoryMessageSources(assistantMessage, memoryMessage)
                assistantMessage.status = assistantMessage.status === 'error' ? 'error' : 'success'
                continue
            }

            if (memoryMessage.role === 'SYSTEM' && !memoryMessage.content?.trim()) {
                continue
            }

            if (memoryMessage.role === 'USER' && memoryMessage.content?.trim()) {
                userQuestionsByTurnNo.set(memoryMessage.turnNo, memoryMessage.content)
                const assistantMessage = assistantMessagesByTurnNo.get(memoryMessage.turnNo)
                if (assistantMessage) {
                    applySameTurnQuestion(assistantMessage, memoryMessage.content)
                }
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
            applySameTurnQuestion(assistantMessage, userQuestionsByTurnNo.get(memoryMessage.turnNo))
            assistantMessage.thinkContent = appendHistoryText(
                assistantMessage.thinkContent,
                memoryMessage.content ?? '',
            )
            applyMemoryMessageMetadata(assistantMessage, memoryMessage)
            continue
        }

        if (memoryMessage.messageType === 'RAG_SOURCES') {
            const sources = parseSourceRefs(memoryMessage.sourceRefsJson)
            const existingAssistantMessage = assistantMessagesByTurnNo.get(memoryMessage.turnNo)
            if (sources && sources.length > 0) {
                const assistantMessage = existingAssistantMessage ?? findOrCreateAssistantMessage(
                    memoryMessage,
                    workspaceMessages,
                    assistantMessagesByTurnNo,
                )
                applySameTurnQuestion(assistantMessage, userQuestionsByTurnNo.get(memoryMessage.turnNo))
                assistantMessage.sources = sources
                applyMemoryMessageMetadata(assistantMessage, memoryMessage)
            } else if (existingAssistantMessage) {
                applySameTurnQuestion(existingAssistantMessage, userQuestionsByTurnNo.get(memoryMessage.turnNo))
                applyMemoryMessageMetadata(existingAssistantMessage, memoryMessage)
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
            applySameTurnQuestion(assistantMessage, userQuestionsByTurnNo.get(memoryMessage.turnNo))
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

export function applyAgentChunkToMessage(message: ChatWorkspaceMessage, chunk: ChatResponse): ChatWorkspaceMessage {
    const nextChatMessage = appendChatChunk(toChatMessage(message), chunk)
    const nextMessage: ChatWorkspaceMessage = {
        ...message,
        content: nextChatMessage.content,
        thinkContent: nextChatMessage.thinkContent,
        status: chunk.type === 'error' ? 'error' : 'updating',
    }

    if (chunk.type === 'error') {
        return {
            ...nextMessage,
            error: nextChatMessage.content,
        }
    }

    return nextMessage
}

export function applyRagEventToMessage(message: ChatWorkspaceMessage, event: RagStreamEvent): ChatWorkspaceMessage {
    switch (event.type) {
        case 'metadata':
            return {
                ...message,
                messageId: event.data.messageId,
                traceId: event.data.traceId,
            }
        case 'retrieval':
            return {
                ...message,
                sources: event.data.sources,
                status: 'updating',
            }
        case 'delta':
            return {
                ...message,
                content: `${message.content}${event.data.content}`,
                status: 'updating',
            }
        case 'error':
            return {
                ...message,
                content: event.data.message,
                error: event.data.message,
                status: 'error',
                traceId: event.data.traceId ?? message.traceId,
            }
        case 'done':
            return {
                ...message,
                status: 'success',
            }
        default:
            return assertNever(event)
    }
}

function toChatMessage(message: ChatWorkspaceMessage): ChatMessage {
    return {
        id: message.id,
        role: message.role === 'assistant' ? 'assistant' : 'user',
        content: message.content,
        thinkContent: message.thinkContent,
    }
}

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

function applySameTurnQuestion(message: ChatWorkspaceMessage, question: string | undefined) {
    if (question?.trim()) {
        message.question = question
    }
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

function applyMemoryMessageSources(
    message: ChatWorkspaceMessage,
    memoryMessage: AgentMemoryMessageResponse,
) {
    const sources = parseSourceRefs(memoryMessage.sourceRefsJson)
    if (sources && sources.length > 0) {
        message.sources = sources
    }
}

function parseSourceRefs(sourceRefsJson: string | undefined): SourceReferenceResponse[] | undefined {
    if (!sourceRefsJson?.trim()) {
        return undefined
    }

    try {
        const parsed: unknown = JSON.parse(sourceRefsJson)
        const sourceRefs = Array.isArray(parsed)
            ? parsed
            : isRecord(parsed) && Array.isArray(parsed.sources)
                ? parsed.sources
                : undefined

        if (!sourceRefs) {
            return undefined
        }

        const validSources = sourceRefs.flatMap((sourceRef) => {
            const source = normalizeSourceReference(sourceRef)
            return source ? [source] : []
        })
        return validSources.length > 0 ? validSources : undefined
    } catch {
        return undefined
    }
}

function normalizeSourceReference(value: unknown): SourceReferenceResponse | undefined {
    if (isFullSourceReference(value)) {
        return value
    }

    if (!isCompactSourceReference(value)) {
        return undefined
    }

    return {
        sourceId: value.sourceId,
        knowledgeBaseId: value.knowledgeBaseId,
        knowledgeBaseName: `Knowledge Base ${value.knowledgeBaseId}`,
        documentId: value.documentId,
        documentName: `Document ${value.documentId}`,
        chunkId: value.chunkId,
        chunkIndex: 0,
        score: 0,
        content: '',
        metadata: {},
    }
}

function isFullSourceReference(value: unknown): value is SourceReferenceResponse {
    if (!isRecord(value)) {
        return false
    }

    return (
        typeof value.sourceId === 'string' &&
        typeof value.knowledgeBaseId === 'number' &&
        typeof value.knowledgeBaseName === 'string' &&
        typeof value.documentId === 'number' &&
        typeof value.documentName === 'string' &&
        typeof value.chunkId === 'number' &&
        typeof value.chunkIndex === 'number' &&
        typeof value.score === 'number' &&
        typeof value.content === 'string' &&
        isRecord(value.metadata)
    )
}

function isCompactSourceReference(value: unknown): value is Pick<
    SourceReferenceResponse,
    'sourceId' | 'knowledgeBaseId' | 'documentId' | 'chunkId'
> {
    if (!isRecord(value)) {
        return false
    }

    return (
        typeof value.sourceId === 'string' &&
        typeof value.knowledgeBaseId === 'number' &&
        typeof value.documentId === 'number' &&
        typeof value.chunkId === 'number'
    )
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function assertNever(value: never): never {
    throw new Error(`Unhandled RAG stream event: ${JSON.stringify(value)}`)
}
