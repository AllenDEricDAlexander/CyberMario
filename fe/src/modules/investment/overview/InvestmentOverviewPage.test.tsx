import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {MemoryRouter} from 'react-router'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentOverviewResponse} from '../types/investmentOverviewTypes'
import InvestmentOverviewPage from './InvestmentOverviewPage'

const mocks = vi.hoisted(() => ({
    getOverview: vi.fn(),
    navigate: vi.fn(),
    workspace: {currentWorkspace: null as null | {id: number; name: string}},
}))

vi.mock('../hooks/useInvestmentWorkspace', () => ({useInvestmentWorkspace: () => mocks.workspace}))
vi.mock('../services/investmentOverviewService', () => ({getInvestmentOverview: mocks.getOverview}))
vi.mock('react-router', async (importOriginal) => ({
    ...await importOriginal<typeof import('react-router')>(),
    useNavigate: () => mocks.navigate,
}))

describe('InvestmentOverviewPage', () => {
    beforeEach(() => {
        mocks.getOverview.mockReset()
        mocks.navigate.mockReset()
        mocks.workspace.currentWorkspace = null
    })

    test('renders the create-or-select state without requesting a private overview', () => {
        renderPage()

        expect(screen.getByText('请先选择或创建一个私人投资工作区，再查看投资总览')).toBeTruthy()
        expect(mocks.getOverview).not.toHaveBeenCalled()
    })

    test('shows one cutoff with market, portfolio, recent backtest and Agent summaries', async () => {
        const user = userEvent.setup()
        mocks.workspace.currentWorkspace = {id: 7, name: '个人投资'}
        mocks.getOverview.mockResolvedValue(overview(7, 71, 81))
        renderPage()

        expect(await screen.findByText('数据截止：2035-01-01T00:00:00Z')).toBeTruthy()
        expect(screen.getByText('存在陈旧或缺失行情，分析与交易可能被服务端拒绝')).toBeTruthy()
        expect(screen.getByText('1125')).toBeTruthy()
        expect(screen.getByText('#71')).toBeTruthy()
        expect(screen.getByText('#81')).toBeTruthy()
        expect(screen.getByText('OPEN_LONG')).toBeTruthy()
        expect(mocks.getOverview).toHaveBeenCalledWith(7)

        await user.click(screen.getByRole('button', {name: '进入合约行情'}))
        await user.click(screen.getByRole('button', {name: '进入模拟盘'}))
        await user.click(screen.getByRole('button', {name: '进入量化回测'}))
        await user.click(screen.getByRole('button', {name: '进入 Agent 交易'}))
        expect(mocks.navigate).toHaveBeenNthCalledWith(1, '/investment/market')
        expect(mocks.navigate).toHaveBeenNthCalledWith(2, '/investment/portfolio')
        expect(mocks.navigate).toHaveBeenNthCalledWith(3, '/investment/quant')
        expect(mocks.navigate).toHaveBeenNthCalledWith(4, '/investment/agent')
    })

    test('keeps available summaries when other sections are error or unavailable', async () => {
        mocks.workspace.currentWorkspace = {id: 7, name: '个人投资'}
        const response = overview(7, 71, 81)
        response.sections = response.sections.map((section) => {
            if (section.code === 'QUANT') return {...section, status: 'ERROR', data: {}, errorCode: 'SECTION_QUERY_FAILED'}
            if (section.code === 'AGENT') return {...section, status: 'UNAVAILABLE', data: {}}
            return section
        })
        mocks.getOverview.mockResolvedValue(response)
        renderPage()

        expect(await screen.findByText('量化回测加载失败')).toBeTruthy()
        expect(screen.getByText('SECTION_QUERY_FAILED')).toBeTruthy()
        expect(screen.getByText('Agent 运行暂不可用')).toBeTruthy()
        expect(screen.getByText('1125')).toBeTruthy()
    })

    test('suppresses an old workspace response after the private scope changes', async () => {
        const old = deferred<InvestmentOverviewResponse>()
        mocks.workspace.currentWorkspace = {id: 7, name: '旧工作区'}
        mocks.getOverview.mockReturnValueOnce(old.promise).mockResolvedValueOnce(overview(8, 72, 82))
        const view = renderPage()

        mocks.workspace.currentWorkspace = {id: 8, name: '新工作区'}
        view.rerender(page())
        expect(await screen.findByText('#72')).toBeTruthy()

        old.resolve(overview(7, 71, 81))
        await Promise.resolve()

        expect(screen.queryByText('#71')).toBeNull()
        expect(screen.getByText('#72')).toBeTruthy()
    })
})

function renderPage() {
    return render(page())
}

function page() {
    return <App><MemoryRouter><InvestmentOverviewPage/></MemoryRouter></App>
}

function overview(workspaceId: number, backtestId: number, agentRunId: number): InvestmentOverviewResponse {
    const dataAsOf = '2035-01-01T00:00:00Z'
    return {
        workspaceId,
        dataAsOf,
        sections: [
            {
                code: 'MARKET', status: 'AVAILABLE', dataAsOf, errorCode: null,
                data: {subscribedInstrumentCount: 3, freshQuoteCount: 2, staleOrMissingQuoteCount: 1, openQualityIssueCount: 0},
            },
            {
                code: 'QUANT', status: 'AVAILABLE', dataAsOf, errorCode: null,
                data: {recentBacktests: [{
                    runId: backtestId, strategyReleaseId: 41, datasetSnapshotId: 51,
                    totalReturn: '0.12', maxDrawdown: '0.04', winRate: '0.6', tradeCount: 9,
                    finishedAt: '2034-12-31T23:59:00Z',
                }]},
            },
            {
                code: 'PORTFOLIO', status: 'AVAILABLE', dataAsOf, errorCode: null,
                data: {
                    accountCount: 2, positionCount: 1, walletBalance: '1000', equity: '1125',
                    availableBalance: '900', unrealizedPnl: '125', grossExposure: '500', maxDrawdown: '0.08',
                    riskWarningCount: 1, positions: [],
                },
            },
            {
                code: 'AGENT', status: 'AVAILABLE', dataAsOf, errorCode: null,
                data: {recentRuns: [{
                    runId: agentRunId, runType: 'AUTO_TRADE', accountId: 31, reportId: 41,
                    dataAsOf: '2034-12-31T23:59:00Z', finishedAt: '2034-12-31T23:59:59Z',
                    decisionId: 91, instrumentId: 501, action: 'OPEN_LONG', confidence: '0.75',
                    executionStatus: 'SUBMITTED', intentId: 101,
                }]},
            },
        ],
    }
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
