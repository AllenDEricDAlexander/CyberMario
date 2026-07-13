export type NutritionStatus = 'ACTIVE' | 'DISABLED' | 'ARCHIVED'
export type NutritionMealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK'
export type NutritionMemberType = 'ADULT' | 'CHILD' | 'ELDER' | 'GUEST'
export type NutritionRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'BLOCKING'
export type NutritionImportType =
    | 'STANDARD_FOOD'
    | 'PUBLIC_RECIPE'
    | 'HEALTH_TAG'
    | 'ALLERGY_TAG'
    | 'DISLIKE_TAG'
    | 'DIET_GOAL'
    | 'PRIVATE_RECIPE'
    | 'FAMILY_INGREDIENT_MAPPING'
    | 'HISTORICAL_PRICE'
export type NutritionImportStatus =
    | 'UPLOADED'
    | 'PARSING'
    | 'VALIDATING'
    | 'PREVIEW_READY'
    | 'CONFIRMED'
    | 'IMPORTING'
    | 'COMPLETED'
    | 'FAILED'
    | 'CANCELLED'
export type NutritionMealPlanStatus =
    | 'DRAFT_AI'
    | 'PENDING_REVIEW'
    | 'ADJUSTED'
    | 'PUBLISHED'
    | 'CONFIRMING'
    | 'CONFIRM_CLOSED'
    | 'PREPARING'
    | 'COMPLETED'
    | 'CANCELLED'
export type NutritionConfirmationStatus = 'PENDING' | 'CONFIRMED' | 'AWAY' | 'CANCELLED' | 'EXPIRED'
export type NutritionShoppingListStatus = 'DRAFT' | 'ACTIVE' | 'PURCHASING' | 'PURCHASED' | 'CLOSED' | 'CANCELLED'
export type NutritionAiJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export type NutritionAiTriggerType = 'SCHEDULED' | 'MANUAL' | 'REGENERATE'
export type NutritionRecipeSourceType = 'PLATFORM_PUBLIC' | 'FAMILY_PRIVATE' | 'AI_GENERATED'
export type NutritionSubjectType = 'USER' | 'ROLE'
export type NutritionRoleCode =
    | 'CLAN_ADMIN'
    | 'CLAN_MEMBER'
    | 'FAMILY_ADMIN'
    | 'COOK'
    | 'MEMBER'
    | 'GUARDIAN'
    | 'PROFILE_OWNER'
    | 'PROFILE_GUARDIAN'
export type NutritionScopeType = 'CLAN' | 'FAMILY' | 'MEMBER_PROFILE'
export type NutritionGrantDataScope =
    | 'FAMILY'
    | 'MEMBER_PROFILE'
    | 'HEALTH_PROFILE'
    | 'MEAL_PLAN'
    | 'SHOPPING_LIST'
    | 'BUDGET'
    | 'NUTRITION_RECORD'
export type NutritionGrantPermissionLevel = 'READ' | 'WRITE' | 'MANAGE'
export type NutritionAmount = number | string
export type NutritionLoadState = 'idle' | 'loading' | 'ready' | 'empty' | 'forbidden' | 'error'

export type NutritionClanResponse = {
    id: number
    name: string
    ownerUserId: number
    status: NutritionStatus
    createdAt: string
    updatedAt: string
}

export type NutritionFamilyResponse = {
    id: number
    name: string
    ownerUserId: number
    region?: string | null
    currency?: string | null
    defaultMealTypes: NutritionMealType[]
    aiEnabled: boolean
    aiGenerateTime?: string | null
    healthAlertEnabled: boolean
    budgetEnabled: boolean
    status: NutritionStatus
    ownerMemberProfileId?: number | null
    createdAt: string
    updatedAt: string
}

export type NutritionNutrients = {
    calories: NutritionAmount
    protein: NutritionAmount
    fat: NutritionAmount
    carbs: NutritionAmount
    sugar: NutritionAmount
    sodium: NutritionAmount
    fiber: NutritionAmount
    cholesterol: NutritionAmount
}

