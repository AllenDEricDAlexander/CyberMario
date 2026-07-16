import {render, screen} from '@testing-library/react'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentBacktestRunResponse} from '../types/investmentQuantTypes'
import {InvestmentBacktestDrawer} from './InvestmentBacktestDrawer'

const mocks = vi.hoisted(() => ({
    polling: vi.fn(),
    trades: vi.fn(),
    events: vi.fn(),
    equity: vi.fn(),
}))

vi.mock('@ant-design/charts', () => ({
    Line: ({data}: {data: unknown[]}) => <div>测试折线图 {data.length}</div>,
}))

vi.mock('./useBacktestRunPolling', () => ({
    useBacktestRunPolling: mocks.polling,
}))

vi.mock('../services/investmentQuantService', () => ({
    listInvestmentBacktestTrades: mocks.trades,
    listInvestmentBacktestEvents: mocks.events,
    getInvestmentBacktestEquity: mocks.equity,
}))

describe('InvestmentBacktestDrawer', () => {
    beforeEach(() => {
        mocks.polling.mockReset().mockReturnValue({
            run: succeededRun(),
            polling: false,
            error: undefined,
            refresh: vi.fn(),
        })
        mocks.trades.mockReset().mockResolvedValue({records: [{
            tradeId: 71,
            instrumentId: 11,
            positionSide: 'LONG',
            entryTime: '2026-07-01T00:01:00Z',
            exitTime: '2026-07-01T00:02:00Z',
            entryPrice: '100.1',
            exitPrice: '110.1',
            quantity: '1',
            leverage: '3',
            grossPnl: '10',
            feeAmount: '0.1',
            fundingAmount: '-0.01',
            netPnl: '9.89',
            exitReason: 'SIGNAL',
        }], page: 1, size: 100, total: 1, totalPages: 1})
        mocks.events.mockReset().mockResolvedValue({records: [{
            eventId: 81,
            instrumentId: 11,
            eventType: 'FUNDING',
            eventTime: '2026-07-01T00:02:00Z',
            amount: '-0.01',
            balanceAfter: '10009.89',
            detailsJson: '{}',
            sequenceNo: 1,
        }], page: 1, size: 100, total: 1, totalPages: 1})
        mocks.equity.mockReset().mockResolvedValue([
            equity('2026-07-01T00:01:00Z', '10000', '0'),
            equity('2026-07-01T00:02:00Z', '10009.89', '0.01'),
        ])
    })

    test('renders deterministic metrics, trades, events and both chart series', async () => {
        render(<App><InvestmentBacktestDrawer onClose={vi.fn()} open runId={51}/></App>)

        expect(await screen.findByText('9.89')).toBeTruthy()
        expect(screen.getByText('FUNDING')).toBeTruthy()
        expect(screen.getAllByText('测试折线图 2')).toHaveLength(2)
        expect(screen.getByRole('region', {name: '回测权益曲线'})).toBeTruthy()
        expect(screen.getByRole('region', {name: '回测回撤曲线'})).toBeTruthy()
        expect(mocks.trades).toHaveBeenCalledWith(51, 1, 100)
        expect(mocks.events).toHaveBeenCalledWith(51, 1, 100)
        expect(mocks.equity).toHaveBeenCalledWith(51)
    })
})

function succeededRun(): InvestmentBacktestRunResponse {
    return {
        runId: 51,
        workspaceId: 7,
        jobId: 61,
        strategyReleaseId: 31,
        datasetSnapshotId: 41,
        status: 'SUCCEEDED',
        initialEquity: '10000',
        totalReturn: '0.01',
        annualizedReturn: '0.1',
        maxDrawdown: '0.02',
        sharpeRatio: '1.2',
        sortinoRatio: '1.5',
        winRate: '0.5',
        profitFactor: '2',
        turnover: '1000',
        tradeCount: 1,
        totalFee: '0.1',
        totalFunding: '-0.01',
        liquidationCount: 0,
        errorCode: null,
        errorMessage: null,
        startedAt: '2026-07-01T00:00:00Z',
        finishedAt: '2026-07-01T00:03:00Z',
        createdAt: '2026-07-01T00:00:00Z',
    }
}

function equity(pointTime: string, value: string, drawdown: string) {
    return {
        pointTime,
        walletBalance: value,
        usedMargin: '0',
        unrealizedPnl: '0',
        equity: value,
        drawdown,
        grossExposure: '0',
    }
}
