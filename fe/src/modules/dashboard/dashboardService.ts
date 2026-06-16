import {requestJson} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {
    ModelAuditDashboardQuery,
    ModelAuditDashboardSummaryResponse,
    ModelAuditRecentCallPage,
    ModelAuditUserOption,
} from './dashboardTypes'

function dashboardBasePath(query: ModelAuditDashboardQuery) {
    return query.scope === 'GLOBAL'
        ? '/api/agent/model-audit/dashboard/global'
        : '/api/agent/model-audit/dashboard/self'
}

export function getModelAuditDashboardSummary(query: ModelAuditDashboardQuery) {
    const search = buildSearchParams({
        startAt: query.startAt,
        endAt: query.endAt,
        userId: query.scope === 'GLOBAL' ? query.userId : undefined,
        provider: query.provider,
        model: query.model,
        scenario: query.scenario,
        status: query.status,
    })
    const path = `${dashboardBasePath(query)}/summary`
    return requestJson<ModelAuditDashboardSummaryResponse>(search ? `${path}?${search}` : path)
}

export function getModelAuditRecentCalls(query: ModelAuditDashboardQuery, page = 1, size = 20) {
    const search = buildSearchParams({
        startAt: query.startAt,
        endAt: query.endAt,
        userId: query.scope === 'GLOBAL' ? query.userId : undefined,
        provider: query.provider,
        model: query.model,
        scenario: query.scenario,
        status: query.status,
        page,
        size,
    })
    return requestJson<ModelAuditRecentCallPage>(`${dashboardBasePath(query)}/recent-calls?${search}`)
}

export function getModelAuditUserOptions(keyword?: string, size = 20) {
    return requestJson<ModelAuditUserOption[]>(`/api/agent/model-audit/dashboard/user-options?${buildSearchParams({
        keyword,
        size,
    })}`)
}
