import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    getInvestmentBacktest,
    getInvestmentBacktestEquity,
    listInvestmentBacktestEvents,
    listInvestmentBacktests,
    listInvestmentBacktestTrades,
    listInvestmentStrategies,
    submitInvestmentBacktest,
} from './investmentQuantService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentQuantService', () => {
    beforeEach(() => vi.clearAllMocks())

    test('uses the frozen strategy and owner-scoped backtest list contracts', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})

        await listInvestmentStrategies()
        await listInvestmentBacktests(7, 2, 20)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/investment/strategies')
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/investment/workspaces/7/backtests?page=2&size=20',
        )
    })

    test('submits exactly the four user-controlled backtest fields', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})
        const request = {
            strategyCode: 'EMA_CROSS',
            instrumentIds: [11, 12],
            startTime: '2026-07-01T00:00:00.000Z',
            endTime: '2026-07-02T00:00:00.000Z',
        }

        await submitInvestmentBacktest(7, request)

        expect(requestJson).toHaveBeenCalledWith(
            '/api/investment/workspaces/7/backtests',
            {method: 'POST', body: request},
        )
        expect(Object.keys(request)).toEqual(['strategyCode', 'instrumentIds', 'startTime', 'endTime'])
    })

    test('loads immutable run detail and server-paged result views by run id', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})

        await getInvestmentBacktest(51)
        await listInvestmentBacktestTrades(51, 1, 100)
        await listInvestmentBacktestEvents(51, 2, 50)
        await getInvestmentBacktestEquity(51)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/investment/backtests/51')
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/investment/backtests/51/trades?page=1&size=100',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            3,
            '/api/investment/backtests/51/events?page=2&size=50',
        )
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/investment/backtests/51/equity')
    })
})
