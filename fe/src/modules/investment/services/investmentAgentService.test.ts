import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    getInvestmentAgentRun,
    listInvestmentAgentRuns,
    submitInvestmentAgentRun,
} from './investmentAgentService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentAgentService', () => {
    beforeEach(() => vi.clearAllMocks())

    test('keeps the workspace scope and fixed run fields in list and submit calls', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})
        const request = {runType: 'AUTO_TRADE' as const, accountId: 21, instrumentIds: [11, 12]}

        await listInvestmentAgentRuns(7, 2, 20)
        await submitInvestmentAgentRun(7, request)

        expect(requestJson).toHaveBeenNthCalledWith(
            1, '/api/investment/workspaces/7/agent-runs?page=2&size=20',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2, '/api/investment/workspaces/7/agent-runs', {method: 'POST', body: request},
        )
    })

    test('loads one owner-scoped run detail', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})

        await getInvestmentAgentRun(41)

        expect(requestJson).toHaveBeenCalledWith('/api/investment/agent-runs/41')
    })
})
