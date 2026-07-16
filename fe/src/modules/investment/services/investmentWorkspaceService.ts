import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {PageResult} from '../../../types/api'
import type {
    CreateInvestmentWorkspaceRequest,
    InvestmentWorkspaceResponse,
} from '../types/investmentWorkspaceTypes'

export function listInvestmentWorkspaces(page = 1, size = 100) {
    const search = buildSearchParams({page, size})
    return requestJson<PageResult<InvestmentWorkspaceResponse>>(`/api/investment/workspaces?${search}`)
}

export function createInvestmentWorkspace(request: CreateInvestmentWorkspaceRequest) {
    return requestJson<InvestmentWorkspaceResponse>('/api/investment/workspaces', {
        method: 'POST',
        body: request,
    })
}
