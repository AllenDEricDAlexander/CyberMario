import {screen} from '@testing-library/react'
import {describe, expect, test, vi} from 'vitest'
import {renderNutritionPage} from '../test/renderNutritionPage'
import {NutritionAsyncState} from './NutritionAsyncState'

describe('NutritionAsyncState', () => {
    test('renders loading, empty, forbidden, error, and ready states', () => {
        const {rerender} = renderNutritionPage(
            <NutritionAsyncState state="loading"><div>内容</div></NutritionAsyncState>,
        )
        expect(screen.getByText('加载中')).toBeTruthy()

        rerender(<NutritionAsyncState state="empty"><div>内容</div></NutritionAsyncState>)
        expect(screen.getByText('暂无营养数据')).toBeTruthy()

        rerender(<NutritionAsyncState state="forbidden"><div>内容</div></NutritionAsyncState>)
        expect(screen.getByText('无权访问当前营养家庭')).toBeTruthy()

        rerender(<NutritionAsyncState error="后端失败" onRetry={vi.fn()} state="error"><div>内容</div></NutritionAsyncState>)
        expect(screen.getByText('后端失败')).toBeTruthy()

        rerender(<NutritionAsyncState state="ready"><div>内容</div></NutritionAsyncState>)
        expect(screen.getByText('内容')).toBeTruthy()
    })
})
