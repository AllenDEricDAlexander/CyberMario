import {beforeEach, describe, expect, test, vi} from 'vitest'
import {
    acknowledgeNutritionMealRisks,
    assignNutritionProfileGuardian,
    bindNutritionMemberUser,
    closeNutritionMealPlanConfirmation,
    confirmNutritionImportJob,
    createNutritionBudgetRule,
    createNutritionImportJob,
    createNutritionMealConfirmation,
    generateNutritionFamilyMonthlyReport,
    generateNutritionFamilyWeeklyReport,
    getNutritionAiRecommendationJob,
    getNutritionDailyOverview,
    getNutritionHomeOverview,
    getNutritionRecipe,
    getNutritionWeeklyBudget,
    listNutritionBudgetRules,
    listNutritionFamilyRecipes,
    listNutritionMealConfirmations,
    listNutritionPriceRecords,
    listNutritionPlatformHealthTags,
    listNutritionShoppingLists,
    previewNutritionShoppingList,
    regenerateNutritionMealPlan,
    revokeNutritionDataGrant,
    transitionNutritionShoppingList,
    updateNutritionFamilySettings,
    updateNutritionMealPlan,
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

    test('uses versioned meal-plan review and polling contracts', async () => {
        const {requestJson} = await import('../../services/request')
        const update = {
            expectedVersion: 3,
            items: [{id: 101, mealType: 'DINNER' as const, recipeId: 51, servingCount: '2', sortOrder: 1}],
        }

        void updateNutritionMealPlan(7, 81, update)
        void acknowledgeNutritionMealRisks(7, 81, {riskIds: [301], note: '已核对'})
        void regenerateNutritionMealPlan(7, 81)
        void getNutritionAiRecommendationJob(7, 401)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/meal-plans/81', {
            method: 'PUT', body: update,
        })
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/nutrition/families/7/meal-plans/81/risks/acknowledge', {
            method: 'POST', body: {riskIds: [301], note: '已核对'},
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/nutrition/families/7/meal-plans/81/regenerate', {
            method: 'POST',
        })
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/nutrition/families/7/ai-recommendation-jobs/401')
    })

    test('uses dish-level confirmation and early-close contracts', async () => {
        const {requestJson} = await import('../../services/request')
        const confirmation = {
            memberProfileId: 11,
            eatAtHome: true,
            items: [{mealPlanItemId: 101, selected: true, servingCount: '1.5', riskAcknowledged: true}],
        }

        void listNutritionMealConfirmations(7, 81)
        void createNutritionMealConfirmation(7, 81, confirmation)
        void closeNutritionMealPlanConfirmation(7, 81, true)

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/meal-plans/81/confirmations')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/nutrition/families/7/meal-plans/81/confirmations', {
            method: 'POST', body: confirmation,
        })
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/nutrition/families/7/meal-plans/81/close-confirmation?closeEarly=true', {
            method: 'POST',
        })
    })

    test('uses shopping preview, list, and transition contracts', async () => {
        const {requestJson} = await import('../../services/request')

        void previewNutritionShoppingList(7, 81)
        void listNutritionShoppingLists(7, 81)
        void listNutritionPriceRecords(7)
        void listNutritionPriceRecords(7, 41)
        void transitionNutritionShoppingList(7, 601, {targetStatus: 'PURCHASING'})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/meal-plans/81/shopping-list/preview')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/nutrition/families/7/shopping-lists?mealPlanId=81')
        expect(requestJson).toHaveBeenNthCalledWith(3, '/api/nutrition/families/7/price-records')
        expect(requestJson).toHaveBeenNthCalledWith(4, '/api/nutrition/families/7/price-records?standardFoodId=41')
        expect(requestJson).toHaveBeenNthCalledWith(5, '/api/nutrition/families/7/shopping-lists/601/transition', {
            method: 'POST', body: {targetStatus: 'PURCHASING'},
        })
    })

    test('uses budget-rule and report snapshot contracts', async () => {
        const {requestJson} = await import('../../services/request')
        const rule = {
            ruleName: '周预算', periodType: 'WEEKLY', amountLimit: 700,
            currency: 'CNY', warningThreshold: 0.8, enabled: true,
        }

        void listNutritionBudgetRules(7)
        void createNutritionBudgetRule(7, rule)
        void generateNutritionFamilyWeeklyReport(7, {weekStart: '2026-07-13'})
        void generateNutritionFamilyMonthlyReport(7, {month: '2026-07-01'})

        expect(requestJson).toHaveBeenNthCalledWith(1, '/api/nutrition/families/7/budget-rules')
        expect(requestJson).toHaveBeenNthCalledWith(2, '/api/nutrition/families/7/budget-rules', {
            method: 'POST', body: rule,
        })
        expect(requestJson).toHaveBeenNthCalledWith(
            3,
            '/api/nutrition/families/7/nutrition-records/reports/family-weekly/generate?weekStart=2026-07-13',
            {method: 'POST'},
        )
        expect(requestJson).toHaveBeenNthCalledWith(
            4,
            '/api/nutrition/families/7/nutrition-records/reports/family-monthly/generate?month=2026-07-01',
            {method: 'POST'},
        )
    })
})
