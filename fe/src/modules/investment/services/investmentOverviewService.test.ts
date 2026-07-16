import {beforeEach, describe, expect, test, vi} from 'vitest'
import {getInvestmentOverview} from './investmentOverviewService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentOverviewService', () => {
    beforeEach(() => vi.clearAllMocks())

    test('loads the single owner-scoped overview snapshot', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})

        await getInvestmentOverview(7)

        expect(requestJson).toHaveBeenCalledWith('/api/investment/workspaces/7/overview')
    })
})
