import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {MemoryRouter} from 'react-router'
import type {InvestmentInstrumentSummaryResponse} from '../types/investmentMarketTypes'
import InvestmentMarketPage from './InvestmentMarketPage'

const mocks = vi.hoisted(() => ({
    list: vi.fn(),
    listWatchlists: vi.fn(),
    addWatchlistItem: vi.fn(),
    workspace: null as null | {id: number; name: string},
}))

vi.mock('../services/investmentMarketService', () => ({
    listInvestmentInstruments: mocks.list,
    listInvestmentWatchlists: mocks.listWatchlists,
    addInvestmentWatchlistItem: mocks.addWatchlistItem,
}))

vi.mock('../hooks/useInvestmentWorkspace', () => ({
    useInvestmentWorkspace: () => ({currentWorkspace: mocks.workspace}),
}))

describe('InvestmentMarketPage', () => {
    beforeEach(() => {
        mocks.workspace = null
        mocks.list.mockReset()
        mocks.listWatchlists.mockReset()
        mocks.addWatchlistItem.mockReset()
    })

    test('shows an explicit empty universe and never loads private watchlists without a workspace', async () => {
        mocks.list.mockResolvedValue(page([]))
        renderPage()

        expect(await screen.findByText('暂无代码接入的合约')).toBeTruthy()
        expect(mocks.list).toHaveBeenCalledWith({page: 1, size: 20, status: undefined, sort: 'SYMBOL_ASC'})
        expect(mocks.listWatchlists).not.toHaveBeenCalled()
        expect(screen.queryByLabelText('订阅合约')).toBeNull()
    })

    test('reloads server data for status and sort filters and renders only returned instruments', async () => {
        mocks.list.mockResolvedValue(page([instrument]))
        renderPage()
        expect(await screen.findByText('BTCUSDT')).toBeTruthy()

        await userEvent.click(screen.getByLabelText('合约状态'))
        await userEvent.click(screen.getByText('交易中'))
        await waitFor(() => expect(mocks.list).toHaveBeenLastCalledWith({
            page: 1,
            size: 20,
            status: 'ACTIVE',
            sort: 'SYMBOL_ASC',
        }))

        await userEvent.click(screen.getByLabelText('合约排序'))
        await userEvent.click(screen.getByText('合约降序'))
        await waitFor(() => expect(mocks.list).toHaveBeenLastCalledWith({
            page: 1,
            size: 20,
            status: 'ACTIVE',
            sort: 'SYMBOL_DESC',
        }))
    })
})

const instrument: InvestmentInstrumentSummaryResponse = {
    instrumentId: 11,
    venueCode: 'BITGET',
    symbol: 'BTCUSDT',
    baseAsset: 'BTC',
    quoteAsset: 'USDT',
    status: 'ACTIVE',
    lastPrice: '65000.000000000000000001',
    markPrice: '64999.999999999999999999',
    change24h: '2.500000000000000000',
    dataAsOf: '2026-07-16T00:00:00Z',
    freshness: {status: 'FRESH', observedAt: '2026-07-16T00:00:00Z', ageSeconds: 0},
    availableCapabilities: ['LATEST_TICKER'],
}

function renderPage() {
    return render(<App><MemoryRouter><InvestmentMarketPage/></MemoryRouter></App>)
}

function page(records: InvestmentInstrumentSummaryResponse[]) {
    return {records, page: 1, size: 20, total: records.length, totalPages: records.length ? 1 : 0}
}
