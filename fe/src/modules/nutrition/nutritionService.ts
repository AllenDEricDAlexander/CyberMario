import {requestJson} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {
    NutritionAiRecommendationJobResponse,
    NutritionAiRecommendationResponse,
    NutritionBudgetSummaryResponse,
    NutritionClanResponse,
    NutritionCreateClanRequest,
    NutritionCreateExtraFoodRecordRequest,
    NutritionCreateFamilyRequest,
    NutritionCreateFoodPriceRecordRequest,
    NutritionCreateImportJobRequest,
    NutritionCreateMemberProfileRequest,
    NutritionCreateRecipeRequest,
    NutritionCreateStandardFoodRequest,
    NutritionDailyOverviewResponse,
    NutritionFamilyResponse,
    NutritionFoodPriceRecordResponse,
    NutritionGenerateAiRecommendationRequest,
    NutritionHealthProfileResponse,
    NutritionImportJobResponse,
    NutritionMealConfirmationRequest,
    NutritionMealConfirmationResponse,
    NutritionMealPlanResponse,
    NutritionMealPlanSummaryResponse,
    NutritionMemberProfileResponse,
    NutritionRecipeResponse,
    NutritionRecordAdjustmentRequest,
    NutritionRecordResponse,
    NutritionReportResponse,
    NutritionShoppingListItemResponse,
    NutritionShoppingListResponse,
    NutritionStandardFoodResponse,
    NutritionUpdateHealthProfileRequest,
    NutritionUpdateShoppingListItemRequest,
} from './nutritionTypes'

type DateQuery = {
    date?: string
}

type WeekQuery = {
    weekStart?: string
}

type MonthQuery = {
    month?: string
}

export function createNutritionClan(request: NutritionCreateClanRequest) {
    return requestJson<NutritionClanResponse>('/api/nutrition/clans', {
        method: 'POST',
        body: request,
    })
}

export function listNutritionClans() {
    return requestJson<NutritionClanResponse[]>('/api/nutrition/clans')
}

export function createNutritionFamily(request: NutritionCreateFamilyRequest) {
    return requestJson<NutritionFamilyResponse>('/api/nutrition/families', {
        method: 'POST',
        body: request,
    })
}

export function listNutritionFamilies() {
    return requestJson<NutritionFamilyResponse[]>('/api/nutrition/families')
}

export function associateNutritionClanFamily(clanId: number, familyId: number) {
    return requestJson<NutritionFamilyResponse>(`/api/nutrition/clans/${clanId}/families/${familyId}`, {
        method: 'POST',
    })
}

export function createNutritionMemberProfile(familyId: number, request: NutritionCreateMemberProfileRequest) {
    return requestJson<NutritionMemberProfileResponse>(familyPath(familyId, '/members'), {
        method: 'POST',
        body: request,
    })
}

export function listNutritionMembers(familyId: number) {
    return requestJson<NutritionMemberProfileResponse[]>(familyPath(familyId, '/members'))
}

export function listNutritionHealthProfiles(familyId: number) {
    return requestJson<NutritionHealthProfileResponse[]>(familyPath(familyId, '/health-profiles'))
}

export function updateNutritionHealthProfile(
    familyId: number,
    memberProfileId: number,
    request: NutritionUpdateHealthProfileRequest,
) {
    return requestJson<NutritionHealthProfileResponse>(
        familyPath(familyId, `/members/${memberProfileId}/health-profile`),
        {
            method: 'PUT',
            body: request,
        },
    )
}

export function listNutritionStandardFoods() {
    return requestJson<NutritionStandardFoodResponse[]>('/api/nutrition/platform/standard-foods')
}

export function createNutritionStandardFood(request: NutritionCreateStandardFoodRequest) {
    return requestJson<NutritionStandardFoodResponse>('/api/nutrition/platform/standard-foods', {
        method: 'POST',
        body: request,
    })
}

export function listNutritionFamilyRecipes(familyId: number) {
    return requestJson<NutritionRecipeResponse[]>(familyPath(familyId, '/recipes'))
}

export function createNutritionFamilyRecipe(familyId: number, request: NutritionCreateRecipeRequest) {
    return requestJson<NutritionRecipeResponse>(familyPath(familyId, '/recipes'), {
        method: 'POST',
        body: request,
    })
}

export function listNutritionMealPlans(familyId: number) {
    return requestJson<NutritionMealPlanResponse[]>(mealPlanPath(familyId))
}

export function listTodayNutritionMealPlans(familyId: number) {
    return requestJson<NutritionMealPlanResponse[]>(mealPlanPath(familyId, '/today'))
}

export function publishNutritionMealPlan(familyId: number, mealPlanId: number) {
    return requestJson<NutritionMealPlanResponse>(mealPlanPath(familyId, `/${mealPlanId}/publish`), {
        method: 'POST',
    })
}

export function closeNutritionMealPlanConfirmation(familyId: number, mealPlanId: number) {
    return requestJson<NutritionMealPlanResponse>(mealPlanPath(familyId, `/${mealPlanId}/close-confirmation`), {
        method: 'POST',
    })
}

export function completeNutritionMealPlan(familyId: number, mealPlanId: number) {
    return requestJson<NutritionMealPlanResponse>(mealPlanPath(familyId, `/${mealPlanId}/complete`), {
        method: 'POST',
    })
}

