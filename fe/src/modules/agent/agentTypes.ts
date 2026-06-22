import type {PageResult} from '../../types/api'
import type {ChatResponse} from '../chat/chatTypes'

export type AgentConversationStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
export type AgentConversationRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type AgentConversationMessageType = 'MESSAGE' | 'THINK' | 'ERROR'
export type AgentRunAuditStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
export type AgentRunEventStatus = 'STARTED' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
export type AgentMemoryEntryType = 'AGENT_CHAT' | 'AGENT_DEBUG' | 'RAG_CHAT' | 'BUTLER_AGENT'
export type AgentMemorySessionStatus = 'ACTIVE' | 'RELEASED' | 'ARCHIVED' | 'DELETED'
export type AgentMemoryMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type AgentMemoryMessageType = 'MESSAGE' | 'THINK' | 'ERROR' | 'RAG_SOURCES'
export type AgentMemoryMessageStatus = 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export type AgentMemoryExtractionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'
export type AgentRunEventType =
    | 'RUN_STARTED'
    | 'USER_MESSAGE'
    | 'MODEL_REQUEST'
    | 'MODEL_RESPONSE'
    | 'TOOL_REQUEST'
    | 'TOOL_RESPONSE'
    | 'ASSISTANT_THINK'
    | 'ASSISTANT_MESSAGE'
    | 'RUN_COMPLETED'
    | 'RUN_FAILED'
    | 'RUN_CANCELLED'
export type AgentRunToolType = 'LOCAL' | 'MCP' | 'UNKNOWN'
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
    sessionId?: string
    memoryEnabled?: boolean
    longTermExtractionEnabled?: boolean
    presetId?: number
    overrides?: AgentPresetConfig
}

export type AgentMemorySessionResponse = {
    sessionId: string
    entryType: AgentMemoryEntryType
    title?: string
    status: AgentMemorySessionStatus
    memoryEnabled: boolean
    longTermExtractionEnabled: boolean
    shortTermWindowTurns: number
    lastActiveAt?: string
    releasedAt?: string
    archivedAt?: string
    createdAt?: string
    updatedAt?: string
}

export type AgentMemorySessionRequest = {
    entryType: AgentMemoryEntryType
    title?: string
    memoryEnabled?: boolean
    longTermExtractionEnabled?: boolean
}

export type AgentMemoryMessageResponse = {
    id: number
    sessionId: string
    entryType: AgentMemoryEntryType
    seqNo: number
    turnNo: number
    role: AgentMemoryMessageRole
    messageType: AgentMemoryMessageType
    content?: string
    contentChars?: number
    sourceRefsJson?: string
    traceId?: string
    requestId?: string
    messageStatus?: AgentMemoryMessageStatus
    errorCode?: string
    errorMessage?: string
    metadataJson?: string
    createdAt?: string
}

export type AgentLongTermMemoryResponse = {
    scopeType?: string
    contentMarkdown: string
    contentChars: number
    activeVersionId?: number
    status: string
    createdAt?: string
    updatedAt?: string
}

export type AgentLongTermMemoryVersionResponse = {
    id: number
    memoryId: number
    versionNo: number
    contentMarkdown: string
    contentChars: number
    changeSummary?: string
    sourceSessionIds?: string
    sourceMessageIds?: string
    requestId?: string
    traceId?: string
    createdAt?: string
}

export type AgentMemoryExtractionAuditResponse = {
    id: number
    sessionId?: string
    entryType?: AgentMemoryEntryType
    sourceMessageIds?: string
    status: AgentMemoryExtractionStatus
    extractedMarkdown?: string
    mergedVersionId?: number
    errorCode?: string
    errorMessage?: string
    requestId?: string
    traceId?: string
    startedAt?: string
    finishedAt?: string
    createdAt?: string
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

export type AgentRunAuditResponse = {
    id: number
    requestId?: string
    traceId?: string
    threadId: string
    userId?: number
    username?: string
    presetId?: number
    runtimeFingerprint?: string
    effectiveConfigJson?: string
    userMessage?: string
    finalMessage?: string
    finalThinking?: string
    status: AgentRunAuditStatus
    modelCallCount?: number
    toolCallCount?: number
    mcpToolCallCount?: number
    startedAt: string
    finishedAt?: string
    durationMs?: number
    errorCode?: string
    errorMessage?: string
    createdAt?: string
}

export type AgentRunEventAuditResponse = {
    id: number
    runId: number
    requestId?: string
    traceId?: string
    threadId?: string
    seqNo: number
    eventType: AgentRunEventType
    reactRound?: number
    toolCallId?: string
    toolName?: string
    toolType?: AgentRunToolType
    mcpServerCode?: string
    status: AgentRunEventStatus
    startedAt: string
    finishedAt?: string
    durationMs?: number
    modelProvider?: AgentModelProviderType
    modelName?: string
    promptText?: string
    requestMessagesJson?: string
    requestOptionsJson?: string
    availableToolsJson?: string
    responseText?: string
    toolArguments?: string
    toolResult?: string
    metadataJson?: string
    errorCode?: string
    errorMessage?: string
    createdAt?: string
}

export type AgentPage<T> = PageResult<T>
export type AgentStreamChunk = ChatResponse
