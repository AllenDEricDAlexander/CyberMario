import {act, renderHook} from '@testing-library/react'
import {afterEach, describe, expect, test, vi} from 'vitest'
import type {
    InvestmentAgentRunDetailResponse,
    InvestmentAgentRunStatus,
} from '../types/investmentAgentTypes'
import {useAgentRunPolling} from './useAgentRunPolling'

describe('useAgentRunPolling', () => {
    afterEach(() => vi.useRealTimers())

    test('polls with the bounded policy and stops after a terminal result', async () => {
        vi.useFakeTimers()
        const loadRun = vi.fn()
            .mockResolvedValueOnce(detail(41, 'RUNNING'))
            .mockResolvedValueOnce(detail(41, 'SUCCEEDED'))
        const now = () => 0
        const {result} = renderHook(() => useAgentRunPolling(41, {loadRun, now}))

        await flushPromises()
        expect(result.current.detail?.run.status).toBe('RUNNING')
        await act(async () => vi.advanceTimersByTimeAsync(2_000))
        expect(result.current.detail?.run.status).toBe('SUCCEEDED')
        expect(result.current.polling).toBe(false)
        await act(async () => vi.advanceTimersByTimeAsync(10_000))
        expect(loadRun).toHaveBeenCalledTimes(2)
    })

    test('backs off after one minute and refreshes immediately', async () => {
        vi.useFakeTimers()
        let nowValue = 0
        const loadRun = vi.fn().mockResolvedValue(detail(41, 'RUNNING'))
        const now = () => nowValue
        const {result} = renderHook(() => useAgentRunPolling(41, {loadRun, now}))
        await flushPromises()
        nowValue = 61_000

        await act(async () => vi.advanceTimersByTimeAsync(2_000))
        await act(async () => vi.advanceTimersByTimeAsync(4_999))
        expect(loadRun).toHaveBeenCalledTimes(2)
        await act(async () => vi.advanceTimersByTimeAsync(1))
        expect(loadRun).toHaveBeenCalledTimes(3)
        act(() => result.current.refresh())
        await flushPromises()
        expect(loadRun).toHaveBeenCalledTimes(4)
    })

    test('clears stale detail and ignores an old response when the run changes', async () => {
        vi.useFakeTimers()
        const first = deferred<InvestmentAgentRunDetailResponse>()
        const loadRun = vi.fn()
            .mockReturnValueOnce(first.promise)
            .mockResolvedValueOnce(detail(42, 'RUNNING'))
        const now = () => 0
        const {result, rerender, unmount} = renderHook(
            ({runId}) => useAgentRunPolling(runId, {loadRun, now}),
            {initialProps: {runId: 41}},
        )

        rerender({runId: 42})
        await flushPromises()
        expect(result.current.detail?.run.id).toBe(42)
        first.resolve(detail(41, 'FAILED'))
        await flushPromises()
        expect(result.current.detail?.run.id).toBe(42)

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

function detail(id: number, status: InvestmentAgentRunStatus): InvestmentAgentRunDetailResponse {
    return {
        run: {
            id, workspaceId: 7, accountId: 21, presetCode: 'INVESTMENT_ANALYST_V1',
            genericAgentRunAuditId: 61, runType: 'AUTO_TRADE', status,
            dataAsOf: '2026-07-17T00:00:00Z', reportId: null,
            startedAt: '2026-07-17T00:00:00Z', finishedAt: null,
            errorCode: null, errorMessage: null, createdAt: '2026-07-17T00:00:00Z',
        },
        decisions: [],
    }
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((promiseResolve) => {
        resolve = promiseResolve
    })
    return {promise, resolve}
}
