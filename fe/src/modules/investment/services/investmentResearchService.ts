import {requestJson} from '../../../services/request'
import {buildSearchParams} from '../../../services/urlSearch'
import type {
    CreateInvestmentReportRequest,
    CreateInvestmentReportResponse,
    InvestmentReportDetailResponse,
    InvestmentReportListQuery,
    InvestmentReportPage,
} from '../types/investmentResearchTypes'

export function listInvestmentReports(workspaceId: number, query: InvestmentReportListQuery) {
    const search = buildSearchParams({
        reportType: query.reportType,
        page: query.page,
        size: query.size,
    })
    return requestJson<InvestmentReportPage>(
        `/api/investment/workspaces/${workspaceId}/reports?${search}`,
    )
}

export function createInvestmentReport(workspaceId: number, request: CreateInvestmentReportRequest) {
    return requestJson<CreateInvestmentReportResponse>(
        `/api/investment/workspaces/${workspaceId}/reports`,
        {method: 'POST', body: request},
    )
}

export function getInvestmentReport(reportId: number) {
    return requestJson<InvestmentReportDetailResponse>(`/api/investment/reports/${reportId}`)
}
