import type {PageResult} from '../../../types/api'

export type InvestmentDecimal = string
export type InvestmentLoadState = 'idle' | 'loading' | 'ready' | 'empty' | 'forbidden' | 'error'
export type InvestmentPageResult<T> = PageResult<T>

export type InvestmentFreshnessResponse = {
    status: 'FRESH' | 'STALE' | 'UNAVAILABLE'
    observedAt: string | null
    ageSeconds: number
}

export type InvestmentInstrumentSummaryResponse = {
    instrumentId: number
    venueCode: string
    symbol: string
    baseAsset: string
    quoteAsset: string
    status: string
    lastPrice: InvestmentDecimal | null
    markPrice: InvestmentDecimal | null
    change24h: InvestmentDecimal | null
    dataAsOf: string
    freshness: InvestmentFreshnessResponse
    availableCapabilities: string[]
}

export type InvestmentCandleResponse = {
    openTime: string
    closeTime: string
    open: InvestmentDecimal
    high: InvestmentDecimal
    low: InvestmentDecimal
    close: InvestmentDecimal
    baseVolume: InvestmentDecimal
    quoteVolume: InvestmentDecimal
    isClosed: boolean
    revision: number
    dataAsOf: string
}
