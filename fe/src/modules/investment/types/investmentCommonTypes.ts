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
    id: number
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
    capabilities: string[]
}

export type InvestmentCandleResponse = {
    openTime: string
    closeTime: string
    openPrice: InvestmentDecimal
    highPrice: InvestmentDecimal
    lowPrice: InvestmentDecimal
    closePrice: InvestmentDecimal
    baseVolume: InvestmentDecimal
    quoteVolume: InvestmentDecimal
    closed: boolean
    revision: number
    dataAsOf: string
}
