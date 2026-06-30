import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as RecipeLibraryPage} from './RecipeLibraryPage'

describe('RecipeLibraryPage', () => {
    test('recipe page renders public and family recipes', () => {
        const markup = renderToStaticMarkup(<RecipeLibraryPage/>)

        expect(markup).toContain('家庭菜谱')
        expect(markup).toContain('公开标准食材')
        expect(markup).toContain('家庭菜谱')
        expect(markup).toContain('食材映射')
        expect(markup).toContain('导入校验')
    })
})
