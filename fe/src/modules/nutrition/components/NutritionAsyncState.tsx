import {Alert, Button, Empty, Flex, Spin} from 'antd'
import type {ReactNode} from 'react'
import type {NutritionLoadState} from '../nutritionTypes'
import {ApiRequestError} from '../../../types/api'

type NutritionAsyncStateProps = {
    state: NutritionLoadState
    error?: string
    emptyDescription?: string
    onRetry?: () => void
    children: ReactNode
}

export function NutritionAsyncState({
    state,
    error,
    emptyDescription = '暂无营养数据',
    onRetry,
    children,
}: NutritionAsyncStateProps) {
    if (state === 'idle' || state === 'loading') {
        return <Flex align="center" justify="center" style={{minHeight: 180}}><Spin description="加载中"/></Flex>
    }
    if (state === 'empty') {
        return <Empty description={emptyDescription}/>
    }
    if (state === 'forbidden') {
        return <Alert showIcon title="无权访问当前营养家庭" type="warning"/>
    }
    if (state === 'error') {
        return (
            <Alert
                action={onRetry ? <Button onClick={onRetry} size="small">重试</Button> : undefined}
                description={error}
                showIcon
                title="营养数据加载失败"
                type="error"
            />
        )
    }
    return children
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
