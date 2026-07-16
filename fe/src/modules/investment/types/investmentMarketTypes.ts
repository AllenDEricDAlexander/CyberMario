import type {InvestmentInstrumentSummaryResponse} from './investmentCommonTypes'

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

export type {InvestmentInstrumentSummaryResponse}
