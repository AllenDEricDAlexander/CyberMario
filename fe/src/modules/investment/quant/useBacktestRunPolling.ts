import {useCallback, useEffect, useRef, useState} from 'react'
import {getInvestmentBacktest} from '../services/investmentQuantService'
import type {InvestmentBacktestRunResponse} from '../types/investmentQuantTypes'

const FAST_POLL_MILLIS = 2_000
const SLOW_POLL_MILLIS = 5_000
const FAST_POLL_WINDOW_MILLIS = 60_000

type PollingOptions = {
    loadRun?: (runId: number) => Promise<InvestmentBacktestRunResponse>
    now?: () => number
}

export function useBacktestRunPolling(runId?: number, options: PollingOptions = {}) {
    const loadRun = options.loadRun ?? getInvestmentBacktest
    const now = options.now ?? Date.now
    const [run, setRun] = useState<InvestmentBacktestRunResponse>()
    const [error, setError] = useState<string>()
    const [polling, setPolling] = useState(false)
    const [refreshVersion, setRefreshVersion] = useState(0)
    const generationRef = useRef(0)
    const startedAtRef = useRef(0)
    const displayedRunIdRef = useRef<number | undefined>(undefined)

    useEffect(() => {
        startedAtRef.current = now()
    }, [now, runId])

    useEffect(() => {
        const generation = ++generationRef.current
        let timer: number | undefined
        if (runId === undefined) {
            setRun(undefined)
            displayedRunIdRef.current = undefined
            setError(undefined)
            setPolling(false)
            return
        }
        if (displayedRunIdRef.current !== runId) {
            displayedRunIdRef.current = runId
            setRun(undefined)
        }
        setPolling(true)
        setError(undefined)

        const schedule = () => {
            const elapsed = now() - startedAtRef.current
            const delay = elapsed < FAST_POLL_WINDOW_MILLIS ? FAST_POLL_MILLIS : SLOW_POLL_MILLIS
            timer = window.setTimeout(() => void load(), delay)
        }
        const load = async () => {
            try {
                const response = await loadRun(runId)
                if (generation !== generationRef.current) {
                    return
                }
                setRun(response)
                setError(undefined)
                if (isTerminalBacktestStatus(response.status)) {
                    setPolling(false)
                    return
                }
            } catch (reason) {
                if (generation !== generationRef.current) {
                    return
                }
                setError(errorMessage(reason, '回测状态刷新失败'))
            }
            if (generation === generationRef.current) {
                schedule()
            }
        }
        void load()
        return () => {
            generationRef.current += 1
            if (timer !== undefined) {
                window.clearTimeout(timer)
            }
        }
    }, [loadRun, now, refreshVersion, runId])

    const refresh = useCallback(() => setRefreshVersion((value) => value + 1), [])
    return {run, error, polling, refresh}
}

export function isTerminalBacktestStatus(status: string) {
    return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED'
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}
