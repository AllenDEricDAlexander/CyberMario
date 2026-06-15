import type {PageResult} from '../../types/api'
import type {ChatResponse} from '../chat/chatTypes'

export type AgentConversationStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
export type AgentConversationRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type AgentConversationMessageType = 'MESSAGE' | 'THINK' | 'ERROR'
export type AgentModelProviderType = 'DASHSCOPE' | 'DEEPSEEK'

export type AgentModelConfig = {
    provider?: AgentModelProviderType
    model?: string
}

export type AgentModelOptions = {
    temperature?: number
    maxTokens?: number
    topP?: number
    topK?: number
    enableThinking?: boolean
    thinkingBudget?: number
    enableSearch?: boolean
    multiModel?: boolean
    providerOptions?: Record<string, unknown>
}

export type AgentToolConfig = {
    enabledToolNames?: string[]
}

export type AgentOptions = {
    parallelToolExecution?: boolean
    maxParallelTools?: number
    toolExecutionTimeoutSeconds?: number
}

export type AgentPresetConfig = {
    modelConfig?: AgentModelConfig
    modelOptions?: AgentModelOptions
    systemPrompt?: string
    toolConfig?: AgentToolConfig
    agentOptions?: AgentOptions
}

export type AgentPresetRequest = {
    name: string
    description?: string
    config?: AgentPresetConfig
    enabled?: boolean
}

export type AgentPresetResponse = {
    id: number
    name: string
    description?: string
    config?: AgentPresetConfig
    enabled: boolean
    createdBy?: number
    updatedBy?: number
    createdAt?: string
    updatedAt?: string
}

export type AgentDebugChatRequest = {
    message: string
    threadId?: string
    presetId?: number
    overrides?: AgentPresetConfig
}

export type AgentConversationAuditResponse = {
    id: number
    requestId?: string
    traceId?: string
    userId?: number
    username?: string
    threadId: string
    presetId?: number
    runtimeFingerprint?: string
    effectiveConfigJson?: string
    status: AgentConversationStatus
    startedAt: string
    finishedAt?: string
    durationMs?: number
    errorCode?: string
    errorMessage?: string
    ip?: string
    userAgent?: string
    createdAt?: string
}

export type AgentConversationMessageAuditResponse = {
    id: number
    conversationAuditId: number
    seqNo: number
    role: AgentConversationRole
    messageType: AgentConversationMessageType
    content?: string
    contentChars?: number
    createdAt?: string
}

export type AgentPage<T> = PageResult<T>
export type AgentStreamChunk = ChatResponse
