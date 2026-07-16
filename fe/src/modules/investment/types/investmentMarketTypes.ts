import type {
    InvestmentCandleResponse,
    InvestmentDecimal,
    InvestmentFreshnessResponse,
    InvestmentInstrumentSummaryResponse,
} from './investmentCommonTypes'

export type InvestmentMarketSort = 'SYMBOL_ASC' | 'SYMBOL_DESC'
export type InvestmentInstrumentStatus = 'ACTIVE' | 'SUSPENDED' | 'OFFLINE'

export type InvestmentMarketListQuery = {
    page: number
    size: number
    status?: InvestmentInstrumentStatus
    sort: InvestmentMarketSort
}

export type InvestmentWatchlistItemResponse = {
    id: number
    instrumentId: number
    sortNo: number
    note: string | null
    createdAt: string
}

export type InvestmentWatchlistResponse = {
    id: number
    workspaceId: number
    name: string
    description: string | null
    sortNo: number
    items: InvestmentWatchlistItemResponse[]
    createdAt: string
}

export type AddInvestmentWatchlistItemRequest = {
    instrumentId: number
    note: string | null
}

export type InvestmentContractSpecResponse = {
    pricePrecision: number
    quantityPrecision: number
    priceEndStep: InvestmentDecimal
    quantityStep: InvestmentDecimal
    contractMultiplier: InvestmentDecimal
    minTradeQuantity: InvestmentDecimal
    minTradeNotional: InvestmentDecimal
    maxMarketOrderQuantity: InvestmentDecimal
    maxLimitOrderQuantity: InvestmentDecimal
    makerFeeRate: InvestmentDecimal
    takerFeeRate: InvestmentDecimal
    minLeverage: InvestmentDecimal
    maxLeverage: InvestmentDecimal
    fundingIntervalHours: number
    buyLimitPriceRatio: InvestmentDecimal
    sellLimitPriceRatio: InvestmentDecimal
    sourceUpdatedAt: string | null
    ingestedAt: string
    revision: number
}

export type InvestmentInstrumentDetailResponse = {
    instrumentId: number
    venueCode: string
    symbol: string
    baseAsset: string
    quoteAsset: string
    settlementAsset: string
    marginAsset: string
    productType: string
    contractType: string
    status: string
    launchTime: string | null
    availableCapabilities: string[]
    availablePriceTypes: InvestmentPriceType[]
    availableIntervals: InvestmentBarInterval[]
    dataAsOf: string
    freshness: InvestmentFreshnessResponse
    contractSpecAvailable: boolean
    contractSpec: InvestmentContractSpecResponse | null
}

export type InvestmentQuoteResponse = {
    instrumentId: number
    lastPrice: InvestmentDecimal | null
    markPrice: InvestmentDecimal | null
    indexPrice: InvestmentDecimal | null
    bidPrice: InvestmentDecimal | null
    askPrice: InvestmentDecimal | null
    bidQuantity: InvestmentDecimal | null
    askQuantity: InvestmentDecimal | null
    open24h: InvestmentDecimal | null
    high24h: InvestmentDecimal | null
    low24h: InvestmentDecimal | null
    baseVolume24h: InvestmentDecimal | null
    quoteVolume24h: InvestmentDecimal | null
    change24h: InvestmentDecimal | null
    fundingRate: InvestmentDecimal | null
    nextFundingTime: string | null
    openInterest: InvestmentDecimal | null
    sourceTime: string
    receivedAt: string
    version: number
    dataAsOf: string
    freshness: InvestmentFreshnessResponse
}

export type InvestmentFundingRateResponse = {
    fundingTime: string
    fundingRate: InvestmentDecimal
    revision: number
    dataAsOf: string
}

export type InvestmentPositionTierResponse = {
    tierLevel: number
    startNotional: InvestmentDecimal
    endNotional: InvestmentDecimal
    maxLeverage: InvestmentDecimal
    maintenanceMarginRate: InvestmentDecimal
    observedAt: string
    dataAsOf: string
}

export type InvestmentIndicatorPoint = {
    openTime: string
    close: InvestmentDecimal
    sma20: InvestmentDecimal | null
    ema20: InvestmentDecimal | null
    rsi14: InvestmentDecimal | null
    macd: InvestmentDecimal | null
    macdSignal: InvestmentDecimal | null
    macdHistogram: InvestmentDecimal | null
    bollingerUpper: InvestmentDecimal | null
    bollingerMiddle: InvestmentDecimal | null
    bollingerLower: InvestmentDecimal | null
    atr14: InvestmentDecimal | null
}

export type InvestmentIndicatorSnapshot = {
    instrumentId: number
    priceType: InvestmentPriceType
    interval: InvestmentBarInterval
    dataStartTime: string
    dataEndTime: string
    dataAsOf: string
    inputHash: string
    revisions: number[]
    points: InvestmentIndicatorPoint[]
}

export type InvestmentPriceType = 'MARKET' | 'MARK' | 'INDEX'
export type InvestmentBarInterval = 'M1' | 'M5' | 'M15' | 'M30' | 'H1' | 'H4' | 'D1'

export type InvestmentCandleQuery = {
    instrumentId: number
    priceType: InvestmentPriceType
    interval: InvestmentBarInterval
    from: string
    to: string
    dataAsOf?: string
    limit?: number
}

export type {InvestmentCandleResponse, InvestmentInstrumentSummaryResponse}
