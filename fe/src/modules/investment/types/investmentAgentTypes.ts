import type {PageResult} from '../../../types/api'
import type {InvestmentDecimal} from './investmentCommonTypes'

export type InvestmentAgentRunType =
    | 'MARKET_ANALYSIS'
    | 'INSTRUMENT_ANALYSIS'
    | 'STRATEGY_REVIEW'
    | 'PORTFOLIO_REVIEW'
    | 'AUTO_TRADE'

export type InvestmentAgentAction = 'HOLD' | 'OPEN_LONG' | 'OPEN_SHORT' | 'CLOSE' | 'REDUCE'
export type InvestmentAgentExecutionStatus = 'NOT_APPLICABLE' | 'PENDING' | 'SUBMITTED' | 'FAILED'
export type InvestmentAgentRunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export type SubmitInvestmentAgentRunRequest = {
    runType: InvestmentAgentRunType
    accountId: number | null
    instrumentIds: number[]
}

export type InvestmentAgentRunResponse = {
    id: number
    workspaceId: number
    accountId: number | null
    presetCode: string
    genericAgentRunAuditId: number
    runType: InvestmentAgentRunType
    status: InvestmentAgentRunStatus
    dataAsOf: string
    reportId: number | null
    startedAt: string
    finishedAt: string | null
    errorCode: string | null
    errorMessage: string | null
    createdAt: string
}

export type InvestmentAgentRiskCheck = {
    ruleCode: string
    passed: boolean
    observedValue: InvestmentDecimal | null
    limitValue: InvestmentDecimal | null
    message: string
    details: Record<string, string>
    checkedAt: string
}

export type InvestmentAgentOrder = {
    id: number
    status: string
    submittedAt: string
    matchedAt: string | null
}

export type InvestmentAgentFill = {
    id: number
    price: InvestmentDecimal
    quantity: InvestmentDecimal
    feeAmount: InvestmentDecimal
    filledAt: string
}

export type InvestmentAgentExecutionResponse = {
    intentId: number
    intentStatus: string
    riskChecks: InvestmentAgentRiskCheck[]
    order: InvestmentAgentOrder | null
    fill: InvestmentAgentFill | null
}

export type InvestmentAgentDecisionResponse = {
    id: number
    instrumentId: number | null
    action: InvestmentAgentAction
    confidence: InvestmentDecimal
    horizon: string
    thesis: string
    risks: string[]
    invalidation: string[]
    requestedQuantity: InvestmentDecimal | null
    requestedNotional: InvestmentDecimal | null
    requestedLeverage: InvestmentDecimal | null
    orderType: 'MARKET' | 'LIMIT' | null
    limitPrice: InvestmentDecimal | null
    intentId: number | null
    executionStatus: InvestmentAgentExecutionStatus
    dataAsOf: string
    expiresAt: string | null
    status: string
    createdAt: string
    execution: InvestmentAgentExecutionResponse | null
}

export type InvestmentAgentRunDetailResponse = {
    run: InvestmentAgentRunResponse
    decisions: InvestmentAgentDecisionResponse[]
}

export type SubmitInvestmentAgentRunResponse = {
    run: InvestmentAgentRunResponse
    jobId: number | null
    duplicate: boolean
}

export type InvestmentAgentRunPage = PageResult<InvestmentAgentRunResponse>
