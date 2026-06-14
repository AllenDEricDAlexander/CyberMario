import {requestJson} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {ModelAuditDashboardQuery, ModelAuditDashboardResponse, ModelAuditUserOption} from './dashboardTypes'

export function getModelAuditDashboard(query: ModelAuditDashboardQuery) {
    const path = query.scope === 'GLOBAL'
        ? '/api/agent/model-audit/dashboard/global'
        : '/api/agent/model-audit/dashboard/self'
    const search = buildSearchParams({
        startAt: query.startAt,
        endAt: query.endAt,
        userId: query.scope === 'GLOBAL' ? query.userId : undefined,
        provider: query.provider,
        model: query.model,
        scenario: query.scenario,
        status: query.status,
    })
    return requestJson<ModelAuditDashboardResponse>(search ? `${path}?${search}` : path)
}

export function getModelAuditUserOptions(keyword?: string, size = 20) {
    return requestJson<ModelAuditUserOption[]>(`/api/agent/model-audit/dashboard/user-options?${buildSearchParams({
        keyword,
        size,
    })}`)
}
