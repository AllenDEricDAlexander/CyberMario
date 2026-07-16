import type {InvestmentDecimal} from './investmentCommonTypes'
import type {InvestmentPosition} from './investmentPortfolioTypes'

export type InvestmentOverviewSectionCode = 'MARKET' | 'QUANT' | 'PORTFOLIO' | 'AGENT'
export type InvestmentOverviewSectionStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'ERROR'

export type InvestmentMarketOverviewData = {
    subscribedInstrumentCount: number
    freshQuoteCount: number
    staleOrMissingQuoteCount: number
    openQualityIssueCount: number
}

export type InvestmentPortfolioOverviewData = {
    accountCount: number
    positionCount: number
    walletBalance: InvestmentDecimal | null
    equity: InvestmentDecimal | null
    availableBalance: InvestmentDecimal | null
    unrealizedPnl: InvestmentDecimal | null
    grossExposure: InvestmentDecimal | null
    maxDrawdown: InvestmentDecimal | null
    riskWarningCount: number
    positions: InvestmentPosition[]
}

export type InvestmentOverviewBacktest = {
    runId: number
    strategyReleaseId: number
    datasetSnapshotId: number
    totalReturn: InvestmentDecimal | null
    maxDrawdown: InvestmentDecimal | null
    winRate: InvestmentDecimal | null
    tradeCount: number | null
    finishedAt: string
}

export type InvestmentQuantOverviewData = {
    recentBacktests: InvestmentOverviewBacktest[]
}

export type InvestmentOverviewAgentRun = {
    runId: number
    runType: string
    accountId: number | null
    reportId: number | null
    dataAsOf: string
    finishedAt: string
    decisionId?: number
    instrumentId?: number | null
    action?: string
    confidence?: InvestmentDecimal
    executionStatus?: string
    intentId?: number | null
}

export type InvestmentAgentOverviewData = {
    recentRuns: InvestmentOverviewAgentRun[]
}

export type InvestmentOverviewSection = {
    code: InvestmentOverviewSectionCode
    status: InvestmentOverviewSectionStatus
    dataAsOf: string
    data: Record<string, unknown>
    errorCode: string | null
}

export type InvestmentOverviewResponse = {
    workspaceId: number
    dataAsOf: string
    sections: InvestmentOverviewSection[]
}
