import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentPaperAccountDetail} from '../types/investmentPortfolioTypes'
import InvestmentPortfolioPage from './InvestmentPortfolioPage'

const mocks = vi.hoisted(() => ({
    workspace: {
        currentWorkspace: {id: 7, name: '个人投资'},
        currentPaperAccount: nullableAccount(),
        setCurrentPaperAccount: vi.fn(),
    },
    listAccounts: vi.fn(),
    detail: vi.fn(),
    positions: vi.fn(),
    orders: vi.fn(),
    ledger: vi.fn(),
    equity: vi.fn(),
    updateSwitches: vi.fn(),
}))

function nullableAccount(): {
    id: number; workspaceId: number; name: string; baseCurrency: string; status: string
} | null {
    return {id: 21, workspaceId: 7, name: '模拟 A', baseCurrency: 'USDT', status: 'ACTIVE'}
}

vi.mock('../../auth/authStore', () => ({
    useAuth: () => ({roleCodes: []}),
    canUseRbacButton: () => true,
}))

vi.mock('../hooks/useInvestmentWorkspace', () => ({
    useInvestmentWorkspace: () => mocks.workspace,
}))

vi.mock('../services/investmentPortfolioService', () => ({
    listInvestmentPaperAccounts: mocks.listAccounts,
    getInvestmentPaperAccount: mocks.detail,
    listInvestmentPositions: mocks.positions,
    listInvestmentPaperOrders: mocks.orders,
    listInvestmentLedger: mocks.ledger,
    listInvestmentEquity: mocks.equity,
    updateInvestmentPaperAccountSwitches: mocks.updateSwitches,
    createInvestmentPaperAccount: vi.fn(),
    updateInvestmentRiskProfile: vi.fn(),
    submitInvestmentPaperTrade: vi.fn(),
    cancelInvestmentPaperOrder: vi.fn(),
    listInvestmentPaperFills: vi.fn(),
}))

vi.mock('@ant-design/charts', () => ({Line: () => <div>权益图</div>}))

describe('InvestmentPortfolioPage', () => {
    beforeEach(() => {
        mocks.workspace.currentWorkspace = {id: 7, name: '个人投资'}
        mocks.workspace.currentPaperAccount = {
            id: 21, workspaceId: 7, name: '模拟 A', baseCurrency: 'USDT', status: 'ACTIVE',
        }
        mocks.workspace.setCurrentPaperAccount.mockReset()
        mocks.listAccounts.mockReset().mockResolvedValue(page([accountDetail().account], 100))
        mocks.detail.mockReset().mockResolvedValue(accountDetail())
        mocks.positions.mockReset().mockResolvedValue([])
        mocks.orders.mockReset().mockResolvedValue(page([], 20))
        mocks.ledger.mockReset().mockResolvedValue(page([], 50))
        mocks.equity.mockReset().mockResolvedValue(page([{
            snapshotTime: '2026-07-17T00:00:00Z', walletBalance: '123.45', usedMargin: '0',
            maintenanceMargin: '0', unrealizedPnl: '0', equity: '123.45', availableBalance: '123.45',
            grossExposure: '0', totalReturn: '0.2345', drawdown: '0', positionCount: 0,
        }], 500))
        mocks.updateSwitches.mockReset()
    })

    test('rolls an optimistic switch back when the owner-scoped update fails', async () => {
        const user = userEvent.setup()
        mocks.updateSwitches.mockRejectedValue(new Error('版本冲突'))
        renderPage()

        expect(await screen.findAllByText('123.45 USDT')).not.toHaveLength(0)
        const manualSwitch = screen.getAllByRole('switch')[0]
        expect(manualSwitch.getAttribute('aria-checked')).toBe('false')
        await user.click(manualSwitch)

        await waitFor(() => expect(mocks.updateSwitches).toHaveBeenCalledWith(21, {
            tradingEnabled: true, agentAutoTradeEnabled: false, version: 3,
        }))
        await waitFor(() => expect(manualSwitch.getAttribute('aria-checked')).toBe('false'))
        expect(await screen.findByText('版本冲突')).toBeTruthy()
    })

    test('clears old account facts immediately when the workspace and account change', async () => {
        const view = renderPage()
        expect(await screen.findByText(/最新权益 123.45 USDT/)).toBeTruthy()

        mocks.workspace.currentWorkspace = {id: 8, name: '另一个工作区'}
        mocks.workspace.currentPaperAccount = null
        mocks.listAccounts.mockResolvedValueOnce(page([], 100))
        view.rerender(<App><InvestmentPortfolioPage/></App>)

        await waitFor(() => expect(screen.queryByText(/最新权益 123.45 USDT/)).toBeNull())
        expect(screen.getByText('请选择模拟账户后查看私人仓位与交易事实')).toBeTruthy()
        await waitFor(() => expect(mocks.listAccounts).toHaveBeenCalledWith(8, 1, 100))
    })
})

function renderPage() {
    return render(<App><InvestmentPortfolioPage/></App>)
}

function accountDetail(): InvestmentPaperAccountDetail {
    return {
        account: {
            id: 21, workspaceId: 7, name: '模拟 A', baseCurrency: 'USDT', initialEquity: '100',
            walletBalance: '123.45', equity: '123.45', usedMargin: '0', availableBalance: '123.45',
            grossExposure: '0', unrealizedPnl: '0', tradingEnabled: false,
            agentAutoTradeEnabled: false, status: 'ACTIVE', openedAt: '2026-07-17T00:00:00Z', version: 3,
        },
        riskProfile: {
            id: 31, accountId: 21, maxLeverage: '10', maxOrderNotional: '1000',
            maxPositionNotional: '5000', maxGrossExposureNotional: '10000', maxOpenPositions: 5,
            maxDailyLossAmount: '500', maxDrawdownRatio: '0.2', maxOrdersPerHour: 60,
            cooldownSeconds: 0, maxMarketDataAgeSeconds: 30, maxSlippageBps: '20', version: 9,
        },
    }
}

function page<T>(records: T[], size: number) {
    return {records, page: 1, size, total: records.length, totalPages: records.length ? 1 : 0}
}
