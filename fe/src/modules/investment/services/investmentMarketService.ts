import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {PageResult} from '../../../types/api'
import type {
    AddInvestmentWatchlistItemRequest,
    InvestmentInstrumentSummaryResponse,
    InvestmentMarketListQuery,
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
