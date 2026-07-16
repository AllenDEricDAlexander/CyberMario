import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    cancelInvestmentPaperOrder,
    createInvestmentPaperAccount,
    listInvestmentPaperFills,
    listInvestmentPaperOrders,
    updateInvestmentPaperAccountSwitches,
} from './investmentPortfolioService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentPortfolioService', () => {
    beforeEach(() => vi.clearAllMocks())

    test('keeps private account scope in every paper write', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})
        const riskProfile = {
            maxLeverage: '10', maxOrderNotional: '1000', maxPositionNotional: '5000',
            maxGrossExposureNotional: '10000', maxOpenPositions: 5, maxDailyLossAmount: '500',
            maxDrawdownRatio: '0.2', maxOrdersPerHour: 60, cooldownSeconds: 0,
            maxMarketDataAgeSeconds: 30, maxSlippageBps: '20',
        }

        await createInvestmentPaperAccount(7, {name: '模拟账户', initialEquity: '10000', riskProfile})
        await updateInvestmentPaperAccountSwitches(21, {
            tradingEnabled: true, agentAutoTradeEnabled: false, version: 3,
        })
        await cancelInvestmentPaperOrder(41)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/investment/workspaces/7/paper-accounts', {
            method: 'POST', body: {name: '模拟账户', initialEquity: '10000', riskProfile},
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/investment/paper-accounts/21/switches', {
            method: 'PATCH',
            body: {tradingEnabled: true, agentAutoTradeEnabled: false, version: 3},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/investment/paper-orders/41/cancel', {method: 'POST'})
    })

    test('builds bounded server paging and required marker dimensions', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: [], page: 1, size: 20, total: 0, totalPages: 0})

        await listInvestmentPaperOrders(21, 2, 20)
        await listInvestmentPaperFills(
            21, 501, '2026-07-01T00:00:00.000Z', '2026-07-17T00:00:00.000Z', 3, 100,
        )

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/investment/paper-accounts/21/orders?page=2&size=20')
        expect(requestJson).toHaveBeenNthCalledWith(2,
            '/api/investment/paper-accounts/21/fills?instrumentId=501&from=2026-07-01T00%3A00%3A00.000Z&to=2026-07-17T00%3A00%3A00.000Z&page=3&size=100')
    })
})
