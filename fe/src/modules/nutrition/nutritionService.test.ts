import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    confirmNutritionImportJob,
    createNutritionImportJob,
    getNutritionDailyOverview,
    getNutritionWeeklyBudget,
    listNutritionFamilyRecipes,
} from './nutritionService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
}))

describe('nutritionService', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    test('nutritionService builds family scoped URLs', async () => {
        const {requestJson} = await import('../../services/request')

        void listNutritionFamilyRecipes(7)
        void getNutritionWeeklyBudget(7, {weekStart: '2026-07-01'})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/recipes')
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/nutrition/families/7/budget/weekly?weekStart=2026-07-01',
        )
    })

    test('uses the platform import-job URL for create and confirm', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            importType: 'STANDARD_FOOD' as const,
            fileName: 'foods.csv',
            csvContent: 'name,calories',
        }

        void createNutritionImportJob(request)
        void confirmNutritionImportJob(12)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/platform/import-jobs', {
            method: 'POST',
            body: request,
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/nutrition/platform/import-jobs/12/confirm', {
            method: 'POST',
        })
    })

    test('omits empty query params and keeps provided dates encoded', async () => {
        const {requestJson} = await import('../../services/request')

        void getNutritionDailyOverview(7, {})
        void getNutritionDailyOverview(7, {date: '2026-07-01'})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/nutrition-records/daily')
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/nutrition/families/7/nutrition-records/daily?date=2026-07-01',
        )
    })
})