export type NutritionCreateClanRequest = {
    name: string
}

export type NutritionCreateFamilyRequest = {
    name: string
    region?: string
    currency?: string
    defaultMealTypes?: string[]
    ownerNickname?: string
}

export type NutritionUpdateFamilySettingsRequest = {
    region?: string
    currency?: string
    defaultMealTypes?: NutritionMealType[]
    aiEnabled: boolean
    aiGenerateTime?: string | null
    healthAlertEnabled: boolean
    budgetEnabled: boolean
}

export type NutritionCreateScopedRoleBindingRequest = {
    subjectType: NutritionSubjectType
    subjectId: number
    roleCode: NutritionRoleCode
    scopeType: NutritionScopeType
    scopeId: number
}

export type NutritionScopedRoleBindingResponse = NutritionCreateScopedRoleBindingRequest & {
    id: number
    status: NutritionStatus
    createdAt: string
    updatedAt: string
}

export type NutritionCreateDataGrantRequest = {
    memberProfileId?: number
    granteeType: 'USER' | 'CLAN'
    granteeId: number
    dataScope: NutritionGrantDataScope
    permissionLevel: NutritionGrantPermissionLevel
    expiresAt?: string | null
}

export type NutritionUpdateDataGrantRequest = {
    permissionLevel: NutritionGrantPermissionLevel
    expiresAt?: string | null
}

export type NutritionDataGrantResponse = NutritionCreateDataGrantRequest & {
    id: number
    familyId: number
    status: NutritionStatus
    createdAt: string
    updatedAt: string
}

export type NutritionClanFamilyRelationResponse = {
    id: number
    clanId: number
    familyId: number
    relationStatus: NutritionStatus
    joinedAt?: string | null
    createdAt: string
    updatedAt: string
}

export type NutritionCreateMemberProfileRequest = {
    boundUserId?: number
    nickname: string
    gender?: string
    birthDate?: string
    heightCm?: NutritionAmount
    weightKg?: NutritionAmount
    memberType: NutritionMemberType
    loginEnabled?: boolean
    guardianMemberId?: number
}

export type NutritionMemberProfileResponse = {
    id: number
    familyId: number
    boundUserId?: number | null
    nickname: string
    gender?: string | null
    birthDate?: string | null
    heightCm?: NutritionAmount | null
    weightKg?: NutritionAmount | null
    memberType: NutritionMemberType
    loginEnabled: boolean
    guardianMemberId?: number | null
    status: NutritionStatus
    createdAt: string
    updatedAt: string
}

export type NutritionUpdateMemberProfileRequest = {
    nickname: string
    gender?: string
    birthDate?: string
    heightCm?: NutritionAmount
    weightKg?: NutritionAmount
    memberType: NutritionMemberType
    loginEnabled: boolean
    guardianMemberId?: number
}

export type NutritionBindMemberUserRequest = {
    userId: number
}

export type NutritionAssignProfileGuardianRequest = {
    userId: number
}

export type NutritionUpdateHealthProfileRequest = {
    activityLevel?: string
    dietGoals?: string[]
    allergyTags?: string[]
    dislikeTags?: string[]
    restrictionTags?: string[]
    targetCalories?: NutritionAmount
    targetProtein?: NutritionAmount
    targetFat?: NutritionAmount
    targetCarbs?: NutritionAmount
    targetSodium?: NutritionAmount
    targetSugar?: NutritionAmount
}

export type NutritionHealthProfileResponse = NutritionUpdateHealthProfileRequest & {
    id: number
    familyId: number
    memberProfileId: number
    createdAt: string
    updatedAt: string
}

export type NutritionHealthTagResponse = {
    id: number
    tagType: string
    tagCode: string
    name: string
    description?: string | null
    sortOrder: number
    status: NutritionStatus
    createdAt: string
    updatedAt: string
}

