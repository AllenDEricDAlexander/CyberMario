import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {
    CreateMcpServerRequest,
    McpConnectionTestResponse,
    McpPage,
    McpServerResponse,
    McpToolCallLogResponse,
    McpToolDiscoveryResponse,
    McpToolResponse,
    UpdateMcpServerRequest,
    UpdateMcpToolPolicyRequest,
} from './mcpTypes'

type PageParams = {
    page?: number
    size?: number
}

export function getMcpServers() {
    return requestJson<McpServerResponse[]>('/api/admin/agent/mcp/servers')
}

export function createMcpServer(request: CreateMcpServerRequest) {
    return requestJson<McpServerResponse>('/api/admin/agent/mcp/servers', {
        method: 'POST',
        body: request,
    })
}

export function updateMcpServer(id: number, request: UpdateMcpServerRequest) {
    return requestJson<McpServerResponse>(`/api/admin/agent/mcp/servers/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deleteMcpServer(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/servers/${id}`, {
        method: 'DELETE',
    })
}

export function enableMcpServer(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/servers/${id}/enable`, {
        method: 'POST',
    })
}

export function disableMcpServer(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/servers/${id}/disable`, {
        method: 'POST',
    })
}

export function testMcpServer(id: number) {
    return requestJson<McpConnectionTestResponse>(`/api/admin/agent/mcp/servers/${id}/test`, {
        method: 'POST',
    })
}

export function discoverMcpTools(id: number) {
    return requestJson<McpToolDiscoveryResponse>(`/api/admin/agent/mcp/servers/${id}/discover-tools`, {
        method: 'POST',
    })
}

export function getMcpTools(serverId?: number) {
    const query = buildSearchParams({serverId})
    return requestJson<McpToolResponse[]>(`/api/admin/agent/mcp/tools${query ? `?${query}` : ''}`)
}

export function updateMcpToolPolicy(id: number, request: UpdateMcpToolPolicyRequest) {
    return requestJson<McpToolResponse>(`/api/admin/agent/mcp/tools/${id}/policy`, {
        method: 'PUT',
        body: request,
    })
}

export function enableMcpTool(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/tools/${id}/enable`, {
        method: 'POST',
    })
}

export function disableMcpTool(id: number) {
    return requestJson<void>(`/api/admin/agent/mcp/tools/${id}/disable`, {
        method: 'POST',
    })
}

export function getMcpToolCallLogs(params: PageParams) {
    return requestJson<McpPage<McpToolCallLogResponse>>(`/api/admin/agent/mcp/tool-calls?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
    })}`)
}
