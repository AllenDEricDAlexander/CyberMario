import {requestJson} from '../../../services/request'
import type {InvestmentOverviewResponse} from '../types/investmentOverviewTypes'

export function getInvestmentOverview(workspaceId: number) {
    return requestJson<InvestmentOverviewResponse>(
        `/api/investment/workspaces/${workspaceId}/overview`,
    )
}
