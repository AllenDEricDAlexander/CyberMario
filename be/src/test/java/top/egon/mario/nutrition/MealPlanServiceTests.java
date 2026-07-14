package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.AcknowledgeMealRiskRequest;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.MealPlanItemRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.dto.request.UpdateMealPlanRequest;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.po.NutritionAiRecommendationJobPo;
import top.egon.mario.nutrition.po.NutritionAiRecommendationPo;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionAiJobStatus;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationJobRepository;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMealOperationLogRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeStepRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.MealPlanService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies cook-owned meal plan review state transitions.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class MealPlanServiceTests {

    private static final Long COOK_USER_ID = 9101L;
    private static final Long MEMBER_USER_ID = 9102L;

    @Autowired
    private MealPlanService mealPlanService;
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
    private NutritionScopedRoleBindingRepository roleBindingRepository;
    @Autowired
    private NutritionDataGrantRepository dataGrantRepository;
    @Autowired
    private NutritionRiskCheckResultRepository riskCheckResultRepository;
    @Autowired
    private NutritionMealPlanRepository mealPlanRepository;
    @Autowired
    private NutritionAiRecommendationRepository aiRecommendationRepository;
    @Autowired
    private NutritionAiRecommendationJobRepository aiJobRepository;
    @Autowired
    private NutritionMealPlanItemRepository mealPlanItemRepository;
    @Autowired
    private NutritionMealOperationLogRepository operationLogRepository;
    @Autowired
    private NutritionMealConfirmationRepository confirmationRepository;
    @Autowired
    private NutritionMealConfirmationItemRepository confirmationItemRepository;
    @Autowired
    private NutritionRecipeStepRepository recipeStepRepository;
    @Autowired
    private NutritionRecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionStandardFoodRepository standardFoodRepository;
    @Autowired
    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
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
        dataGrantRepository.deleteAll();
        roleBindingRepository.deleteAll();
        clanFamilyRepository.deleteAll();
        healthProfileRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
        clanRepository.deleteAll();
    }

    @Test
    void cookCanPublishPendingReviewMenu() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        makePublishable(family, mealPlan);

        MealPlanResponse response = mealPlanService.publishMealPlan(family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.PUBLISHED);
        assertThat(response.publishedAt()).isNotNull();
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionMealPlanStatus.PUBLISHED);
            assertThat(saved.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void ordinaryMemberCannotPublishMenu() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), MEMBER_USER_ID);
        roleBinding(MEMBER_USER_ID, NutritionRoleCode.MEMBER, NutritionScopeType.FAMILY, family.getId());
        roleBinding(MEMBER_USER_ID, NutritionRoleCode.PROFILE_OWNER,
                NutritionScopeType.MEMBER_PROFILE, member.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> mealPlanService.publishMealPlan(
                family.getId(), mealPlan.getId(), MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionMealPlanStatus.PENDING_REVIEW);
    }

    @Test
    void cookCanClosePublishedMenuWithoutConfirmations() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED);

        MealPlanResponse response = mealPlanService.closeConfirmation(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.CONFIRM_CLOSED);
        assertThat(response.confirmedMemberCount()).isZero();
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionMealPlanStatus.CONFIRM_CLOSED);
            assertThat(saved.getConfirmedMemberCount()).isZero();
        });
    }

    @Test
    void cookCanCompleteClosedConfirmationMenuThroughPreparingTransition() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        makePublishable(family, mealPlan);
        mealPlanService.publishMealPlan(family.getId(), mealPlan.getId(), COOK_USER_ID);
        mealPlanService.closeConfirmation(family.getId(), mealPlan.getId(), true, COOK_USER_ID);

        MealPlanResponse response = mealPlanService.completeMealPlan(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionMealPlanStatus.COMPLETED);
    }

    @Test
    void cookCanCompletePreparingMenuDirectly() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PREPARING);

        MealPlanResponse response = mealPlanService.completeMealPlan(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionMealPlanStatus.COMPLETED);
    }

    @Test
    void replacingDishRecalculatesSnapshotsAndWritesOperationLog() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        Long originalRecipeId = recipe(family.getId(), "Tomato Soup", "Tomato", "18");
        Long replacementRecipeId = recipe(family.getId(), "Pumpkin Soup", "Pumpkin", "26");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        NutritionMealPlanItemPo item = mealPlanItem(plan.getId(), family.getId(), originalRecipeId, "Tomato Soup", 0);

        MealPlanResponse updated = mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                new UpdateMealPlanRequest(plan.getVersion(), Instant.now().plusSeconds(3600), List.of(
                        new MealPlanItemRequest(item.getId(), NutritionMealType.DINNER,
                                replacementRecipeId, new BigDecimal("3"), 0))), COOK_USER_ID);

        assertThat(updated.status()).isEqualTo(NutritionMealPlanStatus.ADJUSTED);
        assertThat(updated.items()).singleElement().satisfies(saved -> {
            assertThat(saved.recipeId()).isEqualTo(replacementRecipeId);
            assertThat(saved.servingCount()).isEqualByComparingTo("3");
        });
        assertThat(updated.nutritionSnapshot()).contains("calories");
        assertThat(updated.version()).isGreaterThan(plan.getVersion());
        assertThat(operationLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getOperationType()).isEqualTo("EDIT");
            assertThat(log.getBeforeSnapshot()).isNotEqualTo(log.getAfterSnapshot());
        });
    }

    @Test
    void staleExpectedVersionIsRejected() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        Long recipeId = recipe(family.getId(), "Tomato Soup", "Tomato", "18");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        NutritionMealPlanItemPo item = mealPlanItem(plan.getId(), family.getId(), recipeId, "Tomato Soup", 0);

        assertThatThrownBy(() -> mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                new UpdateMealPlanRequest(plan.getVersion() + 1, Instant.now().plusSeconds(3600), List.of(
                        new MealPlanItemRequest(item.getId(), NutritionMealType.DINNER,
                                recipeId, BigDecimal.ONE, 0))), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_VERSION_CONFLICT");
    }

    @Test
    void mediumRiskRequiresAcknowledgementWithNoteBeforePublish() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo member = memberProfile(family.getId(), MEMBER_USER_ID);
        healthProfile(family.getId(), member.getId(), "[]", "[\"CILANTRO\"]");
        Long recipeId = recipe(family.getId(), "Cilantro Soup", "Cilantro", "23");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        NutritionMealPlanItemPo item = mealPlanItem(plan.getId(), family.getId(), recipeId, "Cilantro Soup", 0);
        MealPlanResponse adjusted = mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                new UpdateMealPlanRequest(plan.getVersion(), Instant.now().plusSeconds(3600), List.of(
                        new MealPlanItemRequest(item.getId(), NutritionMealType.DINNER,
                                recipeId, BigDecimal.ONE, 0))), COOK_USER_ID);

        assertThat(adjusted.publishable()).isFalse();
        assertThat(adjusted.risks()).singleElement().satisfies(risk -> {
            assertThat(risk.riskLevel().name()).isEqualTo("MEDIUM");
            assertThat(risk.acknowledged()).isFalse();
        });
        assertThatThrownBy(() -> mealPlanService.publishMealPlan(family.getId(), plan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_RISK_ACKNOWLEDGEMENT_REQUIRED");

        MealPlanResponse acknowledged = mealPlanService.acknowledgeRisks(
                family.getId(), plan.getId(), new AcknowledgeMealRiskRequest(
                        adjusted.risks().stream().map(risk -> risk.id()).toList(), "Family accepted dislike"),
                COOK_USER_ID);
        MealPlanResponse published = mealPlanService.publishMealPlan(
                family.getId(), plan.getId(), COOK_USER_ID);

        assertThat(acknowledged.risks()).singleElement().satisfies(risk -> {
            assertThat(risk.acknowledged()).isTrue();
            assertThat(risk.acknowledgementNote()).isEqualTo("Family accepted dislike");
        });
        assertThat(published.status()).isEqualTo(NutritionMealPlanStatus.PUBLISHED);
        assertThat(operationLogRepository.findAll()).extracting(log -> log.getOperationType())
                .containsExactly("EDIT", "ACKNOWLEDGE_RISK", "PUBLISH");
    }

    @Test
    void highRiskAndPastCutoffBlockPublish() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo member = memberProfile(family.getId(), MEMBER_USER_ID);
        healthProfile(family.getId(), member.getId(), "[\"PEANUT\"]", "[]");
        Long recipeId = recipe(family.getId(), "Peanut Soup", "Peanut", "567");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        mealPlanItem(plan.getId(), family.getId(), recipeId, "Peanut Soup", 0);
        plan.setConfirmationCutoffAt(Instant.now().plusSeconds(3600));
        mealPlanRepository.saveAndFlush(plan);
        Long planId = plan.getId();

        assertThatThrownBy(() -> mealPlanService.publishMealPlan(family.getId(), planId, COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_NOT_PUBLISHABLE");

        healthProfileRepository.deleteAll();
        plan = mealPlanRepository.findById(planId).orElseThrow();
        plan.setConfirmationCutoffAt(Instant.now().minusSeconds(1));
        mealPlanRepository.saveAndFlush(plan);
        assertThatThrownBy(() -> mealPlanService.publishMealPlan(family.getId(), planId, COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_CONFIRMATION_CUTOFF_INVALID");
    }

    @Test
    void publishedMealPlanIsImmutable() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        Long recipeId = recipe(family.getId(), "Tomato Soup", "Tomato", "18");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED);
        NutritionMealPlanItemPo item = mealPlanItem(plan.getId(), family.getId(), recipeId, "Tomato Soup", 0);

        assertThatThrownBy(() -> mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                new UpdateMealPlanRequest(plan.getVersion(), Instant.now().plusSeconds(3600), List.of(
                        new MealPlanItemRequest(item.getId(), NutritionMealType.DINNER,
                                recipeId, BigDecimal.ONE, 0))), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_PLAN_IMMUTABLE");
    }

    @Test
    void cookCanAddRemoveAndReorderDishesInOneEdit() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        Long removedRecipeId = recipe(family.getId(), "Tomato Soup", "Tomato", "18");
        Long retainedRecipeId = recipe(family.getId(), "Pumpkin Soup", "Pumpkin", "26");
        Long addedRecipeId = recipe(family.getId(), "Bean Soup", "Bean", "127");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        NutritionMealPlanItemPo removed = mealPlanItem(
                plan.getId(), family.getId(), removedRecipeId, "Tomato Soup", 0);
        NutritionMealPlanItemPo retained = mealPlanItem(
                plan.getId(), family.getId(), retainedRecipeId, "Pumpkin Soup", 1);

        MealPlanResponse updated = mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                new UpdateMealPlanRequest(plan.getVersion(), Instant.now().plusSeconds(3600), List.of(
                        new MealPlanItemRequest(retained.getId(), NutritionMealType.DINNER,
                                retainedRecipeId, new BigDecimal("2"), 0),
                        new MealPlanItemRequest(null, NutritionMealType.DINNER,
                                addedRecipeId, BigDecimal.ONE, 1))), COOK_USER_ID);

        assertThat(updated.items()).extracting(item -> item.recipeId())
                .containsExactly(retainedRecipeId, addedRecipeId);
        assertThat(updated.items()).extracting(item -> item.sortOrder()).containsExactly(0, 1);
        assertThat(mealPlanItemRepository.findById(removed.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionStatus.ARCHIVED);
    }

    @Test
    void invisibleAndRequiredUnmappedRecipesAreRejected() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionFamilyPo otherFamily = family("Luigi Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        Long visibleRecipeId = recipe(family.getId(), "Tomato Soup", "Tomato", "18");
        Long invisibleRecipeId = recipe(otherFamily.getId(), "Private Soup", "Bean", "127");
        Long unmappedRecipeId = recipeService.createFamilyRecipe(family.getId(), new CreateRecipeRequest(
                "Unknown Soup", "DINNER", "", 2, null, null, List.of(), List.of(),
                List.of(new RecipeIngredientRequest(null, "Unknown", "TEST",
                        new BigDecimal("100"), "g", null, false)), List.of()), COOK_USER_ID).id();
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        NutritionMealPlanItemPo item = mealPlanItem(
                plan.getId(), family.getId(), visibleRecipeId, "Tomato Soup", 0);

        assertThatThrownBy(() -> mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                updateRequest(plan, item, invisibleRecipeId), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_NOT_FOUND");
        assertThatThrownBy(() -> mealPlanService.updateMealPlan(family.getId(), plan.getId(),
                updateRequest(plan, item, unmappedRecipeId), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_INVALID");
    }

    @Test
    void regenerationPreservesPriorRecommendationAndPlan() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionAiRecommendationJobPo previousJob = aiJob(family.getId(), NutritionAiTriggerType.MANUAL,
                NutritionAiJobStatus.SUCCEEDED);
        NutritionAiRecommendationPo recommendation = aiRecommendation(family.getId(), previousJob.getId());
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        plan.setAiRecommendationId(recommendation.getId());
        mealPlanRepository.saveAndFlush(plan);
        Long recipeId = recipe(family.getId(), "Tomato Soup", "Tomato", "18");
        mealPlanItem(plan.getId(), family.getId(), recipeId, "Tomato Soup", 0);

        NutritionAiRecommendationJobResponse job = mealPlanService.regenerateMealPlan(
                family.getId(), plan.getId(), COOK_USER_ID);

        assertThat(job.triggerType()).isEqualTo(NutritionAiTriggerType.REGENERATE);
        assertThat(job.status()).isEqualTo(NutritionAiJobStatus.PENDING);
        assertThat(aiRecommendationRepository.findById(recommendation.getId())).isPresent();
        assertThat(mealPlanRepository.findById(plan.getId()).orElseThrow().getAiRecommendationId())
                .isEqualTo(recommendation.getId());
        assertThat(aiJobRepository.findAll()).hasSize(2);
        assertThat(operationLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getOperationType()).isEqualTo("REGENERATE");
            assertThat(log.getBeforeSnapshot()).isEqualTo(log.getAfterSnapshot());
        });
    }

    @Test
    void stateTransitionsWriteAuditRows() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo completedPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED);
        mealPlanService.closeConfirmation(family.getId(), completedPlan.getId(), COOK_USER_ID);
        mealPlanService.startPreparing(family.getId(), completedPlan.getId(), COOK_USER_ID);
        mealPlanService.completeMealPlan(family.getId(), completedPlan.getId(), COOK_USER_ID);
        NutritionMealPlanPo cancelledPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED);

        mealPlanService.cancelMealPlan(family.getId(), cancelledPlan.getId(), COOK_USER_ID);

        assertThat(operationLogRepository.findAll()).extracting(log -> log.getOperationType())
                .containsExactly("CLOSE_CONFIRMATION", "START_PREPARING", "COMPLETE", "CANCEL");
        assertThat(operationLogRepository.findAll()).allSatisfy(log -> {
            assertThat(log.getBeforeSnapshot()).isNotBlank();
            assertThat(log.getAfterSnapshot()).isNotBlank();
            assertThat(log.getBeforeSnapshot()).isNotEqualTo(log.getAfterSnapshot());
        });
    }

    private UpdateMealPlanRequest updateRequest(NutritionMealPlanPo plan,
                                                NutritionMealPlanItemPo item, Long recipeId) {
        return new UpdateMealPlanRequest(plan.getVersion(), Instant.now().plusSeconds(3600), List.of(
                new MealPlanItemRequest(item.getId(), NutritionMealType.DINNER,
                        recipeId, BigDecimal.ONE, 0)));
    }

    private NutritionAiRecommendationJobPo aiJob(Long familyId, NutritionAiTriggerType triggerType,
                                                  NutritionAiJobStatus status) {
        NutritionAiRecommendationJobPo job = new NutritionAiRecommendationJobPo();
        job.setFamilyId(familyId);
        job.setTriggerType(triggerType);
        job.setStatus(status);
        job.setRequestedBy(COOK_USER_ID);
        job.setPlannedDate(LocalDate.of(2026, 7, 1));
        return aiJobRepository.saveAndFlush(job);
    }

    private NutritionAiRecommendationPo aiRecommendation(Long familyId, Long aiJobId) {
        NutritionAiRecommendationPo recommendation = new NutritionAiRecommendationPo();
        recommendation.setFamilyId(familyId);
        recommendation.setAiJobId(aiJobId);
        recommendation.setRecommendationDate(LocalDate.of(2026, 7, 1));
        recommendation.setTitle("Previous recommendation");
        recommendation.setStatus(NutritionStatus.ACTIVE);
        return aiRecommendationRepository.saveAndFlush(recommendation);
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setStatus(NutritionStatus.ACTIVE);
        return familyRepository.saveAndFlush(family);
    }

    private NutritionMealPlanPo mealPlan(Long familyId, NutritionMealPlanStatus status) {
        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(familyId);
        mealPlan.setPlanDate(LocalDate.of(2026, 7, 1));
        mealPlan.setTitle("Family dinner");
        mealPlan.setStatus(status);
        return mealPlanRepository.saveAndFlush(mealPlan);
    }

    private NutritionMemberProfilePo memberProfile(Long familyId, Long boundUserId) {
        NutritionMemberProfilePo memberProfile = new NutritionMemberProfilePo();
        memberProfile.setFamilyId(familyId);
        memberProfile.setBoundUserId(boundUserId);
        memberProfile.setNickname("Mario");
        memberProfile.setMemberType(NutritionMemberType.ADULT);
        memberProfile.setStatus(NutritionStatus.ACTIVE);
        return memberProfileRepository.saveAndFlush(memberProfile);
    }

    private NutritionHealthProfilePo healthProfile(Long familyId, Long memberProfileId,
                                                   String allergyTags, String dislikeTags) {
        NutritionHealthProfilePo health = new NutritionHealthProfilePo();
        health.setFamilyId(familyId);
        health.setMemberProfileId(memberProfileId);
        health.setAllergyTags(allergyTags);
        health.setDislikeTags(dislikeTags);
        return healthProfileRepository.saveAndFlush(health);
    }

    private Long recipe(Long familyId, String name, String foodName, String calories) {
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn(foodName);
        food.setCategory("TEST");
        food.setCaloriesPer100g(new BigDecimal(calories));
        food.setDataQuality("TEST");
        food.setStatus(NutritionStatus.ACTIVE);
        food = standardFoodRepository.saveAndFlush(food);
        return recipeService.createFamilyRecipe(familyId, new CreateRecipeRequest(
                name, "DINNER", "", 2, null, null, List.of(), List.of(),
                List.of(new RecipeIngredientRequest(food.getId(), foodName, "TEST",
                        new BigDecimal("200"), "g", null, false)), List.of()), COOK_USER_ID).id();
    }

    private NutritionMealPlanItemPo mealPlanItem(Long mealPlanId, Long familyId, Long recipeId,
                                                 String dishName, int sortOrder) {
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(familyId);
        item.setMealPlanId(mealPlanId);
        item.setMealType(NutritionMealType.DINNER);
        item.setRecipeId(recipeId);
        item.setDishName(dishName);
        item.setServingCount(BigDecimal.ONE);
        item.setSortOrder(sortOrder);
        item.setStatus(NutritionStatus.ACTIVE);
        return mealPlanItemRepository.saveAndFlush(item);
    }

    private void makePublishable(NutritionFamilyPo family, NutritionMealPlanPo mealPlan) {
        Long recipeId = recipe(family.getId(), "Tomato Soup", "Tomato", "18");
        mealPlanItem(mealPlan.getId(), family.getId(), recipeId, "Tomato Soup", 0);
        mealPlan.setConfirmationCutoffAt(Instant.now().plusSeconds(3600));
        mealPlanRepository.saveAndFlush(mealPlan);
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
}
