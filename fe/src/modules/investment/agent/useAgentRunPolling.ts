import {useCallback, useEffect, useRef, useState} from 'react'
import {getInvestmentAgentRun} from '../services/investmentAgentService'
import type {InvestmentAgentRunDetailResponse} from '../types/investmentAgentTypes'

const FAST_POLL_MILLIS = 2_000
const SLOW_POLL_MILLIS = 5_000
const FAST_POLL_WINDOW_MILLIS = 60_000

type PollingOptions = {
    loadRun?: (runId: number) => Promise<InvestmentAgentRunDetailResponse>
    now?: () => number
}

export function useAgentRunPolling(runId?: number, options: PollingOptions = {}) {
    const loadRun = options.loadRun ?? getInvestmentAgentRun
    const now = options.now ?? Date.now
    const [detail, setDetail] = useState<InvestmentAgentRunDetailResponse>()
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
            displayedRunIdRef.current = undefined
            setDetail(undefined)
            setError(undefined)
            setPolling(false)
            return
        }
        if (displayedRunIdRef.current !== runId) {
            displayedRunIdRef.current = runId
            setDetail(undefined)
        }
        setError(undefined)
        setPolling(true)

        const schedule = () => {
            const elapsed = now() - startedAtRef.current
            const delay = elapsed < FAST_POLL_WINDOW_MILLIS ? FAST_POLL_MILLIS : SLOW_POLL_MILLIS
            timer = window.setTimeout(() => void load(), delay)
        }
        const load = async () => {
            try {
                const response = await loadRun(runId)
                if (generation !== generationRef.current) return
                setDetail(response)
                setError(undefined)
                if (isTerminalAgentRunStatus(response.run.status)) {
                    setPolling(false)
                    return
                }
            } catch (reason) {
                if (generation !== generationRef.current) return
                setError(reason instanceof Error ? reason.message : 'Agent 运行状态刷新失败')
            }
            if (generation === generationRef.current) schedule()
        }
        void load()
        return () => {
            generationRef.current += 1
            if (timer !== undefined) window.clearTimeout(timer)
        }
    }, [loadRun, now, refreshVersion, runId])

    const refresh = useCallback(() => setRefreshVersion((value) => value + 1), [])
    return {detail, error, polling, refresh}
}

export function isTerminalAgentRunStatus(status: string) {
    return status === 'SUCCEEDED' || status === 'FAILED'
}
