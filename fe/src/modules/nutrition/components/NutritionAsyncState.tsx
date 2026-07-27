import {Spin} from 'antd'
import type {ReactNode} from 'react'
import type {NutritionLoadState} from '../nutritionTypes'
import {EmptyState} from '../../../components/EmptyState'
import {ErrorState} from '../../../components/ErrorState'
import {ApiRequestError} from '../../../types/api'

type NutritionAsyncStateProps = {
    state: NutritionLoadState
    error?: string
    /** Headline for the empty state — say what is missing, not just "暂无数据". */
    emptyDescription?: string
    /** Guidance under the empty headline: what the reader should do next. */
    emptyHint?: ReactNode
    /** Call to action for the empty state, e.g. a "新建" button. */
    emptyAction?: ReactNode
    onRetry?: () => void
    children: ReactNode
}

/**
 * The one place nutrition panels decide what a non-ready state looks like.
 *
 * It delegates to the shared `EmptyState` / `ErrorState`, so nutrition panels
 * read like every other panel in the console instead of stacking a bare `Empty`
 * next to a bare `Alert`.
 */
export function NutritionAsyncState({state, children, ...rest}: NutritionAsyncStateProps) {
    return statePlaceholder(state, rest) ?? children
}

/**
 * `locale` for a `DataTable` fed by a nutrition request.
 *
 * `DataTable` already renders its own loading and empty states, so only a
 * failure needs to take over the table body — that keeps the table frame,
 * heading and count on screen while the retry button sits where the rows were.
 */
export function nutritionTableLocale(
    state: NutritionLoadState,
    error?: string,
    onRetry?: () => void,
) {
    if (state !== 'error' && state !== 'forbidden') {
        return undefined
    }
    return {emptyText: statePlaceholder(state, {error, onRetry})}
}

type PlaceholderOptions = Omit<NutritionAsyncStateProps, 'state' | 'children'>

function statePlaceholder(state: NutritionLoadState, options: PlaceholderOptions) {
    const {error, emptyDescription = '暂无营养数据', emptyHint, emptyAction, onRetry} = options
    if (state === 'idle' || state === 'loading') {
        return <div className="state-block"><Spin description="加载中"/></div>
    }
    if (state === 'empty') {
        return <EmptyState action={emptyAction} description={emptyHint} inline title={emptyDescription}/>
    }
    if (state === 'forbidden') {
        return (
            <ErrorState
                inline
                message="当前账号不在该家庭的成员范围内，请让家庭管理员邀请后重试。"
                title="无权访问当前营养家庭"
            />
        )
    }
    if (state === 'error') {
        return (
            <ErrorState
                inline
                message={error ?? '请稍后重试，或切换家庭后重新加载。'}
                onRetry={onRetry}
                title="营养数据加载失败"
            />
        )
    }
    return null
}

export function nutritionLoadFailure(reason: unknown): {
    state: Extract<NutritionLoadState, 'forbidden' | 'error'>
    error: string
} {
    const forbidden = reason instanceof ApiRequestError
        ? reason.status === 403
        : typeof reason === 'object' && reason !== null && 'status' in reason && reason.status === 403
    const error = reason instanceof Error
        ? reason.message
        : typeof reason === 'object' && reason !== null && 'message' in reason && typeof reason.message === 'string'
            ? reason.message
            : '营养数据加载失败'
    return {state: forbidden ? 'forbidden' : 'error', error}
}
