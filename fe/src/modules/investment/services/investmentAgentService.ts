import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {
    InvestmentAgentRunDetailResponse,
    InvestmentAgentRunPage,
    SubmitInvestmentAgentRunRequest,
    SubmitInvestmentAgentRunResponse,
} from '../types/investmentAgentTypes'

export function listInvestmentAgentRuns(workspaceId: number, page = 1, size = 20) {
    return requestJson<InvestmentAgentRunPage>(
        `/api/investment/workspaces/${workspaceId}/agent-runs?${buildSearchParams({page, size})}`,
    )
}

export function submitInvestmentAgentRun(workspaceId: number, request: SubmitInvestmentAgentRunRequest) {
    return requestJson<SubmitInvestmentAgentRunResponse>(
        `/api/investment/workspaces/${workspaceId}/agent-runs`,
        {method: 'POST', body: request},
    )
}

export function getInvestmentAgentRun(runId: number) {
    return requestJson<InvestmentAgentRunDetailResponse>(`/api/investment/agent-runs/${runId}`)
}
