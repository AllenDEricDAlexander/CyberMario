import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    addInvestmentWatchlistItem,
    listInvestmentInstruments,
    listInvestmentWatchlists,
} from './investmentMarketService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentMarketService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('sends pagination, status and the fixed server sort contract', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: [], page: 2, size: 20, total: 0, totalPages: 0})

        await listInvestmentInstruments({page: 2, size: 20, status: 'ACTIVE', sort: 'SYMBOL_DESC'})

        expect(requestJson).toHaveBeenCalledWith(
            '/api/investment/market/instruments?page=2&size=20&status=ACTIVE&sort=SYMBOL_DESC',
        )
    })

    test('omits an empty status rather than widening the API with client symbols', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: [], page: 1, size: 20, total: 0, totalPages: 0})

        await listInvestmentInstruments({page: 1, size: 20, sort: 'SYMBOL_ASC'})

        expect(requestJson).toHaveBeenCalledWith(
            '/api/investment/market/instruments?page=1&size=20&sort=SYMBOL_ASC',
        )
    })

    test('keeps watchlist reads and writes bound to workspace and internal instrument ids', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: [], page: 1, size: 100, total: 0, totalPages: 0})

        await listInvestmentWatchlists(7)
        await addInvestmentWatchlistItem(7, 9, {instrumentId: 11, note: null})

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/investment/workspaces/7/watchlists?page=1&size=100',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/investment/watchlists/9/items?workspaceId=7',
            {method: 'POST', body: {instrumentId: 11, note: null}},
        )
    })
})
