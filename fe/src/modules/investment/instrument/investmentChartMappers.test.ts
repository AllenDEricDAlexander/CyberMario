import {describe, expect, test} from 'vitest'
import type {InvestmentCandleResponse} from '../types/investmentMarketTypes'
import {toInvestmentCandlestickData} from './investmentChartMappers'

describe('investmentChartMappers', () => {
    test('keeps only closed bars, sorts UTC ascending and converts decimals only at the chart boundary', () => {
        const result = toInvestmentCandlestickData([
            candle('2026-07-16T00:02:00.000Z', true, '2.125', '3.25', '1.75', '2.5'),
            candle('2026-07-16T00:03:00.000Z', false, '99', '100', '98', '99.5'),
            candle('2026-07-16T00:01:00.000Z', true, '1.125', '2.25', '0.75', '1.5'),
        ])

        expect(result).toEqual([
            {time: 1784160060, open: 1.125, high: 2.25, low: 0.75, close: 1.5},
            {time: 1784160120, open: 2.125, high: 3.25, low: 1.75, close: 2.5},
        ])
    })

    test('rejects invalid UTC time or decimal rather than drawing misleading data', () => {
        expect(() => toInvestmentCandlestickData([
            candle('not-a-time', true, '1', '2', '0', '1'),
        ])).toThrow('Invalid candle UTC time')
        expect(() => toInvestmentCandlestickData([
            candle('2026-07-16T00:01:00.000Z', true, 'not-a-number', '2', '0', '1'),
        ])).toThrow('Invalid candle open decimal')
    })
})

function candle(
    openTime: string,
    isClosed: boolean,
    open: string,
    high: string,
    low: string,
    close: string,
): InvestmentCandleResponse {
    return {
        openTime,
        closeTime: openTime,
        open,
        high,
        low,
        close,
        baseVolume: '10.00000000',
        quoteVolume: '20.00000000',
        isClosed,
        revision: 1,
        dataAsOf: '2026-07-16T00:10:00.000Z',
    }
}
