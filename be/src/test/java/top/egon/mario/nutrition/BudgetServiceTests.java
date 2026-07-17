package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.UpsertBudgetRuleRequest;
import top.egon.mario.nutrition.dto.response.BudgetRuleResponse;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionBudgetRuleRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListItemRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.BudgetService;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies budget summaries prefer actual shopping costs and keep daily detail.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class BudgetServiceTests {

    private static final Long COOK_USER_ID = 9401L;

    @Autowired
    private BudgetService budgetService;
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
    private NutritionBudgetRuleRepository budgetRuleRepository;
    @Autowired
    private NutritionShoppingListRepository shoppingListRepository;
    @Autowired
    private NutritionShoppingListItemRepository shoppingListItemRepository;
    @Autowired
    private NutritionFoodPriceRecordRepository priceRecordRepository;

    @BeforeEach
    void setUp() {
        priceRecordRepository.deleteAll();
        shoppingListItemRepository.deleteAll();
        shoppingListRepository.deleteAll();
        confirmationItemRepository.deleteAll();
        confirmationRepository.deleteAll();
        budgetRuleRepository.deleteAll();
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
    void budgetUsesActualPriceBeforeEstimatedPrice() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                "Actual dinner", new BigDecimal("100.00"), 2);
        mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER,
                "Tomato Pasta", new BigDecimal("2.000"), 0);
        NutritionShoppingListPo shoppingList = shoppingList(family.getId(), mealPlan.getId(),
                LocalDate.of(2026, 7, 6), new BigDecimal("100.00"), new BigDecimal("42.00"));
        shoppingListItem(family.getId(), shoppingList.getId(), "Tomato", new BigDecimal("500.000"),
                "g", "Supermarket", "CHECKED", new BigDecimal("42.00"), null);

        BudgetSummaryResponse response = budgetService.weeklyBudget(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(response.periodStart()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(response.periodEnd()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(response.totalAmount()).isEqualByComparingTo("42.00");
        assertThat(response.totalActualAmount()).isEqualByComparingTo("42.00");
        assertThat(response.totalEstimatedAmount()).isEqualByComparingTo("100.00");
        assertThat(response.dailySummaries()).singleElement()
                .satisfies(day -> assertThat(day.totalAmount()).isEqualByComparingTo("42.00"));
    }

    @Test
    void weeklyBudgetIncludesMealDailyAndPerPersonCosts() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo monday = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                "Monday dinner", new BigDecimal("30.00"), 3);
        NutritionMealPlanPo tuesday = mealPlan(family.getId(), LocalDate.of(2026, 7, 7),
                "Tuesday lunch", new BigDecimal("18.00"), 2);
        NutritionMealPlanItemPo mondayItem = mealPlanItem(family.getId(), monday.getId(), NutritionMealType.DINNER,
                "Tomato Pasta", new BigDecimal("3.000"), 0);
        mealPlanItem(family.getId(), tuesday.getId(), NutritionMealType.LUNCH,
                "Vegetable Soup", new BigDecimal("2.000"), 0);
        NutritionShoppingListPo mondayList = shoppingList(family.getId(), monday.getId(),
                LocalDate.of(2026, 7, 6), new BigDecimal("30.00"), new BigDecimal("24.00"));
        shoppingListItem(family.getId(), mondayList.getId(), "Tomato", new BigDecimal("500.000"),
                "g", "Supermarket", "CHECKED", new BigDecimal("12.00"), new BigDecimal("12.00"));
        shoppingListItem(family.getId(), mondayList.getId(), "Pasta", new BigDecimal("300.000"),
                "g", "Market", "PLANNED", null, new BigDecimal("18.00"));

        BudgetSummaryResponse response = budgetService.weeklyBudget(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(response.mealPlanCount()).isEqualTo(2);
        assertThat(response.mealCount()).isEqualTo(2);
        assertThat(response.confirmedMemberCount()).isEqualTo(5);
        assertThat(response.totalAmount()).isEqualByComparingTo("30.00");
        assertThat(response.perPersonCost()).isEqualByComparingTo("6.00");
        assertThat(response.usageRate()).isNull();
        assertThat(response.shoppingCompletionRate()).isEqualByComparingTo("0.5000");
        assertThat(response.dailySummaries()).hasSize(2);
        assertThat(response.dailySummaries()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 7, 6)))
                .singleElement()
                .satisfies(day -> {
                    assertThat(day.mealPlanCount()).isEqualTo(1);
                    assertThat(day.totalAmount()).isEqualByComparingTo("12.00");
                    assertThat(day.perPersonCost()).isEqualByComparingTo("4.00");
                });
        assertThat(response.dailySummaries()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 7, 7)))
                .singleElement()
                .satisfies(day -> assertThat(day.totalAmount()).isEqualByComparingTo("18.00"));
        assertThat(response.dishSummaries()).filteredOn(dish -> dish.itemId().equals(mondayItem.getId()))
                .singleElement()
                .satisfies(dish -> {
                    assertThat(dish.dishName()).isEqualTo("Tomato Pasta");
                    assertThat(dish.amount()).isEqualByComparingTo("0.00");
                });
        assertThat(response.ingredientSummaries()).hasSize(2);
        assertThat(response.channelSummaries()).extracting(BudgetSummaryResponse.ChannelSummary::channel)
                .containsExactlyInAnyOrder("Market", "Supermarket");
    }

    @Test
    void weeklyBudgetDishServingsFollowConfirmationSelectedMealTypes() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                "Selected meals", new BigDecimal("20.00"), 2);
        NutritionMealPlanItemPo lunch = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.LUNCH,
                "Vegetable Soup", new BigDecimal("1.000"), 0);
        NutritionMealPlanItemPo dinner = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER,
                "Tomato Pasta", new BigDecimal("2.000"), 1);
        NutritionMealConfirmationPo first = confirmation(family.getId(), mealPlan.getId(), 101L, "[]");
        NutritionMealConfirmationPo second = confirmation(family.getId(), mealPlan.getId(), 102L, "[\"DINNER\"]");
        confirmationItem(family.getId(), first.getId(), lunch, true, "1.000");
        confirmationItem(family.getId(), first.getId(), dinner, true, "2.000");
        confirmationItem(family.getId(), second.getId(), dinner, true, "2.000");
        dinner.setMetadataJson("{\"finalServingCount\":5}");
        mealPlanItemRepository.saveAndFlush(dinner);

        BudgetSummaryResponse response = budgetService.weeklyBudget(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(response.dishSummaries()).filteredOn(dish -> dish.itemId().equals(lunch.getId()))
                .singleElement()
                .satisfies(dish -> assertThat(dish.confirmedServingCount()).isEqualByComparingTo("1.000"));
        assertThat(response.dishSummaries()).filteredOn(dish -> dish.itemId().equals(dinner.getId()))
                .singleElement()
                .satisfies(dish -> {
                    assertThat(dish.confirmedServingCount()).isEqualByComparingTo("4.000");
                    assertThat(dish.finalServingCount()).isEqualByComparingTo("5.000");
                });
    }

    @Test
    void usageRateUsesApplicableBudgetLimitAndKeepsShoppingCompletionSeparate() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        BudgetRuleResponse rule = budgetService.createBudgetRule(family.getId(),
                new UpsertBudgetRuleRequest("Weekly food", "WEEKLY", new BigDecimal("500.00"),
                        "CNY", new BigDecimal("0.8000"), true), COOK_USER_ID);
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                "Budget dinner", new BigDecimal("150.00"), 2);
        mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER,
                "Family dinner", new BigDecimal("2.000"), 0);
        NutritionShoppingListPo list = shoppingList(family.getId(), mealPlan.getId(),
                LocalDate.of(2026, 7, 6), new BigDecimal("150.00"), new BigDecimal("125.00"));
        shoppingListItem(family.getId(), list.getId(), "Vegetables", new BigDecimal("500.000"),
                "g", "Market", "PURCHASED", new BigDecimal("125.00"), null);
        shoppingListItem(family.getId(), list.getId(), "Rice", new BigDecimal("500.000"),
                "g", "Market", "PLANNED", null, new BigDecimal("25.00"));

        BudgetSummaryResponse summary = budgetService.weeklyBudget(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(rule.periodType()).isEqualTo("WEEKLY");
        assertThat(summary.budgetLimit()).isEqualByComparingTo("500.00");
        assertThat(summary.totalAmount()).isEqualByComparingTo("125.00");
        assertThat(summary.usageRate()).isEqualByComparingTo("0.2500");
        assertThat(summary.shoppingCompletionRate()).isEqualByComparingTo("0.5000");
        assertThat(budgetService.listBudgetRules(family.getId(), COOK_USER_ID)).hasSize(1);
    }

    @Test
    void dishCostsComeFromEachRecipesIngredientsInsteadOfSplittingPlanTotal() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionStandardFoodPo tomato = standardFood("Tomato", "VEGETABLE");
        NutritionRecipePo smallDish = recipe(family.getId(), "Small dish", 2);
        recipeIngredient(family.getId(), smallDish.getId(), tomato.getId(), "Tomato", "100.000");
        NutritionRecipePo largeDish = recipe(family.getId(), "Large dish", 1);
        recipeIngredient(family.getId(), largeDish.getId(), tomato.getId(), "Tomato", "300.000");
        foodPrice(family.getId(), tomato.getId(), "Tomato", "0.1000");
        NutritionMealPlanPo plan = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                "Ingredient costs", new BigDecimal("100.00"), 1);
        NutritionMealPlanItemPo small = mealPlanItem(family.getId(), plan.getId(), NutritionMealType.LUNCH,
                smallDish.getId(), "Small dish", new BigDecimal("2.000"), 0);
        NutritionMealPlanItemPo large = mealPlanItem(family.getId(), plan.getId(), NutritionMealType.DINNER,
                largeDish.getId(), "Large dish", new BigDecimal("1.000"), 1);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), plan.getId(), 101L, "[]");
        confirmationItem(family.getId(), confirmation.getId(), small, true, "2.000");
        confirmationItem(family.getId(), confirmation.getId(), large, true, "1.000");
        large.setMetadataJson("{\"finalServingCount\":2}");
        mealPlanItemRepository.saveAndFlush(large);

        BudgetSummaryResponse summary = budgetService.weeklyBudget(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(summary.dishSummaries()).filteredOn(dish -> dish.itemId().equals(small.getId()))
                .singleElement().extracting(BudgetSummaryResponse.DishSummary::amount)
                .isEqualTo(new BigDecimal("10.00"));
        assertThat(summary.dishSummaries()).filteredOn(dish -> dish.itemId().equals(large.getId()))
                .singleElement().extracting(BudgetSummaryResponse.DishSummary::amount)
                .isEqualTo(new BigDecimal("60.00"));
    }

    @Test
    void budgetLimitFallsBackFromExactPeriodToDailyThenPerMeal() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo plan = mealPlan(family.getId(), LocalDate.of(2026, 7, 6),
                "One dish", new BigDecimal("20.00"), 1);
        mealPlanItem(family.getId(), plan.getId(), NutritionMealType.DINNER,
                "Dinner", new BigDecimal("1.000"), 0);
        BudgetRuleResponse daily = budgetService.createBudgetRule(family.getId(),
                new UpsertBudgetRuleRequest("Daily", "DAILY", new BigDecimal("10.00"),
                        "CNY", null, true), COOK_USER_ID);

        assertThat(budgetService.weeklyBudget(family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID)
                .budgetLimit()).isEqualByComparingTo("70.00");

        budgetService.deactivateBudgetRule(family.getId(), daily.id(), COOK_USER_ID);
        budgetService.createBudgetRule(family.getId(),
                new UpsertBudgetRuleRequest("Per dish", "PER_MEAL", new BigDecimal("25.00"),
                        "CNY", null, true), COOK_USER_ID);
        assertThat(budgetService.weeklyBudget(family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID)
                .budgetLimit()).isEqualByComparingTo("25.00");
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setStatus(NutritionStatus.ACTIVE);
        return familyRepository.saveAndFlush(family);
    }

    private NutritionMealPlanPo mealPlan(Long familyId, LocalDate planDate, String title,
                                         BigDecimal estimatedCost, int confirmedMemberCount) {
        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(familyId);
        mealPlan.setPlanDate(planDate);
        mealPlan.setTitle(title);
        mealPlan.setStatus(NutritionMealPlanStatus.CONFIRM_CLOSED);
        mealPlan.setEstimatedCost(estimatedCost);
        mealPlan.setConfirmedMemberCount(confirmedMemberCount);
        return mealPlanRepository.saveAndFlush(mealPlan);
    }

    private NutritionMealPlanItemPo mealPlanItem(Long familyId, Long mealPlanId, NutritionMealType mealType,
                                                 String dishName, BigDecimal servingCount, int sortOrder) {
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(familyId);
        item.setMealPlanId(mealPlanId);
        item.setMealType(mealType);
        item.setDishName(dishName);
        item.setServingCount(servingCount);
        item.setSortOrder(sortOrder);
        item.setStatus(NutritionStatus.ACTIVE);
        return mealPlanItemRepository.saveAndFlush(item);
    }

    private NutritionMealPlanItemPo mealPlanItem(Long familyId, Long mealPlanId, NutritionMealType mealType,
                                                 Long recipeId, String dishName, BigDecimal servingCount,
                                                 int sortOrder) {
        NutritionMealPlanItemPo item = mealPlanItem(
                familyId, mealPlanId, mealType, dishName, servingCount, sortOrder);
        item.setRecipeId(recipeId);
        return mealPlanItemRepository.saveAndFlush(item);
    }

    private NutritionStandardFoodPo standardFood(String name, String category) {
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn(name);
        food.setCategory(category);
        food.setDataQuality("MANUAL");
        food.setStatus(NutritionStatus.ACTIVE);
        return standardFoodRepository.saveAndFlush(food);
    }

    private NutritionRecipePo recipe(Long familyId, String name, int servingCount) {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(familyId);
        recipe.setSourceType(NutritionRecipeSourceType.FAMILY_PRIVATE);
        recipe.setName(name);
        recipe.setCategory("DINNER");
        recipe.setDescription("");
        recipe.setServingCount(servingCount);
        recipe.setStatus(NutritionStatus.ACTIVE);
        return recipeRepository.saveAndFlush(recipe);
    }

    private NutritionRecipeIngredientPo recipeIngredient(Long familyId, Long recipeId, Long standardFoodId,
                                                         String rawFoodName, String amount) {
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(familyId);
        ingredient.setRecipeId(recipeId);
        ingredient.setStandardFoodId(standardFoodId);
        ingredient.setRawFoodName(rawFoodName);
        ingredient.setAmount(new BigDecimal(amount));
        ingredient.setUnit("g");
        ingredient.setMappingStatus("MAPPED");
        return recipeIngredientRepository.saveAndFlush(ingredient);
    }

    private NutritionFoodPriceRecordPo foodPrice(Long familyId, Long standardFoodId,
                                                 String rawFoodName, String normalizedUnitPrice) {
        NutritionFoodPriceRecordPo price = new NutritionFoodPriceRecordPo();
        price.setFamilyId(familyId);
        price.setStandardFoodId(standardFoodId);
        price.setRawFoodName(rawFoodName);
        price.setPriceDate(LocalDate.of(2026, 7, 1));
        price.setSpecAmount(new BigDecimal("100.000"));
        price.setSpecUnit("g");
        price.setPurchaseQuantity(BigDecimal.ONE);
        price.setTotalPrice(new BigDecimal("10.00"));
        price.setNormalizedUnitPrice(new BigDecimal(normalizedUnitPrice));
        price.setSourceType("MANUAL");
        return priceRecordRepository.saveAndFlush(price);
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

    private NutritionShoppingListItemPo shoppingListItem(Long familyId, Long shoppingListId, String rawFoodName,
                                                         BigDecimal plannedAmount, String plannedUnit,
                                                         String channel, String itemStatus,
                                                         BigDecimal totalPrice, BigDecimal estimatedPrice) {
        NutritionShoppingListItemPo item = new NutritionShoppingListItemPo();
        item.setFamilyId(familyId);
        item.setShoppingListId(shoppingListId);
        item.setRawFoodName(rawFoodName);
        item.setPlannedAmount(plannedAmount);
        item.setPlannedUnit(plannedUnit);
        item.setChannel(channel);
        item.setItemStatus(itemStatus);
        item.setTotalPrice(totalPrice);
        item.setMetadataJson(estimatedPrice == null ? "{}" : "{\"estimatedTotalPrice\":" + estimatedPrice + "}");
        return shoppingListItemRepository.saveAndFlush(item);
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
