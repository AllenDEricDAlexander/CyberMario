package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import top.egon.mario.nutrition.dto.request.AcknowledgeMealRiskRequest;
import top.egon.mario.nutrition.dto.request.CreateClanRequest;
import top.egon.mario.nutrition.dto.request.CreateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateScopedRoleBindingRequest;
import top.egon.mario.nutrition.dto.request.MealConfirmationItemRequest;
import top.egon.mario.nutrition.dto.request.MealConfirmationRequest;
import top.egon.mario.nutrition.dto.request.MealPlanItemRequest;
import top.egon.mario.nutrition.dto.request.NutritionNutrientsRequest;
import top.egon.mario.nutrition.dto.request.NutritionRecordAdjustmentRequest;
import top.egon.mario.nutrition.dto.request.UpdateFamilySettingsRequest;
import top.egon.mario.nutrition.dto.request.UpdateMealPlanRequest;
import top.egon.mario.nutrition.dto.request.UpsertBudgetRuleRequest;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.MealConfirmationResponse;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.dto.response.MealPlanSummaryResponse;
import top.egon.mario.nutrition.dto.response.NutritionHomeOverviewResponse;
import top.egon.mario.nutrition.dto.response.NutritionRecordResponse;
import top.egon.mario.nutrition.dto.response.ShoppingListResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionRecordPo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationJobRepository;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationRepository;
import top.egon.mario.nutrition.repository.NutritionBudgetRuleRepository;
import top.egon.mario.nutrition.repository.NutritionBudgetSnapshotRepository;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionExtraFoodRecordRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealOperationLogRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeStepRepository;
import top.egon.mario.nutrition.repository.NutritionRecordAdjustmentRepository;
import top.egon.mario.nutrition.repository.NutritionRecordRepository;
import top.egon.mario.nutrition.repository.NutritionReportSnapshotRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListItemRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.BudgetService;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.MealConfirmationService;
import top.egon.mario.nutrition.service.MealPlanService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.NutritionHomeQueryService;
import top.egon.mario.nutrition.service.NutritionRecordService;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.nutrition.service.ShoppingListService;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.nutrition.service.ai.NutritionAiModelClient;
import top.egon.mario.nutrition.service.ai.NutritionAiModelRequest;
import top.egon.mario.nutrition.service.ai.NutritionAiRecommendationScheduler;
import top.egon.mario.nutrition.service.ai.NutritionAiService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the recovered nutrition feature across persisted family workflow boundaries.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.nutrition.ai.recommendation.runner.enabled=false"
})
class NutritionVerticalFlowTests {

    private static final Long OWNER_USER_ID = 9901L;
    private static final Long COOK_USER_ID = 9902L;
    private static final Long MEMBER_USER_ID = 9903L;
    private static final LocalDate PLAN_DATE = LocalDate.of(2026, 7, 8);

    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private NutritionAccessService accessService;
    @Autowired
    private NutritionAiRecommendationScheduler aiScheduler;
    @Autowired
    private NutritionAiService aiService;
    @Autowired
    private FakeNutritionAiModelClient aiModelClient;
    @Autowired
    private MealPlanService mealPlanService;
    @Autowired
    private MealConfirmationService confirmationService;
    @Autowired
    private ShoppingListService shoppingListService;
    @Autowired
    private BudgetService budgetService;
    @Autowired
    private NutritionRecordService recordService;
    @Autowired
    private NutritionHomeQueryService homeQueryService;
    @Autowired
    private NutritionClanRepository clanRepository;
    @Autowired
    private NutritionFamilyRepository familyRepository;
    @Autowired
    private NutritionClanFamilyRepository clanFamilyRepository;
    @Autowired
    private NutritionMemberProfileRepository memberProfileRepository;
    @Autowired
    private NutritionHealthProfileRepository healthProfileRepository;
    @Autowired
    private NutritionHealthTagRepository healthTagRepository;
    @Autowired
    private NutritionScopedRoleBindingRepository roleBindingRepository;
    @Autowired
    private NutritionDataGrantRepository dataGrantRepository;
    @Autowired
    private NutritionStandardFoodRepository standardFoodRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionRecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    private NutritionRecipeStepRepository recipeStepRepository;
    @Autowired
    private NutritionImportJobRepository importJobRepository;
    @Autowired
    private NutritionImportErrorRepository importErrorRepository;
    @Autowired
    private NutritionAiRecommendationJobRepository aiJobRepository;
    @Autowired
    private NutritionAiRecommendationRepository aiRecommendationRepository;
    @Autowired
    private NutritionMealPlanRepository mealPlanRepository;
    @Autowired
    private NutritionMealPlanItemRepository mealPlanItemRepository;
    @Autowired
    private NutritionMealOperationLogRepository operationLogRepository;
    @Autowired
    private NutritionRiskCheckResultRepository riskCheckResultRepository;
    @Autowired
    private NutritionMealConfirmationRepository confirmationRepository;
    @Autowired
    private NutritionMealConfirmationItemRepository confirmationItemRepository;
    @Autowired
    private NutritionShoppingListRepository shoppingListRepository;
    @Autowired
    private NutritionShoppingListItemRepository shoppingListItemRepository;
    @Autowired
    private NutritionFoodPriceRecordRepository priceRecordRepository;
    @Autowired
    private NutritionBudgetRuleRepository budgetRuleRepository;
    @Autowired
    private NutritionBudgetSnapshotRepository budgetSnapshotRepository;
    @Autowired
    private NutritionRecordRepository recordRepository;
    @Autowired
    private NutritionRecordAdjustmentRepository adjustmentRepository;
    @Autowired
    private NutritionExtraFoodRecordRepository extraFoodRecordRepository;
    @Autowired
    private NutritionReportSnapshotRepository reportSnapshotRepository;

