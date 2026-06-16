import type {PageResult} from '../../types/api'

export type ModelAuditDashboardScope = 'SELF' | 'GLOBAL'
export type ModelAuditProvider = 'DASHSCOPE'
export type ModelAuditScenario = 'UNKNOWN' | 'AGENT_CHAT' | 'RAG_CHAT' | 'RAG_SUMMARY' | 'BACKGROUND_TASK'
export type ModelAuditStatus = 'SUCCESS' | 'FAILED' | 'CANCELLED'

export type ModelAuditDashboardQuery = {
    scope: ModelAuditDashboardScope
    startAt?: string
    endAt?: string
    userId?: number
    provider?: ModelAuditProvider
    model?: string
    scenario?: ModelAuditScenario
    status?: ModelAuditStatus
}

export type ModelAuditOverview = {
    callCount: number
    successCount: number
    failedCount: number
    successRate: number
    promptTokens: number
    completionTokens: number
    totalTokens: number
    promptChars: number
    completionChars: number
    avgDurationMs: number
    streamingCount: number
}

export type ModelAuditTrendPoint = {
    date: string
    metric: string
    value: number
}

export type ModelAuditDimensionStat = {
    name: string
    callCount: number
    totalTokens: number
    avgDurationMs: number
}

export type ModelAuditUserStat = {
    userId?: number | null
    username?: string
    nickname?: string
    callCount: number
    totalTokens: number
}

export type ModelAuditRecentCall = {
    id: number
    createdAt: string
    userId?: number
    username?: string
    nickname?: string
    provider: ModelAuditProvider
    model: string
    scenario: ModelAuditScenario
    status: ModelAuditStatus
    promptTokens?: number
    completionTokens?: number
    totalTokens?: number
    durationMs?: number
    traceId?: string
}

export type ModelAuditUserOption = {
    id: number
    username: string
    nickname?: string
}

export type ModelAuditDashboardSummaryResponse = {
    scope: ModelAuditDashboardScope
    startAt: string
    endAt: string
    overview: ModelAuditOverview
    tokenTrend: ModelAuditTrendPoint[]
    callTrend: ModelAuditTrendPoint[]
    providerStats: ModelAuditDimensionStat[]
    modelStats: ModelAuditDimensionStat[]
    scenarioStats: ModelAuditDimensionStat[]
    statusStats: ModelAuditDimensionStat[]
    userStats: ModelAuditUserStat[]
}

export type ModelAuditRecentCallPage = PageResult<ModelAuditRecentCall>