export type NutritionUpsertHealthTagRequest = {
    tagType: string
    tagCode: string
    name: string
    description?: string
    sortOrder: number
}

export type NutritionCreateStandardFoodRequest = {
    nameCn: string
    nameEn?: string
    aliases?: string[]
    category: string
    externalSource?: string
    externalFoodId?: string
    caloriesPer100g?: NutritionAmount
    proteinPer100g?: NutritionAmount
    fatPer100g?: NutritionAmount
    carbsPer100g?: NutritionAmount
    sugarPer100g?: NutritionAmount
    sodiumPer100g?: NutritionAmount
    fiberPer100g?: NutritionAmount
    cholesterolPer100g?: NutritionAmount
    purineLevel?: string
    giValue?: NutritionAmount
    allergenTags?: string[]
    suitableTags?: string[]
    dataQuality: string
    status: NutritionStatus
}

export type NutritionStandardFoodResponse = NutritionCreateStandardFoodRequest & {
    id: number
    status: NutritionStatus
    createdAt: string
    updatedAt: string
}

export type NutritionRecipeIngredientRequest = {
    standardFoodId?: number
    foodName: string
    category?: string
    amount: NutritionAmount
    unit: string
    gramsPerUnit?: NutritionAmount
    optional?: boolean
}

export type NutritionRecipeStepRequest = {
    stepNo: number
    title?: string
    instruction: string
}

export type NutritionCreateRecipeRequest = {
    name: string
    category?: string
    description?: string
    servingCount?: number
    cookingMinutes?: number
    difficultyLevel?: string
    suitableTags?: string[]
    allergenTags?: string[]
    ingredients: NutritionRecipeIngredientRequest[]
    steps?: NutritionRecipeStepRequest[]
}

export type NutritionRecipeIngredientResponse = {
    id: number
    recipeId: number
    standardFoodId?: number | null
    rawFoodName: string
    amount: NutritionAmount
    unit: string
    gramsPerUnit?: NutritionAmount | null
    mappingStatus: string
    optional: boolean
    nutritionSnapshot?: NutritionNutrients | null
}

export type NutritionRecipeStepResponse = {
    id: number
    recipeId: number
    stepNo: number
    title?: string | null
    instruction: string
}

export type NutritionRecipeResponse = {
    id: number
    familyId?: number | null
    sourceType: NutritionRecipeSourceType
    name: string
    category?: string | null
    description?: string | null
    servingCount: number
    cookingMinutes?: number | null
    difficultyLevel?: string | null
    suitableTags: string[]
    allergenTags: string[]
    nutritionSnapshot?: NutritionNutrients | null
    estimatedCost?: NutritionAmount | null
    status: NutritionStatus
    ingredients: NutritionRecipeIngredientResponse[]
    steps: NutritionRecipeStepResponse[]
    createdAt: string
    updatedAt: string
}

export type NutritionUpdateRecipeIngredientMappingRequest = {
    standardFoodId: number
    gramsPerUnit?: NutritionAmount
}

export type NutritionRecipeValidationResponse = {
    publishable: boolean
    errors: string[]
    warnings: string[]
}

export type NutritionMealPlanItemResponse = {
    id: number
    mealPlanId: number
    mealType: NutritionMealType
    recipeId?: number | null
    dishName: string
    servingCount: NutritionAmount
    sortOrder: number
}

export type NutritionMealPlanResponse = {
    id: number
    familyId: number
    aiRecommendationId?: number | null
    planDate: string
    status: NutritionMealPlanStatus
    title: string
    publishedAt?: string | null
    confirmationCutoffAt?: string | null
    confirmedMemberCount: number
    estimatedCost?: NutritionAmount | null
    items: NutritionMealPlanItemResponse[]
    createdAt: string
    updatedAt: string
}

