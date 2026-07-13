package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateExtraFoodRecordRequest;
import top.egon.mario.nutrition.dto.request.NutritionNutrientsRequest;
import top.egon.mario.nutrition.dto.request.NutritionRecordAdjustmentRequest;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.dto.response.NutritionDailyOverviewResponse;
import top.egon.mario.nutrition.dto.response.NutritionRecordResponse;
import top.egon.mario.nutrition.dto.response.NutritionReportResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionRecordPo;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionExtraFoodRecordRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecordAdjustmentRepository;
import top.egon.mario.nutrition.repository.NutritionRecordRepository;
import top.egon.mario.nutrition.repository.NutritionReportSnapshotRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListItemRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.MealPlanService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.NutritionRecordService;
import top.egon.mario.nutrition.service.RecipeService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies completed meal nutrition records, corrections, extra foods, and basic family reports.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionRecordServiceTests {

    private static final Long COOK_USER_ID = 9501L;
    private static final Long MARIO_USER_ID = 9502L;
    private static final Long PEACH_USER_ID = 9503L;

    @Autowired
    private NutritionRecordService recordService;
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
    private NutritionStandardFoodRepository standardFoodRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionRecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    private NutritionMealPlanRepository mealPlanRepository;
    @Autowired
    private NutritionMealPlanItemRepository mealPlanItemRepository;
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
    private NutritionRiskCheckResultRepository riskCheckResultRepository;
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
        adjustmentRepository.deleteAll();
        recordRepository.deleteAll();
        extraFoodRecordRepository.deleteAll();
        reportSnapshotRepository.deleteAll();
        riskCheckResultRepository.deleteAll();
        priceRecordRepository.deleteAll();
        shoppingListItemRepository.deleteAll();
        shoppingListRepository.deleteAll();
        confirmationItemRepository.deleteAll();
        confirmationRepository.deleteAll();
        mealPlanItemRepository.deleteAll();
        mealPlanRepository.deleteAll();
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
    void completingMealGeneratesMemberNutritionRecords() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        NutritionMemberProfilePo peach = memberProfile(family.getId(), PEACH_USER_ID, "Peach");
        NutritionRecipePo recipe = chickenDinnerRecipe(family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), LocalDate.of(2026, 7, 8),
                NutritionMealPlanStatus.PREPARING, "Chicken dinner", new BigDecimal("30.00"), 2);
        NutritionMealPlanItemPo dinner = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER, recipe.getId(),
                "Chicken Plate", new BigDecimal("1.000"), 0);
        NutritionMealConfirmationPo marioConfirmation = confirmation(
                family.getId(), mealPlan.getId(), mario.getId(), "[]");
        NutritionMealConfirmationPo peachConfirmation = confirmation(
                family.getId(), mealPlan.getId(), peach.getId(), "[\"DINNER\"]");
        confirmationItem(family.getId(), marioConfirmation.getId(), dinner, true, "1.000");
        confirmationItem(family.getId(), peachConfirmation.getId(), dinner, true, "0.500");

        MealPlanResponse first = mealPlanService.completeMealPlan(family.getId(), mealPlan.getId(), COOK_USER_ID);
        MealPlanResponse second = mealPlanService.completeMealPlan(family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(first.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        List<NutritionRecordPo> records = recordRepository
                .findByFamilyIdAndRecordDateAndStatusAndDeletedFalseOrderByMemberProfileIdAscMealTypeAscIdAsc(
                        family.getId(), LocalDate.of(2026, 7, 8), NutritionStatus.ACTIVE);
        assertThat(records).hasSize(2);
        assertThat(records).allSatisfy(record -> {
            assertThat(record.getSourceType()).isEqualTo("MEAL_PLAN");
            assertThat(record.getMealPlanId()).isEqualTo(mealPlan.getId());
            assertThat(record.getMealConfirmationId()).isNotNull();
            assertThat(record.getMetadataJson()).contains("\"sourceMealPlanItemId\":" + dinner.getId());
        });
        assertThat(records).filteredOn(record -> record.getMemberProfileId().equals(mario.getId()))
                .singleElement().satisfies(record -> {
                    assertThat(record.getCalories()).isEqualByComparingTo("200.000");
                    assertThat(record.getProtein()).isEqualByComparingTo("20.000");
                });
        assertThat(records).filteredOn(record -> record.getMemberProfileId().equals(peach.getId()))
                .singleElement().satisfies(record -> {
                    assertThat(record.getCalories()).isEqualByComparingTo("100.000");
                    assertThat(record.getProtein()).isEqualByComparingTo("10.000");
                });
    }

    @Test
    void completedMealRecordGenerationRejectsPrivateRecipeFromAnotherFamily() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionFamilyPo otherFamily = family("Peach Family", 9504L);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        NutritionStandardFoodPo peach = standardFood("Peach", "FRUIT",
                "60.000", "1.000", "0.000", "15.000");
        NutritionRecipePo otherRecipe = recipe(otherFamily.getId(), "Peach Dessert", 1);
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(otherFamily.getId());
        ingredient.setRecipeId(otherRecipe.getId());
        ingredient.setStandardFoodId(peach.getId());
        ingredient.setRawFoodName("Peach");
        ingredient.setAmount(new BigDecimal("100.000"));
        ingredient.setUnit("g");
        ingredient.setMappingStatus(RecipeService.MAPPING_STATUS_MAPPED);
        recipeIngredientRepository.saveAndFlush(ingredient);
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), LocalDate.of(2026, 7, 8),
                NutritionMealPlanStatus.COMPLETED, "Peach dinner", new BigDecimal("10.00"), 1);
        NutritionMealPlanItemPo mealItem = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER, otherRecipe.getId(),
                "Peach Dessert", new BigDecimal("1.000"), 0);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), mealPlan.getId(), mario.getId(), "[]");
        confirmationItem(family.getId(), confirmation.getId(), mealItem, true, "1.000");

        assertThatThrownBy(() -> recordService.generateForCompletedMealPlan(
                family.getId(), mealPlan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_NOT_FOUND");
        assertThat(recordRepository.findAll()).isEmpty();
    }

    @Test
    void completedMealRecordGenerationAllowsPlatformPublicRecipe() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        NutritionStandardFoodPo rice = standardFood("Rice", "GRAIN",
                "120.000", "2.000", "0.500", "26.000");
        NutritionRecipePo publicRecipe = platformRecipe("Rice Bowl", 1);
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(null);
        ingredient.setRecipeId(publicRecipe.getId());
        ingredient.setStandardFoodId(rice.getId());
        ingredient.setRawFoodName("Rice");
        ingredient.setAmount(new BigDecimal("100.000"));
        ingredient.setUnit("g");
        ingredient.setMappingStatus(RecipeService.MAPPING_STATUS_MAPPED);
        recipeIngredientRepository.saveAndFlush(ingredient);
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), LocalDate.of(2026, 7, 8),
                NutritionMealPlanStatus.COMPLETED, "Rice lunch", new BigDecimal("8.00"), 1);
        NutritionMealPlanItemPo mealItem = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.LUNCH, publicRecipe.getId(),
                "Rice Bowl", new BigDecimal("1.000"), 0);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), mealPlan.getId(), mario.getId(), "[]");
        confirmationItem(family.getId(), confirmation.getId(), mealItem, true, "1.000");

        List<NutritionRecordResponse> records = recordService.generateForCompletedMealPlan(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.memberProfileId()).isEqualTo(mario.getId());
            assertThat(record.nutrients().calories()).isEqualByComparingTo("120.000");
            assertThat(record.nutrients().protein()).isEqualByComparingTo("2.000");
        });
    }

    @Test
    void recordAdjustmentKeepsOriginalAndStoresCorrection() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        NutritionRecordPo original = nutritionRecord(family.getId(), mario.getId(),
                LocalDate.of(2026, 7, 9), NutritionMealType.DINNER, "MEAL_PLAN",
                nutrients("200.000", "20.000", "8.000", "10.000"));

        NutritionRecordResponse correction = recordService.adjustRecord(family.getId(), original.getId(),
                new NutritionRecordAdjustmentRequest(
                        nutrientsRequest("50.000", "5.000", "2.000", "3.000"), "ate half portion"),
                COOK_USER_ID);
        NutritionRecordResponse latestCorrection = recordService.adjustRecord(family.getId(), correction.id(),
                new NutritionRecordAdjustmentRequest(
                        nutrientsRequest("40.000", "4.000", "1.000", "2.000"), "corrected again"),
                COOK_USER_ID);

        assertThat(correction.sourceType()).isEqualTo("ADJUSTMENT");
        assertThat(correction.nutrients().calories()).isEqualByComparingTo("50.000");
        assertThat(recordRepository.findById(original.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionStatus.ACTIVE);
            assertThat(saved.getCalories()).isEqualByComparingTo("200.000");
        });
        assertThat(recordRepository.findAll()).hasSize(3);
        assertThat(recordRepository.findAll()).filteredOn(record -> "ADJUSTMENT".equals(record.getSourceType()))
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.getMetadataJson()).contains("\"originalRecordId\":"
                        + original.getId()));
        assertThat(adjustmentRepository.findAll()).hasSize(2)
                .allSatisfy(adjustment -> assertThat(adjustment.getNutritionRecordId())
                        .isEqualTo(original.getId()));
        assertThat(latestCorrection.nutrients().calories()).isEqualByComparingTo("40.000");

        NutritionDailyOverviewResponse overview = recordService.dailyOverview(
                family.getId(), LocalDate.of(2026, 7, 9), COOK_USER_ID);
        assertThat(overview.totalNutrients().calories()).isEqualByComparingTo("40.000");

        NutritionReportResponse report = recordService.familyWeeklyReport(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);
        assertThat(report.totalNutrients().calories()).isEqualByComparingTo("40.000");
    }

    @Test
    void extraFoodRecordContributesToDailyOverview() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");

        recordService.createExtraFoodRecord(family.getId(), new CreateExtraFoodRecordRequest(
                mario.getId(), LocalDate.of(2026, 7, 10), NutritionMealType.SNACK, "Yogurt",
                null, new BigDecimal("100.000"), "g",
                nutrientsRequest("90.000", "5.000", "1.000", "12.000"), "afternoon snack"), COOK_USER_ID);

        NutritionDailyOverviewResponse overview = recordService.dailyOverview(
                family.getId(), LocalDate.of(2026, 7, 10), COOK_USER_ID);

        assertThat(overview.totalNutrients().calories()).isEqualByComparingTo("90.000");
        assertThat(overview.totalNutrients().protein()).isEqualByComparingTo("5.000");
        assertThat(overview.memberSummaries()).singleElement().satisfies(member -> {
            assertThat(member.memberProfileId()).isEqualTo(mario.getId());
            assertThat(member.totalNutrients().carbs()).isEqualByComparingTo("12.000");
            assertThat(member.records()).singleElement()
                    .satisfies(record -> assertThat(record.sourceType()).isEqualTo("EXTRA_FOOD"));
        });
    }

    @Test
    void profileOwnerCanRecordStandardFoodAndCannotChangeAnotherProfile() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        NutritionMemberProfilePo peach = memberProfile(family.getId(), PEACH_USER_ID, "Peach");
        NutritionStandardFoodPo chicken = standardFood("Chicken Breast", "MEAT",
                "200.000", "20.000", "8.000", "10.000");
        NutritionRecordPo peachRecord = nutritionRecord(family.getId(), peach.getId(),
                LocalDate.of(2026, 7, 10), NutritionMealType.SNACK, "EXTRA_FOOD",
                nutrients("90.000", "5.000", "1.000", "12.000"));

        NutritionRecordResponse response = recordService.createExtraFoodRecord(family.getId(),
                new CreateExtraFoodRecordRequest(mario.getId(), LocalDate.of(2026, 7, 10),
                        NutritionMealType.SNACK, "Chicken snack", chicken.getId(),
                        new BigDecimal("50.000"), "g", null, null), MARIO_USER_ID);

        assertThat(response.nutrients().calories()).isEqualByComparingTo("100.000");
        assertThat(response.nutrients().protein()).isEqualByComparingTo("10.000");
        assertThat(response.calculationSnapshot()).contains("STANDARD_FOOD_CALCULATED");
        assertThatThrownBy(() -> recordService.adjustRecord(family.getId(), peachRecord.getId(),
                new NutritionRecordAdjustmentRequest(
                        nutrientsRequest("50.000", "2.000", "1.000", "5.000"), "not mine"),
                MARIO_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void dailyOverviewComparesActualNutrientsWithMemberTargets() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        roleBinding(MARIO_USER_ID, NutritionRoleCode.MEMBER, NutritionScopeType.FAMILY, family.getId());
        healthProfile(family.getId(), mario.getId(), "2000.000", "100.000", "60.000", "250.000");
        nutritionRecord(family.getId(), mario.getId(), LocalDate.of(2026, 7, 10),
                NutritionMealType.DINNER, "MEAL_PLAN",
                nutrients("200.000", "20.000", "8.000", "10.000"));

        NutritionDailyOverviewResponse overview = recordService.dailyOverview(
                family.getId(), LocalDate.of(2026, 7, 10), MARIO_USER_ID);

        assertThat(overview.targetNutrients().calories()).isEqualByComparingTo("2000.000");
        assertThat(overview.remainingNutrients().calories()).isEqualByComparingTo("1800.000");
        assertThat(overview.memberSummaries()).singleElement().satisfies(member -> {
            assertThat(member.targetNutrients().protein()).isEqualByComparingTo("100.000");
            assertThat(member.remainingNutrients().protein()).isEqualByComparingTo("80.000");
        });
    }

    @Test
    void familyWeeklyReportIncludesRiskCountsCostAndCommonDishes() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        NutritionMemberProfilePo peach = memberProfile(family.getId(), PEACH_USER_ID, "Peach");
        NutritionMealPlanPo monday = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                NutritionMealPlanStatus.COMPLETED, "Monday dinner", new BigDecimal("50.00"), 2);
        NutritionMealPlanPo wednesday = mealPlan(family.getId(), LocalDate.of(2026, 7, 8),
                NutritionMealPlanStatus.COMPLETED, "Wednesday dinner", new BigDecimal("20.00"), 2);
        mealPlanItem(family.getId(), monday.getId(), NutritionMealType.DINNER, null,
                "Tomato Pasta", new BigDecimal("2.000"), 0);
        mealPlanItem(family.getId(), monday.getId(), NutritionMealType.LUNCH, null,
                "Vegetable Soup", new BigDecimal("2.000"), 1);
        mealPlanItem(family.getId(), wednesday.getId(), NutritionMealType.DINNER, null,
                "Tomato Pasta", new BigDecimal("2.000"), 0);
        shoppingList(family.getId(), monday.getId(), LocalDate.of(2026, 7, 6),
                new BigDecimal("50.00"), new BigDecimal("42.00"));
        risk(family.getId(), monday.getId(), NutritionRiskLevel.HIGH);
        risk(family.getId(), wednesday.getId(), NutritionRiskLevel.MEDIUM);
        mealPlanNutritionRecord(family.getId(), mario.getId(), monday.getId(), LocalDate.of(2026, 7, 6),
                NutritionMealType.DINNER, "Tomato Pasta");
        mealPlanNutritionRecord(family.getId(), peach.getId(), wednesday.getId(), LocalDate.of(2026, 7, 8),
                NutritionMealType.DINNER, "Tomato Pasta");

        NutritionReportResponse response = recordService.familyWeeklyReport(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(response.periodType()).isEqualTo("WEEKLY");
        assertThat(response.totalCost()).isEqualByComparingTo("62.00");
        assertThat(response.riskCounts()).containsEntry(NutritionRiskLevel.HIGH, 1L)
                .containsEntry(NutritionRiskLevel.MEDIUM, 1L);
        assertThat(response.commonDishes()).first()
                .satisfies(dish -> {
                    assertThat(dish.dishName()).isEqualTo("Tomato Pasta");
                    assertThat(dish.count()).isEqualTo(2);
                });
        assertThat(reportSnapshotRepository.findAll()).isEmpty();

        NutritionReportResponse generated = recordService.generateFamilyWeeklyReport(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);
        assertThat(generated.snapshotId()).isNotNull();
        assertThat(generated.trends()).hasSize(2);
        assertThat(reportSnapshotRepository.findAll()).singleElement()
                .satisfies(snapshot -> assertThat(snapshot.getReportSnapshot()).contains("Tomato Pasta"));
    }

    @Test
    void familyWeeklyReportExcludesMealPlanRisksOutsidePeriod() {
        LocalDate weekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters
                .previousOrSame(java.time.DayOfWeek.MONDAY));
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo current = mealPlan(family.getId(), weekStart,
                NutritionMealPlanStatus.COMPLETED, "Current dinner", new BigDecimal("10.00"), 1);
        NutritionMealPlanPo future = mealPlan(family.getId(), weekStart.plusDays(8),
                NutritionMealPlanStatus.COMPLETED, "Future dinner", new BigDecimal("20.00"), 1);
        risk(family.getId(), current.getId(), NutritionRiskLevel.MEDIUM);
        risk(family.getId(), future.getId(), NutritionRiskLevel.HIGH);

        NutritionReportResponse response = recordService.familyWeeklyReport(
                family.getId(), weekStart, COOK_USER_ID);

        assertThat(response.riskCounts()).containsEntry(NutritionRiskLevel.MEDIUM, 1L);
        assertThat(response.riskCounts()).doesNotContainKey(NutritionRiskLevel.HIGH);
    }

    @Test
    void commonDishesUseGeneratedEatenRecords() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMemberProfilePo mario = memberProfile(family.getId(), MARIO_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                NutritionMealPlanStatus.PREPARING, "Selected dinner", new BigDecimal("20.00"), 1);
        NutritionMealPlanItemPo lunch = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.LUNCH, null,
                "Vegetable Soup", new BigDecimal("1.000"), 0);
        NutritionMealPlanItemPo dinner = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER, null,
                "Tomato Pasta", new BigDecimal("1.000"), 1);
        NutritionMealConfirmationPo confirmation = confirmation(
                family.getId(), mealPlan.getId(), mario.getId(), "[\"DINNER\"]");
        confirmationItem(family.getId(), confirmation.getId(), lunch, false, "1.000");
        confirmationItem(family.getId(), confirmation.getId(), dinner, true, "1.000");
        mealPlanService.completeMealPlan(family.getId(), mealPlan.getId(), COOK_USER_ID);

        NutritionReportResponse response = recordService.familyWeeklyReport(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(response.commonDishes()).extracting(NutritionReportResponse.CommonDish::dishName)
                .contains("Tomato Pasta")
                .doesNotContain("Vegetable Soup");
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setStatus(NutritionStatus.ACTIVE);
        return familyRepository.saveAndFlush(family);
    }

    private NutritionMemberProfilePo memberProfile(Long familyId, Long boundUserId, String nickname) {
        NutritionMemberProfilePo memberProfile = new NutritionMemberProfilePo();
        memberProfile.setFamilyId(familyId);
        memberProfile.setBoundUserId(boundUserId);
        memberProfile.setNickname(nickname);
        memberProfile.setMemberType(NutritionMemberType.ADULT);
        memberProfile.setStatus(NutritionStatus.ACTIVE);
        return memberProfileRepository.saveAndFlush(memberProfile);
    }

    private NutritionHealthProfilePo healthProfile(Long familyId, Long memberProfileId, String calories,
                                                    String protein, String fat, String carbs) {
        NutritionHealthProfilePo health = new NutritionHealthProfilePo();
        health.setFamilyId(familyId);
        health.setMemberProfileId(memberProfileId);
        health.setTargetCalories(new BigDecimal(calories));
        health.setTargetProtein(new BigDecimal(protein));
        health.setTargetFat(new BigDecimal(fat));
        health.setTargetCarbs(new BigDecimal(carbs));
        health.setTargetSugar(new BigDecimal("50.000"));
        health.setTargetSodium(new BigDecimal("2000.000"));
        return healthProfileRepository.saveAndFlush(health);
    }

    private NutritionRecipePo chickenDinnerRecipe(Long familyId) {
        NutritionStandardFoodPo chicken = standardFood("Chicken Breast", "MEAT",
                "200.000", "20.000", "8.000", "10.000");
        NutritionRecipePo recipe = recipe(familyId, "Chicken Plate", 2);
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(familyId);
        ingredient.setRecipeId(recipe.getId());
        ingredient.setStandardFoodId(chicken.getId());
        ingredient.setRawFoodName("Chicken Breast");
        ingredient.setAmount(new BigDecimal("200.000"));
        ingredient.setUnit("g");
        ingredient.setMappingStatus(RecipeService.MAPPING_STATUS_MAPPED);
        recipeIngredientRepository.saveAndFlush(ingredient);
        return recipe;
    }

    private NutritionStandardFoodPo standardFood(String name, String category, String calories, String protein,
                                                 String fat, String carbs) {
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(calories));
        food.setProteinPer100g(new BigDecimal(protein));
        food.setFatPer100g(new BigDecimal(fat));
        food.setCarbsPer100g(new BigDecimal(carbs));
        food.setSugarPer100g(BigDecimal.ZERO);
        food.setSodiumPer100g(BigDecimal.ZERO);
        food.setFiberPer100g(BigDecimal.ZERO);
        food.setCholesterolPer100g(BigDecimal.ZERO);
        food.setDataQuality("TEST");
        food.setStatus(NutritionStatus.ACTIVE);
        return standardFoodRepository.saveAndFlush(food);
    }

    private NutritionRecipePo recipe(Long familyId, String name, int servingCount) {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(familyId);
        recipe.setSourceType(NutritionRecipeSourceType.FAMILY_PRIVATE);
        recipe.setName(name);
        recipe.setCategory("DINNER");
        recipe.setServingCount(servingCount);
        recipe.setStatus(NutritionStatus.ACTIVE);
        return recipeRepository.saveAndFlush(recipe);
    }

    private NutritionRecipePo platformRecipe(String name, int servingCount) {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(null);
        recipe.setSourceType(NutritionRecipeSourceType.PLATFORM_PUBLIC);
        recipe.setName(name);
        recipe.setCategory("LUNCH");
        recipe.setServingCount(servingCount);
        recipe.setStatus(NutritionStatus.ACTIVE);
        return recipeRepository.saveAndFlush(recipe);
    }

    private NutritionMealPlanPo mealPlan(Long familyId, LocalDate planDate, NutritionMealPlanStatus status,
                                         String title, BigDecimal estimatedCost, int confirmedMemberCount) {
        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(familyId);
        mealPlan.setPlanDate(planDate);
        mealPlan.setTitle(title);
        mealPlan.setStatus(status);
        mealPlan.setEstimatedCost(estimatedCost);
        mealPlan.setConfirmedMemberCount(confirmedMemberCount);
        return mealPlanRepository.saveAndFlush(mealPlan);
    }

    private NutritionMealPlanItemPo mealPlanItem(Long familyId, Long mealPlanId, NutritionMealType mealType,
                                                 Long recipeId, String dishName, BigDecimal servingCount,
                                                 int sortOrder) {
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(familyId);
        item.setMealPlanId(mealPlanId);
        item.setMealType(mealType);
        item.setRecipeId(recipeId);
        item.setDishName(dishName);
        item.setServingCount(servingCount);
        item.setSortOrder(sortOrder);
        item.setStatus(NutritionStatus.ACTIVE);
        return mealPlanItemRepository.saveAndFlush(item);
    }

    private NutritionMealConfirmationPo confirmation(Long familyId, Long mealPlanId, Long memberProfileId,
                                                     String selectedMealTypes) {
        NutritionMealConfirmationPo confirmation = new NutritionMealConfirmationPo();
        confirmation.setFamilyId(familyId);
        confirmation.setMealPlanId(mealPlanId);
        confirmation.setMemberProfileId(memberProfileId);
        confirmation.setConfirmedByUserId(memberProfileId);
        confirmation.setConfirmationStatus(NutritionConfirmationStatus.CONFIRMED);
        confirmation.setEatAtHome(true);
        confirmation.setSelectedMealTypes(selectedMealTypes);
        return confirmationRepository.saveAndFlush(confirmation);
    }

    private NutritionMealConfirmationItemPo confirmationItem(Long familyId, Long confirmationId,
                                                              NutritionMealPlanItemPo mealItem,
                                                              boolean selected, String servingCount) {
        NutritionMealConfirmationItemPo item = new NutritionMealConfirmationItemPo();
        item.setFamilyId(familyId);
        item.setConfirmationId(confirmationId);
        item.setMealPlanItemId(mealItem.getId());
        item.setMealType(mealItem.getMealType());
        item.setSelected(selected);
        item.setServingCount(new BigDecimal(servingCount));
        return confirmationItemRepository.saveAndFlush(item);
    }

    private NutritionShoppingListPo shoppingList(Long familyId, Long mealPlanId, LocalDate listDate,
                                                 BigDecimal estimatedTotalPrice, BigDecimal actualTotalPrice) {
        NutritionShoppingListPo shoppingList = new NutritionShoppingListPo();
        shoppingList.setFamilyId(familyId);
        shoppingList.setMealPlanId(mealPlanId);
        shoppingList.setListDate(listDate);
        shoppingList.setStatus(NutritionShoppingListStatus.ACTIVE);
        shoppingList.setTitle("Family shopping");
        shoppingList.setEstimatedTotalPrice(estimatedTotalPrice);
        shoppingList.setActualTotalPrice(actualTotalPrice);
        return shoppingListRepository.saveAndFlush(shoppingList);
    }

    private NutritionRiskCheckResultPo risk(Long familyId, Long mealPlanId, NutritionRiskLevel riskLevel) {
        NutritionRiskCheckResultPo risk = new NutritionRiskCheckResultPo();
        risk.setFamilyId(familyId);
        risk.setSourceType("MEAL_PLAN");
        risk.setSourceId(mealPlanId);
        risk.setRuleCode("TEST_" + riskLevel);
        risk.setRiskLevel(riskLevel);
        risk.setRiskMessage("test risk");
        risk.setStatus(NutritionStatus.ACTIVE);
        risk.setResolved(false);
        return riskCheckResultRepository.saveAndFlush(risk);
    }

    private NutritionRecordPo nutritionRecord(Long familyId, Long memberProfileId, LocalDate recordDate,
                                              NutritionMealType mealType, String sourceType,
                                              NutritionNutrientsRequest nutrients) {
        NutritionRecordPo record = new NutritionRecordPo();
        record.setFamilyId(familyId);
        record.setMemberProfileId(memberProfileId);
        record.setRecordDate(recordDate);
        record.setMealType(mealType);
        record.setSourceType(sourceType);
        apply(record, nutrients);
        record.setStatus(NutritionStatus.ACTIVE);
        return recordRepository.saveAndFlush(record);
    }

    private NutritionRecordPo mealPlanNutritionRecord(Long familyId, Long memberProfileId, Long mealPlanId,
                                                      LocalDate recordDate, NutritionMealType mealType,
                                                      String dishName) {
        NutritionRecordPo record = new NutritionRecordPo();
        record.setFamilyId(familyId);
        record.setMemberProfileId(memberProfileId);
        record.setMealPlanId(mealPlanId);
        record.setRecordDate(recordDate);
        record.setMealType(mealType);
        record.setSourceType("MEAL_PLAN");
        apply(record, nutrients("10.000", "1.000", "1.000", "1.000"));
        record.setStatus(NutritionStatus.ACTIVE);
        record.setMetadataJson("{\"dishName\":\"" + dishName + "\"}");
        return recordRepository.saveAndFlush(record);
    }

    private NutritionNutrientsRequest nutrients(String calories, String protein, String fat, String carbs) {
        return nutrientsRequest(calories, protein, fat, carbs);
    }

    private NutritionNutrientsRequest nutrientsRequest(String calories, String protein, String fat, String carbs) {
        return new NutritionNutrientsRequest(new BigDecimal(calories), new BigDecimal(protein),
                new BigDecimal(fat), new BigDecimal(carbs), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void apply(NutritionRecordPo record, NutritionNutrientsRequest nutrients) {
        record.setCalories(nutrients.calories());
        record.setProtein(nutrients.protein());
        record.setFat(nutrients.fat());
        record.setCarbs(nutrients.carbs());
        record.setSugar(nutrients.sugar());
        record.setSodium(nutrients.sodium());
        record.setFiber(nutrients.fiber());
        record.setCholesterol(nutrients.cholesterol());
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
