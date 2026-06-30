import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as ShoppingListPage} from './ShoppingListPage'

describe('ShoppingListPage', () => {
    test('shopping page records channel price details', () => {
        const markup = renderToStaticMarkup(<ShoppingListPage/>)

        expect(markup).toContain('采购清单')
        expect(markup).toContain('采购项')
        expect(markup).toContain('已勾选')
        expect(markup).toContain('渠道价格明细')
        expect(markup).toContain('记录价格')
        expect(markup).toMatch(/<button[^>]*disabled[^>]*>[\s\S]*记录价格/)
    })
})
