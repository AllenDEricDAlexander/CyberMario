import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import type {
    InvestmentCandleQuery,
    InvestmentCandleResponse,
    InvestmentIndicatorSnapshot,
} from '../types/investmentMarketTypes'
import {InvestmentKlinePanel} from './InvestmentKlinePanel'

const mocks = vi.hoisted(() => ({
    candles: vi.fn(),
    indicators: vi.fn(),
    fills: vi.fn(),
}))

vi.mock('../services/investmentMarketService', () => ({
    listInvestmentCandles: mocks.candles,
    getInvestmentIndicators: mocks.indicators,
}))

vi.mock('../services/investmentPortfolioService', () => ({
    listInvestmentPaperFills: mocks.fills,
}))

vi.mock('../components/InvestmentCandlestickChart', () => ({
    InvestmentCandlestickChart: ({data, markers}: {data: unknown[]; markers: unknown[]}) => (
        <div aria-label="测试 K 线图">
            <span>{data.length} points</span>
            <span>{markers.length} markers</span>
        </div>
    ),
}))

describe('InvestmentKlinePanel', () => {
    beforeEach(() => {
        mocks.candles.mockReset()
        mocks.indicators.mockReset()
        mocks.fills.mockReset().mockResolvedValue({records: [], page: 1, size: 100, total: 0, totalPages: 0})
        mocks.candles.mockResolvedValue([candle('2026-07-15T23:59:00.000Z', true)])
        mocks.indicators.mockResolvedValue(indicatorSnapshot())
    })

    test('uses one frozen cutoff and renders only closed candles with an accessible table fallback', async () => {
        mocks.candles.mockResolvedValue([
            candle('2026-07-15T23:59:00.000Z', true),
            candle('2026-07-16T00:00:00.000Z', false),
        ])
        renderPanel()

        expect(await screen.findByText('1 points')).toBeTruthy()
        expect(screen.getByText(/共 1 根已关闭 K 线/)).toBeTruthy()
        expect(screen.getByText('65000.000000000000000001')).toBeTruthy()
        await waitFor(() => expect(mocks.indicators).toHaveBeenCalledTimes(1))

        const candleQuery = mocks.candles.mock.calls[0][0] as InvestmentCandleQuery
        const indicatorQuery = mocks.indicators.mock.calls[0][0] as InvestmentCandleQuery
        expect(candleQuery).toEqual({
            instrumentId: 11,
            priceType: 'MARKET',
            interval: 'M1',
            from: '2026-07-15T00:00:00.000Z',
            to: '2026-07-16T00:00:00.000Z',
            dataAsOf: '2026-07-16T00:00:00.000Z',
            limit: 2_000,
        })
        expect(indicatorQuery).toEqual(candleQuery)
        expect(screen.queryByText('66000.000000000000000001')).toBeNull()
    })

    test('reloads the server boundary when price type, interval or range changes', async () => {
        renderPanel()
        await screen.findByText('1 points')

        await selectOption('K 线价型', 'MARK')
        await waitFor(() => expect(mocks.candles).toHaveBeenLastCalledWith(expect.objectContaining({priceType: 'MARK'})))

        await selectOption('K 线周期', 'H1')
        await waitFor(() => expect(mocks.candles).toHaveBeenLastCalledWith(expect.objectContaining({interval: 'H1'})))

        await selectOption('K 线范围', '7 天')
        await waitFor(() => expect(mocks.candles).toHaveBeenLastCalledWith(expect.objectContaining({
            priceType: 'MARK',
            interval: 'H1',
            from: '2026-07-09T00:00:00.000Z',
            to: '2026-07-16T00:00:00.000Z',
        })))
    })

    test('suppresses an older candle response after the selected price type changes', async () => {
        const first = deferred<InvestmentCandleResponse[]>()
        mocks.candles.mockReset()
        mocks.candles.mockReturnValueOnce(first.promise)
            .mockResolvedValueOnce([candle('2026-07-15T23:58:00.000Z', true, '65111.000000000000000001')])
        renderPanel()
        await waitFor(() => expect(mocks.candles).toHaveBeenCalledTimes(1))

        await selectOption('K 线价型', 'MARK')
        expect(await screen.findByText('65111.000000000000000001')).toBeTruthy()

        first.resolve([candle('2026-07-15T23:57:00.000Z', true, '65222.000000000000000001')])
        await Promise.resolve()

        expect(screen.queryByText('65222.000000000000000001')).toBeNull()
        expect(screen.getByText('65111.000000000000000001')).toBeTruthy()
    })

    test('keeps candles visible when the optional indicator request fails', async () => {
        mocks.indicators.mockRejectedValue(new Error('indicator capability unavailable'))
        renderPanel()

        expect(await screen.findByText('1 points')).toBeTruthy()
        expect(await screen.findByText('技术指标独立加载失败')).toBeTruthy()
        expect(screen.getByText('indicator capability unavailable')).toBeTruthy()
        expect(screen.getByText('65000.000000000000000001')).toBeTruthy()
    })

    test('keeps public candles visible when the private marker request fails', async () => {
        mocks.fills.mockRejectedValue(new Error('private activity unavailable'))
        renderPanel(21)

        expect(await screen.findByText('1 points')).toBeTruthy()
        expect(await screen.findByText('私人交易标记独立加载失败')).toBeTruthy()
        expect(screen.getByText('private activity unavailable')).toBeTruthy()
        expect(screen.getByText('65000.000000000000000001')).toBeTruthy()
    })

    test('loads private markers independently and clears them immediately when the account changes', async () => {
        mocks.fills.mockResolvedValueOnce({
            records: [fill(71)], page: 1, size: 100, total: 1, totalPages: 1,
        }).mockResolvedValueOnce({records: [], page: 1, size: 100, total: 0, totalPages: 0})
        const view = renderPanel(21)

        expect(await screen.findByText('1 markers')).toBeTruthy()
        expect(screen.getByText('AGENT')).toBeTruthy()
        expect(mocks.fills).toHaveBeenCalledWith(
            21, 11, '2026-07-15T00:00:00.000Z', '2026-07-16T00:00:00.000Z', 1, 100,
        )

        view.rerender(panel(22))

        expect(screen.getByText('1 points')).toBeTruthy()
        expect(screen.queryByText('1 markers')).toBeNull()
        expect(screen.queryByText('AGENT')).toBeNull()
        expect(await screen.findByText('0 markers')).toBeTruthy()
        await waitFor(() => expect(mocks.fills).toHaveBeenLastCalledWith(
            22, 11, '2026-07-15T00:00:00.000Z', '2026-07-16T00:00:00.000Z', 1, 100,
        ))
    })
})

