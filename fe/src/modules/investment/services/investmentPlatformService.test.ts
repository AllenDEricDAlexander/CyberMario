import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
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

    test('exposes exactly the two approved platform mutations', async () => {
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
        expect(vi.mocked(requestJson).mock.calls.flat().join(' ')).not.toContain('/workspaces')
        expect(vi.mocked(requestJson).mock.calls.flat().join(' ')).not.toContain('/paper-accounts')
    })
})
