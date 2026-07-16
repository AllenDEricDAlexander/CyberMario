import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {
    CreateInvestmentPaperAccountRequest,
    InvestmentEquityPage,
    InvestmentFillMarkerPage,
    InvestmentLedgerPage,
    InvestmentPaperAccountDetail,
    InvestmentPaperAccountPage,
    InvestmentPaperAccountResponse,
    InvestmentPaperOrder,
    InvestmentPaperOrderPage,
    InvestmentPaperTradeResult,
    InvestmentPosition,
    InvestmentRiskProfile,
    SubmitInvestmentPaperTradeRequest,
    UpdateInvestmentPaperAccountSwitchesRequest,
    UpdateInvestmentRiskProfileRequest,
} from '../types/investmentPortfolioTypes'

export function listInvestmentPaperAccounts(workspaceId: number, page = 1, size = 100) {
    return requestJson<InvestmentPaperAccountPage>(
        `/api/investment/workspaces/${workspaceId}/paper-accounts?${buildSearchParams({page, size})}`,
    )
}

export function createInvestmentPaperAccount(workspaceId: number, request: CreateInvestmentPaperAccountRequest) {
    return requestJson<InvestmentPaperAccountDetail>(
        `/api/investment/workspaces/${workspaceId}/paper-accounts`,
        {method: 'POST', body: request},
    )
}

export function getInvestmentPaperAccount(accountId: number) {
    return requestJson<InvestmentPaperAccountDetail>(`/api/investment/paper-accounts/${accountId}`)
}

export function updateInvestmentPaperAccountSwitches(
    accountId: number,
    request: UpdateInvestmentPaperAccountSwitchesRequest,
) {
    return requestJson<InvestmentPaperAccountResponse>(
        `/api/investment/paper-accounts/${accountId}/switches`,
        {method: 'PATCH', body: request},
    )
}

export function getInvestmentRiskProfile(accountId: number) {
    return requestJson<InvestmentRiskProfile>(`/api/investment/paper-accounts/${accountId}/risk-profile`)
}

export function updateInvestmentRiskProfile(accountId: number, request: UpdateInvestmentRiskProfileRequest) {
    return requestJson<InvestmentRiskProfile>(
        `/api/investment/paper-accounts/${accountId}/risk-profile`,
        {method: 'PUT', body: request},
    )
}

export function submitInvestmentPaperTrade(accountId: number, request: SubmitInvestmentPaperTradeRequest) {
    return requestJson<InvestmentPaperTradeResult>(
        `/api/investment/paper-accounts/${accountId}/trade-intents`,
        {method: 'POST', body: request},
    )
}

export function listInvestmentPaperOrders(accountId: number, page = 1, size = 20) {
    return requestJson<InvestmentPaperOrderPage>(
        `/api/investment/paper-accounts/${accountId}/orders?${buildSearchParams({page, size})}`,
    )
}

export function cancelInvestmentPaperOrder(orderId: number) {
    return requestJson<InvestmentPaperOrder>(
        `/api/investment/paper-orders/${orderId}/cancel`,
        {method: 'POST'},
    )
}

export function listInvestmentPaperFills(
    accountId: number,
    instrumentId: number,
    from: string,
    to: string,
    page = 1,
    size = 100,
) {
    const search = buildSearchParams({instrumentId, from, to, page, size})
    return requestJson<InvestmentFillMarkerPage>(`/api/investment/paper-accounts/${accountId}/fills?${search}`)
}

export function listInvestmentPositions(accountId: number) {
    return requestJson<InvestmentPosition[]>(`/api/investment/paper-accounts/${accountId}/positions`)
}

export function listInvestmentLedger(accountId: number, page = 1, size = 50) {
    return requestJson<InvestmentLedgerPage>(
        `/api/investment/paper-accounts/${accountId}/ledger?${buildSearchParams({page, size})}`,
    )
}

export function listInvestmentEquity(accountId: number, page = 1, size = 500) {
    return requestJson<InvestmentEquityPage>(
        `/api/investment/paper-accounts/${accountId}/equity?${buildSearchParams({page, size})}`,
    )
}
