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
    priority: number
    attempts: number
    maxAttempts: number
    availableAt: string
    lastErrorCode: string | null
    lastErrorMessage: string | null
    createdAt: string
    updatedAt: string
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
