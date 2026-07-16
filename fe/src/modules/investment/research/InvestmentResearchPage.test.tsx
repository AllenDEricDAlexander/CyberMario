import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {MemoryRouter} from 'react-router'
import {ApiRequestError} from '../../../types/api'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {
    CreateInvestmentReportResponse,
    InvestmentReportPage,
    InvestmentReportSummaryResponse,
} from '../types/investmentResearchTypes'
import InvestmentResearchPage from './InvestmentResearchPage'

const mocks = vi.hoisted(() => ({
    list: vi.fn(),
    create: vi.fn(),
    workspace: {id: 7, name: '研究工作区'},
    canCreate: true,
}))

vi.mock('../services/investmentResearchService', () => ({
    listInvestmentReports: mocks.list,
    createInvestmentReport: mocks.create,
}))

vi.mock('../hooks/useInvestmentWorkspace', () => ({
    useInvestmentWorkspace: () => ({currentWorkspace: mocks.workspace}),
}))

vi.mock('../../auth/authStore', () => ({
    useAuth: () => ({roleCodes: [], hasPermission: vi.fn(), hasAnyButton: vi.fn()}),
    canUseRbacButton: () => mocks.canCreate,
}))

vi.mock('./InvestmentReportDrawer', () => ({
    InvestmentReportDrawer: ({open, reportId}: {open: boolean; reportId?: number}) => (
        open ? <div aria-label="报告详情抽屉">report {reportId}</div> : null
    ),
}))

describe('InvestmentResearchPage', () => {
    beforeEach(() => {
        mocks.workspace = {id: 7, name: '研究工作区'}
        mocks.canCreate = true
        mocks.list.mockReset()
        mocks.create.mockReset()
        mocks.list.mockResolvedValue(page([]))
    })

    test('owns the paged list and displays queued, terminal and failed versions with their cutoff', async () => {
        mocks.list.mockResolvedValue(page([
            report(1, '队列报告', 'PENDING', 1),
            report(2, '完成报告', 'READY', 4),
            report(3, '失败报告', 'FAILED', 2, '上游数据不足'),
        ]))
        renderPage()

        expect(await screen.findByText('队列报告')).toBeTruthy()
        expect(screen.getByText('完成报告')).toBeTruthy()
        expect(screen.getByText('失败报告')).toBeTruthy()
        expect(screen.getByText('v4')).toBeTruthy()
        expect(screen.getAllByText('2026-07-16T00:00:00.000Z').length).toBeGreaterThan(0)
        expect(screen.getByText('上游数据不足')).toBeTruthy()

        await userEvent.click(screen.getByRole('button', {name: '查看报告 完成报告'}))
        expect(screen.getByLabelText('报告详情抽屉').textContent).toBe('report 2')
        expect(mocks.list).toHaveBeenCalledWith(7, {reportType: undefined, page: 1, size: 20})
    })

    test('shows an explicit empty state for a fixed type whose generator is not installed', async () => {
        renderPage()
        await screen.findByText('全部类型 暂无报告')

        await userEvent.click(screen.getByLabelText('报告类型筛选'))
        const options = await screen.findAllByText('Agent 分析')
        await userEvent.click(options.at(-1) as HTMLElement)

        expect(await screen.findByText('Agent 分析 暂无报告；对应代码生成器尚未接入')).toBeTruthy()
        expect(mocks.list).toHaveBeenLastCalledWith(7, {reportType: 'AGENT_ANALYSIS', page: 1, size: 20})
    })

    test('queues a supported report and reloads the page-owned list', async () => {
        const queued = report(9, '市场概览（生成中）', 'PENDING', 1)
        mocks.list.mockResolvedValueOnce(page([])).mockResolvedValueOnce(page([queued]))
        mocks.create.mockResolvedValue({report: queued, jobId: 81} satisfies CreateInvestmentReportResponse)
        renderPage()
        await screen.findByText('全部类型 暂无报告')

        await userEvent.click(screen.getByRole('button', {name: '创建报告'}))
        await userEvent.click(screen.getByRole('button', {name: '加入生成队列'}))

        await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(7, {reportType: 'MARKET_OVERVIEW'}))
        expect(await screen.findByText('市场概览（生成中）')).toBeTruthy()
        expect(mocks.list).toHaveBeenCalledTimes(2)
    })

    test('distinguishes missing report capability from a generic create API failure', async () => {
        mocks.create
            .mockRejectedValueOnce(new ApiRequestError('market source unavailable', {
                code: 'INVESTMENT_CAPABILITY_UNAVAILABLE',
                status: 409,
            }))
            .mockRejectedValueOnce(new Error('gateway timeout'))
        renderPage()
        await screen.findByText('全部类型 暂无报告')
        await userEvent.click(screen.getByRole('button', {name: '创建报告'}))

        await userEvent.click(screen.getByRole('button', {name: '加入生成队列'}))
        expect(await screen.findByText('报告能力尚未接入')).toBeTruthy()
        expect(screen.getByText('market source unavailable')).toBeTruthy()

        await userEvent.click(screen.getByRole('button', {name: '加入生成队列'}))
        expect(await screen.findByText('报告创建失败')).toBeTruthy()
        expect(screen.getByText('gateway timeout')).toBeTruthy()
    })

    test('suppresses the previous workspace list response after selection changes', async () => {
        const first = deferred<InvestmentReportPage>()
        mocks.list.mockImplementation((workspaceId: number) => workspaceId === 7
            ? first.promise
            : Promise.resolve(page([report(22, '新工作区报告', 'READY', 1)])))
        const view = renderPage()
        await waitFor(() => expect(mocks.list).toHaveBeenCalledWith(7, expect.anything()))

        mocks.workspace = {id: 8, name: '新工作区'}
        view.rerender(pageTree())
        expect(await screen.findByText('新工作区报告')).toBeTruthy()

        first.resolve(page([report(11, '旧工作区报告', 'READY', 1)]))
        await Promise.resolve()

        expect(screen.queryByText('旧工作区报告')).toBeNull()
        expect(screen.getByText('新工作区报告')).toBeTruthy()
    })

    test('does not show report creation when the RBAC button is absent', async () => {
        mocks.canCreate = false
        renderPage()
        await screen.findByText('全部类型 暂无报告')

        expect(screen.queryByRole('button', {name: '创建报告'})).toBeNull()
    })
})

function renderPage() {
    return render(pageTree())
}

function pageTree() {
    return <App><MemoryRouter><InvestmentResearchPage/></MemoryRouter></App>
}

function report(
    reportId: number,
    title: string,
    status: InvestmentReportSummaryResponse['status'],
    reportVersion: number,
    summary: string | null = '冻结报告摘要',
): InvestmentReportSummaryResponse {
    return {
        reportId,
        workspaceId: mocks.workspace?.id ?? 7,
        instrumentId: null,
        reportType: 'MARKET_OVERVIEW',
        title,
        summary,
        status,
        reportVersion,
        dataAsOf: '2026-07-16T00:00:00.000Z',
        createdAt: '2026-07-16T00:01:00.000Z',
    }
}

function page(records: InvestmentReportSummaryResponse[]): InvestmentReportPage {
    return {records, page: 1, size: 20, total: records.length, totalPages: records.length > 0 ? 1 : 0}
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
