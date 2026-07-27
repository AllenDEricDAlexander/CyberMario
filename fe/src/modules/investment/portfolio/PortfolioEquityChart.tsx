import {Line} from '@ant-design/charts'
import {Typography} from 'antd'
import {EmptyState} from '../../../components/EmptyState'
import {PageStack} from '../../../components/PageSection'
import type {InvestmentEquityPoint} from '../types/investmentPortfolioTypes'

export const MAX_PORTFOLIO_CHART_POINTS = 400

export function PortfolioEquityChart({points}: {points: InvestmentEquityPoint[]}) {
    const sampled = downsampleEquityPoints(points)
    if (sampled.length === 0) {
        return (
            <EmptyState
                description="服务端每次结算才写入权益快照；下单或等待下一次结算后再回来查看。"
                inline
                title="暂无模拟账户权益快照"
            />
        )
    }
    const chronological = [...sampled].sort((left, right) => left.snapshotTime.localeCompare(right.snapshotTime))
    const equity = chronological.map((point) => ({time: point.snapshotTime, value: Number(point.equity)}))
    const drawdown = chronological.map((point) => ({time: point.snapshotTime, value: Number(point.drawdown)}))
    const latest = chronological.at(-1)!
    return (
        <PageStack>
            <Typography.Text aria-live="polite">
                最新权益 {latest.equity} USDT，回撤 {latest.drawdown}，共 {points.length} 个服务端快照。
            </Typography.Text>
            <section aria-label="模拟账户权益曲线">
                <Line data={equity} height={240} xField="time" yField="value"/>
            </section>
            <section aria-label="模拟账户回撤曲线">
                <Line data={drawdown} height={160} xField="time" yField="value"/>
            </section>
        </PageStack>
    )
}

export function downsampleEquityPoints(
    points: InvestmentEquityPoint[],
    maximum = MAX_PORTFOLIO_CHART_POINTS,
) {
    const chronological = [...points].sort((left, right) => left.snapshotTime.localeCompare(right.snapshotTime))
    if (maximum < 2 || chronological.length <= maximum) {
        return chronological
    }
    const lastIndex = chronological.length - 1
    return Array.from({length: maximum}, (_, index) =>
        chronological[Math.round(index * lastIndex / (maximum - 1))])
}
