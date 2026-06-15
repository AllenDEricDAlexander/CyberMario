import {requestJson, streamJsonLines} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {
    AgentConversationAuditResponse,
    AgentConversationMessageAuditResponse,
    AgentConversationStatus,
    AgentDebugChatRequest,
    AgentPage,
    AgentPresetRequest,
    AgentPresetResponse,
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
