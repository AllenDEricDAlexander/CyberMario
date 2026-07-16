import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createInvestmentReport,
    getInvestmentReport,
    listInvestmentReports,
} from './investmentResearchService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentResearchService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('keeps report lists owner-scoped, filtered and server paginated', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: [], page: 2, size: 20, total: 0, totalPages: 0})

        await listInvestmentReports(7, {reportType: 'INSTRUMENT_ANALYSIS', page: 2, size: 20})

        expect(requestJson).toHaveBeenCalledWith(
            '/api/investment/workspaces/7/reports?reportType=INSTRUMENT_ANALYSIS&page=2&size=20',
        )
    })

    test('queues only the bounded server report request', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})
        const request = {
            reportType: 'INSTRUMENT_ANALYSIS' as const,
            instrumentId: 11,
            priceType: 'MARK' as const,
            interval: 'H1' as const,
            fromInclusive: '2026-07-15T00:00:00.000Z',
            toExclusive: '2026-07-16T00:00:00.000Z',
        }

        await createInvestmentReport(7, request)

        expect(requestJson).toHaveBeenCalledWith(
            '/api/investment/workspaces/7/reports',
            {method: 'POST', body: request},
        )
    })

    test('loads report detail by immutable report id outside the list endpoint', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({})

        await getInvestmentReport(19)

        expect(requestJson).toHaveBeenCalledWith('/api/investment/reports/19')
    })
})
