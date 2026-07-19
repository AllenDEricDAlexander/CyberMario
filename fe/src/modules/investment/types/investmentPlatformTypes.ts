import type {PageResult} from '../../../types/api'

export type InvestmentPlatformSubscriptionResponse = {
    sourceCode: string
    productType: string
    symbol: string
    status: string
    capabilities: string[]
    priceTypes: string[]
    intervals: string[]
    refreshIntervals: Record<string, string>
    backfillWindows: Record<string, string>
    retention: Record<string, string>
}

export type InvestmentPlatformJobResponse = {
    id: number
    jobType: string
    status: string
    triggerSource: 'SCHEDULED' | 'MANUAL'
    sourceCode: string | null
    symbol: string | null
    capability: string | null
    priceType: string | null
    interval: string | null
    startInclusive: string | null
    endExclusive: string | null
    priority: number
    attempts: number
    maxAttempts: number
    availableAt: string
    startedAt: string | null
    finishedAt: string | null
    fetchedCount: number | null
    writtenCount: number | null
    lastErrorCode: string | null
    lastErrorMessage: string | null
    createdAt: string
    updatedAt: string
}

export type InvestmentMarketDataPullSymbol = 'BTCUSDT' | 'SOLUSDT'

export type InvestmentMarketDataPullCapability = 'MARKET_CANDLE' | 'FUNDING_RATE'

export type InvestmentMarketDataPullInterval = 'M1' | 'D1'

export type InvestmentMarketDataPullRequest = {
    symbol: InvestmentMarketDataPullSymbol
    capability: InvestmentMarketDataPullCapability
    interval: InvestmentMarketDataPullInterval | null
    startInclusive: string
    endExclusive: string
}

export type InvestmentMarketDataPullResponse = {
    jobId: number
    jobType: 'BAR_BACKFILL' | 'FUNDING_RATE_BACKFILL'
    status: 'PENDING'
    createdAt: string
}

export type InvestmentDataQualityIssueResponse = {
    id: number
    instrumentId: number
    dataType: string
    priceType: string | null
    interval: string | null
    pointTime: string
    issueCode: string
    severity: string
    resolutionStatus: string
    resolvedAt: string | null
    createdAt: string
}

export type InvestmentPlatformPage<T> = PageResult<T>

export type PlatformJobQuery = {
    status?: string
    jobType?: string
    page: number
    size: number
}

export type PlatformQualityIssueQuery = {
    resolutionStatus?: string
    severity?: string
    page: number
    size: number
}
