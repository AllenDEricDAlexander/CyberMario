import {act, renderHook} from '@testing-library/react'
import {afterEach, describe, expect, test, vi} from 'vitest'
import type {InvestmentBacktestRunResponse, InvestmentBacktestStatus} from '../types/investmentQuantTypes'
import {useBacktestRunPolling} from './useBacktestRunPolling'

describe('useBacktestRunPolling', () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    test('polls every two seconds and stops at a terminal status', async () => {
        vi.useFakeTimers()
        const loadRun = vi.fn()
            .mockResolvedValueOnce(run(51, 'QUEUED'))
            .mockResolvedValueOnce(run(51, 'SUCCEEDED'))
        const now = () => 0
        const {result} = renderHook(() => useBacktestRunPolling(51, {loadRun, now}))

        await flushPromises()
        expect(result.current.run?.status).toBe('QUEUED')
        expect(result.current.polling).toBe(true)

        await act(async () => vi.advanceTimersByTimeAsync(2_000))

        expect(result.current.run?.status).toBe('SUCCEEDED')
        expect(result.current.polling).toBe(false)
        await act(async () => vi.advanceTimersByTimeAsync(10_000))
        expect(loadRun).toHaveBeenCalledTimes(2)
    })

    test('backs off to five seconds after the first minute and manual refresh is immediate', async () => {
        vi.useFakeTimers()
        let nowValue = 0
        const now = () => nowValue
        const loadRun = vi.fn().mockResolvedValue(run(51, 'RUNNING'))
        const {result} = renderHook(() => useBacktestRunPolling(51, {loadRun, now}))
        await flushPromises()
        nowValue = 61_000

        await act(async () => vi.advanceTimersByTimeAsync(2_000))
        expect(loadRun).toHaveBeenCalledTimes(2)
        await act(async () => vi.advanceTimersByTimeAsync(4_999))
        expect(loadRun).toHaveBeenCalledTimes(2)
        await act(async () => vi.advanceTimersByTimeAsync(1))
        expect(loadRun).toHaveBeenCalledTimes(3)

        act(() => result.current.refresh())
        await flushPromises()
        expect(loadRun).toHaveBeenCalledTimes(4)
    })

    test('cleans timers and suppresses a stale response after the run changes', async () => {
        vi.useFakeTimers()
        const first = deferred<InvestmentBacktestRunResponse>()
        const loadRun = vi.fn()
            .mockReturnValueOnce(first.promise)
            .mockResolvedValueOnce(run(52, 'RUNNING'))
        const now = () => 0
        const {result, rerender, unmount} = renderHook(
            ({runId}) => useBacktestRunPolling(runId, {loadRun, now}),
            {initialProps: {runId: 51}},
        )

        rerender({runId: 52})
        await flushPromises()
        expect(result.current.run?.runId).toBe(52)

        first.resolve(run(51, 'FAILED'))
        await flushPromises()
        expect(result.current.run?.runId).toBe(52)

        unmount()
        await act(async () => vi.advanceTimersByTimeAsync(10_000))
        expect(loadRun).toHaveBeenCalledTimes(2)
    })
})

async function flushPromises() {
    await act(async () => {
        await Promise.resolve()
        await Promise.resolve()
    })
}

function run(runId: number, status: InvestmentBacktestStatus): InvestmentBacktestRunResponse {
    return {
        runId,
        workspaceId: 7,
        jobId: 61,
        strategyReleaseId: 31,
        datasetSnapshotId: 41,
        status,
        initialEquity: '10000',
        totalReturn: null,
        annualizedReturn: null,
        maxDrawdown: null,
        sharpeRatio: null,
        sortinoRatio: null,
        winRate: null,
        profitFactor: null,
        turnover: null,
        tradeCount: null,
        totalFee: null,
        totalFunding: null,
        liquidationCount: null,
        errorCode: null,
        errorMessage: null,
        startedAt: null,
        finishedAt: null,
        createdAt: '2026-07-16T00:00:00Z',
    }
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
