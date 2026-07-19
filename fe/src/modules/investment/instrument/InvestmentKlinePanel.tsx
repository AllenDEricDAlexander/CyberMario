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
    refreshIntervalMs?: number
}

const HOUR_MILLIS = 60 * 60 * 1_000
const DAY_MILLIS = 24 * HOUR_MILLIS
const CANDLE_WINDOW_CONFIG: Record<InvestmentBarInterval, {millis: number; label: string}> = {
    M1: {millis: 12 * HOUR_MILLIS, label: '12 小时'},
    M5: {millis: 60 * HOUR_MILLIS, label: '60 小时'},
    M15: {millis: 180 * HOUR_MILLIS, label: '7.5 天'},
    M30: {millis: 360 * HOUR_MILLIS, label: '15 天'},
    H1: {millis: 720 * HOUR_MILLIS, label: '30 天'},
    H4: {millis: 720 * 4 * HOUR_MILLIS, label: '120 天'},
    D1: {millis: 720 * DAY_MILLIS, label: '720 天'},
}

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
    refreshIntervalMs = 30_000,
}: InvestmentKlinePanelProps) {
    const [priceType, setPriceType] = useState<InvestmentPriceType>(availablePriceTypes[0] ?? 'MARKET')
    const [interval, setInterval] = useState<InvestmentBarInterval>(availableIntervals[0] ?? 'M1')
    const [candles, setCandles] = useState<InvestmentCandleResponse[]>([])
    const [loadedFrom, setLoadedFrom] = useState<string>()
    const [loadedTo, setLoadedTo] = useState<string>()
    const [canLoadEarlier, setCanLoadEarlier] = useState(true)
    const [loadingEarlier, setLoadingEarlier] = useState(false)
    const [loadEarlierError, setLoadEarlierError] = useState<string>()
    const [indicators, setIndicators] = useState<InvestmentIndicatorSnapshot>()
    const [indicatorError, setIndicatorError] = useState<string>()
    const [tradeActivityState, setTradeActivityState] = useState<TradeActivityState>()
    const [loadState, setLoadState] = useState<InvestmentLoadState>('loading')
    const [loadError, setLoadError] = useState<string>()
    const generationRef = useRef(0)
    const latestRequestRef = useRef(0)
    const tradeGenerationRef = useRef(0)
    const loadingEarlierRef = useRef(false)

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

    const loadLatest = useCallback(async (background: boolean, generation: number) => {
        const request = ++latestRequestRef.current
        const query = candleQuery(instrumentId, priceType, interval, now())
        if (!background) {
            setLoadState('loading')
            setLoadError(undefined)
            setLoadEarlierError(undefined)
            setIndicatorError(undefined)
            setIndicators(undefined)
            setCandles([])
            setLoadedFrom(undefined)
            setLoadedTo(undefined)
            setCanLoadEarlier(true)
        }
        try {
            const response = await listInvestmentCandles(query)
            if (generation !== generationRef.current || request !== latestRequestRef.current) {
                return
            }
            const closed = response.filter(({isClosed}) => isClosed)
            setCandles((current) => background ? mergeCandles(current, closed) : closed)
            setLoadedFrom((current) => background && current && current < query.from ? current : query.from)
            setLoadedTo(query.to)
            setLoadError(undefined)
            setLoadState((current) => background && current === 'ready'
                ? current
                : closed.length === 0 ? 'empty' : 'ready')
        } catch (reason) {
            if (generation !== generationRef.current || request !== latestRequestRef.current) {
                return
            }
            if (background) {
                return
            }
            setCandles([])
            setLoadState('error')
            setLoadError(errorMessage(reason, 'K 线加载失败'))
            return
        }
        try {
            const response = await getInvestmentIndicators(query)
            if (generation === generationRef.current && request === latestRequestRef.current) {
                setIndicators(response)
                setIndicatorError(undefined)
            }
        } catch (reason) {
            if (generation === generationRef.current && request === latestRequestRef.current) {
                setIndicatorError(errorMessage(reason, '指标暂不可用'))
            }
        }
    }, [instrumentId, interval, now, priceType])

    const reload = useCallback(() => {
        const generation = ++generationRef.current
        loadingEarlierRef.current = false
        setLoadingEarlier(false)
        void loadLatest(false, generation)
    }, [loadLatest])

    useEffect(() => {
        reload()
        return () => {
            generationRef.current += 1
        }
    }, [reload])

    useEffect(() => {
        if (refreshIntervalMs <= 0) {
            return
        }
        const timer = window.setInterval(
            () => void loadLatest(true, generationRef.current),
            refreshIntervalMs,
        )
        return () => window.clearInterval(timer)
    }, [loadLatest, refreshIntervalMs])

    const loadEarlier = useCallback(async () => {
        if (!loadedFrom || !canLoadEarlier || loadingEarlierRef.current) {
            return
        }
        const generation = generationRef.current
        const query = earlierCandleQuery(instrumentId, priceType, interval, loadedFrom, now())
        loadingEarlierRef.current = true
        setLoadingEarlier(true)
        setLoadEarlierError(undefined)
        try {
            const response = await listInvestmentCandles(query)
            if (generation !== generationRef.current) {
                return
            }
            const closed = response.filter(({isClosed}) => isClosed)
            if (closed.length === 0) {
                setCanLoadEarlier(false)
                return
            }
            setCandles((current) => mergeCandles(current, closed))
            setLoadedFrom(query.from)
        } catch (reason) {
            if (generation === generationRef.current) {
                setLoadEarlierError(errorMessage(reason, '更早 K 线加载失败'))
            }
        } finally {
            if (generation === generationRef.current) {
                loadingEarlierRef.current = false
                setLoadingEarlier(false)
            }
        }
    }, [canLoadEarlier, instrumentId, interval, loadedFrom, now, priceType])

    useEffect(() => {
        const generation = ++tradeGenerationRef.current
        const activityScope = tradeActivityScope(
            accountId, instrumentId, priceType, interval, loadedFrom, loadedTo,
        )
        setTradeActivityState(undefined)
        if (accountId === undefined || !loadedFrom || !loadedTo) {
            return
        }
        void listInvestmentPaperFills(
            accountId, instrumentId, loadedFrom, loadedTo, 1, 100,
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
    }, [accountId, instrumentId, interval, loadedFrom, loadedTo, priceType])

    const chartData = useMemo<CandlestickData<UTCTimestamp>[]>(
        () => toInvestmentCandlestickData(candles),
        [candles],
    )
    const latestIndicator = indicators?.points.at(-1)
    const currentActivityScope = tradeActivityScope(
        accountId, instrumentId, priceType, interval, loadedFrom, loadedTo,
    )
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
                    <Typography.Text type="secondary">
                        初始及每次向左加载 {candleWindowLabel(interval)}
                    </Typography.Text>
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
                onRetry={reload}
                state={loadState}
            >
                <InvestmentCandlestickChart
                    canLoadEarlier={canLoadEarlier}
                    data={chartData}
                    loadingEarlier={loadingEarlier}
                    markers={tradeMarkers}
                    onLoadEarlier={() => void loadEarlier()}
                    resetKey={`${instrumentId}:${priceType}:${interval}`}
                />
                {loadingEarlier && <Typography.Text type="secondary">正在加载更早的 K 线…</Typography.Text>}
                {!canLoadEarlier && <Typography.Text type="secondary">已到达当前可用历史数据起点</Typography.Text>}
                {loadEarlierError && (
                    <Alert
                        description={loadEarlierError}
                        showIcon
                        title="历史 K 线分片加载失败"
                        type="warning"
                    />
                )}
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
                    <Typography.Text type="secondary">
                        指标按最新 {candleWindowLabel(interval)}计算
                    </Typography.Text>
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
    nowMillis: number,
) {
    const dataAsOf = new Date(nowMillis)
    const to = candleQueryEnd(interval, dataAsOf)
    return candleWindowQuery(instrumentId, priceType, interval, to, dataAsOf)
}

export function earlierCandleQuery(
    instrumentId: number,
    priceType: InvestmentPriceType,
    interval: InvestmentBarInterval,
    currentFrom: string,
    nowMillis: number,
) {
    return candleWindowQuery(
        instrumentId,
        priceType,
        interval,
        new Date(currentFrom),
        new Date(nowMillis),
    )
}

export function candleWindowLabel(interval: InvestmentBarInterval) {
    return CANDLE_WINDOW_CONFIG[interval].label
}

function candleWindowQuery(
    instrumentId: number,
    priceType: InvestmentPriceType,
    interval: InvestmentBarInterval,
    to: Date,
    dataAsOf: Date,
) {
    const from = new Date(to.getTime() - CANDLE_WINDOW_CONFIG[interval].millis)
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

function candleQueryEnd(interval: InvestmentBarInterval, dataAsOf: Date) {
    return interval === 'D1'
        ? new Date(Date.UTC(dataAsOf.getUTCFullYear(), dataAsOf.getUTCMonth(), dataAsOf.getUTCDate()))
        : dataAsOf
}

function mergeCandles(
    current: InvestmentCandleResponse[],
    incoming: InvestmentCandleResponse[],
) {
    const merged = new Map(current.map((candle) => [candle.openTime, candle]))
    incoming.forEach((candle) => merged.set(candle.openTime, candle))
    return [...merged.values()].sort((left, right) => left.openTime.localeCompare(right.openTime))
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}

function tradeActivityScope(
    accountId: number | undefined,
    instrumentId: number,
    priceType: InvestmentPriceType,
    interval: InvestmentBarInterval,
    loadedFrom?: string,
    loadedTo?: string,
) {
    return `${accountId ?? 'none'}:${instrumentId}:${priceType}:${interval}:${loadedFrom ?? 'none'}:${loadedTo ?? 'none'}`
}
