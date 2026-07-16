import {render, screen} from '@testing-library/react'
import {describe, expect, test, vi} from 'vitest'
import type {InvestmentEquityPoint} from '../types/investmentPortfolioTypes'
import {
    downsampleEquityPoints,
    MAX_PORTFOLIO_CHART_POINTS,
    PortfolioEquityChart,
} from './PortfolioEquityChart'

const chart = vi.hoisted(() => vi.fn(({data}: {data: unknown[]}) => <div>图表点 {data.length}</div>))
vi.mock('@ant-design/charts', () => ({Line: chart}))

describe('PortfolioEquityChart', () => {
    test('renders an accessible empty state', () => {
        render(<PortfolioEquityChart points={[]}/>)
        expect(screen.getByText('暂无模拟账户权益快照')).toBeTruthy()
    })

    test('downsamples large server series while preserving exact text fallback and endpoints', () => {
        const points = Array.from({length: 1000}, (_, index) => point(index))
        const sampled = downsampleEquityPoints(points)
        render(<PortfolioEquityChart points={points}/>)

        expect(sampled).toHaveLength(MAX_PORTFOLIO_CHART_POINTS)
        expect(sampled[0].snapshotTime).toBe(points[0].snapshotTime)
        expect(sampled.at(-1)?.snapshotTime).toBe(points.at(-1)?.snapshotTime)
        expect(screen.getByText(/最新权益 10999 USDT，回撤 0.01，/)).toBeTruthy()
        expect(screen.getAllByText(`图表点 ${MAX_PORTFOLIO_CHART_POINTS}`)).toHaveLength(2)
    })
})

function point(index: number): InvestmentEquityPoint {
    return {
        snapshotTime: new Date(Date.UTC(2026, 0, 1, 0, 0, index)).toISOString(),
        walletBalance: '10000', usedMargin: '1', maintenanceMargin: '0.5', unrealizedPnl: '0',
        equity: String(10000 + index), availableBalance: '9999', grossExposure: '10',
        totalReturn: '0', drawdown: '0.01', positionCount: 1,
    }
}
