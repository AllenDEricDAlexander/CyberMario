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

type InvestmentCandlestickChartProps = {
    data: CandlestickData<UTCTimestamp>[]
    markers?: SeriesMarker<UTCTimestamp>[]
    ariaLabel?: string
    height?: number
}

/**
 * Direct official Lightweight Charts integration with one chart lifecycle per mount.
 */
export function InvestmentCandlestickChart({
    data,
    markers = [],
    ariaLabel = '合约 K 线图',
    height = 420,
}: InvestmentCandlestickChartProps) {
    const containerRef = useRef<HTMLDivElement>(null)
    const chartRef = useRef<IChartApi | null>(null)
    const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
    const markersRef = useRef<ISeriesMarkersPluginApi<Time> | null>(null)

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
        const resizeObserver = new ResizeObserver((entries) => {
            const width = entries[0]?.contentRect.width
            applyChartWidth(chart, width)
        })
        resizeObserver.observe(container)
        return () => {
            resizeObserver.disconnect()
            markersRef.current?.detach()
            markersRef.current = null
            seriesRef.current = null
            chartRef.current = null
            chart.remove()
        }
    }, [height])

    useEffect(() => {
        seriesRef.current?.setData(data)
        if (data.length > 0) {
            chartRef.current?.timeScale().fitContent()
        }
    }, [data])

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
