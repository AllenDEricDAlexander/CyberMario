import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    createInvestmentMarketDataPull,
    listInvestmentDataQualityIssues,
    listInvestmentPlatformJobs,
    listInvestmentPlatformSubscriptions,
    resolveInvestmentDataQualityIssue,
    retryInvestmentPlatformJob,
} from './investmentPlatformService'

vi.mock('../../../services/request', () => ({requestJson: vi.fn()}))

describe('investmentPlatformService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('uses only the frozen read-only platform subscription route', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue([])

        await listInvestmentPlatformSubscriptions()

        expect(requestJson).toHaveBeenCalledWith('/api/investment/platform/subscriptions')
    })

    test('preserves bounded job and quality pagination filters', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({records: [], page: 2, size: 20, total: 0, totalPages: 0})

        await listInvestmentPlatformJobs({status: 'FAILED', jobType: 'QUOTE_REFRESH', page: 2, size: 20})
        await listInvestmentDataQualityIssues({
            resolutionStatus: 'OPEN',
            severity: 'ERROR',
            page: 3,
            size: 20,
        })

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/investment/platform/jobs?status=FAILED&jobType=QUOTE_REFRESH&page=2&size=20',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/investment/platform/data-quality-issues?resolutionStatus=OPEN&severity=ERROR&page=3&size=20',
        )
    })

    test('posts a manual market-data pull with the frozen request body', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue({
            jobId: 12,
            jobType: 'BAR_BACKFILL',
            status: 'PENDING',
            createdAt: '2026-07-19T02:00:00Z',
        })
        const request = {
            symbol: 'BTCUSDT' as const,
            capability: 'MARKET_CANDLE' as const,
            interval: 'M1' as const,
            startInclusive: '2026-07-18T00:00:00.000Z',
            endExclusive: '2026-07-19T00:00:00.000Z',
        }

        await createInvestmentMarketDataPull(request)

        expect(requestJson).toHaveBeenCalledWith(
            '/api/investment/platform/market-data/pulls',
            {method: 'POST', body: request},
        )
    })

    test('keeps platform mutations outside private workspace routes', async () => {
        const {requestJson} = await import('../../../services/request')
        vi.mocked(requestJson).mockResolvedValue(undefined)

        await retryInvestmentPlatformJob(7)
        await resolveInvestmentDataQualityIssue(9)

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/investment/platform/jobs/7/retry',
            {method: 'POST'},
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/investment/platform/data-quality-issues/9/resolve',
            {method: 'POST'},
        )
        for (const [url] of vi.mocked(requestJson).mock.calls) {
            expect(url).not.toContain('/workspaces')
            expect(url).not.toContain('/paper-accounts')
        }
    })
})
