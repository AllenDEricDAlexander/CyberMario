import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentReportPage, InvestmentReportSummaryResponse} from '../types/investmentResearchTypes'
import {InvestmentInstrumentReportsPanel} from './InvestmentInstrumentReportsPanel'

const mocks = vi.hoisted(() => ({list: vi.fn()}))

vi.mock('../services/investmentResearchService', () => ({listInvestmentReports: mocks.list}))
vi.mock('../research/InvestmentReportDrawer', () => ({
    InvestmentReportDrawer: ({open, reportId}: {open: boolean; reportId?: number}) => (
        open ? <div>报告证据抽屉 #{reportId}</div> : null
    ),
}))

describe('InvestmentInstrumentReportsPanel', () => {
    beforeEach(() => {
        mocks.list.mockReset().mockResolvedValue(page([
            report(11, 71, 'INSTRUMENT_ANALYSIS', 'READY', 2),
            report(11, 72, 'AGENT_ANALYSIS', 'PENDING', 1),
            report(12, 73, 'AGENT_ANALYSIS', 'READY', 1),
        ]))
    })

    test('shows fixed traditional and Agent versions with explicit evidence links', async () => {
        const user = userEvent.setup()
        renderPanel(7, 11)

        expect(await screen.findByText('传统报告 #71')).toBeTruthy()
        expect(screen.getByText('Agent 报告 #72')).toBeTruthy()
        expect(screen.queryByText('Agent 报告 #73')).toBeNull()
        expect(screen.getByText('v2')).toBeTruthy()
        expect(screen.getAllByText('2026-07-17T00:00:00Z')).toHaveLength(2)
        expect(screen.getAllByRole('button', {name: '查看报告与证据'})).toHaveLength(2)

        await user.click(screen.getAllByRole('button', {name: '查看报告与证据'})[0])
        expect(screen.getByText('报告证据抽屉 #71')).toBeTruthy()
        expect(mocks.list).toHaveBeenCalledWith(7, {page: 1, size: 100})
    })

    test('distinguishes traditional and Agent empty states and requires a private workspace', async () => {
        mocks.list.mockResolvedValue(page([]))
        const view = renderPanel(7, 11)

        expect(await screen.findByText('当前合约暂无传统分析报告')).toBeTruthy()
        expect(screen.getByText('当前合约暂无 Agent 分析报告')).toBeTruthy()

        view.rerender(<App><InvestmentInstrumentReportsPanel instrumentId={11}/></App>)
        expect(screen.getByText('选择私人投资工作区后可查看固定版本的传统与 Agent 报告')).toBeTruthy()
    })

    test('shows a retryable report error state', async () => {
        mocks.list.mockRejectedValue(new Error('report service unavailable'))
        renderPanel(7, 11)

        expect(await screen.findByText('report service unavailable')).toBeTruthy()
        expect(screen.getByRole('button', {name: /重\s*试/})).toBeTruthy()
    })

    test('suppresses an old workspace response after the private scope changes', async () => {
        const old = deferred<InvestmentReportPage>()
        mocks.list.mockReturnValueOnce(old.promise)
            .mockResolvedValueOnce(page([report(11, 82, 'AGENT_ANALYSIS', 'READY', 1)]))
        const view = renderPanel(7, 11)
        view.rerender(<App><InvestmentInstrumentReportsPanel instrumentId={11} workspaceId={8}/></App>)

        expect(await screen.findByText('Agent 报告 #82')).toBeTruthy()
        old.resolve(page([report(11, 81, 'AGENT_ANALYSIS', 'READY', 1)]))
        await Promise.resolve()

        expect(screen.queryByText('Agent 报告 #81')).toBeNull()
        expect(screen.getByText('Agent 报告 #82')).toBeTruthy()
    })
})

function renderPanel(workspaceId?: number, instrumentId = 11) {
    return render(<App><InvestmentInstrumentReportsPanel instrumentId={instrumentId} workspaceId={workspaceId}/></App>)
}

function page(records: InvestmentReportSummaryResponse[]): InvestmentReportPage {
    return {records, page: 1, size: 100, total: records.length, totalPages: records.length ? 1 : 0}
}

function report(
    instrumentId: number,
    reportId: number,
    reportType: InvestmentReportSummaryResponse['reportType'],
    status: InvestmentReportSummaryResponse['status'],
    reportVersion: number,
): InvestmentReportSummaryResponse {
    return {
        reportId, workspaceId: 7, instrumentId, reportType,
        title: `${reportType === 'AGENT_ANALYSIS' ? 'Agent ' : '传统'}报告 #${reportId}`,
        summary: null, status, reportVersion, dataAsOf: '2026-07-17T00:00:00Z',
        createdAt: '2026-07-17T00:01:00Z',
    }
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
