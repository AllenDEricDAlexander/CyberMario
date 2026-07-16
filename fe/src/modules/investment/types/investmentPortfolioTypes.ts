import type {PageResult} from '../../../types/api'
import type {InvestmentDecimal} from './investmentCommonTypes'

export type InvestmentPaperAccountResponse = {
    id: number
    workspaceId: number
    name: string
    baseCurrency: string
    initialEquity: InvestmentDecimal
    walletBalance: InvestmentDecimal
    equity: InvestmentDecimal
    usedMargin: InvestmentDecimal
    availableBalance: InvestmentDecimal
    grossExposure: InvestmentDecimal
    unrealizedPnl: InvestmentDecimal
    tradingEnabled: boolean
    agentAutoTradeEnabled: boolean
    status: string
    openedAt: string
    version: number
}

export type InvestmentRiskProfile = {
    id: number
    accountId: number
    maxLeverage: InvestmentDecimal
    maxOrderNotional: InvestmentDecimal
    maxPositionNotional: InvestmentDecimal
    maxGrossExposureNotional: InvestmentDecimal
    maxOpenPositions: number
    maxDailyLossAmount: InvestmentDecimal
    maxDrawdownRatio: InvestmentDecimal
    maxOrdersPerHour: number
    cooldownSeconds: number
    maxMarketDataAgeSeconds: number
    maxSlippageBps: InvestmentDecimal
    version: number
}

export type InvestmentRiskProfileInput = Omit<InvestmentRiskProfile, 'id' | 'accountId' | 'version'>

export type InvestmentPaperAccountDetail = {
    account: InvestmentPaperAccountResponse
    riskProfile: InvestmentRiskProfile
}

export type CreateInvestmentPaperAccountRequest = {
    name: string
    initialEquity: InvestmentDecimal
    riskProfile: InvestmentRiskProfileInput
}

export type UpdateInvestmentPaperAccountSwitchesRequest = {
    tradingEnabled: boolean
    agentAutoTradeEnabled: boolean
    version: number
}

export type UpdateInvestmentRiskProfileRequest = InvestmentRiskProfileInput & {version: number}

export type InvestmentPaperOrder = {
    orderId: number
    status: string
    submittedAt: string
    matchedAt: string | null
}

export type InvestmentPaperFill = {
    fillId: number
    fillPrice: InvestmentDecimal
    quantity: InvestmentDecimal
    feeAmount: InvestmentDecimal
    filledAt: string
}

export type InvestmentPaperRiskCheck = {
    ruleCode: string
    passed: boolean
    observedValue: InvestmentDecimal | null
    limitValue: InvestmentDecimal | null
    message: string
    details: Record<string, string>
    checkedAt: string
}

export type InvestmentPaperTradeResult = {
    intentId: number
    intentStatus: string
    riskResults: InvestmentPaperRiskCheck[]
    order: InvestmentPaperOrder | null
    fill: InvestmentPaperFill | null
}

export type SubmitInvestmentPaperTradeRequest = {
    instrumentId: number
    positionAction: 'OPEN' | 'CLOSE'
    positionSide: 'LONG' | 'SHORT'
    orderType: 'MARKET' | 'LIMIT'
    quantity: InvestmentDecimal
    requestedNotional: InvestmentDecimal
    leverage: InvestmentDecimal
    limitPrice: InvestmentDecimal | null
    reduceOnly: boolean
    reason: string | null
    dataAsOf: string
    expiresAt: string | null
    idempotencyKey: string
}

export type InvestmentPosition = {
    id: number
    instrumentId: number
    positionSide: 'LONG' | 'SHORT'
    quantity: InvestmentDecimal
    entryPrice: InvestmentDecimal
    leverage: InvestmentDecimal
    markPrice: InvestmentDecimal | null
    liquidationPrice: InvestmentDecimal
    isolatedMargin: InvestmentDecimal
    maintenanceMargin: InvestmentDecimal
    realizedPnl: InvestmentDecimal
    fundingPnl: InvestmentDecimal
    unrealizedPnl: InvestmentDecimal | null
    lastFillAt: string | null
    lastMarginCheckAt: string | null
}

export type InvestmentFillMarker = {
    id: number
    instrumentId: number
    marketBarOpenTime: string | null
    eventTime: string
    side: string
    actionType: string
    orderOrigin: string
    eventType: string
    price: InvestmentDecimal
    quantity: InvestmentDecimal
    liquidation: boolean
}

export type InvestmentLedgerEntry = {
    id: number
    sequenceNo: number
    eventType: string
    amount: InvestmentDecimal
    balanceAfter: InvestmentDecimal
    instrumentId: number | null
    referenceType: string
    referenceId: string
    occurredAt: string
}

export type InvestmentEquityPoint = {
    snapshotTime: string
    walletBalance: InvestmentDecimal
    usedMargin: InvestmentDecimal
    maintenanceMargin: InvestmentDecimal
    unrealizedPnl: InvestmentDecimal
    equity: InvestmentDecimal
    availableBalance: InvestmentDecimal
    grossExposure: InvestmentDecimal
    totalReturn: InvestmentDecimal
    drawdown: InvestmentDecimal
    positionCount: number
}

export type InvestmentPaperAccountPage = PageResult<InvestmentPaperAccountResponse>
export type InvestmentPaperOrderPage = PageResult<InvestmentPaperOrder>
export type InvestmentFillMarkerPage = PageResult<InvestmentFillMarker>
export type InvestmentLedgerPage = PageResult<InvestmentLedgerEntry>
export type InvestmentEquityPage = PageResult<InvestmentEquityPoint>
