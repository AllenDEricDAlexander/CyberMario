import {screen} from '@testing-library/react'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {clearCurrentNutritionFamilyId} from './currentFamilyStore'
import {getNutritionHomeOverview, listNutritionFamilies} from './nutritionService'
import {family, homeOverview} from './test/nutritionTestData'
import {renderNutritionPage} from './test/renderNutritionPage'
import {Component as NutritionHomePage} from './NutritionHomePage'

vi.mock('./nutritionService', () => ({
    getNutritionHomeOverview: vi.fn(),
    listNutritionFamilies: vi.fn(),
}))

describe('NutritionHomePage', () => {
    beforeEach(() => {
        clearCurrentNutritionFamilyId()
        vi.clearAllMocks()
    })

    test('loads the selected family live overview', async () => {
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(getNutritionHomeOverview).mockResolvedValue(homeOverview)

        renderNutritionPage(<NutritionHomePage/>)

        expect(screen.getByText('加载中')).toBeTruthy()
        await screen.findByText('Sunday dinner')
        expect(screen.getByText('Mario Family')).toBeTruthy()
        expect(screen.getByText('2')).toBeTruthy()
        expect(screen.getByText('¥42.00')).toBeTruthy()
        const [calledFamilyId, calledParams] = vi.mocked(getNutritionHomeOverview).mock.calls[0]
        expect(calledFamilyId).toBe(7)
        expect(calledParams?.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    })

    test('shows distinct empty and forbidden family states', async () => {
        vi.mocked(listNutritionFamilies).mockResolvedValueOnce([])
        const empty = renderNutritionPage(<NutritionHomePage/>)
        expect(await screen.findByText('暂无营养数据')).toBeTruthy()

        empty.unmount()
        vi.mocked(listNutritionFamilies).mockRejectedValueOnce({status: 403, message: 'forbidden'})
        renderNutritionPage(<NutritionHomePage/>)
        expect(await screen.findByText('无权访问当前营养家庭')).toBeTruthy()
    })

    test('shows overview request errors after a family is selected', async () => {
        vi.mocked(listNutritionFamilies).mockResolvedValue([family])
        vi.mocked(getNutritionHomeOverview).mockRejectedValue(new Error('首页接口失败'))

        renderNutritionPage(<NutritionHomePage/>)

        expect(await screen.findByText('首页接口失败')).toBeTruthy()
        expect(screen.getByText('营养数据加载失败')).toBeTruthy()
    })
})
