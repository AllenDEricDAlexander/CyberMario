import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    addInvestmentWatchlistItem,
    getInvestmentIndicators,
    getInvestmentInstrument,
    getInvestmentQuote,
    listInvestmentCandles,
    listInvestmentFundingRates,
    listInvestmentInstruments,
    listInvestmentPositionTiers,
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

    test('uses the public instrument detail and quote contracts', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})

        await getInvestmentInstrument(11)
        await getInvestmentQuote(11)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/investment/market/instruments/11')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/investment/market/instruments/11/quote')
    })

    test('freezes candle and indicator requests to the same internal dimensions and cutoff', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue([])
        const query = {
            instrumentId: 11,
            priceType: 'MARK' as const,
            interval: 'H1' as const,
            from: '2026-07-15T00:00:00.000Z',
            to: '2026-07-16T00:00:00.000Z',
            dataAsOf: '2026-07-16T00:00:01.000Z',
            limit: 900,
        }

        await listInvestmentCandles(query)
        await getInvestmentIndicators(query)

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/investment/market/instruments/11/candles?priceType=MARK&interval=H1&from=2026-07-15T00%3A00%3A00.000Z&to=2026-07-16T00%3A00%3A00.000Z&dataAsOf=2026-07-16T00%3A00%3A01.000Z&limit=900',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/investment/market/instruments/11/indicators?priceType=MARK&interval=H1&from=2026-07-15T00%3A00%3A00.000Z&to=2026-07-16T00%3A00%3A00.000Z&dataAsOf=2026-07-16T00%3A00%3A01.000Z',
        )
    })

    test('keeps funding and position-tier reads on the selected instrument and cutoff', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: []})

        await listInvestmentFundingRates(
            11,
            '2026-06-16T00:00:00.000Z',
            '2026-07-16T00:00:00.000Z',
            '2026-07-16T00:00:01.000Z',
        )
        await listInvestmentPositionTiers(11, '2026-07-16T00:00:01.000Z')

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/investment/market/instruments/11/funding-rates?from=2026-06-16T00%3A00%3A00.000Z&to=2026-07-16T00%3A00%3A00.000Z&dataAsOf=2026-07-16T00%3A00%3A01.000Z&page=1&size=200',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/investment/market/instruments/11/position-tiers?dataAsOf=2026-07-16T00%3A00%3A01.000Z',
        )
    })
})
