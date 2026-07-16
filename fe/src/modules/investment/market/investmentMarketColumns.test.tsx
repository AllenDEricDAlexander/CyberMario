import {render, screen, within} from '@testing-library/react'
import {App, Table} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {MemoryRouter} from 'react-router'
import type {InvestmentInstrumentSummaryResponse} from '../types/investmentMarketTypes'
import {investmentMarketColumns} from './investmentMarketColumns'

const instrument: InvestmentInstrumentSummaryResponse = {
    instrumentId: 11,
    venueCode: 'BITGET',
    symbol: 'BTCUSDT',
    baseAsset: 'BTC',
    quoteAsset: 'USDT',
    status: 'ACTIVE',
    lastPrice: '12345678901234567890.123456789012345678',
    markPrice: '12345678901234567889.999999999999999999',
    change24h: '-1.250000000000000000',
    dataAsOf: '2026-07-16T00:00:00Z',
    freshness: {status: 'STALE', observedAt: '2026-07-15T23:00:00Z', ageSeconds: 3600},
    availableCapabilities: ['MARKET_CANDLE', 'LATEST_TICKER'],
}

describe('investmentMarketColumns', () => {
    test('keeps decimal strings, capabilities and an accessible detail link', () => {
        renderTable(true)

        const row = screen.getByRole('row', {name: /BTCUSDT/})
        expect(within(row).getByText(instrument.lastPrice!)).toBeTruthy()
        expect(within(row).getByText('STALE')).toBeTruthy()
        expect(within(row).getByText('MARKET_CANDLE')).toBeTruthy()
        expect(within(row).getByRole('link', {name: '查看 BTCUSDT 详情'}).getAttribute('href'))
            .toBe('/investment/instruments/11')
    })

    test('disables add-to-watchlist with a visible explanation when workspace is missing', () => {
        renderTable(false)

        const button = screen.getByRole('button', {name: '加入自选 BTCUSDT'})
        expect(button.hasAttribute('disabled')).toBe(true)
        expect(button.getAttribute('title')).toBe('请先选择或创建投资工作区')
    })
})

function renderTable(canAddToWatchlist: boolean) {
    return render(
        <App>
            <MemoryRouter>
                <Table
                    columns={investmentMarketColumns({
                        canAddToWatchlist,
                        watchlistDisabledReason: canAddToWatchlist ? undefined : '请先选择或创建投资工作区',
                        onAddToWatchlist: vi.fn(),
                    })}
                    dataSource={[instrument]}
                    pagination={false}
                    rowKey="instrumentId"
                />
            </MemoryRouter>
        </App>,
    )
}
