import {Spin} from 'antd'
import type {ReactNode} from 'react'
import {EmptyState} from '../../../components/EmptyState'
import {ErrorState} from '../../../components/ErrorState'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'

type InvestmentAsyncStateProps = {
    state: InvestmentLoadState
    error?: string
    /** Headline for the empty state — say what is missing, not just "暂无数据". */
    emptyDescription?: string
    /** Guidance under the empty headline: what the reader should do next. */
    emptyHint?: ReactNode
    /** Call to action for the empty state, e.g. a "创建" button. */
    emptyAction?: ReactNode
    onRetry?: () => void
    children: ReactNode
}

/**
 * The one place investment panels decide what a non-ready state looks like.
 *
 * It delegates to the shared `EmptyState` / `ErrorState`, so an empty investment
 * panel reads like every other empty panel in the console instead of the bare
 * `Empty` + `Alert` pairs each page used to assemble for itself.
 */
export function InvestmentAsyncState({state, children, ...rest}: InvestmentAsyncStateProps) {
    return statePlaceholder(state, rest) ?? children
}

type InvestmentTableFailureProps = {
    state: InvestmentLoadState
    error?: string
    onRetry?: () => void
}

/**
 * The failure placeholder that takes over a `DataTable` body.
 *
 * It receives `onRetry` as a JSX prop instead of as an argument to
 * `investmentTableLocale`, because reload handlers close over their page's
 * generation ref and React's compiler rejects handing a ref-reading function to
 * a plain function call made during render.
 */
export function InvestmentTableFailure({state, error, onRetry}: InvestmentTableFailureProps) {
    return statePlaceholder(state, {error, onRetry})
}

/**
 * `locale` for a `DataTable` fed by an investment request.
 *
 * `DataTable` already renders its own loading and empty states, so only a
 * failure needs to take over the table body — that keeps the table frame,
 * heading and count on screen while the retry button sits where the rows were.
 */
export function investmentTableLocale(state: InvestmentLoadState, failure: ReactNode) {
    if (state !== 'error' && state !== 'forbidden') {
        return undefined
    }
    return {emptyText: failure}
}

type PlaceholderOptions = Omit<InvestmentAsyncStateProps, 'state' | 'children'>

function statePlaceholder(state: InvestmentLoadState, options: PlaceholderOptions) {
    const {error, emptyDescription = '暂无投资数据', emptyHint, emptyAction, onRetry} = options
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
                message="当前账号没有查看该投资数据的权限，请联系管理员申请后重试。"
                title="无权访问当前投资数据"
            />
        )
    }
    if (state === 'error') {
        return (
            <ErrorState
                inline
                message={error ?? '请稍后重试，或联系管理员确认服务端数据接入状态。'}
                onRetry={onRetry}
                title="投资数据加载失败"
            />
        )
    }
    return null
}