function renderPanel(accountId?: number) {
    return render(panel(accountId))
}

function panel(accountId?: number) {
    return (
        <App>
            <InvestmentKlinePanel
                accountId={accountId}
                availableIntervals={['M1', 'H1']}
                availablePriceTypes={['MARKET', 'MARK']}
                instrumentId={11}
                now={now}
            />
        </App>
    )
}

const now = () => Date.parse('2026-07-16T00:00:00.000Z')

async function selectOption(label: string, option: string) {
    await userEvent.click(screen.getByLabelText(label))
    const options = await screen.findAllByText(option)
    await userEvent.click(options.at(-1) as HTMLElement)
}

function candle(
    openTime: string,
    isClosed: boolean,
    close = isClosed ? '65000.000000000000000001' : '66000.000000000000000001',
): InvestmentCandleResponse {
    return {
        openTime,
        closeTime: new Date(Date.parse(openTime) + 60_000).toISOString(),
        open: '64000.000000000000000001',
        high: '67000.000000000000000001',
        low: '63000.000000000000000001',
        close,
        baseVolume: '10.000000000000000001',
        quoteVolume: '650000.000000000000000001',
        isClosed,
        revision: 1,
        dataAsOf: '2026-07-16T00:00:00.000Z',
    }
}

function indicatorSnapshot(): InvestmentIndicatorSnapshot {
    return {
        instrumentId: 11,
        priceType: 'MARKET',
        interval: 'M1',
        dataStartTime: '2026-07-15T00:00:00.000Z',
        dataEndTime: '2026-07-16T00:00:00.000Z',
        dataAsOf: '2026-07-16T00:00:00.000Z',
        inputHash: 'hash',
        revisions: [1],
        points: [{
            openTime: '2026-07-15T23:59:00.000Z',
            close: '65000',
            sma20: '64900',
            ema20: '64800',
            rsi14: '55',
            macd: '10',
            macdSignal: '9',
            macdHistogram: '1',
            bollingerUpper: '67000',
            bollingerMiddle: '65000',
            bollingerLower: '63000',
            atr14: '500',
        }],
    }
}

function fill(id: number) {
    return {
        id, instrumentId: 11, marketBarOpenTime: '2026-07-15T23:59:00.000Z',
        eventTime: '2026-07-15T23:59:30.000Z', side: 'LONG', actionType: 'OPEN',
        orderOrigin: 'AGENT', eventType: 'FILL', price: '65000', quantity: '0.1', liquidation: false,
    }
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
