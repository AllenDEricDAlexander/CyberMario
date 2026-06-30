package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
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
        assertThat(response.totalAmount()).isEqualByComparingTo("42.00");
        assertThat(response.perPersonCost()).isEqualByComparingTo("8.40");
        assertThat(response.usageRate()).isEqualByComparingTo("0.5000");
        assertThat(response.dailySummaries()).hasSize(2);
        assertThat(response.dailySummaries()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 7, 6)))
                .singleElement()
                .satisfies(day -> {
                    assertThat(day.mealPlanCount()).isEqualTo(1);
                    assertThat(day.totalAmount()).isEqualByComparingTo("24.00");
                    assertThat(day.perPersonCost()).isEqualByComparingTo("8.00");
                });
        assertThat(response.dailySummaries()).filteredOn(day -> day.date().equals(LocalDate.of(2026, 7, 7)))
                .singleElement()
                .satisfies(day -> assertThat(day.totalAmount()).isEqualByComparingTo("18.00"));
        assertThat(response.dishSummaries()).filteredOn(dish -> dish.itemId().equals(mondayItem.getId()))
                .singleElement()
                .satisfies(dish -> {
                    assertThat(dish.dishName()).isEqualTo("Tomato Pasta");
                    assertThat(dish.amount()).isEqualByComparingTo("24.00");
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
        confirmation(family.getId(), mealPlan.getId(), 101L, "[]");
        confirmation(family.getId(), mealPlan.getId(), 102L, "[\"DINNER\"]");

        BudgetSummaryResponse response = budgetService.weeklyBudget(
                family.getId(), LocalDate.of(2026, 7, 6), COOK_USER_ID);

        assertThat(response.dishSummaries()).filteredOn(dish -> dish.itemId().equals(lunch.getId()))
                .singleElement()
                .satisfies(dish -> assertThat(dish.confirmedServingCount()).isEqualByComparingTo("1.000"));
        assertThat(response.dishSummaries()).filteredOn(dish -> dish.itemId().equals(dinner.getId()))
                .singleElement()
                .satisfies(dish -> assertThat(dish.confirmedServingCount()).isEqualByComparingTo("4.000"));
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
