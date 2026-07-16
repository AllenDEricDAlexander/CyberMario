import {describe, expect, test} from 'vitest'
import type {InvestmentCandleResponse} from '../types/investmentMarketTypes'
import type {InvestmentFillMarker} from '../types/investmentPortfolioTypes'
import {toInvestmentTradeMarkers} from './investmentTradeMarkerMappers'

describe('investmentTradeMarkerMappers', () => {
    test('aligns fills to the containing closed bar and preserves accessible direction text', () => {
        const markers = toInvestmentTradeMarkers([
            fill(3, '2026-07-17T01:10:00Z', 'SHORT', 'OPEN', false),
            fill(1, '2026-07-17T00:05:00Z', 'LONG', 'OPEN', false),
            fill(2, '2026-07-17T00:20:00Z', 'LONG', 'CLOSE', true),
        ], [candle('2026-07-17T00:00:00Z', '2026-07-17T01:00:00Z'),
            candle('2026-07-17T01:00:00Z', '2026-07-17T02:00:00Z')])

        expect(markers.map(({id, time, text, shape}) => ({id, time, text, shape}))).toEqual([
            {id: 'fill:1', time: 1_784_246_400, text: '开多', shape: 'arrowUp'},
            {id: 'fill:2', time: 1_784_246_400, text: '强平', shape: 'circle'},
            {id: 'fill:3', time: 1_784_250_000, text: '开空', shape: 'arrowDown'},
        ])
    })

    test('drops invalid, out-of-range, and unclosed activity rather than placing misleading markers', () => {
        expect(toInvestmentTradeMarkers([
            fill(1, 'invalid', 'LONG', 'OPEN', false),
            fill(2, '2026-07-18T00:00:00Z', 'LONG', 'OPEN', false),
        ], [{...candle('2026-07-17T00:00:00Z', '2026-07-17T01:00:00Z'), isClosed: false}])).toEqual([])
    })
})

function candle(openTime: string, closeTime: string): InvestmentCandleResponse {
    return {
        openTime, closeTime, open: '100', high: '110', low: '90', close: '105',
        baseVolume: '1', quoteVolume: '100', isClosed: true, revision: 1,
        dataAsOf: '2026-07-17T02:00:00Z',
    }
}

function fill(
    id: number,
    eventTime: string,
    side: string,
    actionType: string,
    liquidation: boolean,
): InvestmentFillMarker {
    return {
        id, instrumentId: 11, marketBarOpenTime: null, eventTime, side, actionType,
        orderOrigin: 'AGENT', eventType: liquidation ? 'LIQUIDATION' : 'FILL',
        price: '101.2', quantity: '0.5', liquidation,
    }
}
