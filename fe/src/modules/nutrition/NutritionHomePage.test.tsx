import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as NutritionHomePage} from './NutritionHomePage'

describe('NutritionHomePage', () => {
    test('home page shows pending AI menus and budget usage', () => {
        const markup = renderToStaticMarkup(<NutritionHomePage/>)

        expect(markup).toContain('营养首页')
        expect(markup).toContain('待处理 AI 菜单')
        expect(markup).toContain('预算使用率')
        expect(markup).toContain('待确认成员')
        expect(markup).toContain('查看确认')
    })
})