export type NutritionMealPlanSummaryResponse = {
    mealPlanId: number
    confirmedMemberCount: number
    dishes: Array<{
        itemId: number
        dishName: string
        mealType: NutritionMealType
        servingCount: NutritionAmount
        confirmedServingTotal: NutritionAmount
    }>
}

export type NutritionMealConfirmationRequest = {
    memberProfileId: number
    eatAtHome?: boolean
    selectedMealTypes?: NutritionMealType[]
    riskConfirmed?: boolean
    riskConfirmationNote?: string
    remark?: string
}

export type NutritionMealConfirmationResponse = NutritionMealConfirmationRequest & {
    id: number
    familyId: number
    mealPlanId: number
    confirmedByUserId?: number | null
    proxyByUserId?: number | null
    confirmationStatus: NutritionConfirmationStatus
    confirmedAt?: string | null
    createdAt: string
    updatedAt: string
}

export type NutritionGenerateAiRecommendationRequest = {
    plannedDate: string
    mealTypes?: NutritionMealType[]
}

export type NutritionAiRecommendationJobResponse = {
    id: number
    familyId: number
    triggerType: NutritionAiTriggerType
    status: NutritionAiJobStatus
    requestedBy?: number | null
    plannedDate: string
    targetMealTypes: NutritionMealType[]
    recommendationId?: number | null
    mealPlanId?: number | null
    errorMessage?: string | null
    startedAt?: string | null
    completedAt?: string | null
    createdAt: string
    updatedAt: string
}

export type NutritionAiRecommendationResponse = {
    id: number
    familyId: number
    aiJobId: number
    recommendationDate: string
    title: string
    reason?: string | null
    mealTypes: NutritionMealType[]
    costEstimate?: NutritionAmount | null
    status: NutritionStatus
    mealPlanId?: number | null
    createdAt: string
    updatedAt: string
}

export type NutritionShoppingListItemResponse = {
    id: number
    shoppingListId: number
    standardFoodId?: number | null
    rawFoodName: string
    category?: string | null
    plannedAmount?: NutritionAmount | null
    plannedUnit?: string | null
    purchasedAmount?: NutritionAmount | null
    purchasedUnit?: string | null
    channel?: string | null
    brand?: string | null
    specAmount?: NutritionAmount | null
    specUnit?: string | null
    purchaseQuantity?: NutritionAmount | null
    totalPrice?: NutritionAmount | null
    normalizedUnitPrice?: NutritionAmount | null
    itemStatus?: string | null
    note?: string | null
    createdAt: string
    updatedAt: string
}

export type NutritionShoppingListResponse = {
    id: number
    familyId: number
    mealPlanId: number
    listDate: string
    status: NutritionShoppingListStatus
    title: string
    estimatedTotalPrice?: NutritionAmount | null
    actualTotalPrice?: NutritionAmount | null
    items: NutritionShoppingListItemResponse[]
    createdAt: string
    updatedAt: string
}

export type NutritionUpdateShoppingListItemRequest = {
    purchasedAmount?: NutritionAmount
    purchasedUnit?: string
    checked?: boolean
    itemStatus?: string
    channel?: string
    brand?: string
    specAmount?: NutritionAmount
    specUnit?: string
    purchaseQuantity?: NutritionAmount
    totalPrice?: NutritionAmount
    note?: string
}

export type NutritionCreateFoodPriceRecordRequest = {
    shoppingListItemId?: number
    standardFoodId?: number
    rawFoodName?: string
    priceDate?: string
    channel?: string
    brand?: string
    specAmount?: NutritionAmount
    specUnit?: string
    purchaseQuantity?: NutritionAmount
    totalPrice: NutritionAmount
    sourceType?: string
    note?: string
}

export type NutritionFoodPriceRecordResponse = NutritionCreateFoodPriceRecordRequest & {
    id: number
    familyId: number
    normalizedUnitPrice?: NutritionAmount | null
    createdAt: string
    updatedAt: string
}

