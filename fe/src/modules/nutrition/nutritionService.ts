import {requestJson} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {
    NutritionAiRecommendationJobResponse,
    NutritionAiRecommendationResponse,
    NutritionAcknowledgeMealRisksRequest,
    NutritionBudgetRuleResponse,
    NutritionBudgetSummaryResponse,
    NutritionAssignProfileGuardianRequest,
    NutritionBindMemberUserRequest,
    NutritionClanFamilyRelationResponse,
    NutritionClanResponse,
    NutritionCreateClanRequest,
    NutritionCreateDataGrantRequest,
    NutritionCreateExtraFoodRecordRequest,
    NutritionCreateFamilyRequest,
    NutritionCreateFoodPriceRecordRequest,
    NutritionCreateImportJobRequest,
    NutritionCreateMemberProfileRequest,
    NutritionCreateRecipeRequest,
    NutritionCreateStandardFoodRequest,
    NutritionCreateScopedRoleBindingRequest,
    NutritionDataGrantResponse,
    NutritionDailyOverviewResponse,
    NutritionFamilyResponse,
    NutritionFoodPriceRecordResponse,
    NutritionGenerateAiRecommendationRequest,
    NutritionHealthProfileResponse,
    NutritionHealthTagResponse,
    NutritionHomeOverviewResponse,
    NutritionImportJobResponse,
    NutritionMealConfirmationRequest,
    NutritionMealConfirmationResponse,
    NutritionMealPlanResponse,
    NutritionMealPlanSummaryResponse,
    NutritionMemberProfileResponse,
    NutritionRecipeResponse,
    NutritionRecipeIngredientResponse,
    NutritionRecipeValidationResponse,
    NutritionRecordAdjustmentRequest,
    NutritionRecordResponse,
    NutritionReportResponse,
    NutritionShoppingListItemResponse,
    NutritionShoppingListResponse,
    NutritionStandardFoodResponse,
    NutritionScopedRoleBindingResponse,
    NutritionTransitionShoppingListRequest,
    NutritionUpdateMealPlanRequest,
    NutritionUpdateDataGrantRequest,
    NutritionUpdateFamilySettingsRequest,
    NutritionUpdateHealthProfileRequest,
    NutritionUpdateMemberProfileRequest,
    NutritionUpdateRecipeIngredientMappingRequest,
    NutritionUpdateShoppingListItemRequest,
    NutritionUpsertBudgetRuleRequest,
    NutritionUpsertHealthTagRequest,
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

export function getNutritionFamilySettings(familyId: number) {
    return requestJson<NutritionFamilyResponse>(familyPath(familyId, '/settings'))
}

export function updateNutritionFamilySettings(
    familyId: number,
    request: NutritionUpdateFamilySettingsRequest,
) {
    return requestJson<NutritionFamilyResponse>(familyPath(familyId, '/settings'), {
        method: 'PUT',
        body: request,
    })
}

export function listNutritionRoleBindings(familyId: number) {
    return requestJson<NutritionScopedRoleBindingResponse[]>(familyPath(familyId, '/role-bindings'))
}

export function createNutritionRoleBinding(
    familyId: number,
    request: NutritionCreateScopedRoleBindingRequest,
) {
    return requestJson<NutritionScopedRoleBindingResponse>(familyPath(familyId, '/role-bindings'), {
        method: 'POST',
        body: request,
    })
}

export function updateNutritionRoleBinding(
    familyId: number,
    bindingId: number,
    request: NutritionCreateScopedRoleBindingRequest,
) {
    return requestJson<NutritionScopedRoleBindingResponse>(familyPath(familyId, `/role-bindings/${bindingId}`), {
        method: 'PUT',
        body: request,
    })
}

export function revokeNutritionRoleBinding(familyId: number, bindingId: number) {
    return requestJson<void>(familyPath(familyId, `/role-bindings/${bindingId}`), {method: 'DELETE'})
}

export function listNutritionDataGrants(familyId: number) {
    return requestJson<NutritionDataGrantResponse[]>(familyPath(familyId, '/data-grants'))
}

export function createNutritionDataGrant(familyId: number, request: NutritionCreateDataGrantRequest) {
    return requestJson<NutritionDataGrantResponse>(familyPath(familyId, '/data-grants'), {
        method: 'POST',
        body: request,
    })
}

export function updateNutritionDataGrant(
    familyId: number,
    grantId: number,
    request: NutritionUpdateDataGrantRequest,
) {
    return requestJson<NutritionDataGrantResponse>(familyPath(familyId, `/data-grants/${grantId}`), {
        method: 'PUT',
        body: request,
    })
}

export function revokeNutritionDataGrant(familyId: number, grantId: number) {
    return requestJson<void>(familyPath(familyId, `/data-grants/${grantId}`), {method: 'DELETE'})
}

export function listNutritionClanFamilyRelations(familyId: number) {
    return requestJson<NutritionClanFamilyRelationResponse[]>(familyPath(familyId, '/clan-relations'))
}

export function removeNutritionClanFamilyRelation(familyId: number, relationId: number) {
    return requestJson<void>(familyPath(familyId, `/clan-relations/${relationId}`), {method: 'DELETE'})
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

export function updateNutritionMemberProfile(
    familyId: number,
    memberProfileId: number,
    request: NutritionUpdateMemberProfileRequest,
) {
    return requestJson<NutritionMemberProfileResponse>(familyPath(familyId, `/members/${memberProfileId}`), {
        method: 'PUT',
        body: request,
    })
}

export function deactivateNutritionMemberProfile(familyId: number, memberProfileId: number) {
    return requestJson<NutritionMemberProfileResponse>(familyPath(familyId, `/members/${memberProfileId}`), {
        method: 'DELETE',
    })
}

export function bindNutritionMemberUser(
    familyId: number,
    memberProfileId: number,
    request: NutritionBindMemberUserRequest,
) {
    return requestJson<NutritionMemberProfileResponse>(
        familyPath(familyId, `/members/${memberProfileId}/bind-user`),
        {method: 'POST', body: request},
    )
}

export function unbindNutritionMemberUser(familyId: number, memberProfileId: number) {
    return requestJson<NutritionMemberProfileResponse>(
        familyPath(familyId, `/members/${memberProfileId}/bind-user`),
        {method: 'DELETE'},
    )
}

export function assignNutritionProfileGuardian(
    familyId: number,
    memberProfileId: number,
    request: NutritionAssignProfileGuardianRequest,
) {
    return requestJson<NutritionScopedRoleBindingResponse>(
        familyPath(familyId, `/members/${memberProfileId}/guardians`),
        {method: 'POST', body: request},
    )
}

export function revokeNutritionProfileGuardian(familyId: number, memberProfileId: number, bindingId: number) {
    return requestJson<void>(
        familyPath(familyId, `/members/${memberProfileId}/guardians/${bindingId}`),
        {method: 'DELETE'},
    )
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

export function listNutritionFamilyHealthTags(familyId: number, tagType?: string) {
    return requestJson<NutritionHealthTagResponse[]>(withQuery(
        familyPath(familyId, '/health-tags'),
        {tagType},
    ))
}

export function listNutritionPlatformHealthTags(tagType?: string, activeOnly = false) {
    return requestJson<NutritionHealthTagResponse[]>(withQuery(
        '/api/nutrition/platform/health-tags',
        {tagType, activeOnly},
    ))
}

export function createNutritionPlatformHealthTag(request: NutritionUpsertHealthTagRequest) {
    return requestJson<NutritionHealthTagResponse>('/api/nutrition/platform/health-tags', {
        method: 'POST',
        body: request,
    })
}

export function updateNutritionPlatformHealthTag(tagId: number, request: NutritionUpsertHealthTagRequest) {
    return requestJson<NutritionHealthTagResponse>(`/api/nutrition/platform/health-tags/${tagId}`, {
        method: 'PUT',
        body: request,
    })
}

export function deactivateNutritionPlatformHealthTag(tagId: number) {
    return requestJson<NutritionHealthTagResponse>(`/api/nutrition/platform/health-tags/${tagId}`, {
        method: 'DELETE',
    })
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

export function updateNutritionStandardFood(foodId: number, request: NutritionCreateStandardFoodRequest) {
    return requestJson<NutritionStandardFoodResponse>(`/api/nutrition/platform/standard-foods/${foodId}`, {
        method: 'PUT',
        body: request,
    })
}

export function deactivateNutritionStandardFood(foodId: number) {
    return requestJson<NutritionStandardFoodResponse>(`/api/nutrition/platform/standard-foods/${foodId}`, {
        method: 'DELETE',
    })
}

export function listNutritionFamilyStandardFoods(familyId: number) {
    return requestJson<NutritionStandardFoodResponse[]>(familyPath(familyId, '/standard-foods'))
}

export function listNutritionPlatformRecipes() {
    return requestJson<NutritionRecipeResponse[]>('/api/nutrition/platform/recipes')
}

export function createNutritionPlatformRecipe(request: NutritionCreateRecipeRequest) {
    return requestJson<NutritionRecipeResponse>('/api/nutrition/platform/recipes', {
        method: 'POST',
        body: request,
    })
}

export function updateNutritionPlatformRecipe(recipeId: number, request: NutritionCreateRecipeRequest) {
    return requestJson<NutritionRecipeResponse>(`/api/nutrition/platform/recipes/${recipeId}`, {
        method: 'PUT',
        body: request,
    })
}

export function deactivateNutritionPlatformRecipe(recipeId: number) {
    return requestJson<NutritionRecipeResponse>(`/api/nutrition/platform/recipes/${recipeId}`, {
        method: 'DELETE',
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

export function getNutritionRecipe(familyId: number, recipeId: number) {
    return requestJson<NutritionRecipeResponse>(familyPath(familyId, `/recipes/${recipeId}`))
}

export function updateNutritionFamilyRecipe(
    familyId: number,
    recipeId: number,
    request: NutritionCreateRecipeRequest,
) {
    return requestJson<NutritionRecipeResponse>(familyPath(familyId, `/recipes/${recipeId}`), {
        method: 'PUT',
        body: request,
    })
}

export function deactivateNutritionFamilyRecipe(familyId: number, recipeId: number) {
    return requestJson<NutritionRecipeResponse>(familyPath(familyId, `/recipes/${recipeId}`), {
        method: 'DELETE',
    })
}

export function updateNutritionRecipeIngredientMapping(
    familyId: number,
    recipeId: number,
    ingredientId: number,
    request: NutritionUpdateRecipeIngredientMappingRequest,
) {
    return requestJson<NutritionRecipeIngredientResponse>(
        familyPath(familyId, `/recipes/${recipeId}/ingredients/${ingredientId}/mapping`),
        {method: 'PUT', body: request},
    )
}

export function validateNutritionRecipe(familyId: number, recipeId: number) {
    return requestJson<NutritionRecipeValidationResponse>(
        familyPath(familyId, `/recipes/${recipeId}/validation`),
    )
}

export function getNutritionHomeOverview(familyId: number, params: DateQuery = {}) {
    return requestJson<NutritionHomeOverviewResponse>(withQuery(familyPath(familyId, '/overview'), params))
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

export function updateNutritionMealPlan(
    familyId: number,
    mealPlanId: number,
    request: NutritionUpdateMealPlanRequest,
) {
    return requestJson<NutritionMealPlanResponse>(mealPlanPath(familyId, `/${mealPlanId}`), {
        method: 'PUT',
        body: request,
    })
}

export function acknowledgeNutritionMealRisks(
    familyId: number,
    mealPlanId: number,
    request: NutritionAcknowledgeMealRisksRequest,
) {
    return requestJson<NutritionMealPlanResponse>(mealPlanPath(familyId, `/${mealPlanId}/risks/acknowledge`), {
        method: 'POST',
        body: request,
    })
}

export function regenerateNutritionMealPlan(familyId: number, mealPlanId: number) {
    return requestJson<NutritionAiRecommendationJobResponse>(mealPlanPath(familyId, `/${mealPlanId}/regenerate`), {
        method: 'POST',
    })
}

export function closeNutritionMealPlanConfirmation(familyId: number, mealPlanId: number, closeEarly = false) {
    return requestJson<NutritionMealPlanResponse>(withQuery(
        mealPlanPath(familyId, `/${mealPlanId}/close-confirmation`),
        {closeEarly},
    ), {
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

export function listNutritionMealConfirmations(familyId: number, mealPlanId: number) {
    return requestJson<NutritionMealConfirmationResponse[]>(
        mealPlanPath(familyId, `/${mealPlanId}/confirmations`),
    )
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

export function getNutritionAiRecommendationJob(familyId: number, jobId: number) {
    return requestJson<NutritionAiRecommendationJobResponse>(
        familyPath(familyId, `/ai-recommendation-jobs/${jobId}`),
    )
}

export function previewNutritionShoppingList(familyId: number, mealPlanId: number) {
    return requestJson<NutritionShoppingListResponse>(
        familyPath(familyId, `/meal-plans/${mealPlanId}/shopping-list/preview`),
    )
}

export function listNutritionShoppingLists(familyId: number, mealPlanId?: number) {
    return requestJson<NutritionShoppingListResponse[]>(withQuery(
        familyPath(familyId, '/shopping-lists'),
        {mealPlanId},
    ))
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

export function transitionNutritionShoppingList(
    familyId: number,
    shoppingListId: number,
    request: NutritionTransitionShoppingListRequest,
) {
    return requestJson<NutritionShoppingListResponse>(
        familyPath(familyId, `/shopping-lists/${shoppingListId}/transition`),
        {method: 'POST', body: request},
    )
}

export function createNutritionPriceRecord(familyId: number, request: NutritionCreateFoodPriceRecordRequest) {
    return requestJson<NutritionFoodPriceRecordResponse>(familyPath(familyId, '/price-records'), {
        method: 'POST',
        body: request,
    })
}

export function listNutritionPriceRecords(familyId: number, standardFoodId?: number) {
    return requestJson<NutritionFoodPriceRecordResponse[]>(withQuery(
        familyPath(familyId, '/price-records'),
        {standardFoodId},
    ))
}

export function getNutritionWeeklyBudget(familyId: number, params: WeekQuery = {}) {
    return requestJson<NutritionBudgetSummaryResponse>(withQuery(familyPath(familyId, '/budget/weekly'), params))
}

export function getNutritionMonthlyBudget(familyId: number, params: MonthQuery = {}) {
    return requestJson<NutritionBudgetSummaryResponse>(withQuery(familyPath(familyId, '/budget/monthly'), params))
}

export function listNutritionBudgetRules(familyId: number) {
    return requestJson<NutritionBudgetRuleResponse[]>(familyPath(familyId, '/budget-rules'))
}

export function createNutritionBudgetRule(familyId: number, request: NutritionUpsertBudgetRuleRequest) {
    return requestJson<NutritionBudgetRuleResponse>(familyPath(familyId, '/budget-rules'), {
        method: 'POST',
        body: request,
    })
}

export function updateNutritionBudgetRule(
    familyId: number,
    ruleId: number,
    request: NutritionUpsertBudgetRuleRequest,
) {
    return requestJson<NutritionBudgetRuleResponse>(familyPath(familyId, `/budget-rules/${ruleId}`), {
        method: 'PUT',
        body: request,
    })
}

export function deactivateNutritionBudgetRule(familyId: number, ruleId: number) {
    return requestJson<void>(familyPath(familyId, `/budget-rules/${ruleId}`), {method: 'DELETE'})
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

export function generateNutritionFamilyWeeklyReport(familyId: number, params: WeekQuery = {}) {
    return requestJson<NutritionReportResponse>(
        withQuery(familyPath(familyId, '/nutrition-records/reports/family-weekly/generate'), params),
        {method: 'POST'},
    )
}

export function generateNutritionFamilyMonthlyReport(familyId: number, params: MonthQuery = {}) {
    return requestJson<NutritionReportResponse>(
        withQuery(familyPath(familyId, '/nutrition-records/reports/family-monthly/generate'), params),
        {method: 'POST'},
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