    @BeforeEach
    void setUp() {
        aiModelClient.reset();
        adjustmentRepository.deleteAll();
        recordRepository.deleteAll();
        extraFoodRecordRepository.deleteAll();
        reportSnapshotRepository.deleteAll();
        budgetSnapshotRepository.deleteAll();
        priceRecordRepository.deleteAll();
        shoppingListItemRepository.deleteAll();
        shoppingListRepository.deleteAll();
        confirmationItemRepository.deleteAll();
        confirmationRepository.deleteAll();
        operationLogRepository.deleteAll();
        mealPlanItemRepository.deleteAll();
        mealPlanRepository.deleteAll();
        aiRecommendationRepository.deleteAll();
        aiJobRepository.deleteAll();
        riskCheckResultRepository.deleteAll();
        recipeStepRepository.deleteAll();
        recipeIngredientRepository.deleteAll();
        recipeRepository.deleteAll();
        standardFoodRepository.deleteAll();
        importErrorRepository.deleteAll();
        importJobRepository.deleteAll();
        budgetRuleRepository.deleteAll();
        healthTagRepository.deleteAll();
        dataGrantRepository.deleteAll();
        roleBindingRepository.deleteAll();
        clanFamilyRepository.deleteAll();
        healthProfileRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
        clanRepository.deleteAll();
    }

