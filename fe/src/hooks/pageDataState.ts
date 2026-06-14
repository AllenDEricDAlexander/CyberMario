import type {PageResult} from '../types/api'

export type PageDataState<T> = {
    records: T[]
    page: number
    size: number
    total: number
}

export function toPageDataState<T>(result: PageResult<T>): PageDataState<T> {
    return {
        records: result.records,
        page: result.page,
        size: result.size,
        total: result.total,
    }
}

export function resolvePageLoadParams(
    current: Pick<PageDataState<unknown>, 'page' | 'size'>,
    nextPage = current.page,
    nextSize = current.size,
) {
    return {
        page: nextPage,
        size: nextSize,
    }
}
