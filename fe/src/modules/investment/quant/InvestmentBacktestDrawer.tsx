import {Line} from '@ant-design/charts'
import {Alert, Button, Descriptions, Drawer, Empty, Flex, Space, Spin, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useMemo, useRef, useState} from 'react'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {
    getInvestmentBacktestEquity,
    listInvestmentBacktestEvents,
    listInvestmentBacktestTrades,
} from '../services/investmentQuantService'
import type {
    InvestmentBacktestEquityResponse,
    InvestmentBacktestEventResponse,
    InvestmentBacktestTradeResponse,
} from '../types/investmentQuantTypes'
import {useBacktestRunPolling} from './useBacktestRunPolling'

type InvestmentBacktestDrawerProps = {
    open: boolean
    runId?: number
    onClose: () => void
}

export function InvestmentBacktestDrawer({open, runId, onClose}: InvestmentBacktestDrawerProps) {
    const {run, error: pollingError, polling, refresh} = useBacktestRunPolling(open ? runId : undefined)
    const [trades, setTrades] = useState<InvestmentBacktestTradeResponse[]>([])
    const [events, setEvents] = useState<InvestmentBacktestEventResponse[]>([])
    const [equity, setEquity] = useState<InvestmentBacktestEquityResponse[]>([])
    const [resultLoading, setResultLoading] = useState(false)
    const [resultError, setResultError] = useState<string>()
    const generationRef = useRef(0)

    useEffect(() => {
        const generation = ++generationRef.current
        setTrades([])
        setEvents([])
        setEquity([])
        setResultError(undefined)
        if (!open || runId === undefined || run?.status !== 'SUCCEEDED') {
            setResultLoading(false)
            return
        }
        setResultLoading(true)
        void Promise.all([
            listInvestmentBacktestTrades(runId, 1, 100),
            listInvestmentBacktestEvents(runId, 1, 100),
            getInvestmentBacktestEquity(runId),
        ]).then(([tradePage, eventPage, equityPoints]) => {
            if (generation !== generationRef.current) {
                return
            }
            setTrades(tradePage.records)
            setEvents(eventPage.records)
            setEquity(equityPoints)
        }).catch((reason: unknown) => {
            if (generation === generationRef.current) {
                setResultError(errorMessage(reason, '回测明细加载失败'))
            }
        }).finally(() => {
            if (generation === generationRef.current) {
                setResultLoading(false)
            }
        })
        return () => {
            generationRef.current += 1
        }
    }, [open, run?.status, runId])

    const equityData = useMemo(() => equity.map((point) => ({
        time: point.pointTime,
        value: Number(point.equity),
    })), [equity])
    const drawdownData = useMemo(() => equity.map((point) => ({
        time: point.pointTime,
        value: Number(point.drawdown),
    })), [equity])

    return (
        <Drawer
            destroyOnHidden
            extra={<Button loading={polling} onClick={refresh}>刷新</Button>}
            onClose={onClose}
            open={open}
            size="large"
            title={runId === undefined ? '回测详情' : `回测 #${runId}`}
        >
            {!run && !pollingError && <Flex justify="center"><Spin/></Flex>}
            {pollingError && <Alert description={pollingError} showIcon title="回测状态刷新失败" type="warning"/>}
            {run && (
                <Space orientation="vertical" size={20} style={{width: '100%'}}>
                    <Descriptions
                        bordered
                        column={2}
                        items={[
                            {key: 'status', label: '状态', children: <Tag color={statusColor(run.status)}>{run.status}</Tag>},
                            {key: 'dataset', label: '数据快照', children: `#${run.datasetSnapshotId}`},
                            {key: 'initial', label: '初始权益', children: <InvestmentDecimalText value={run.initialEquity}/>},
                            {key: 'return', label: '总收益率', children: <InvestmentDecimalText value={run.totalReturn}/>},
                            {key: 'annualized', label: '年化收益率', children: <InvestmentDecimalText value={run.annualizedReturn}/>},
                            {key: 'drawdown', label: '最大回撤', children: <InvestmentDecimalText value={run.maxDrawdown}/>},
                            {key: 'sharpe', label: 'Sharpe', children: <InvestmentDecimalText value={run.sharpeRatio}/>},
                            {key: 'sortino', label: 'Sortino', children: <InvestmentDecimalText value={run.sortinoRatio}/>},
                            {key: 'trades', label: '成交笔数', children: run.tradeCount ?? '-'},
                            {key: 'liquidations', label: '强平次数', children: run.liquidationCount ?? '-'},
                        ]}
                    />
                    {run.status === 'FAILED' && (
                        <Alert
                            description={run.errorMessage ?? '回测执行失败'}
                            showIcon
                            title={run.errorCode ?? 'BACKTEST_FAILED'}
                            type="error"
                        />
                    )}
                    {run.status === 'SUCCEEDED' && resultLoading && <Flex justify="center"><Spin/></Flex>}
                    {resultError && <Alert description={resultError} showIcon title="回测结果加载失败" type="error"/>}
                    {run.status === 'SUCCEEDED' && !resultLoading && !resultError && (
                        <>
                            <section aria-label="回测权益曲线">
                                <Typography.Title level={5}>权益曲线</Typography.Title>
                                {equityData.length === 0 ? <Empty description="暂无权益点"/> : (
                                    <Line data={equityData} height={240} xField="time" yField="value"/>
                                )}
                            </section>
                            <section aria-label="回测回撤曲线">
                                <Typography.Title level={5}>回撤曲线</Typography.Title>
                                {drawdownData.length === 0 ? <Empty description="暂无回撤点"/> : (
                                    <Line data={drawdownData} height={180} xField="time" yField="value"/>
                                )}
                            </section>
                            <Typography.Title level={5}>交易记录</Typography.Title>
                            <Table
                                columns={tradeColumns}
                                dataSource={trades}
                                pagination={{pageSize: 20, hideOnSinglePage: true}}
                                rowKey="tradeId"
                                scroll={{x: 1_200}}
                                size="small"
                            />
                            <Typography.Title level={5}>事件流水</Typography.Title>
                            <Table
                                columns={eventColumns}
                                dataSource={events}
                                pagination={{pageSize: 20, hideOnSinglePage: true}}
                                rowKey="eventId"
                                scroll={{x: 900}}
                                size="small"
                            />
                        </>
                    )}
                </Space>
            )}
        </Drawer>
    )
}