export function getNutritionMealPlanSummary(familyId: number, mealPlanId: number) {
    return requestJson<NutritionMealPlanSummaryResponse>(mealPlanPath(familyId, `/${mealPlanId}/summary`))
}

export function createNutritionMealConfirmation(
    familyId: number,
    mealPlanId: number,
    request: NutritionMealConfirmationRequest,
) {
    return requestJson<NutritionMealConfirmationResponse>(familyPath(familyId, `/meal-plans/${mealPlanId}/confirmations`), {
        method: 'POST',
        body: request,
    })
}

export function updateNutritionMealConfirmation(
    familyId: number,
    confirmationId: number,
    request: NutritionMealConfirmationRequest,
) {
    return requestJson<NutritionMealConfirmationResponse>(familyPath(familyId, `/confirmations/${confirmationId}`), {
        method: 'PUT',
        body: request,
    })
}

export function generateNutritionAiRecommendation(
    familyId: number,
    request: NutritionGenerateAiRecommendationRequest,
) {
    return requestJson<NutritionAiRecommendationJobResponse>(familyPath(familyId, '/ai-recommendations/generate'), {
        method: 'POST',
        body: request,
    })
}

export function listNutritionAiRecommendations(familyId: number) {
    return requestJson<NutritionAiRecommendationResponse[]>(familyPath(familyId, '/ai-recommendations'))
}

export function getNutritionAiRecommendation(familyId: number, recommendationId: number) {
    return requestJson<NutritionAiRecommendationResponse>(
        familyPath(familyId, `/ai-recommendations/${recommendationId}`),
    )
}

export function generateNutritionShoppingList(familyId: number, mealPlanId: number) {
    return requestJson<NutritionShoppingListResponse>(
        familyPath(familyId, `/meal-plans/${mealPlanId}/shopping-list/generate`),
        {method: 'POST'},
    )
}

export function getNutritionShoppingList(familyId: number, shoppingListId: number) {
    return requestJson<NutritionShoppingListResponse>(familyPath(familyId, `/shopping-lists/${shoppingListId}`))
}

export function updateNutritionShoppingListItem(
    familyId: number,
    shoppingListId: number,
    itemId: number,
    request: NutritionUpdateShoppingListItemRequest,
) {
    return requestJson<NutritionShoppingListItemResponse>(
        familyPath(familyId, `/shopping-lists/${shoppingListId}/items/${itemId}`),
        {
            method: 'PUT',
            body: request,
        },
    )
}

export function createNutritionPriceRecord(familyId: number, request: NutritionCreateFoodPriceRecordRequest) {
    return requestJson<NutritionFoodPriceRecordResponse>(familyPath(familyId, '/price-records'), {
        method: 'POST',
        body: request,
    })
}

export function getNutritionWeeklyBudget(familyId: number, params: WeekQuery = {}) {
    return requestJson<NutritionBudgetSummaryResponse>(withQuery(familyPath(familyId, '/budget/weekly'), params))
}

export function getNutritionMonthlyBudget(familyId: number, params: MonthQuery = {}) {
    return requestJson<NutritionBudgetSummaryResponse>(withQuery(familyPath(familyId, '/budget/monthly'), params))
}

export function getNutritionDailyOverview(familyId: number, params: DateQuery = {}) {
    return requestJson<NutritionDailyOverviewResponse>(
        withQuery(familyPath(familyId, '/nutrition-records/daily'), params),
    )
}

export function adjustNutritionRecord(
    familyId: number,
    recordId: number,
    request: NutritionRecordAdjustmentRequest,
) {
    return requestJson<NutritionRecordResponse>(
        familyPath(familyId, `/nutrition-records/${recordId}/adjustments`),
        {
            method: 'POST',
            body: request,
        },
    )
}

export function createNutritionExtraFoodRecord(familyId: number, request: NutritionCreateExtraFoodRecordRequest) {
    return requestJson<NutritionRecordResponse>(familyPath(familyId, '/nutrition-records/extra-foods'), {
        method: 'POST',
        body: request,
    })
}

export function getNutritionFamilyWeeklyReport(familyId: number, params: WeekQuery = {}) {
    return requestJson<NutritionReportResponse>(
        withQuery(familyPath(familyId, '/nutrition-records/reports/family-weekly'), params),
    )
}

export function getNutritionFamilyMonthlyReport(familyId: number, params: MonthQuery = {}) {
    return requestJson<NutritionReportResponse>(
        withQuery(familyPath(familyId, '/nutrition-records/reports/family-monthly'), params),
    )
}

export function createNutritionImportJob(request: NutritionCreateImportJobRequest) {
    return requestJson<NutritionImportJobResponse>('/api/nutrition/platform/import-jobs', {
        method: 'POST',
        body: request,
    })
}

export function getNutritionImportJob(jobId: number) {
    return requestJson<NutritionImportJobResponse>(`/api/nutrition/platform/import-jobs/${jobId}`)
}

export function confirmNutritionImportJob(jobId: number) {
    return requestJson<NutritionImportJobResponse>(`/api/nutrition/platform/import-jobs/${jobId}/confirm`, {
        method: 'POST',
    })
}

function familyPath(familyId: number, path: string) {
    return `/api/nutrition/families/${familyId}${path}`
}

function mealPlanPath(familyId: number, path = '') {
    return familyPath(familyId, `/meal-plans${path}`)
}

function withQuery(path: string, params: Record<string, string | number | boolean | null | undefined>) {
    const search = buildSearchParams(params)
    return search ? `${path}?${search}` : path
}
