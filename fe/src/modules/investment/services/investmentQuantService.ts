import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {
    InvestmentBacktestEquityResponse,
    InvestmentBacktestEventPage,
    InvestmentBacktestPage,
    InvestmentBacktestRunResponse,
    InvestmentBacktestTradePage,
    InvestmentStrategyDescriptor,
    SubmitInvestmentBacktestRequest,
} from '../types/investmentQuantTypes'

export function listInvestmentStrategies() {
    return requestJson<InvestmentStrategyDescriptor[]>('/api/investment/strategies')
}

export function listInvestmentBacktests(workspaceId: number, page = 1, size = 20) {
    const search = buildSearchParams({page, size})
    return requestJson<InvestmentBacktestPage>(
        `/api/investment/workspaces/${workspaceId}/backtests?${search}`,
    )
}

export function submitInvestmentBacktest(workspaceId: number, request: SubmitInvestmentBacktestRequest) {
    return requestJson<InvestmentBacktestRunResponse>(
        `/api/investment/workspaces/${workspaceId}/backtests`,
        {method: 'POST', body: request},
    )
}

export function getInvestmentBacktest(runId: number) {
    return requestJson<InvestmentBacktestRunResponse>(`/api/investment/backtests/${runId}`)
}

export function listInvestmentBacktestTrades(runId: number, page = 1, size = 100) {
    const search = buildSearchParams({page, size})
    return requestJson<InvestmentBacktestTradePage>(
        `/api/investment/backtests/${runId}/trades?${search}`,
    )
}

export function listInvestmentBacktestEvents(runId: number, page = 1, size = 100) {
    const search = buildSearchParams({page, size})
    return requestJson<InvestmentBacktestEventPage>(
        `/api/investment/backtests/${runId}/events?${search}`,
    )
}

export function getInvestmentBacktestEquity(runId: number) {
    return requestJson<InvestmentBacktestEquityResponse[]>(`/api/investment/backtests/${runId}/equity`)
}
