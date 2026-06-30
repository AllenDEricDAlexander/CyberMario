import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as MealSummaryPage} from './MealSummaryPage'

describe('MealSummaryPage', () => {
    test('meal summary page renders serving totals and shopping list action', () => {
        const markup = renderToStaticMarkup(<MealSummaryPage/>)

        expect(markup).toContain('餐食汇总')
        expect(markup).toContain('菜品份数汇总')
        expect(markup).toContain('确认份数')
        expect(markup).toContain('生成采购清单')
        expect(markup).toMatch(/<button[^>]*disabled[^>]*>[\s\S]*生成采购清单/)
    })
})
