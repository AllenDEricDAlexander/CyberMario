import {
    CandlestickSeries,
    ColorType,
    createChart,
    createSeriesMarkers,
    type CandlestickData,
    type IChartApi,
    type ISeriesApi,
    type ISeriesMarkersPluginApi,
    type SeriesMarker,
    type Time,
    type UTCTimestamp,
} from 'lightweight-charts'
import {useEffect, useRef} from 'react'

const LOAD_EARLIER_THRESHOLD = 5

type InvestmentCandlestickChartProps = {
    data: CandlestickData<UTCTimestamp>[]
    markers?: SeriesMarker<UTCTimestamp>[]
    ariaLabel?: string
    height?: number
    canLoadEarlier?: boolean
    loadingEarlier?: boolean
    onLoadEarlier?: () => void
    resetKey?: string
}

/**
 * Direct official Lightweight Charts integration with one chart lifecycle per mount.
 */
export function InvestmentCandlestickChart({
    data,
    markers = [],
    ariaLabel = '合约 K 线图',
    height = 420,
    canLoadEarlier = false,
    loadingEarlier = false,
    onLoadEarlier,
    resetKey,
}: InvestmentCandlestickChartProps) {
    const containerRef = useRef<HTMLDivElement>(null)
    const chartRef = useRef<IChartApi | null>(null)
    const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
    const markersRef = useRef<ISeriesMarkersPluginApi<Time> | null>(null)
    const dataStateRef = useRef<{initialized: boolean; resetKey?: string; firstTime?: UTCTimestamp}>({
        initialized: false,
    })
    const userInteractedRef = useRef(false)
    const loadEarlierRef = useRef({canLoadEarlier, loadingEarlier, onLoadEarlier})

    useEffect(() => {
        loadEarlierRef.current = {canLoadEarlier, loadingEarlier, onLoadEarlier}
    }, [canLoadEarlier, loadingEarlier, onLoadEarlier])

    useEffect(() => {
        const container = containerRef.current
        if (!container) {
            return
        }
        const chart = createChart(container, {
            height,
            width: container.clientWidth,
            layout: {
                background: {type: ColorType.Solid, color: 'transparent'},
                textColor: '#5f6b7a',
            },
            rightPriceScale: {borderVisible: false},
            timeScale: {borderVisible: false, timeVisible: true, secondsVisible: false},
        })
        const series = chart.addSeries(CandlestickSeries, {
            upColor: '#16a085',
            downColor: '#d94f4f',
            borderVisible: false,
            wickUpColor: '#16a085',
            wickDownColor: '#d94f4f',
        })
        chartRef.current = chart
        seriesRef.current = series
        markersRef.current = createSeriesMarkers(series, [])
        const timeScale = chart.timeScale()
        const markUserInteraction = () => {
            userInteractedRef.current = true
        }
        const handleVisibleRangeChange = (range: {from: number; to: number} | null) => {
            const loadEarlier = loadEarlierRef.current
            if (!range || range.from > LOAD_EARLIER_THRESHOLD || !userInteractedRef.current) {
                return
            }
            userInteractedRef.current = false
            if (loadEarlier.canLoadEarlier && !loadEarlier.loadingEarlier && loadEarlier.onLoadEarlier) {
                loadEarlier.onLoadEarlier()
            }
        }
        container.addEventListener('pointerdown', markUserInteraction)
        container.addEventListener('wheel', markUserInteraction)
        timeScale.subscribeVisibleLogicalRangeChange(handleVisibleRangeChange)
        const resizeObserver = new ResizeObserver((entries) => {
            const width = entries[0]?.contentRect.width
            applyChartWidth(chart, width)
        })
        resizeObserver.observe(container)
        return () => {
            resizeObserver.disconnect()
            timeScale.unsubscribeVisibleLogicalRangeChange(handleVisibleRangeChange)
            container.removeEventListener('pointerdown', markUserInteraction)
            container.removeEventListener('wheel', markUserInteraction)
            markersRef.current?.detach()
            markersRef.current = null
            seriesRef.current = null
            chartRef.current = null
            dataStateRef.current = {initialized: false}
            userInteractedRef.current = false
            chart.remove()
        }
    }, [height])

    useEffect(() => {
        const series = seriesRef.current
        const chart = chartRef.current
        if (!series || !chart) {
            return
        }
        const timeScale = chart.timeScale()
        const previous = dataStateRef.current
        const reset = !previous.initialized || previous.resetKey !== resetKey
        const visibleRange = reset ? null : timeScale.getVisibleLogicalRange()
        series.setData(data)
        if (data.length > 0 && (reset || previous.firstTime === undefined)) {
            timeScale.fitContent()
        } else if (visibleRange && previous.firstTime !== undefined) {
            const prependedCount = data.findIndex(({time}) => time === previous.firstTime)
            if (prependedCount > 0) {
                timeScale.setVisibleLogicalRange({
                    from: visibleRange.from + prependedCount,
                    to: visibleRange.to + prependedCount,
                })
            }
        }
        dataStateRef.current = {
            initialized: true,
            resetKey,
            firstTime: data[0]?.time,
        }
    }, [data, resetKey])

    useEffect(() => {
        markersRef.current?.setMarkers(markers)
    }, [markers])

    return <div aria-label={ariaLabel} className="investment-candlestick-chart" ref={containerRef} role="img"/>
}

export function applyChartWidth(chart: Pick<IChartApi, 'applyOptions'>, width?: number) {
    if (width && width > 0) {
        chart.applyOptions({width})
    }
}