export type NutritionBudgetSummaryResponse = {
    periodType: string
    periodStart: string
    periodEnd: string
    totalAmount: NutritionAmount
    totalActualAmount: NutritionAmount
    totalEstimatedAmount: NutritionAmount
    mealPlanCount: number
    mealCount: number
    confirmedMemberCount: number
    perPersonCost: NutritionAmount
    usageRate: NutritionAmount
    dailySummaries: Array<Record<string, unknown>>
    dishSummaries: Array<Record<string, unknown>>
    ingredientSummaries: Array<Record<string, unknown>>
    channelSummaries: Array<Record<string, unknown>>
}

export type NutritionRecordResponse = {
    id: number
    familyId: number
    memberProfileId: number
    mealPlanId?: number | null
    mealConfirmationId?: number | null
    sourceMealPlanItemId?: number | null
    recordDate: string
    mealType: NutritionMealType
    sourceType: string
    nutrients: NutritionNutrients
    riskTags?: string | null
    calculationSnapshot?: string | null
    metadataJson?: string | null
    createdAt: string
    updatedAt: string
}

export type NutritionDailyOverviewResponse = {
    familyId: number
    recordDate: string
    totalNutrients: NutritionNutrients
    targetNutrients: NutritionNutrients
    remainingNutrients: NutritionNutrients
    memberSummaries: Array<{
        memberProfileId: number
        totalNutrients: NutritionNutrients
        targetNutrients: NutritionNutrients
        remainingNutrients: NutritionNutrients
        records: NutritionRecordResponse[]
    }>
}

export type NutritionRecordAdjustmentRequest = {
    nutrients?: NutritionNutrients
    reason?: string
}

export type NutritionCreateExtraFoodRecordRequest = {
    memberProfileId: number
    recordDate: string
    mealType: NutritionMealType
    foodName: string
    standardFoodId?: number
    amount: NutritionAmount
    unit: string
    nutrients: NutritionNutrients
    note?: string
}

export type NutritionTrendPointResponse = {
    date: string
    nutrients: NutritionNutrients
}

export type NutritionReportResponse = {
    snapshotId?: number | null
    periodType: string
    periodStart: string
    periodEnd: string
    totalNutrients: NutritionNutrients
    riskCounts: Partial<Record<NutritionRiskLevel, number>>
    totalCost?: NutritionAmount | null
    actualCost?: NutritionAmount | null
    estimatedCost?: NutritionAmount | null
    perPersonCost?: NutritionAmount | null
    commonDishes: Array<{
        dishName: string
        count: number
    }>
    nutrientReminders: string[]
    trends: NutritionTrendPointResponse[]
}

export type NutritionHomeOverviewResponse = {
    familyId: number
    date: string
    mealPlans: NutritionMealPlanResponse[]
    confirmedMemberCount: number
    awayMemberCount: number
    unconfirmedMemberCount: number
    riskCounts: Partial<Record<NutritionRiskLevel, number>>
    shoppingState?: NutritionShoppingListStatus | null
    actualCost: NutritionAmount
    estimatedCost: NutritionAmount
    budgetUsageRate: NutritionAmount
    nutritionRecordReady: boolean
}

export type NutritionCreateImportJobRequest = {
    importType: NutritionImportType
    familyId?: number | null
    fileName: string
    csvContent: string
}

export type NutritionImportErrorResponse = {
    id: number
    rowNo: number
    columnName?: string | null
    errorCode: string
    errorMessage: string
    severity: string
}

export type NutritionImportJobResponse = {
    id: number
    familyId?: number | null
    importType: NutritionImportType
    fileName: string
    status: NutritionImportStatus
    totalRows: number
    successRows: number
    failedRows: number
    warningRows: number
    errorSummary?: string | null
    errors: NutritionImportErrorResponse[]
    createdAt: string
    completedAt?: string | null
    confirmedAt?: string | null
}
