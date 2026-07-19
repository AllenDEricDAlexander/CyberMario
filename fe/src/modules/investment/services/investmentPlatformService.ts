import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {
    InvestmentDataQualityIssueResponse,
    InvestmentMarketDataPullRequest,
    InvestmentMarketDataPullResponse,
    InvestmentPlatformJobResponse,
    InvestmentPlatformPage,
    InvestmentPlatformSubscriptionResponse,
    PlatformJobQuery,
    PlatformQualityIssueQuery,
} from '../types/investmentPlatformTypes'

export function listInvestmentPlatformSubscriptions() {
    return requestJson<InvestmentPlatformSubscriptionResponse[]>('/api/investment/platform/subscriptions')
}

export function listInvestmentPlatformJobs(query: PlatformJobQuery) {
    const search = buildSearchParams(query)
    return requestJson<InvestmentPlatformPage<InvestmentPlatformJobResponse>>(
        `/api/investment/platform/jobs?${search}`,
    )
}

export function createInvestmentMarketDataPull(request: InvestmentMarketDataPullRequest) {
    return requestJson<InvestmentMarketDataPullResponse>('/api/investment/platform/market-data/pulls', {
        method: 'POST',
        body: request,
    })
}

export function retryInvestmentPlatformJob(jobId: number) {
    return requestJson<void>(`/api/investment/platform/jobs/${jobId}/retry`, {method: 'POST'})
}

export function listInvestmentDataQualityIssues(query: PlatformQualityIssueQuery) {
    const search = buildSearchParams(query)
    return requestJson<InvestmentPlatformPage<InvestmentDataQualityIssueResponse>>(
        `/api/investment/platform/data-quality-issues?${search}`,
    )
}

export function resolveInvestmentDataQualityIssue(issueId: number) {
    return requestJson<void>(`/api/investment/platform/data-quality-issues/${issueId}/resolve`, {method: 'POST'})
}
