import {requestJson, streamJsonLines} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {
    AgentConversationAuditResponse,
    AgentConversationMessageAuditResponse,
    AgentConversationStatus,
    AgentDebugChatRequest,
    AgentLongTermMemoryResponse,
    AgentLongTermMemoryVersionResponse,
    AgentMemoryEntryType,
    AgentMemoryExtractionAuditResponse,
    AgentMemoryMessageResponse,
    AgentMemorySessionRequest,
    AgentMemorySessionResponse,
    AgentMemorySessionStatus,
    AgentPage,
    AgentPresetRequest,
    AgentPresetResponse,
    AgentRunAuditResponse,
    AgentRunAuditStatus,
    AgentRunEventAuditResponse,
    AgentStreamChunk,
} from './agentTypes'

type PageParams = {
    page?: number
    size?: number
}

export function getAgentPresets(params: PageParams) {
    return requestJson<AgentPage<AgentPresetResponse>>(`/api/agent/presets?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
    })}`)
}

export function createAgentPreset(request: AgentPresetRequest) {
    return requestJson<AgentPresetResponse>('/api/agent/presets', {
        method: 'POST',
        body: request,
    })
}

export function updateAgentPreset(id: number, request: AgentPresetRequest) {
    return requestJson<AgentPresetResponse>(`/api/agent/presets/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function updateAgentPresetStatus(id: number, enabled: boolean) {
    return requestJson<AgentPresetResponse>(`/api/agent/presets/${id}/status`, {
        method: 'PATCH',
        body: {enabled},
    })
}

export function deleteAgentPreset(id: number) {
    return requestJson<void>(`/api/agent/presets/${id}`, {
        method: 'DELETE',
    })
}

export function getAgentMemorySessions(params: PageParams & {
    entryType?: AgentMemoryEntryType
    status?: AgentMemorySessionStatus
}) {
    return requestJson<AgentPage<AgentMemorySessionResponse>>(`/api/agent/memory/sessions?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        entryType: params.entryType,
        status: params.status,
    })}`)
}

export function createAgentMemorySession(request: AgentMemorySessionRequest) {
    return requestJson<AgentMemorySessionResponse>('/api/agent/memory/sessions', {
        method: 'POST',
        body: request,
    })
}

export function updateAgentMemorySession(sessionId: string, request: Partial<AgentMemorySessionRequest>) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}`, {
        method: 'PATCH',
        body: request,
    })
}

export function releaseAgentMemorySession(sessionId: string) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}/release`, {
        method: 'POST',
    })
}

export function restoreAgentMemorySession(sessionId: string) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}/restore`, {
        method: 'POST',
    })
}

export function archiveAgentMemorySession(sessionId: string) {
    return requestJson<AgentMemorySessionResponse>(`/api/agent/memory/sessions/${sessionId}/archive`, {
        method: 'POST',
    })
}

export function deleteAgentMemorySession(sessionId: string) {
    return requestJson<void>(`/api/agent/memory/sessions/${sessionId}`, {
        method: 'DELETE',
    })
}

export function getAgentMemoryMessages(sessionId: string) {
    return requestJson<AgentMemoryMessageResponse[]>(`/api/agent/memory/sessions/${sessionId}/messages`)
}

export function getAgentLongTermMemory() {
    return requestJson<AgentLongTermMemoryResponse>('/api/agent/memory/long-term')
}

export function getAgentLongTermMemoryVersions() {
    return requestJson<AgentLongTermMemoryVersionResponse[]>('/api/agent/memory/long-term/versions')
}

export function getAgentMemoryExtractions() {
    return requestJson<AgentMemoryExtractionAuditResponse[]>('/api/agent/memory/extractions')
}

export function streamAgentDebugChat(
    request: AgentDebugChatRequest,
    signal: AbortSignal,
    onChunk: (chunk: AgentStreamChunk) => void,
) {
    return streamJsonLines<AgentStreamChunk>('/api/agent/debug/chat/stream', {body: request, signal}, onChunk)
}

export function getAgentConversationAudits(params: PageParams & {
    startAt?: string
    endAt?: string
    userId?: number
    username?: string
    threadId?: string
    presetId?: number
    status?: AgentConversationStatus
}) {
    return requestJson<AgentPage<AgentConversationAuditResponse>>(`/api/admin/agent/conversation-audits?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        startAt: params.startAt,
        endAt: params.endAt,
        userId: params.userId,
        username: params.username,
        threadId: params.threadId,
        presetId: params.presetId,
        status: params.status,
    })}`)
}

export function getAgentConversationAuditMessages(id: number) {
    return requestJson<AgentConversationMessageAuditResponse[]>(`/api/admin/agent/conversation-audits/${id}/messages`)
}

export function getAgentRunAudits(params: PageParams & {
    startAt?: string
    endAt?: string
    userId?: number
    username?: string
    threadId?: string
    requestId?: string
    traceId?: string
    presetId?: number
    toolName?: string
    mcpServerCode?: string
    status?: AgentRunAuditStatus
}) {
    return requestJson<AgentPage<AgentRunAuditResponse>>(`/api/admin/agent/run-audits?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        startAt: params.startAt,
        endAt: params.endAt,
        userId: params.userId,
        username: params.username,
        threadId: params.threadId,
        requestId: params.requestId,
        traceId: params.traceId,
        presetId: params.presetId,
        toolName: params.toolName,
        mcpServerCode: params.mcpServerCode,
        status: params.status,
    })}`)
}

export function getAgentRunAuditEvents(id: number) {
    return requestJson<AgentRunEventAuditResponse[]>(`/api/admin/agent/run-audits/${id}/events`)
}

export function getAgentRunAuditDetail(id: number) {
    return requestJson<AgentRunAuditResponse>(`/api/admin/agent/run-audits/${id}`)
}
