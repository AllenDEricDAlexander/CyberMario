import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentStrategyDescriptor} from '../types/investmentQuantTypes'
import InvestmentQuantPage from './InvestmentQuantPage'

const mocks = vi.hoisted(() => ({
    strategies: vi.fn(),
    runs: vi.fn(),
    submit: vi.fn(),
}))

vi.mock('../../auth/authStore', () => ({
    useAuth: () => ({roleCodes: []}),
    canUseRbacButton: () => true,
}))

vi.mock('../hooks/useInvestmentWorkspace', () => ({
    useInvestmentWorkspace: () => ({currentWorkspace: {id: 7, name: '个人策略'}}),
}))

vi.mock('../services/investmentQuantService', () => ({
    listInvestmentStrategies: mocks.strategies,
    listInvestmentBacktests: mocks.runs,
    submitInvestmentBacktest: mocks.submit,
}))

vi.mock('./InvestmentBacktestDrawer', () => ({
    InvestmentBacktestDrawer: () => null,
}))

describe('InvestmentQuantPage', () => {
    beforeEach(() => {
        mocks.strategies.mockReset()
        mocks.runs.mockReset().mockResolvedValue(page([]))
        mocks.submit.mockReset()
    })

    test('supports an empty production strategy registry without rendering a fake editor', async () => {
        mocks.strategies.mockResolvedValue([])
        renderPage()

        expect(await screen.findByText('尚未部署可用的生产代码策略')).toBeTruthy()
        expect(screen.queryByRole('button', {name: '发起回测'})).toBeNull()
        expect(screen.queryByText(/策略参数编辑/)).toBeNull()
    })

    test('shows descriptors read-only and the create form contains only approved inputs', async () => {
        mocks.strategies.mockResolvedValue([strategy()])
        renderPage()

        expect(await screen.findByText('EMA 交叉 / 1.0.0')).toBeTruthy()
        expect(screen.getByText('默认杠杆')).toBeTruthy()
        await userEvent.click(screen.getByRole('button', {name: '发起回测'}))

        expect(screen.getByLabelText('回测策略')).toBeTruthy()
        expect(screen.getByLabelText('回测合约 ID')).toBeTruthy()
        expect(screen.getAllByLabelText('回测时间范围')).toHaveLength(2)
        expect(screen.queryByLabelText(/杠杆|周期|手续费|滑点|参数/)).toBeNull()
        expect(screen.queryByRole('textbox', {name: /策略参数/})).toBeNull()
        await waitFor(() => expect(mocks.runs).toHaveBeenCalledWith(7, 1, 20))
    })
})

function renderPage() {
    return render(<App><InvestmentQuantPage/></App>)
}

function page(records: never[]) {
    return {records, page: 1, size: 20, total: records.length, totalPages: 0}
}

function strategy(): InvestmentStrategyDescriptor {
    return {
        strategyCode: 'EMA_CROSS',
        strategyVersion: '1.0.0',
        displayName: 'EMA 交叉',
        description: '固定代码策略',
        engineType: 'JAVA',
        requiredCapabilities: ['MARKET_CANDLE', 'MARK_CANDLE'],
        supportedIntervals: ['M1'],
        evaluationInterval: 'M1',
        priceType: 'MARKET',
        evaluationSchedule: 'ON_BAR_CLOSE',
        positionSizingPolicy: 'FIXED_FRACTION_10_PERCENT',
        defaultLeverage: '3',
        maximumLeverage: '5',
        feeModelCode: 'CONTRACT_RATE_V1',
        slippageModelCode: 'FIXED_BPS_5',
        matchingModelCode: 'NEXT_BAR_V1',
    }
}
