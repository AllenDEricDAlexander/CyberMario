import type {SeriesMarker, SeriesMarkerBar, UTCTimestamp} from 'lightweight-charts'
import type {InvestmentCandleResponse} from '../types/investmentMarketTypes'
import type {InvestmentFillMarker} from '../types/investmentPortfolioTypes'

/** Maps private fills to the exact visible closed bar without coercing decimal prices. */
export function toInvestmentTradeMarkers(
    fills: InvestmentFillMarker[],
    candles: InvestmentCandleResponse[],
): SeriesMarker<UTCTimestamp>[] {
    const windows = candles
        .filter((candle) => candle.isClosed)
        .map((candle) => ({
            openMillis: Date.parse(candle.openTime),
            closeMillis: Date.parse(candle.closeTime),
            time: Math.floor(Date.parse(candle.openTime) / 1_000) as UTCTimestamp,
        }))
        .filter(({openMillis, closeMillis}) => Number.isFinite(openMillis) && Number.isFinite(closeMillis))
        .sort((left, right) => left.openMillis - right.openMillis)

    return fills.flatMap((fill) => {
        const eventMillis = Date.parse(fill.eventTime)
        if (!Number.isFinite(eventMillis)) return []
        const window = windows.find(({openMillis, closeMillis}) => (
            eventMillis >= openMillis && eventMillis < closeMillis
        ))
        if (!window) return []
        const marker = markerStyle(fill)
        return [{
            id: `fill:${fill.id}`,
            time: window.time,
            position: marker.position,
            shape: marker.shape,
            color: marker.color,
            text: marker.text,
        } satisfies SeriesMarker<UTCTimestamp>]
    }).sort((left, right) => Number(left.time) - Number(right.time) || String(left.id).localeCompare(String(right.id)))
}

function markerStyle(fill: InvestmentFillMarker): Omit<SeriesMarkerBar<UTCTimestamp>, 'id' | 'time'> {
    if (fill.liquidation) {
        return {position: 'aboveBar', shape: 'circle', color: '#b42318', text: '强平'}
    }
    if (fill.actionType === 'OPEN' && fill.side === 'LONG') {
        return {position: 'belowBar', shape: 'arrowUp', color: '#067647', text: '开多'}
    }
    if (fill.actionType === 'OPEN' && fill.side === 'SHORT') {
        return {position: 'aboveBar', shape: 'arrowDown', color: '#b42318', text: '开空'}
    }
    return {
        position: fill.side === 'LONG' ? 'aboveBar' : 'belowBar',
        shape: 'square',
        color: '#b54708',
        text: fill.actionType === 'REDUCE' ? '减仓' : '平仓',
    }
}
