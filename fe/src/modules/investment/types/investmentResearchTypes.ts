import type {PageResult} from '../../../types/api'
import type {InvestmentBarInterval, InvestmentPriceType} from './investmentMarketTypes'

export type InvestmentReportType =
    | 'MARKET_OVERVIEW'
    | 'INSTRUMENT_ANALYSIS'
    | 'STRATEGY_ANALYSIS'
    | 'BACKTEST_REPORT'
    | 'PORTFOLIO_REPORT'
    | 'AGENT_ANALYSIS'

export type InvestmentReportStatus = 'PENDING' | 'READY' | 'FAILED'

export type InvestmentReportSummaryResponse = {
    reportId: number
    workspaceId: number
    instrumentId: number | null
    reportType: InvestmentReportType
    title: string
    summary: string | null
    status: InvestmentReportStatus
    reportVersion: number
    dataAsOf: string
    createdAt: string
}

export type InvestmentReportEvidenceResponse = {
    evidenceId: number
    evidenceType: string
    sourceId: number
    instrumentId: number | null
    dataStartTime: string
    dataEndTime: string
    dataAsOf: string
    sourceReference: string
    payloadHash: string
    metadataJson: string
    createdAt: string
}

export type InvestmentReportDetailResponse = {
    report: InvestmentReportSummaryResponse
    sourceType: string
    contentMarkdown: string | null
    metricsJson: string
    evidence: InvestmentReportEvidenceResponse[]
}

export type CreateInvestmentReportRequest = {
    reportType: InvestmentReportType
    instrumentId?: number
    priceType?: InvestmentPriceType
    interval?: InvestmentBarInterval
    fromInclusive?: string
    toExclusive?: string
}

export type CreateInvestmentReportResponse = {
    report: InvestmentReportSummaryResponse
    jobId: number
}

export type InvestmentReportListQuery = {
    reportType?: InvestmentReportType
    page: number
    size: number
}

export type InvestmentReportPage = PageResult<InvestmentReportSummaryResponse>
