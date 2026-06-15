import type {PageResult} from '../../../types/api'

export type McpTransportType = 'STREAMABLE_HTTP' | 'SSE'
export type McpServerStatus = 'DISABLED' | 'CONNECTING' | 'CONNECTED' | 'FAILED'
export type McpToolRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type McpToolRuntimeStatus = 'AVAILABLE' | 'DISABLED' | 'SERVER_DISABLED' | 'SERVER_FAILED' | 'POLICY_BLOCKED'
export type McpToolCallStatus = 'SUCCESS' | 'FAILED' | 'BLOCKED'

export type McpServerResponse = {
    id: number
    serverCode: string
    serverName: string
    transportType: McpTransportType
    baseUrl: string
    endpoint: string
    headers?: Record<string, string>
    enabled: boolean
    connectTimeoutMs: number
    requestTimeoutMs: number
    status: McpServerStatus
    lastError?: string
    lastConnectedAt?: string
    createdAt?: string
    updatedAt?: string
}

export type McpToolResponse = {
    id: number
    serverId: number
    serverCode: string
    toolName: string
    toolKey: string
    displayName: string
    description?: string
    inputSchemaJson?: string
    enabled: boolean
    riskLevel: McpToolRiskLevel
    readonly: boolean
    requireConfirm: boolean
    runtimeStatus: McpToolRuntimeStatus
    lastDiscoveredAt?: string
}

export type CreateMcpServerRequest = {
    serverCode: string
    serverName: string
    transportType: McpTransportType
    baseUrl: string
    endpoint: string
    headers?: Record<string, string>
    connectTimeoutMs?: number
    requestTimeoutMs?: number
}

export type UpdateMcpServerRequest = Omit<CreateMcpServerRequest, 'serverCode'>

export type UpdateMcpToolPolicyRequest = {
    riskLevel: McpToolRiskLevel
    readonly: boolean
    requireConfirm: boolean
}

export type McpConnectionTestResponse = {
    success: boolean
    serverName?: string
    serverVersion?: string
    toolCount: number
    errorMessage?: string
}

export type McpToolDiscoveryResponse = {
    serverId: number
    discoveredCount: number
    createdCount: number
    updatedCount: number
}

export type McpToolCallLogResponse = {
    id: number
    traceId?: string
    threadId?: string
    userId?: number
    serverCode: string
    toolKey: string
    toolName: string
    requestArgsSummary?: string
    responseSummary?: string
    status: McpToolCallStatus
    errorMsg?: string
    costMs: number
    createdAt: string
}

export type McpPage<T> = PageResult<T>
