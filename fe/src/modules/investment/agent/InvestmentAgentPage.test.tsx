import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentAgentRunResponse} from '../types/investmentAgentTypes'
import InvestmentAgentPage, {validRunRequest} from './InvestmentAgentPage'

const mocks = vi.hoisted(() => ({
    workspace: {
        currentWorkspace: {id: 7, name: '个人投资'},
        currentPaperAccount: {id: 21, workspaceId: 7, name: '模拟 A'},
    },
    listRuns: vi.fn(),
    submit: vi.fn(),
    accounts: vi.fn(),
    instruments: vi.fn(),
}))

vi.mock('../../auth/authStore', () => ({
    useAuth: () => ({roleCodes: []}),
    canUseRbacButton: () => true,
}))

vi.mock('../hooks/useInvestmentWorkspace', () => ({
    useInvestmentWorkspace: () => mocks.workspace,
}))

vi.mock('../services/investmentAgentService', () => ({
    listInvestmentAgentRuns: mocks.listRuns,
    submitInvestmentAgentRun: mocks.submit,
}))

vi.mock('../services/investmentPortfolioService', () => ({
    listInvestmentPaperAccounts: mocks.accounts,
}))

vi.mock('../services/investmentMarketService', () => ({
    listInvestmentInstruments: mocks.instruments,
}))

vi.mock('./InvestmentAgentRunDrawer', () => ({
    InvestmentAgentRunDrawer: ({open, runId}: {open: boolean; runId?: number}) => (
        open ? <div>Agent drawer #{runId}</div> : null
    ),
}))

describe('InvestmentAgentPage', () => {
    beforeEach(() => {
        mocks.workspace.currentWorkspace = {id: 7, name: '个人投资'}
        mocks.workspace.currentPaperAccount = {id: 21, workspaceId: 7, name: '模拟 A'}
        mocks.listRuns.mockReset().mockResolvedValue(page([]))
        mocks.accounts.mockReset().mockResolvedValue(page([account()]))
        mocks.instruments.mockReset().mockResolvedValue(page([instrument()]))
        mocks.submit.mockReset().mockResolvedValue({run: run(42), jobId: 71, duplicate: false})
    })

    test('shows the fixed paper-only scope without prompt, tool, strategy, or confirmation editors', async () => {
        renderPage()

        expect(await screen.findByText(/固定预设 INVESTMENT_ANALYST_V1/)).toBeTruthy()
        expect(screen.getByText('仅模拟盘，风控通过后自动执行')).toBeTruthy()
        expect(await screen.findByLabelText('Agent 运行类型')).toBeTruthy()
        expect(await screen.findByLabelText('Agent 模拟账户')).toBeTruthy()
        expect(await screen.findByLabelText('Agent 分析合约')).toBeTruthy()
        expect(screen.queryByLabelText(/prompt/i)).toBeNull()
        expect(screen.queryByLabelText(/tool/i)).toBeNull()
        expect(screen.queryByLabelText(/strategy/i)).toBeNull()
        expect(screen.queryByText(/确认执行/)).toBeNull()
    })

    test('submits AUTO_TRADE directly and explains an account-level risk rejection switch', async () => {
        const user = userEvent.setup()
        renderPage()
        await screen.findByText('当前工作区暂无 Agent 运行')

        await choose(user, 'Agent 运行类型', 'Agent 自动模拟交易')
        expect(await screen.findByText('该模拟账户尚未开启 Agent 自动交易')).toBeTruthy()
        await choose(user, 'Agent 分析合约', /BTCUSDT/)
        await user.click(screen.getByRole('button', {name: '发起固定预设运行'}))

        await waitFor(() => expect(mocks.submit).toHaveBeenCalledWith(7, {
            runType: 'AUTO_TRADE', accountId: 21, instrumentIds: [11],
        }))
        expect(await screen.findByText('Agent drawer #42')).toBeTruthy()
        expect(screen.queryByRole('dialog', {name: /确认/})).toBeNull()
    })

    test('matches backend account and instrument requirements without blocking analysis when auto trade is off', () => {
        expect(validRunRequest('INSTRUMENT_ANALYSIS', undefined, [11])).toBe(true)
        expect(validRunRequest('AUTO_TRADE', undefined, [11])).toBe(false)
        expect(validRunRequest('AUTO_TRADE', 21, [11])).toBe(true)
        expect(validRunRequest('PORTFOLIO_REVIEW', undefined, [])).toBe(true)
        expect(validRunRequest('MARKET_ANALYSIS', undefined, [])).toBe(false)
    })

    test('keeps portfolio review available when the code registry has no instruments', async () => {
        const user = userEvent.setup()
        mocks.instruments.mockResolvedValue(page([]))
        renderPage()

        expect(await screen.findByText('暂无代码接入的可分析合约')).toBeTruthy()
        await choose(user, 'Agent 运行类型', '组合复盘')

        expect(screen.getByRole('button', {name: '发起固定预设运行'}).hasAttribute('disabled')).toBe(false)
    })
})

async function choose(user: ReturnType<typeof userEvent.setup>, label: string, option: string | RegExp) {
    await user.click(screen.getByLabelText(label))
    await user.click(await screen.findByText(option, {selector: '.ant-select-item-option-content'}))
}

function renderPage() {
    return render(<App><InvestmentAgentPage/></App>)
}

function page<T>(records: T[]) {
    return {records, page: 1, size: 100, total: records.length, totalPages: records.length ? 1 : 0}
}

function account() {
    return {
        id: 21, workspaceId: 7, name: '模拟 A', baseCurrency: 'USDT', initialEquity: '10000',
        walletBalance: '10000', equity: '10000', usedMargin: '0', availableBalance: '10000',
        grossExposure: '0', unrealizedPnl: '0', tradingEnabled: true, agentAutoTradeEnabled: false,
        status: 'ACTIVE', openedAt: '2026-07-17T00:00:00Z', version: 1,
    }
}

function instrument() {
    return {
        instrumentId: 11, venueCode: 'BITGET', symbol: 'BTCUSDT', baseAsset: 'BTC', quoteAsset: 'USDT',
        status: 'ACTIVE', lastPrice: '100', markPrice: '100', change24h: '0.01',
        dataAsOf: '2026-07-17T00:00:00Z',
        freshness: {status: 'FRESH' as const, observedAt: '2026-07-17T00:00:00Z', ageSeconds: 0},
        availableCapabilities: ['CANDLES'],
    }
}

function run(id: number): InvestmentAgentRunResponse {
    return {
        id, workspaceId: 7, accountId: 21, presetCode: 'INVESTMENT_ANALYST_V1',
        genericAgentRunAuditId: 61, runType: 'AUTO_TRADE', status: 'PENDING',
        dataAsOf: '2026-07-17T00:00:00Z', reportId: null,
        startedAt: '2026-07-17T00:00:00Z', finishedAt: null,
        errorCode: null, errorMessage: null, createdAt: '2026-07-17T00:00:00Z',
    }
}
