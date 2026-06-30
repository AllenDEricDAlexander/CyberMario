import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as BudgetPage} from './BudgetPage'

describe('BudgetPage', () => {
    test('budget page renders weekly and monthly summaries', () => {
        const markup = renderToStaticMarkup(<BudgetPage/>)

        expect(markup).toContain('预算分析')
        expect(markup).toContain('周预算')
        expect(markup).toContain('月预算')
        expect(markup).toContain('预算规则')
        expect(markup).toContain('人均成本')
    })
})
