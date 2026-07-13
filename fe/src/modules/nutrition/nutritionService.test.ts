import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    assignNutritionProfileGuardian,
    bindNutritionMemberUser,
    confirmNutritionImportJob,
    createNutritionImportJob,
    getNutritionDailyOverview,
    getNutritionHomeOverview,
    getNutritionRecipe,
    getNutritionWeeklyBudget,
    listNutritionFamilyRecipes,
    listNutritionPlatformHealthTags,
    revokeNutritionDataGrant,
    updateNutritionFamilySettings,
    updateNutritionRecipeIngredientMapping,
    validateNutritionRecipe,
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

    test('uses exact administration methods and request bodies', async () => {
        const {requestJson} = await import('../../services/request')
        const settings = {
            region: 'Shanghai',
            currency: 'CNY',
            defaultMealTypes: ['DINNER' as const],
            aiEnabled: true,
            aiGenerateTime: '08:00',
            healthAlertEnabled: true,
            budgetEnabled: true,
        }

        void updateNutritionFamilySettings(7, settings)
        void revokeNutritionDataGrant(7, 12)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/settings', {
            method: 'PUT',
            body: settings,
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/nutrition/families/7/data-grants/12', {
            method: 'DELETE',
        })
    })

    test('uses exact member account and guardian endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        void bindNutritionMemberUser(7, 21, {userId: 8})
        void assignNutritionProfileGuardian(7, 21, {userId: 9})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/members/21/bind-user', {
            method: 'POST',
            body: {userId: 8},
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/nutrition/families/7/members/21/guardians', {
            method: 'POST',
            body: {userId: 9},
        })
    })

    test('uses exact recipe detail, mapping, and validation endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        void getNutritionRecipe(7, 51)
        void updateNutritionRecipeIngredientMapping(7, 51, 61, {standardFoodId: 41})
        void validateNutritionRecipe(7, 51)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/recipes/51')
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/nutrition/families/7/recipes/51/ingredients/61/mapping',
            {method: 'PUT', body: {standardFoodId: 41}},
        )
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/nutrition/families/7/recipes/51/validation')
    })

    test('includes platform tag visibility and home overview query parameters', async () => {
        const {requestJson} = await import('../../services/request')

        void listNutritionPlatformHealthTags(undefined, false)
        void getNutritionHomeOverview(7, {date: '2026-07-13'})

        expect(requestJson).toHaveBeenNthCalledWith(
            1,
            '/api/nutrition/platform/health-tags?activeOnly=false',
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            2,
            '/api/nutrition/families/7/overview?date=2026-07-13',
        )
    })
})
