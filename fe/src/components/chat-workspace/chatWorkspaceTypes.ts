import type {BubbleItemType} from '@ant-design/x'
import type {MessageInfo} from '@ant-design/x-sdk'
import type {ReactNode} from 'react'
import type {AgentMemoryEntryType, AgentMemorySessionResponse} from '../../modules/agent/agentTypes'
import type {ChatResponse} from '../../modules/chat/chatTypes'
import type {RagStreamEvent, SourceReferenceResponse} from '../../modules/rag/ragTypes'

export type ChatWorkspaceRole = 'assistant' | 'user' | 'system'

export type ChatWorkspaceStatus = MessageInfo<Record<string, unknown>>['status']

export type ChatWorkspaceConversation = {
    key: string
    label: ReactNode
    description?: ReactNode
    group?: string
    updatedAt?: string
    session?: AgentMemorySessionResponse
}

export type ChatWorkspaceMessage = {
    id: string
    role: ChatWorkspaceRole
    content: string
    status: ChatWorkspaceStatus
    thinkContent?: string
    sources?: SourceReferenceResponse[]
    traceId?: string
    messageId?: string
    question?: string
    error?: string
    [key: string]: unknown
}

export type ChatWorkspaceRequest = {
    message: string
    conversationKey?: string
    entryType: AgentMemoryEntryType
    extra?: Record<string, unknown>
}

export type ChatWorkspaceMessageInfo = MessageInfo<ChatWorkspaceMessage>

export type ChatWorkspaceAgentStreamChunk = ChatResponse

export type ChatWorkspaceAgentStreamEvent = {
    kind: 'agent'
    chunk: ChatWorkspaceAgentStreamChunk
}

export type ChatWorkspaceRagStreamEvent = {
    kind: 'rag'
    event: RagStreamEvent
}

export type ChatWorkspaceStreamEvent = ChatWorkspaceAgentStreamEvent | ChatWorkspaceRagStreamEvent

export type ChatWorkspaceBubbleExtraInfo = {
    workspaceMessage: ChatWorkspaceMessage
    [key: string]: unknown
}

export type ChatWorkspaceBubbleItem = BubbleItemType & {
    extraInfo: ChatWorkspaceBubbleExtraInfo
}
