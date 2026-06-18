import {appendChatChunk} from '../../modules/chat/chatMessageStream'
import type {ChatMessage, ChatResponse} from '../../modules/chat/chatTypes'
import type {AgentMemorySessionResponse} from '../../modules/agent/agentTypes'
import type {ChatWorkspaceBubbleItem, ChatWorkspaceConversation, ChatWorkspaceMessage} from './chatWorkspaceTypes'
import type {RagStreamEvent} from '../../modules/rag/ragTypes'

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

function assertNever(value: never): never {
    throw new Error(`Unhandled RAG stream event: ${JSON.stringify(value)}`)
}
