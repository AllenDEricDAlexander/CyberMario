import {useCallback, useEffect, useRef, useState} from 'react'
import type {PageResult} from '../types/api'
import {resolvePageLoadParams, toPageDataState} from './pageDataState'

export type PageDataLoader<T> = (params: { page: number; size: number }) => Promise<PageResult<T>>

export function usePageData<T>(
    loader: PageDataLoader<T>,
    options: {
        initialPage?: number
        initialSize?: number
        enabled?: boolean
    } = {},
) {
    const {
        initialPage = 1,
        initialSize = 20,
        enabled = true,
    } = options
    const [loading, setLoading] = useState(false)
    const [records, setRecords] = useState<T[]>([])
    const [page, setPage] = useState(initialPage)
    const [size, setSize] = useState(initialSize)
    const [total, setTotal] = useState(0)
    const pageRef = useRef(initialPage)
    const sizeRef = useRef(initialSize)

    const load = useCallback(async (nextPage = pageRef.current, nextSize = sizeRef.current) => {
        const params = resolvePageLoadParams({page: pageRef.current, size: sizeRef.current}, nextPage, nextSize)
        setLoading(true)
        try {
            const result = await loader(params)
            const nextState = toPageDataState(result)
            pageRef.current = nextState.page
            sizeRef.current = nextState.size
            setRecords(nextState.records)
            setPage(nextState.page)
            setSize(nextState.size)
            setTotal(nextState.total)
        } finally {
            setLoading(false)
        }
    }, [loader])

    useEffect(() => {
        if (enabled) {
            void load(initialPage, sizeRef.current)
        }
    }, [enabled, initialPage, load])

    return {
        loading,
        records,
        setRecords,
        page,
        size,
        total,
        load,
        reload: load,
    }
}
