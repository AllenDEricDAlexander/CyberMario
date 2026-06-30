import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as AiMenuPage} from './AiMenuPage'

describe('AiMenuPage', () => {
    test('AI menu page separates AI suggestion, risk check, and cook adjusted menu', () => {
        const markup = renderToStaticMarkup(<AiMenuPage/>)

        expect(markup).toContain('AI 菜单')
        expect(markup).toContain('AI 建议')
        expect(markup).toContain('风险检查')
        expect(markup).toContain('厨师调整菜单')
        expect(markup).toContain('发布菜单')
        expect(markup).toMatch(/<button[^>]*disabled[^>]*>[\s\S]*生成 AI 建议/)
        expect(markup).toMatch(/<button[^>]*disabled[^>]*>[\s\S]*发布菜单/)
    })
})
