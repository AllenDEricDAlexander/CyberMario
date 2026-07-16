import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {PageResult} from '../../../types/api'
import type {
    AddInvestmentWatchlistItemRequest,
    InvestmentCandleQuery,
    InvestmentCandleResponse,
    InvestmentFundingRateResponse,
    InvestmentIndicatorSnapshot,
    InvestmentInstrumentDetailResponse,
    InvestmentInstrumentSummaryResponse,
    InvestmentMarketListQuery,
    InvestmentPositionTierResponse,
    InvestmentQuoteResponse,
    InvestmentWatchlistItemResponse,
    InvestmentWatchlistResponse,
} from '../types/investmentMarketTypes'

export function listInvestmentInstruments(query: InvestmentMarketListQuery) {
    const search = buildSearchParams({
        page: query.page,
        size: query.size,
        status: query.status,
        sort: query.sort,
    })
    return requestJson<PageResult<InvestmentInstrumentSummaryResponse>>(
        `/api/investment/market/instruments?${search}`,
    )
}

export function listInvestmentWatchlists(workspaceId: number, page = 1, size = 100) {
    const search = buildSearchParams({page, size})
    return requestJson<PageResult<InvestmentWatchlistResponse>>(
        `/api/investment/workspaces/${workspaceId}/watchlists?${search}`,
    )
}

export function addInvestmentWatchlistItem(
    workspaceId: number,
    watchlistId: number,
    request: AddInvestmentWatchlistItemRequest,
) {
    const search = buildSearchParams({workspaceId})
    return requestJson<InvestmentWatchlistItemResponse>(
        `/api/investment/watchlists/${watchlistId}/items?${search}`,
        {method: 'POST', body: request},
    )
}

export function getInvestmentInstrument(instrumentId: number) {
    return requestJson<InvestmentInstrumentDetailResponse>(
        `/api/investment/market/instruments/${instrumentId}`,
    )
}

export function getInvestmentQuote(instrumentId: number) {
    return requestJson<InvestmentQuoteResponse>(
        `/api/investment/market/instruments/${instrumentId}/quote`,
    )
}

export function listInvestmentCandles(query: InvestmentCandleQuery) {
    const search = buildSearchParams({
        priceType: query.priceType,
        interval: query.interval,
        from: query.from,
        to: query.to,
        dataAsOf: query.dataAsOf,
        limit: query.limit ?? 2_000,
    })
    return requestJson<InvestmentCandleResponse[]>(
        `/api/investment/market/instruments/${query.instrumentId}/candles?${search}`,
    )
}

export function listInvestmentFundingRates(
    instrumentId: number,
    from: string,
    to: string,
    dataAsOf?: string,
) {
    const search = buildSearchParams({from, to, dataAsOf, page: 1, size: 200})
    return requestJson<PageResult<InvestmentFundingRateResponse>>(
        `/api/investment/market/instruments/${instrumentId}/funding-rates?${search}`,
    )
}

export function listInvestmentPositionTiers(instrumentId: number, dataAsOf?: string) {
    const search = buildSearchParams({dataAsOf})
    const suffix = search ? `?${search}` : ''
    return requestJson<InvestmentPositionTierResponse[]>(
        `/api/investment/market/instruments/${instrumentId}/position-tiers${suffix}`,
    )
}

export function getInvestmentIndicators(query: InvestmentCandleQuery) {
    const search = buildSearchParams({
        priceType: query.priceType,
        interval: query.interval,
        from: query.from,
        to: query.to,
        dataAsOf: query.dataAsOf,
    })
    return requestJson<InvestmentIndicatorSnapshot>(
        `/api/investment/market/instruments/${query.instrumentId}/indicators?${search}`,
    )
}
