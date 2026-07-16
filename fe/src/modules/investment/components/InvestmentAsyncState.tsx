import {Alert, Button, Empty, Flex, Spin} from 'antd'
import type {ReactNode} from 'react'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'

type InvestmentAsyncStateProps = {
    state: InvestmentLoadState
    error?: string
    emptyDescription?: string
    onRetry?: () => void
    children: ReactNode
}

export function InvestmentAsyncState({
    state,
    error,
    emptyDescription = '暂无投资数据',
    onRetry,
    children,
}: InvestmentAsyncStateProps) {
    if (state === 'idle' || state === 'loading') {
        return <Flex align="center" justify="center" style={{minHeight: 180}}><Spin description="加载中"/></Flex>
    }
    if (state === 'empty') {
        return <Empty description={emptyDescription}/>
    }
    if (state === 'forbidden') {
        return <Alert showIcon title="无权访问当前投资数据" type="warning"/>
    }
    if (state === 'error') {
        return (
            <Alert
                action={onRetry ? <Button onClick={onRetry} size="small">重试</Button> : undefined}
                description={error}
                showIcon
                title="投资数据加载失败"
                type="error"
            />
        )
    }
    return children
}
