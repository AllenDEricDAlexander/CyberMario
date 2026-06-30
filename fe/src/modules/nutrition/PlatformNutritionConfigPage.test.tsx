import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as PlatformNutritionConfigPage} from './PlatformNutritionConfigPage'

describe('PlatformNutritionConfigPage', () => {
    test('platform config page is marked platform admin only', () => {
        const markup = renderToStaticMarkup(<PlatformNutritionConfigPage/>)

        expect(markup).toContain('营养平台')
        expect(markup).toContain('仅平台管理员')
        expect(markup).toContain('标准食材')
        expect(markup).toContain('标签配置')
        expect(markup).toContain('公共菜谱')
        expect(markup).toContain('导入任务')
    })
})
