import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as NutritionRecordPage} from './NutritionRecordPage'

describe('NutritionRecordPage', () => {
    test('record page renders daily intake and adjustment actions', () => {
        const markup = renderToStaticMarkup(<NutritionRecordPage/>)

        expect(markup).toContain('营养记录')
        expect(markup).toContain('每日摄入')
        expect(markup).toContain('调整记录')
        expect(markup).toContain('加餐登记')
        expect(markup).toContain('家庭报告')
    })
})