    @Test
    void ownerConfiguresFamilyAndCookCannotReadUnrelatedFamily() {
        FamilyResponse family = createFamily(OWNER_USER_ID, "Mario Family");
        FamilyResponse updated = clanFamilyService.updateFamilySettings(family.id(),
                new UpdateFamilySettingsRequest("Shanghai", "CNY", List.of(NutritionMealType.DINNER),
                        true, LocalTime.of(8, 0), true, true), OWNER_USER_ID);
        clanFamilyService.createRoleBinding(family.id(),
                new CreateScopedRoleBindingRequest(NutritionSubjectType.USER, COOK_USER_ID,
                        NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.id()), OWNER_USER_ID);
        FamilyResponse unrelated = createFamily(9910L, "Unrelated Family");

        assertThat(updated.region()).isEqualTo("Shanghai");
        assertThat(updated.defaultMealTypes()).containsExactly("DINNER");
        assertThat(clanFamilyService.getFamilySettings(family.id(), COOK_USER_ID).id())
                .isEqualTo(family.id());
        assertThatThrownBy(() -> clanFamilyService.getFamilySettings(unrelated.id(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void clanDataAccessRequiresExplicitScopedGrantAndStopsAfterRevoke() {
        var clan = clanFamilyService.createClan(new CreateClanRequest("Mario Clan"), OWNER_USER_ID);
        FamilyResponse family = createFamily(OWNER_USER_ID, "Mario Family");
        clanFamilyService.associateClanFamily(clan.id(), family.id(), OWNER_USER_ID);
        roleBinding(MEMBER_USER_ID, NutritionRoleCode.CLAN_MEMBER, NutritionScopeType.CLAN, clan.id());

        assertThat(accessService.canReadFamilyScope(
                MEMBER_USER_ID, family.id(), NutritionGrantDataScope.HEALTH_PROFILE)).isFalse();

        var grant = clanFamilyService.createDataGrant(family.id(), new CreateDataGrantRequest(
                null, "CLAN", clan.id(), NutritionGrantDataScope.HEALTH_PROFILE,
                NutritionGrantPermissionLevel.READ, null), OWNER_USER_ID);

        assertThat(accessService.canReadFamilyScope(
                MEMBER_USER_ID, family.id(), NutritionGrantDataScope.HEALTH_PROFILE)).isTrue();

        clanFamilyService.revokeDataGrant(family.id(), grant.id(), OWNER_USER_ID);

        assertThat(accessService.canReadFamilyScope(
                MEMBER_USER_ID, family.id(), NutritionGrantDataScope.HEALTH_PROFILE)).isFalse();
        assertThat(dataGrantRepository.findById(grant.id()).orElseThrow().getStatus())
                .isEqualTo(NutritionStatus.DISABLED);
    }

    @Test
    void scheduledAiPersistsCompleteContextAndRealRecipeBackedDraft() {
        FamilyResponse family = createFamily(OWNER_USER_ID, "Mario Family");
        clanFamilyService.updateFamilySettings(family.id(), new UpdateFamilySettingsRequest(
                "Shanghai", "CNY", List.of(NutritionMealType.DINNER), true,
                LocalTime.of(8, 0), true, true), OWNER_USER_ID);
        aiModelClient.addResponse(aiMenuJson());

        var jobs = aiScheduler.generateDueRecommendations(PLAN_DATE, LocalTime.of(8, 5));

        assertThat(jobs).singleElement().satisfies(job -> assertThat(job.familyId()).isEqualTo(family.id()));
        assertThat(aiService.runPendingJobs(1)).isEqualTo(1);
        assertThat(aiJobRepository.findById(jobs.getFirst().id()).orElseThrow()).satisfies(job -> {
            assertThat(job.getInputSnapshot()).contains(
                    "members", "recipes", "budgetRules", "recentPrices", "recentMeals");
            assertThat(job.getOutputSnapshot()).contains("Tomato Pasta");
        });
        assertThat(mealPlanRepository.findAll()).singleElement().satisfies(plan ->
                assertThat(plan.getStatus()).isEqualTo(NutritionMealPlanStatus.PENDING_REVIEW));
        assertThat(mealPlanItemRepository.findAll()).singleElement().satisfies(item -> {
            assertThat(item.getDishName()).isEqualTo("Tomato Pasta");
            assertThat(item.getRecipeId()).isNotNull();
        });
        assertThat(recipeRepository.findAll()).singleElement().satisfies(recipe -> {
            assertThat(recipe.getSourceType()).isEqualTo(NutritionRecipeSourceType.AI_GENERATED);
            assertThat(recipeIngredientRepository.findByRecipeIdAndDeletedFalseOrderByIdAsc(recipe.getId()))
                    .singleElement().satisfies(ingredient ->
                            assertThat(ingredient.getRawFoodName()).isEqualTo("Tomato"));
        });
    }

    @Test
    void allergyBlocksPublishingWhileMediumRiskRequiresAcknowledgement() {
        NutritionFamilyPo blockedFamily = family("Allergy Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK,
                NutritionScopeType.FAMILY, blockedFamily.getId());
        NutritionMemberProfilePo allergicMember = memberProfile(blockedFamily.getId(), MEMBER_USER_ID, "Mario");
        healthProfile(blockedFamily.getId(), allergicMember.getId(), "[\"PEANUT\"]", "[]");
        NutritionRecipePo peanutRecipe = recipeWithFood(
                blockedFamily.getId(), "Peanut Soup", "Peanut", "567", 2, "200.000");
        NutritionMealPlanPo blockedPlan = mealPlan(
                blockedFamily.getId(), NutritionMealPlanStatus.PENDING_REVIEW, PLAN_DATE);
        mealPlanItem(blockedFamily.getId(), blockedPlan.getId(), peanutRecipe.getId(),
                "Peanut Soup", BigDecimal.ONE, 0);
        blockedPlan.setConfirmationCutoffAt(Instant.now().plusSeconds(3600));
        mealPlanRepository.saveAndFlush(blockedPlan);

        assertThatThrownBy(() -> mealPlanService.publishMealPlan(
                blockedFamily.getId(), blockedPlan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_NOT_PUBLISHABLE");

        NutritionFamilyPo reviewFamily = family("Review Family", 9911L);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK,
                NutritionScopeType.FAMILY, reviewFamily.getId());
        NutritionMemberProfilePo member = memberProfile(reviewFamily.getId(), 9912L, "Luigi");
        healthProfile(reviewFamily.getId(), member.getId(), "[]", "[\"CILANTRO\"]");
        NutritionRecipePo cilantroRecipe = recipeWithFood(
                reviewFamily.getId(), "Cilantro Soup", "Cilantro", "23", 2, "200.000");
        NutritionMealPlanPo reviewPlan = mealPlan(
                reviewFamily.getId(), NutritionMealPlanStatus.PENDING_REVIEW, PLAN_DATE);
        NutritionMealPlanItemPo item = mealPlanItem(reviewFamily.getId(), reviewPlan.getId(),
                cilantroRecipe.getId(), "Cilantro Soup", BigDecimal.ONE, 0);
        MealPlanResponse adjusted = mealPlanService.updateMealPlan(reviewFamily.getId(), reviewPlan.getId(),
                new UpdateMealPlanRequest(reviewPlan.getVersion(), Instant.now().plusSeconds(3600), List.of(
                        new MealPlanItemRequest(item.getId(), NutritionMealType.DINNER,
                                cilantroRecipe.getId(), BigDecimal.ONE, 0))), COOK_USER_ID);

        assertThatThrownBy(() -> mealPlanService.publishMealPlan(
                reviewFamily.getId(), reviewPlan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_RISK_ACKNOWLEDGEMENT_REQUIRED");

        mealPlanService.acknowledgeRisks(reviewFamily.getId(), reviewPlan.getId(),
                new AcknowledgeMealRiskRequest(
                        adjusted.risks().stream().map(risk -> risk.id()).toList(),
                        "Family accepted dislike"), COOK_USER_ID);

        assertThat(mealPlanService.publishMealPlan(
                reviewFamily.getId(), reviewPlan.getId(), COOK_USER_ID).status())
                .isEqualTo(NutritionMealPlanStatus.PUBLISHED);
    }

    @Test
    void cookEditRecalculatesNutritionAndWritesBeforeAfterAudit() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionRecipePo tomato = recipeWithFood(
                family.getId(), "Tomato Soup", "Tomato", "18", 2, "200.000");
        NutritionRecipePo pumpkin = recipeWithFood(
                family.getId(), "Pumpkin Soup", "Pumpkin", "26", 2, "200.000");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW, PLAN_DATE);
        NutritionMealPlanItemPo item = mealPlanItem(family.getId(), plan.getId(), tomato.getId(),
                "Tomato Soup", BigDecimal.ONE, 0);

        MealPlanResponse response = mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                new UpdateMealPlanRequest(plan.getVersion(), Instant.now().plusSeconds(3600), List.of(
                        new MealPlanItemRequest(item.getId(), NutritionMealType.DINNER,
                                pumpkin.getId(), new BigDecimal("3.000"), 0))), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.ADJUSTED);
        assertThat(response.nutritionSnapshot()).contains("calories");
        assertThat(response.items()).singleElement().satisfies(saved -> {
            assertThat(saved.recipeId()).isEqualTo(pumpkin.getId());
            assertThat(saved.servingCount()).isEqualByComparingTo("3.000");
        });
        assertThat(operationLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getOperationType()).isEqualTo("EDIT");
            assertThat(log.getBeforeSnapshot()).isNotEqualTo(log.getAfterSnapshot());
        });
    }

    @Test
    void memberConfirmationKeepsExactDishServingSelection() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo member = memberProfile(family.getId(), MEMBER_USER_ID, "Mario");
        memberBindings(family.getId(), member);
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED, PLAN_DATE);
        plan.setConfirmationCutoffAt(Instant.now().plusSeconds(3600));
        mealPlanRepository.saveAndFlush(plan);
        NutritionMealPlanItemPo item = mealPlanItem(family.getId(), plan.getId(), null,
                "Tomato Pasta", BigDecimal.ONE, 0);

        MealConfirmationResponse confirmation = confirmationService.confirmMeal(family.getId(), plan.getId(),
                confirmationRequest(member.getId(), item.getId(), "1.500"), MEMBER_USER_ID);
        MealPlanSummaryResponse summary = mealPlanService.summary(family.getId(), plan.getId(), COOK_USER_ID);

        assertThat(confirmation.items()).singleElement().satisfies(saved -> {
            assertThat(saved.mealPlanItemId()).isEqualTo(item.getId());
            assertThat(saved.servingCount()).isEqualByComparingTo("1.500");
        });
        assertThat(summary.dishes()).singleElement().satisfies(dish -> {
            assertThat(dish.selectedMemberCount()).isEqualTo(1);
            assertThat(dish.confirmedServingTotal()).isEqualByComparingTo("1.500");
        });
    }

