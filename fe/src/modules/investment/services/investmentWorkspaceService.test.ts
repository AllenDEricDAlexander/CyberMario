import {beforeEach, describe, expect, test, vi} from 'vitest'
import {createInvestmentWorkspace, listInvestmentWorkspaces} from './investmentWorkspaceService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentWorkspaceService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('reads the unwrapped page envelope with bounded paging', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: [], page: 1, size: 100, total: 0, totalPages: 0})

        await expect(listInvestmentWorkspaces()).resolves.toEqual({
            records: [],
            page: 1,
            size: 100,
            total: 0,
            totalPages: 0,
        })
        expect(requestJson).toHaveBeenCalledWith('/api/investment/workspaces?page=1&size=100')
    })

    test('creates a workspace without adding client-owned defaults', async () => {
        const {requestJson} = await import('../../../services/request')
        const response = {
            id: 7,
            name: '合约研究',
            baseCurrency: 'USDT',
            timezone: 'UTC',
            status: 'ACTIVE',
            createdAt: '2026-07-16T00:00:00Z',
        }
        vi.mocked(requestJson).mockResolvedValue(response)

        await expect(createInvestmentWorkspace({name: '合约研究'})).resolves.toBe(response)
        expect(requestJson).toHaveBeenCalledWith('/api/investment/workspaces', {
            method: 'POST',
            body: {name: '合约研究'},
        })
    })
})
