import {screen} from '@testing-library/react'
import {describe, expect, test, vi} from 'vitest'
import {renderInvestmentPage} from '../test/renderInvestmentPage'
import {InvestmentAsyncState} from './InvestmentAsyncState'

describe('InvestmentAsyncState', () => {
    test('renders loading, empty, forbidden, error and ready states explicitly', () => {
        const {rerender} = renderInvestmentPage(
            <InvestmentAsyncState state="loading"><div>投资内容</div></InvestmentAsyncState>,
        )
        expect(screen.getByText('加载中')).toBeTruthy()

        rerender(<InvestmentAsyncState state="empty"><div>投资内容</div></InvestmentAsyncState>)
        expect(screen.getByText('暂无投资数据')).toBeTruthy()

        rerender(<InvestmentAsyncState state="forbidden"><div>投资内容</div></InvestmentAsyncState>)
        expect(screen.getByText('无权访问当前投资数据')).toBeTruthy()

        rerender(
            <InvestmentAsyncState error="后端失败" onRetry={vi.fn()} state="error">
                <div>投资内容</div>
            </InvestmentAsyncState>,
        )
        expect(screen.getByText('后端失败')).toBeTruthy()

        rerender(<InvestmentAsyncState state="ready"><div>投资内容</div></InvestmentAsyncState>)
        expect(screen.getByText('投资内容')).toBeTruthy()
    })
})
