import {Alert, Card, Flex, Select, Table, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import type {CandlestickData, UTCTimestamp} from 'lightweight-charts'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {InvestmentCandlestickChart} from '../components/InvestmentCandlestickChart'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {getInvestmentIndicators, listInvestmentCandles} from '../services/investmentMarketService'
import type {InvestmentDecimal, InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentBarInterval,
    InvestmentCandleResponse,
    InvestmentIndicatorSnapshot,
    InvestmentPriceType,
} from '../types/investmentMarketTypes'
import {toInvestmentCandlestickData} from './investmentChartMappers'

type InvestmentKlinePanelProps = {
    instrumentId: number
    availablePriceTypes: InvestmentPriceType[]
    availableIntervals: InvestmentBarInterval[]
    now?: () => number
}

type InvestmentRange = '1H' | '24H' | '7D'

export function InvestmentKlinePanel({
    instrumentId,
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
    const [loadState, setLoadState] = useState<InvestmentLoadState>('loading')
    const [loadError, setLoadError] = useState<string>()
    const generationRef = useRef(0)

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

    const chartData = useMemo<CandlestickData<UTCTimestamp>[]>(
        () => toInvestmentCandlestickData(candles),
        [candles],
    )
    const latestIndicator = indicators?.points.at(-1)

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
                <InvestmentCandlestickChart data={chartData}/>
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
