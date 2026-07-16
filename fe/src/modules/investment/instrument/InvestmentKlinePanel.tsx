import {Alert, Card, Flex, Select, Table, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import type {CandlestickData, UTCTimestamp} from 'lightweight-charts'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {InvestmentCandlestickChart} from '../components/InvestmentCandlestickChart'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {getInvestmentIndicators, listInvestmentCandles} from '../services/investmentMarketService'
import {listInvestmentPaperFills} from '../services/investmentPortfolioService'
import type {InvestmentDecimal, InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentBarInterval,
    InvestmentCandleResponse,
    InvestmentIndicatorSnapshot,
    InvestmentPriceType,
} from '../types/investmentMarketTypes'
import type {InvestmentFillMarker} from '../types/investmentPortfolioTypes'
import {toInvestmentCandlestickData} from './investmentChartMappers'
import {toInvestmentTradeMarkers} from './investmentTradeMarkerMappers'

type InvestmentKlinePanelProps = {
    instrumentId: number
    accountId?: number
    availablePriceTypes: InvestmentPriceType[]
    availableIntervals: InvestmentBarInterval[]
    now?: () => number
}

type InvestmentRange = '1H' | '24H' | '7D'

type TradeActivityState = {
    scope: string
    records: InvestmentFillMarker[]
    error?: string
}

export function InvestmentKlinePanel({
    instrumentId,
    accountId,
    availablePriceTypes,
    availableIntervals,
    now = Date.now,
}: InvestmentKlinePanelProps) {
    const [priceType, setPriceType] = useState<InvestmentPriceType>(availablePriceTypes[0] ?? 'MARKET')
    const [interval, setInterval] = useState<InvestmentBarInterval>(availableIntervals[0] ?? 'M1')
    const [range, setRange] = useState<InvestmentRange>('24H')
    const [candles, setCandles] = useState<InvestmentCandleResponse[]>([])
    const [indicators, setIndicators] = useState<InvestmentIndicatorSnapshot>()
    const [indicatorError, setIndicatorError] = useState<string>()
    const [tradeActivityState, setTradeActivityState] = useState<TradeActivityState>()
    const [loadState, setLoadState] = useState<InvestmentLoadState>('loading')
    const [loadError, setLoadError] = useState<string>()
    const generationRef = useRef(0)
    const tradeGenerationRef = useRef(0)

    useEffect(() => {
        if (!availablePriceTypes.includes(priceType)) {
            setPriceType(availablePriceTypes[0] ?? 'MARKET')
        }
    }, [availablePriceTypes, priceType])

    useEffect(() => {
        if (!availableIntervals.includes(interval)) {
            setInterval(availableIntervals[0] ?? 'M1')
        }
    }, [availableIntervals, interval])

    const load = useCallback(async () => {
        const generation = ++generationRef.current
        const query = candleQuery(instrumentId, priceType, interval, range, now())
        setLoadState('loading')
        setLoadError(undefined)
        setIndicatorError(undefined)
        setIndicators(undefined)
        try {
            const response = await listInvestmentCandles(query)
            if (generation !== generationRef.current) {
                return
            }
            const closed = response.filter(({isClosed}) => isClosed)
            setCandles(closed)
            setLoadState(closed.length === 0 ? 'empty' : 'ready')
        } catch (reason) {
            if (generation !== generationRef.current) {
                return
            }
            setCandles([])
            setLoadState('error')
            setLoadError(errorMessage(reason, 'K 线加载失败'))
            return
        }
        try {
            const response = await getInvestmentIndicators(query)
            if (generation === generationRef.current) {
                setIndicators(response)
            }
        } catch (reason) {
            if (generation === generationRef.current) {
                setIndicatorError(errorMessage(reason, '指标暂不可用'))
            }
        }
    }, [instrumentId, interval, now, priceType, range])

    useEffect(() => {
        void load()
        return () => {
            generationRef.current += 1
        }
    }, [load])

    useEffect(() => {
        const generation = ++tradeGenerationRef.current
        const activityScope = tradeActivityScope(accountId, instrumentId, priceType, interval, range)
        setTradeActivityState(undefined)
        if (accountId === undefined) {
            return
        }
        const query = candleQuery(instrumentId, priceType, interval, range, now())
        void listInvestmentPaperFills(
            accountId, instrumentId, query.from, query.to, 1, 100,
        ).then((response) => {
            if (generation === tradeGenerationRef.current) {
                setTradeActivityState({scope: activityScope, records: response.records})
            }
        }).catch((reason: unknown) => {
            if (generation === tradeGenerationRef.current) {
                setTradeActivityState({
                    scope: activityScope,
                    records: [],
                    error: errorMessage(reason, '私人模拟盘活动暂不可用'),
                })
            }
        })
        return () => {
            tradeGenerationRef.current += 1
        }
    }, [accountId, instrumentId, interval, now, priceType, range])

    const chartData = useMemo<CandlestickData<UTCTimestamp>[]>(
        () => toInvestmentCandlestickData(candles),
        [candles],
    )
    const latestIndicator = indicators?.points.at(-1)
    const currentActivityScope = tradeActivityScope(accountId, instrumentId, priceType, interval, range)
    const visibleTradeActivity = useMemo(
        () => tradeActivityState?.scope === currentActivityScope ? tradeActivityState.records : [],
        [currentActivityScope, tradeActivityState],
    )
    const tradeActivityError = tradeActivityState?.scope === currentActivityScope
        ? tradeActivityState.error
        : undefined
    const tradeMarkers = useMemo(
        () => toInvestmentTradeMarkers(visibleTradeActivity, candles),
        [candles, visibleTradeActivity],
    )

    return (
        <Card title="K 线与技术指标">
            <Flex gap={12} justify="space-between" wrap>
                <Flex gap={12} wrap>
                    <Select
                        aria-label="K 线价型"
                        onChange={setPriceType}
                        options={availablePriceTypes.map((value) => ({label: value, value}))}
                        style={{minWidth: 120}}
                        value={priceType}
                    />
                    <Select
                        aria-label="K 线周期"
                        onChange={setInterval}
                        options={availableIntervals.map((value) => ({label: value, value}))}
                        style={{minWidth: 120}}
                        value={interval}
                    />
                    <Select
                        aria-label="K 线范围"
                        onChange={setRange}
                        options={[
                            {label: '1 小时', value: '1H'},
                            {label: '24 小时', value: '24H'},
                            {label: '7 天', value: '7D'},
                        ]}
                        style={{minWidth: 120}}
                        value={range}
                    />
                </Flex>
                <Typography.Text type="secondary">
                    {candles.length > 0
                        ? `${candles[0].openTime} 至 ${candles.at(-1)?.closeTime}，共 ${candles.length} 根已关闭 K 线`
                        : '等待已关闭 K 线'}
                </Typography.Text>
            </Flex>
            <InvestmentAsyncState
                emptyDescription="当前范围暂无已关闭 K 线"
                error={loadError}
                onRetry={() => void load()}
                state={loadState}
            >
                <InvestmentCandlestickChart data={chartData} markers={tradeMarkers}/>
                <Table
                    columns={[
                        {title: '开盘时间', dataIndex: 'openTime'},
                        {title: '开', dataIndex: 'open', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                        {title: '高', dataIndex: 'high', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                        {title: '低', dataIndex: 'low', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                        {title: '收', dataIndex: 'close', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                    ]}
                    dataSource={candles}
                    pagination={{pageSize: 20, hideOnSinglePage: true}}
                    rowKey={(candle) => `${candle.openTime}:${candle.revision}`}
                    size="small"
                />
            </InvestmentAsyncState>
            {accountId === undefined && (
                <Alert showIcon title="选择当前工作区的模拟账户后可叠加私人成交与强平标记" type="info"/>
            )}
            {tradeActivityError && (
                <Alert description={tradeActivityError} showIcon title="私人交易标记独立加载失败" type="warning"/>
            )}
            {accountId !== undefined && !tradeActivityError && (
                <section aria-label="模拟盘活动文本摘要">
                    <Typography.Title level={5}>模拟盘成交与强平活动</Typography.Title>
                    <Table
                        columns={[
                            {title: '事件时间', dataIndex: 'eventTime'},
                            {title: '方向', dataIndex: 'side'},
                            {title: '动作', dataIndex: 'actionType'},
                            {title: '来源', dataIndex: 'orderOrigin'},
                            {title: '事件', dataIndex: 'eventType'},
                            {title: '价格', dataIndex: 'price', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                            {title: '数量', dataIndex: 'quantity', render: (value: InvestmentDecimal) => <InvestmentDecimalText value={value}/>},
                            {title: '强平', dataIndex: 'liquidation', render: (value: boolean) => value ? '是' : '否'},
                        ]}
                        dataSource={visibleTradeActivity}
                        locale={{emptyText: '当前 K 线范围暂无私人模拟盘活动'}}
                        pagination={{pageSize: 20, hideOnSinglePage: true}}
                        rowKey="id"
                        scroll={{x: 900}}
                        size="small"
                    />
                </section>
            )}
            {indicatorError && <Alert description={indicatorError} showIcon title="技术指标独立加载失败" type="warning"/>}
            {latestIndicator && (
                <Flex gap={16} wrap>
                    <Typography.Text>SMA20：<InvestmentDecimalText value={latestIndicator.sma20}/></Typography.Text>
                    <Typography.Text>EMA20：<InvestmentDecimalText value={latestIndicator.ema20}/></Typography.Text>
                    <Typography.Text>RSI14：<InvestmentDecimalText value={latestIndicator.rsi14}/></Typography.Text>
                    <Typography.Text>MACD：<InvestmentDecimalText value={latestIndicator.macd}/></Typography.Text>
                    <Typography.Text>ATR14：<InvestmentDecimalText value={latestIndicator.atr14}/></Typography.Text>
                </Flex>
            )}
        </Card>
    )
}

export function candleQuery(
    instrumentId: number,
    priceType: InvestmentPriceType,
    interval: InvestmentBarInterval,
    range: InvestmentRange,
    nowMillis: number,
) {
    const dataAsOf = new Date(nowMillis)
    const to = interval === 'D1'
        ? new Date(Date.UTC(dataAsOf.getUTCFullYear(), dataAsOf.getUTCMonth(), dataAsOf.getUTCDate()))
        : dataAsOf
    const rangeMillis = range === '1H' ? 60 * 60 * 1_000
        : range === '24H' ? 24 * 60 * 60 * 1_000 : 7 * 24 * 60 * 60 * 1_000
    const minimumDailyRange = 24 * 60 * 60 * 1_000
    const from = new Date(to.getTime() - (interval === 'D1' ? Math.max(rangeMillis, minimumDailyRange) : rangeMillis))
    return {
        instrumentId,
        priceType,
        interval,
        from: from.toISOString(),
        to: to.toISOString(),
        dataAsOf: dataAsOf.toISOString(),
        limit: 2_000,
    }
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}

function tradeActivityScope(
    accountId: number | undefined,
    instrumentId: number,
    priceType: InvestmentPriceType,
    interval: InvestmentBarInterval,
    range: InvestmentRange,
) {
    return `${accountId ?? 'none'}:${instrumentId}:${priceType}:${interval}:${range}`
}