    @Test
    void closedConfirmationProducesServingDerivedShoppingSnapshot() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo member = memberProfile(family.getId(), MEMBER_USER_ID, "Mario");
        memberBindings(family.getId(), member);
        NutritionRecipePo recipe = recipeWithFood(
                family.getId(), "Potato Stew", "Potato", "77", 2, "300.000");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED, PLAN_DATE);
        plan.setConfirmationCutoffAt(Instant.now().plusSeconds(3600));
        mealPlanRepository.saveAndFlush(plan);
        NutritionMealPlanItemPo item = mealPlanItem(family.getId(), plan.getId(), recipe.getId(),
                "Potato Stew", new BigDecimal("2.000"), 0);
        confirmationService.confirmMeal(family.getId(), plan.getId(),
                confirmationRequest(member.getId(), item.getId(), "1.500"), MEMBER_USER_ID);

        mealPlanService.closeConfirmation(family.getId(), plan.getId(), true, COOK_USER_ID);
        ShoppingListResponse shopping = shoppingListService.generateFinalShoppingList(
                family.getId(), plan.getId(), COOK_USER_ID);

        assertThat(mealPlanRepository.findById(plan.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionMealPlanStatus.CONFIRM_CLOSED);
        assertThat(shopping.generatedSnapshot()).contains("mealPlanVersion", "confirmations");
        assertThat(shopping.items()).singleElement().satisfies(shoppingItem -> {
            assertThat(shoppingItem.rawFoodName()).isEqualTo("Potato");
            assertThat(shoppingItem.plannedAmount()).isEqualByComparingTo("225.000");
        });
    }

    @Test
    void budgetUsageAndShoppingCompletionRemainSeparateMetrics() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        budgetService.createBudgetRule(family.getId(), new UpsertBudgetRuleRequest(
                "Weekly food", "WEEKLY", new BigDecimal("500.00"), "CNY",
                new BigDecimal("0.8000"), true), COOK_USER_ID);
        NutritionMealPlanPo plan = mealPlan(
                family.getId(), NutritionMealPlanStatus.CONFIRM_CLOSED, LocalDate.of(2026, 7, 6));
        plan.setEstimatedCost(new BigDecimal("150.00"));
        plan.setConfirmedMemberCount(2);
        mealPlanRepository.saveAndFlush(plan);
        mealPlanItem(family.getId(), plan.getId(), null,
                "Family dinner", new BigDecimal("2.000"), 0);
        NutritionShoppingListPo shoppingList = shoppingList(
                family.getId(), plan.getId(), LocalDate.of(2026, 7, 6), "150.00", "125.00");
        shoppingListItem(family.getId(), shoppingList.getId(), "Vegetables", "PURCHASED", "125.00", null);
        shoppingListItem(family.getId(), shoppingList.getId(), "Rice", "PLANNED", null, "25.00");

        BudgetSummaryResponse summary = budgetService.weeklyBudget(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(summary.totalAmount()).isEqualByComparingTo("125.00");
        assertThat(summary.budgetLimit()).isEqualByComparingTo("500.00");
        assertThat(summary.usageRate()).isEqualByComparingTo("0.2500");
        assertThat(summary.shoppingCompletionRate()).isEqualByComparingTo("0.5000");
    }

    @Test
    void completionIsIdempotentAndCorrectionPreservesOriginalRecord() {
        CompletedMeal completed = completedMeal("Idempotent Family", PLAN_DATE);

        MealPlanResponse first = mealPlanService.completeMealPlan(
                completed.family().getId(), completed.plan().getId(), COOK_USER_ID);
        List<NutritionRecordPo> firstRecords = recordRepository.findAll();
        MealPlanResponse second = mealPlanService.completeMealPlan(
                completed.family().getId(), completed.plan().getId(), COOK_USER_ID);
        List<NutritionRecordPo> secondRecords = recordRepository.findAll();

        assertThat(first.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        assertThat(firstRecords).singleElement();
        assertThat(secondRecords).singleElement()
                .extracting(NutritionRecordPo::getId).isEqualTo(firstRecords.getFirst().getId());
        NutritionRecordPo original = firstRecords.getFirst();
        BigDecimal originalCalories = original.getCalories();

        NutritionRecordResponse correction = recordService.adjustRecord(completed.family().getId(), original.getId(),
                new NutritionRecordAdjustmentRequest(
                        nutrients("50.000", "5.000", "2.000", "3.000"), "ate half portion"),
                COOK_USER_ID);

        assertThat(correction.sourceType()).isEqualTo("ADJUSTMENT");
        assertThat(recordRepository.findById(original.getId()).orElseThrow().getCalories())
                .isEqualByComparingTo(originalCalories);
        assertThat(recordRepository.findAll()).hasSize(2);
        assertThat(adjustmentRepository.findAll()).singleElement().satisfies(adjustment ->
                assertThat(adjustment.getNutritionRecordId()).isEqualTo(original.getId()));
    }

    @Test
    void overviewProjectsTheSamePersistedCompletedWorkflow() {
        CompletedMeal completed = completedMeal("Overview Family", PLAN_DATE);
        mealPlanService.completeMealPlan(completed.family().getId(), completed.plan().getId(), COOK_USER_ID);
        NutritionShoppingListPo shoppingList = shoppingList(
                completed.family().getId(), completed.plan().getId(), PLAN_DATE, "15.00", "12.00");
        shoppingListItem(completed.family().getId(), shoppingList.getId(),
                "Chicken Breast", "PURCHASED", "12.00", null);

        NutritionHomeOverviewResponse overview = homeQueryService.overview(
                completed.family().getId(), PLAN_DATE, COOK_USER_ID);

        assertThat(overview.mealPlans()).singleElement().satisfies(plan -> {
            assertThat(plan.id()).isEqualTo(completed.plan().getId());
            assertThat(plan.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        });
        assertThat(overview.confirmedMemberCount()).isEqualTo(1);
        assertThat(overview.shoppingState()).isEqualTo(NutritionShoppingListStatus.ACTIVE);
        assertThat(overview.actualCost()).isEqualByComparingTo("12.00");
        assertThat(overview.estimatedCost()).isEqualByComparingTo("15.00");
        assertThat(overview.nutritionRecordReady()).isTrue();
    }

    private FamilyResponse createFamily(Long ownerUserId, String name) {
        return clanFamilyService.createFamily(new CreateFamilyRequest(
                name, "Shanghai", "CNY", List.of("DINNER"), "Owner"), ownerUserId);
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setCurrency("CNY");
        family.setStatus(NutritionStatus.ACTIVE);
        return familyRepository.saveAndFlush(family);
    }

    private NutritionMemberProfilePo memberProfile(Long familyId, Long userId, String nickname) {
        NutritionMemberProfilePo member = new NutritionMemberProfilePo();
        member.setFamilyId(familyId);
        member.setBoundUserId(userId);
        member.setNickname(nickname);
        member.setMemberType(NutritionMemberType.ADULT);
        member.setStatus(NutritionStatus.ACTIVE);
        return memberProfileRepository.saveAndFlush(member);
    }

    private NutritionHealthProfilePo healthProfile(Long familyId, Long memberProfileId,
                                                   String allergyTags, String dislikeTags) {
        NutritionHealthProfilePo profile = new NutritionHealthProfilePo();
        profile.setFamilyId(familyId);
        profile.setMemberProfileId(memberProfileId);
        profile.setAllergyTags(allergyTags);
        profile.setDislikeTags(dislikeTags);
        return healthProfileRepository.saveAndFlush(profile);
    }

    private void memberBindings(Long familyId, NutritionMemberProfilePo member) {
        roleBinding(member.getBoundUserId(), NutritionRoleCode.MEMBER,
                NutritionScopeType.FAMILY, familyId);
        roleBinding(member.getBoundUserId(), NutritionRoleCode.PROFILE_OWNER,
                NutritionScopeType.MEMBER_PROFILE, member.getId());
    }

    private NutritionScopedRoleBindingPo roleBinding(Long userId, NutritionRoleCode roleCode,
                                                     NutritionScopeType scopeType, Long scopeId) {
        NutritionScopedRoleBindingPo binding = new NutritionScopedRoleBindingPo();
        binding.setSubjectType(NutritionSubjectType.USER);
        binding.setSubjectId(userId);
        binding.setRoleCode(roleCode);
        binding.setScopeType(scopeType);
        binding.setScopeId(scopeId);
        binding.setStatus(NutritionStatus.ACTIVE);
        return roleBindingRepository.saveAndFlush(binding);
    }

    private NutritionRecipePo recipeWithFood(Long familyId, String recipeName, String foodName,
                                             String calories, int servingCount, String amount) {
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn(foodName);
        food.setCategory("TEST");
        food.setCaloriesPer100g(new BigDecimal(calories));
        food.setProteinPer100g(new BigDecimal("20.000"));
        food.setFatPer100g(new BigDecimal("8.000"));
        food.setCarbsPer100g(new BigDecimal("10.000"));
        food.setSugarPer100g(BigDecimal.ZERO);
        food.setSodiumPer100g(BigDecimal.ZERO);
        food.setFiberPer100g(BigDecimal.ZERO);
        food.setCholesterolPer100g(BigDecimal.ZERO);
        food.setDataQuality("TEST");
        food.setStatus(NutritionStatus.ACTIVE);
        NutritionStandardFoodPo savedFood = standardFoodRepository.saveAndFlush(food);

        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(familyId);
        recipe.setSourceType(NutritionRecipeSourceType.FAMILY_PRIVATE);
        recipe.setName(recipeName);
        recipe.setCategory("DINNER");
        recipe.setServingCount(servingCount);
        recipe.setStatus(NutritionStatus.ACTIVE);
        NutritionRecipePo savedRecipe = recipeRepository.saveAndFlush(recipe);

        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(familyId);
        ingredient.setRecipeId(savedRecipe.getId());
        ingredient.setStandardFoodId(savedFood.getId());
        ingredient.setRawFoodName(foodName);
        ingredient.setAmount(new BigDecimal(amount));
        ingredient.setUnit("g");
        ingredient.setMappingStatus(RecipeService.MAPPING_STATUS_MAPPED);
        recipeIngredientRepository.saveAndFlush(ingredient);
        return savedRecipe;
    }

    private NutritionMealPlanPo mealPlan(Long familyId, NutritionMealPlanStatus status, LocalDate planDate) {
        NutritionMealPlanPo plan = new NutritionMealPlanPo();
        plan.setFamilyId(familyId);
        plan.setPlanDate(planDate);
        plan.setTitle("Family dinner");
        plan.setStatus(status);
        return mealPlanRepository.saveAndFlush(plan);
    }

    private NutritionMealPlanItemPo mealPlanItem(Long familyId, Long planId, Long recipeId,
                                                 String dishName, BigDecimal servingCount, int sortOrder) {
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(familyId);
        item.setMealPlanId(planId);
        item.setMealType(NutritionMealType.DINNER);
        item.setRecipeId(recipeId);
        item.setDishName(dishName);
        item.setServingCount(servingCount);
        item.setSortOrder(sortOrder);
        item.setStatus(NutritionStatus.ACTIVE);
        return mealPlanItemRepository.saveAndFlush(item);
    }

    private MealConfirmationRequest confirmationRequest(Long memberProfileId, Long mealPlanItemId,
                                                        String servingCount) {
        return new MealConfirmationRequest(memberProfileId, true, List.of(
                new MealConfirmationItemRequest(mealPlanItemId, true,
                        new BigDecimal(servingCount), false, null)), null);
    }

    private NutritionMealConfirmationPo confirmation(Long familyId, Long mealPlanId, Long memberProfileId) {
        NutritionMealConfirmationPo confirmation = new NutritionMealConfirmationPo();
        confirmation.setFamilyId(familyId);
        confirmation.setMealPlanId(mealPlanId);
        confirmation.setMemberProfileId(memberProfileId);
        confirmation.setConfirmedByUserId(memberProfileId);
        confirmation.setConfirmationStatus(NutritionConfirmationStatus.CONFIRMED);
        confirmation.setEatAtHome(true);
        confirmation.setSelectedMealTypes("[\"DINNER\"]");
        return confirmationRepository.saveAndFlush(confirmation);
    }

    private NutritionMealConfirmationItemPo confirmationItem(Long familyId, Long confirmationId,
                                                              NutritionMealPlanItemPo mealItem,
                                                              String servingCount) {
        NutritionMealConfirmationItemPo item = new NutritionMealConfirmationItemPo();
        item.setFamilyId(familyId);
        item.setConfirmationId(confirmationId);
        item.setMealPlanItemId(mealItem.getId());
        item.setMealType(mealItem.getMealType());
        item.setSelected(true);
        item.setServingCount(new BigDecimal(servingCount));
        return confirmationItemRepository.saveAndFlush(item);
    }

    private CompletedMeal completedMeal(String familyName, LocalDate planDate) {
        NutritionFamilyPo family = family(familyName, COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo member = memberProfile(family.getId(), MEMBER_USER_ID, "Mario");
        NutritionRecipePo recipe = recipeWithFood(
                family.getId(), "Chicken Plate", "Chicken Breast", "200.000", 2, "200.000");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PREPARING, planDate);
        NutritionMealPlanItemPo item = mealPlanItem(family.getId(), plan.getId(), recipe.getId(),
                "Chicken Plate", BigDecimal.ONE, 0);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), plan.getId(), member.getId());
        confirmationItem(family.getId(), confirmation.getId(), item, "1.000");
        return new CompletedMeal(family, plan);
    }

    private NutritionShoppingListPo shoppingList(Long familyId, Long mealPlanId, LocalDate listDate,
                                                 String estimatedTotal, String actualTotal) {
        NutritionShoppingListPo list = new NutritionShoppingListPo();
        list.setFamilyId(familyId);
        list.setMealPlanId(mealPlanId);
        list.setListDate(listDate);
        list.setStatus(NutritionShoppingListStatus.ACTIVE);
        list.setTitle("Family shopping");
        list.setEstimatedTotalPrice(new BigDecimal(estimatedTotal));
        list.setActualTotalPrice(new BigDecimal(actualTotal));
        return shoppingListRepository.saveAndFlush(list);
    }

    private NutritionShoppingListItemPo shoppingListItem(Long familyId, Long shoppingListId,
                                                         String rawFoodName, String itemStatus,
                                                         String totalPrice, String estimatedPrice) {
        NutritionShoppingListItemPo item = new NutritionShoppingListItemPo();
        item.setFamilyId(familyId);
        item.setShoppingListId(shoppingListId);
        item.setRawFoodName(rawFoodName);
        item.setPlannedAmount(new BigDecimal("500.000"));
        item.setPlannedUnit("g");
        item.setChannel("Market");
        item.setItemStatus(itemStatus);
        item.setTotalPrice(totalPrice == null ? null : new BigDecimal(totalPrice));
        item.setMetadataJson(estimatedPrice == null
                ? "{}"
                : "{\"estimatedTotalPrice\":" + estimatedPrice + "}");
        return shoppingListItemRepository.saveAndFlush(item);
    }

    private NutritionNutrientsRequest nutrients(String calories, String protein, String fat, String carbs) {
        return new NutritionNutrientsRequest(new BigDecimal(calories), new BigDecimal(protein),
                new BigDecimal(fat), new BigDecimal(carbs), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private String aiMenuJson() {
        return """
                {"title":"Family dinner","reason":"balanced family dinner","mealTypes":["DINNER"],"recipes":[{"mealType":"DINNER","name":"Tomato Pasta","servingCount":2,"ingredients":[{"foodName":"Tomato","category":"VEGETABLE","amount":200,"unit":"g","optional":true}],"steps":[],"reason":"simple pantry meal"}],"costEstimate":12.50}
                """;
    }

    private record CompletedMeal(NutritionFamilyPo family, NutritionMealPlanPo plan) {
    }

    @TestConfiguration
    static class NutritionAiTestConfiguration {

        @Bean
        @Primary
        FakeNutritionAiModelClient fakeNutritionAiModelClient() {
            return new FakeNutritionAiModelClient();
        }
    }

    static class FakeNutritionAiModelClient implements NutritionAiModelClient {

        private final Queue<String> responses = new ArrayDeque<>();

        void addResponse(String response) {
            responses.add(response);
        }

        void reset() {
            responses.clear();
        }

        @Override
        public String generateMenu(NutritionAiModelRequest request) {
            return responses.remove();
        }
    }
}
