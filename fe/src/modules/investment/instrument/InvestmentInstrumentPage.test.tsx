import {screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {Link, Route, Routes} from 'react-router'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {renderInvestmentPage} from '../test/renderInvestmentPage'
import type {
    InvestmentInstrumentDetailResponse,
    InvestmentQuoteResponse,
} from '../types/investmentMarketTypes'
import InvestmentInstrumentPage from './InvestmentInstrumentPage'

const mocks = vi.hoisted(() => ({
    instrument: vi.fn(),
    quote: vi.fn(),
    funding: vi.fn(),
    tiers: vi.fn(),
}))

vi.mock('../services/investmentMarketService', () => ({
    getInvestmentInstrument: mocks.instrument,
    getInvestmentQuote: mocks.quote,
    listInvestmentFundingRates: mocks.funding,
    listInvestmentPositionTiers: mocks.tiers,
}))

vi.mock('./InvestmentKlinePanel', () => ({
    InvestmentKlinePanel: ({instrumentId}: {instrumentId: number}) => (
        <div aria-label="K 线面板">instrument {instrumentId}</div>
    ),
}))

describe('InvestmentInstrumentPage', () => {
    beforeEach(() => {
        mocks.instrument.mockReset()
        mocks.quote.mockReset()
        mocks.funding.mockReset()
        mocks.tiers.mockReset()
        mocks.instrument.mockResolvedValue(detail(11, 'BTCUSDT'))
        mocks.quote.mockResolvedValue(quote(11))
        mocks.funding.mockResolvedValue({records: [], page: 1, size: 200, total: 0, totalPages: 0})
        mocks.tiers.mockResolvedValue([])
    })

    test('renders primary quote/spec/K-line data while optional panels fail independently', async () => {
        mocks.funding.mockRejectedValue(new Error('funding temporarily unavailable'))
        mocks.tiers.mockResolvedValue([{
            tierLevel: 1,
            startNotional: '0.000000000000000001',
            endNotional: '100000.000000000000000001',
            maxLeverage: '50.000000000000000001',
            maintenanceMarginRate: '0.005000000000000001',
            observedAt: '2026-07-16T00:00:00.000Z',
            dataAsOf: '2026-07-16T00:00:00.000Z',
        }])
        renderPage('/investment/instruments/11')

        expect(await screen.findByRole('heading', {name: 'BTCUSDT'})).toBeTruthy()
        expect(screen.getByLabelText('K 线面板').textContent).toContain('11')
        expect(screen.getByText('65000.000000000000000001')).toBeTruthy()
        expect(screen.getByText('0.005000000000000001')).toBeTruthy()
        expect(await screen.findByText('资金费率独立加载失败')).toBeTruthy()
        expect(screen.getByText('funding temporarily unavailable')).toBeTruthy()

        expect(mocks.funding).toHaveBeenCalledWith(
            11,
            '2026-06-16T00:00:00.000Z',
            '2026-07-16T00:00:00.000Z',
            '2026-07-16T00:00:00.000Z',
        )
        expect(mocks.tiers).toHaveBeenCalledWith(11, '2026-07-16T00:00:00.000Z')
    })

    test('ignores the previous route response after the instrument id changes', async () => {
        const oldInstrument = deferred<InvestmentInstrumentDetailResponse>()
        mocks.instrument.mockImplementation((instrumentId: number) => instrumentId === 11
            ? oldInstrument.promise
            : Promise.resolve(detail(12, 'ETHUSDT')))
        mocks.quote.mockImplementation((instrumentId: number) => Promise.resolve(quote(instrumentId)))
        renderPage('/investment/instruments/11', true)
        await waitFor(() => expect(mocks.instrument).toHaveBeenCalledWith(11))

        await userEvent.click(screen.getByRole('link', {name: '下一个合约'}))
        expect(await screen.findByRole('heading', {name: 'ETHUSDT'})).toBeTruthy()

        oldInstrument.resolve(detail(11, 'BTCUSDT'))
        await Promise.resolve()

        expect(screen.queryByRole('heading', {name: 'BTCUSDT'})).toBeNull()
        expect(screen.getByRole('heading', {name: 'ETHUSDT'})).toBeTruthy()
        expect(mocks.quote).not.toHaveBeenCalledWith(11)
    })

    test('rejects an invalid route parameter without calling market APIs', async () => {
        renderPage('/investment/instruments/not-a-number')

        expect(await screen.findByText('无效的合约编号')).toBeTruthy()
        expect(mocks.instrument).not.toHaveBeenCalled()
        expect(mocks.quote).not.toHaveBeenCalled()
    })

    test('does not request capabilities that are absent from the code subscription', async () => {
        const noOptionalCapability = detail(11, 'BTCUSDT')
        noOptionalCapability.availableCapabilities = ['CONTRACT_METADATA']
        noOptionalCapability.availablePriceTypes = []
        noOptionalCapability.availableIntervals = []
        mocks.instrument.mockResolvedValue(noOptionalCapability)
        renderPage('/investment/instruments/11')

        expect(await screen.findByRole('heading', {name: 'BTCUSDT'})).toBeTruthy()
        expect(screen.getByText('该合约尚未接入最新行情能力')).toBeTruthy()
        expect(screen.getByText('该合约尚未接入 K 线能力')).toBeTruthy()
        expect(screen.getByText('该合约尚未接入资金费率')).toBeTruthy()
        expect(screen.getByText('该合约尚未接入仓位档位')).toBeTruthy()
        expect(mocks.quote).not.toHaveBeenCalled()
        expect(mocks.funding).not.toHaveBeenCalled()
        expect(mocks.tiers).not.toHaveBeenCalled()
    })
})

function renderPage(path: string, withNavigation = false) {
    return renderInvestmentPage(
        <Routes>
            <Route
                element={(
                    <>
                        {withNavigation && <Link to="/investment/instruments/12">下一个合约</Link>}
                        <InvestmentInstrumentPage/>
                    </>
                )}
                path="/investment/instruments/:instrumentId"
            />
        </Routes>,
        [path],
    )
}

function detail(instrumentId: number, symbol: string): InvestmentInstrumentDetailResponse {
    return {
        instrumentId,
        venueCode: 'BITGET',
        symbol,
        baseAsset: symbol.startsWith('BTC') ? 'BTC' : 'ETH',
        quoteAsset: 'USDT',
        settlementAsset: 'USDT',
        marginAsset: 'USDT',
        productType: 'USDT_FUTURES',
        contractType: 'PERPETUAL',
        status: 'ACTIVE',
        launchTime: '2024-01-01T00:00:00.000Z',
        availableCapabilities: ['CONTRACT_METADATA', 'LATEST_TICKER', 'MARKET_CANDLE', 'FUNDING_RATE', 'POSITION_TIER'],
        availablePriceTypes: ['MARKET'],
        availableIntervals: ['M1'],
        dataAsOf: '2026-07-16T00:00:00.000Z',
        freshness: {status: 'FRESH', observedAt: '2026-07-16T00:00:00.000Z', ageSeconds: 0},
        contractSpecAvailable: true,
        contractSpec: {
            pricePrecision: 2,
            quantityPrecision: 6,
            priceEndStep: '0.01',
            quantityStep: '0.000001',
            contractMultiplier: '1.000000000000000001',
            minTradeQuantity: '0.000001',
            minTradeNotional: '5.000000000000000001',
            maxMarketOrderQuantity: '100.000000000000000001',
            maxLimitOrderQuantity: '200.000000000000000001',
            makerFeeRate: '0.000200000000000001',
            takerFeeRate: '0.000600000000000001',
            minLeverage: '1.000000000000000001',
            maxLeverage: '50.000000000000000001',
            fundingIntervalHours: 8,
            buyLimitPriceRatio: '1.050000000000000001',
            sellLimitPriceRatio: '0.950000000000000001',
            sourceUpdatedAt: '2026-07-16T00:00:00.000Z',
            ingestedAt: '2026-07-16T00:00:00.000Z',
            revision: 2,
        },
    }
}

function quote(instrumentId: number): InvestmentQuoteResponse {
    return {
        instrumentId,
        lastPrice: '65000.000000000000000001',
        markPrice: '64999.000000000000000001',
        indexPrice: '64998.000000000000000001',
        bidPrice: '64997.000000000000000001',
        askPrice: '64999.000000000000000001',
        bidQuantity: '1.000000000000000001',
        askQuantity: '2.000000000000000001',
        open24h: '64000.000000000000000001',
        high24h: '66000.000000000000000001',
        low24h: '63000.000000000000000001',
        baseVolume24h: '1000.000000000000000001',
        quoteVolume24h: '65000000.000000000000000001',
        change24h: '1.500000000000000001',
        fundingRate: '0.000100000000000001',
        nextFundingTime: '2026-07-16T08:00:00.000Z',
        openInterest: '500.000000000000000001',
        sourceTime: '2026-07-16T00:00:00.000Z',
        receivedAt: '2026-07-16T00:00:00.000Z',
        version: 1,
        dataAsOf: '2026-07-16T00:00:00.000Z',
        freshness: {status: 'FRESH', observedAt: '2026-07-16T00:00:00.000Z', ageSeconds: 0},
    }
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
