import type {PageResult} from '../../../types/api'
import type {InvestmentDecimal} from './investmentCommonTypes'
import type {InvestmentBarInterval, InvestmentPriceType} from './investmentMarketTypes'

export type InvestmentStrategyDescriptor = {
    strategyCode: string
    strategyVersion: string
    displayName: string
    description: string
    engineType: 'JAVA'
    requiredCapabilities: string[]
    supportedIntervals: InvestmentBarInterval[]
    evaluationInterval: InvestmentBarInterval
    priceType: InvestmentPriceType
    evaluationSchedule: string
    positionSizingPolicy: string
    defaultLeverage: InvestmentDecimal | number
    maximumLeverage: InvestmentDecimal | number
    feeModelCode: string
    slippageModelCode: string
    matchingModelCode: string
}

export type InvestmentBacktestStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type SubmitInvestmentBacktestRequest = {
    strategyCode: string
    instrumentIds: number[]
    startTime: string
    endTime: string
}

export type InvestmentBacktestRunResponse = {
    runId: number
    workspaceId: number
    jobId: number
    strategyReleaseId: number
    datasetSnapshotId: number
    status: InvestmentBacktestStatus
    initialEquity: InvestmentDecimal
    totalReturn: InvestmentDecimal | null
    annualizedReturn: InvestmentDecimal | null
    maxDrawdown: InvestmentDecimal | null
    sharpeRatio: InvestmentDecimal | null
    sortinoRatio: InvestmentDecimal | null
    winRate: InvestmentDecimal | null
    profitFactor: InvestmentDecimal | null
    turnover: InvestmentDecimal | null
    tradeCount: number | null
    totalFee: InvestmentDecimal | null
    totalFunding: InvestmentDecimal | null
    liquidationCount: number | null
    errorCode: string | null
    errorMessage: string | null
    startedAt: string | null
    finishedAt: string | null
    createdAt: string
}

export type InvestmentBacktestTradeResponse = {
    tradeId: number
    instrumentId: number
    positionSide: 'LONG' | 'SHORT'
    entryTime: string
    exitTime: string
    entryPrice: InvestmentDecimal
    exitPrice: InvestmentDecimal
    quantity: InvestmentDecimal
    leverage: InvestmentDecimal
    grossPnl: InvestmentDecimal
    feeAmount: InvestmentDecimal
    fundingAmount: InvestmentDecimal
    netPnl: InvestmentDecimal
    exitReason: string
}

export type InvestmentBacktestEventResponse = {
    eventId: number
    instrumentId: number | null
    eventType: string
    eventTime: string
    amount: InvestmentDecimal | null
    balanceAfter: InvestmentDecimal | null
    detailsJson: string
    sequenceNo: number
}

export type InvestmentBacktestEquityResponse = {
    pointTime: string
    walletBalance: InvestmentDecimal
    usedMargin: InvestmentDecimal
    unrealizedPnl: InvestmentDecimal
    equity: InvestmentDecimal
    drawdown: InvestmentDecimal
    grossExposure: InvestmentDecimal
}

export type InvestmentBacktestPage = PageResult<InvestmentBacktestRunResponse>
export type InvestmentBacktestTradePage = PageResult<InvestmentBacktestTradeResponse>
export type InvestmentBacktestEventPage = PageResult<InvestmentBacktestEventResponse>
