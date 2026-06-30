import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as MealConfirmationPage} from './MealConfirmationPage'

describe('MealConfirmationPage', () => {
    test('confirmation page blocks high risk and requires confirmation for medium risk', () => {
        const markup = renderToStaticMarkup(<MealConfirmationPage/>)

        expect(markup).toContain('用餐确认')
        expect(markup).toContain('当前菜单')
        expect(markup).toContain('成员档案')
        expect(markup).toContain('中风险需确认')
        expect(markup).toContain('高风险阻断')
        expect(markup).toContain('提交确认')
        expect(markup).toMatch(/<button[^>]*disabled[^>]*>[\s\S]*提交确认/)
    })
})
