import type {CandlestickData, UTCTimestamp} from 'lightweight-charts'
import type {InvestmentCandleResponse} from '../types/investmentMarketTypes'

/**
 * The only boundary where lossless server decimals become chart-library numbers.
 */
export function toInvestmentCandlestickData(
    candles: InvestmentCandleResponse[],
): CandlestickData<UTCTimestamp>[] {
    return candles
        .filter(({isClosed}) => isClosed)
        .slice()
        .sort((left, right) => Date.parse(left.openTime) - Date.parse(right.openTime))
        .map((candle) => ({
            time: utcTimestamp(candle.openTime),
            open: chartNumber(candle.open, 'open'),
            high: chartNumber(candle.high, 'high'),
            low: chartNumber(candle.low, 'low'),
            close: chartNumber(candle.close, 'close'),
        }))
}

function utcTimestamp(value: string) {
    const milliseconds = Date.parse(value)
    if (!Number.isFinite(milliseconds)) {
        throw new Error(`Invalid candle UTC time: ${value}`)
    }
    return Math.floor(milliseconds / 1_000) as UTCTimestamp
}

function chartNumber(value: string, field: string) {
    const parsed = Number(value)
    if (!Number.isFinite(parsed)) {
        throw new Error(`Invalid candle ${field} decimal`)
    }
    return parsed
}
