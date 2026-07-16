import {render, screen, waitFor} from '@testing-library/react'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentReportDetailResponse} from '../types/investmentResearchTypes'
import {InvestmentReportDrawer} from './InvestmentReportDrawer'

const mocks = vi.hoisted(() => ({get: vi.fn()}))

vi.mock('../services/investmentResearchService', () => ({
    getInvestmentReport: mocks.get,
}))

describe('InvestmentReportDrawer', () => {
    beforeEach(() => {
        mocks.get.mockReset()
        mocks.get.mockResolvedValue(readyDetail(19, 'BTC 合约分析'))
    })

    test('loads detail by report id and exposes immutable version, cutoff, safe markdown and evidence', async () => {
        renderDrawer(19)

        expect(await screen.findByText('BTC 合约分析')).toBeTruthy()
        expect(screen.getByText('v3')).toBeTruthy()
        expect(screen.getAllByText('2026-07-16T00:00:00.000Z').length).toBeGreaterThan(0)
        expect(screen.getByText('source:1/instrument:11/MARK/H1')).toBeTruthy()
        expect(screen.getByText('a'.repeat(64))).toBeTruthy()
        expect(screen.getByText('可信正文')).toBeTruthy()
        expect(document.querySelector('script')).toBeNull()
        expect(mocks.get).toHaveBeenCalledWith(19)
    })

    test('shows pending and failed states without pretending content is ready', async () => {
        mocks.get.mockResolvedValueOnce(statusDetail(20, 'PENDING', '等待生成'))
        const view = renderDrawer(20)
        expect(await screen.findByText('报告已进入生成队列')).toBeTruthy()
        expect(screen.queryByLabelText('报告正文')).toBeNull()

        mocks.get.mockResolvedValueOnce(statusDetail(21, 'FAILED', '上游数据不足'))
        view.rerender(<App><InvestmentReportDrawer onClose={vi.fn()} open reportId={21}/></App>)

        expect(await screen.findByText('报告生成失败')).toBeTruthy()
        expect(screen.getAllByText('上游数据不足').length).toBeGreaterThan(0)
        expect(screen.queryByLabelText('报告正文')).toBeNull()
    })

    test('suppresses stale detail when report id changes', async () => {
        const first = deferred<InvestmentReportDetailResponse>()
        mocks.get.mockReturnValueOnce(first.promise)
            .mockResolvedValueOnce(readyDetail(22, '新报告'))
        const view = renderDrawer(19)
        await waitFor(() => expect(mocks.get).toHaveBeenCalledWith(19))

        view.rerender(<App><InvestmentReportDrawer onClose={vi.fn()} open reportId={22}/></App>)
        expect(await screen.findByText('新报告')).toBeTruthy()

        first.resolve(readyDetail(19, '旧报告'))
        await Promise.resolve()
        expect(screen.queryByText('旧报告')).toBeNull()
        expect(screen.getByText('新报告')).toBeTruthy()
    })
})

function renderDrawer(reportId: number) {
    return render(<App><InvestmentReportDrawer onClose={vi.fn()} open reportId={reportId}/></App>)
}

function readyDetail(reportId: number, title: string): InvestmentReportDetailResponse {
    return {
        report: {
            reportId,
            workspaceId: 7,
            instrumentId: 11,
            reportType: 'INSTRUMENT_ANALYSIS',
            title,
            summary: '冻结指标摘要',
            status: 'READY',
            reportVersion: 3,
            dataAsOf: '2026-07-16T00:00:00.000Z',
            createdAt: '2026-07-16T00:01:00.000Z',
        },
        sourceType: 'USER',
        contentMarkdown: '# 可信正文\n\n<script>window.evil = true</script>\n\n**只渲染安全 Markdown**',
        metricsJson: '{"rsi14":"55.000000000000000001"}',
        evidence: [{
            evidenceId: 31,
            evidenceType: 'CLOSED_CANDLE_INDICATORS',
            sourceId: 1,
            instrumentId: 11,
            dataStartTime: '2026-07-15T00:00:00.000Z',
            dataEndTime: '2026-07-15T23:00:00.000Z',
            dataAsOf: '2026-07-16T00:00:00.000Z',
            sourceReference: 'source:1/instrument:11/MARK/H1',
            payloadHash: 'a'.repeat(64),
            metadataJson: '{"revisions":[1,2]}',
            createdAt: '2026-07-16T00:01:00.000Z',
        }],
    }
}

function statusDetail(
    reportId: number,
    status: 'PENDING' | 'FAILED',
    summary: string,
): InvestmentReportDetailResponse {
    const detail = readyDetail(reportId, status === 'PENDING' ? '等待生成' : '失败报告')
    detail.report.status = status
    detail.report.summary = summary
    detail.contentMarkdown = null
    detail.evidence = []
    return detail
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
