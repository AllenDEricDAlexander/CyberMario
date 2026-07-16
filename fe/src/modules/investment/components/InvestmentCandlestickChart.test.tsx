import {render, screen} from '@testing-library/react'
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest'
import type {CandlestickData, UTCTimestamp} from 'lightweight-charts'
import {applyChartWidth, InvestmentCandlestickChart} from './InvestmentCandlestickChart'

const mocks = vi.hoisted(() => ({
    createChart: vi.fn(),
    addSeries: vi.fn(),
    applyOptions: vi.fn(),
    fitContent: vi.fn(),
    remove: vi.fn(),
    setData: vi.fn(),
    disconnect: vi.fn(),
    observe: vi.fn(),
}))

vi.mock('lightweight-charts', () => ({
    CandlestickSeries: Symbol('CandlestickSeries'),
    ColorType: {Solid: 'Solid'},
    createChart: mocks.createChart,
}))

describe('InvestmentCandlestickChart', () => {
    beforeEach(() => {
        Object.values(mocks).forEach((value) => {
            if (typeof value === 'function' && 'mockReset' in value) {
                value.mockReset()
            }
        })
        mocks.addSeries.mockReturnValue({setData: mocks.setData})
        mocks.createChart.mockReturnValue({
            addSeries: mocks.addSeries,
            applyOptions: mocks.applyOptions,
            remove: mocks.remove,
            timeScale: () => ({fitContent: mocks.fitContent}),
        })
        vi.spyOn(ResizeObserver.prototype, 'disconnect').mockImplementation(mocks.disconnect)
        vi.spyOn(ResizeObserver.prototype, 'observe').mockImplementation(mocks.observe)
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test('creates one official chart lifecycle and updates the existing series data', () => {
        const first = [bar(1, 100)]
        const second = [bar(1, 100), bar(2, 101)]
        const {rerender, unmount} = render(<InvestmentCandlestickChart data={first}/>)

        expect(screen.getByRole('img', {name: '合约 K 线图'})).toBeTruthy()
        expect(mocks.createChart).toHaveBeenCalledTimes(1)
        expect(mocks.addSeries).toHaveBeenCalledTimes(1)
        expect(mocks.setData).toHaveBeenLastCalledWith(first)
        expect(mocks.fitContent).toHaveBeenCalledTimes(1)

        rerender(<InvestmentCandlestickChart data={second}/>)

        expect(mocks.createChart).toHaveBeenCalledTimes(1)
        expect(mocks.addSeries).toHaveBeenCalledTimes(1)
        expect(mocks.setData).toHaveBeenLastCalledWith(second)
        expect(mocks.fitContent).toHaveBeenCalledTimes(2)

        unmount()
        expect(mocks.disconnect).toHaveBeenCalledTimes(1)
        expect(mocks.remove).toHaveBeenCalledTimes(1)
    })

    test('resizes the existing chart without recreating it', () => {
        render(<InvestmentCandlestickChart data={[]}/>)
        const target = screen.getByRole('img', {name: '合约 K 线图'})

        applyChartWidth({applyOptions: mocks.applyOptions}, 720)

        expect(mocks.applyOptions).toHaveBeenCalledWith({width: 720})
        expect(mocks.createChart).toHaveBeenCalledTimes(1)
        expect(mocks.fitContent).not.toHaveBeenCalled()
        expect(mocks.observe).toHaveBeenCalledWith(target)
    })
})

function bar(time: number, close: number): CandlestickData<UTCTimestamp> {
    return {time: time as UTCTimestamp, open: close - 1, high: close + 1, low: close - 2, close}
}