const tradeColumns: ColumnsType<InvestmentBacktestTradeResponse> = [
    {title: '合约 ID', dataIndex: 'instrumentId'},
    {title: '方向', dataIndex: 'positionSide'},
    {title: '入场时间', dataIndex: 'entryTime'},
    {title: '出场时间', dataIndex: 'exitTime'},
    {title: '入场价', dataIndex: 'entryPrice', render: decimalCell},
    {title: '出场价', dataIndex: 'exitPrice', render: decimalCell},
    {title: '净损益', dataIndex: 'netPnl', render: decimalCell},
    {title: '原因', dataIndex: 'exitReason'},
]

const eventColumns: ColumnsType<InvestmentBacktestEventResponse> = [
    {title: '序号', dataIndex: 'sequenceNo'},
    {title: '时间', dataIndex: 'eventTime'},
    {title: '合约 ID', dataIndex: 'instrumentId', render: (value: number | null) => value ?? '-'},
    {title: '事件', dataIndex: 'eventType'},
    {title: '金额', dataIndex: 'amount', render: decimalCell},
    {title: '余额', dataIndex: 'balanceAfter', render: decimalCell},
]

function decimalCell(value: string | null) {
    return <InvestmentDecimalText value={value}/>
}

function statusColor(status: string) {
    if (status === 'SUCCEEDED') return 'success'
    if (status === 'FAILED' || status === 'CANCELLED') return 'error'
    if (status === 'RUNNING') return 'processing'
    return 'default'
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}
