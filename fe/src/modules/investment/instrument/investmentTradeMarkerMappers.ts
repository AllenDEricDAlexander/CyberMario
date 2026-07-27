import type {SeriesMarker, SeriesMarkerBar, UTCTimestamp} from 'lightweight-charts'
import {palette} from '../../../theme/designTokens'
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

/**
 * Marker colours come from the shared `palette` rather than `var(--…)`:
 * Lightweight Charts paints markers into a canvas, which cannot resolve CSS
 * custom properties. Sourcing the literals from the design tokens keeps them in
 * step with the rest of the console even though the theme cannot swap them.
 */
function markerStyle(fill: InvestmentFillMarker): Omit<SeriesMarkerBar<UTCTimestamp>, 'id' | 'time'> {
    if (fill.liquidation) {
        return {position: 'aboveBar', shape: 'circle', color: palette.error, text: '强平'}
    }
    if (fill.actionType === 'OPEN' && fill.side === 'LONG') {
        return {position: 'belowBar', shape: 'arrowUp', color: palette.success, text: '开多'}
    }
    if (fill.actionType === 'OPEN' && fill.side === 'SHORT') {
        return {position: 'aboveBar', shape: 'arrowDown', color: palette.error, text: '开空'}
    }
    return {
        position: fill.side === 'LONG' ? 'aboveBar' : 'belowBar',
        shape: 'square',
        color: palette.amber,
        text: fill.actionType === 'REDUCE' ? '减仓' : '平仓',
    }
}
