package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFoodPriceRecordRequest;
import top.egon.mario.nutrition.dto.request.TransitionShoppingListRequest;
import top.egon.mario.nutrition.dto.response.FoodPriceRecordResponse;
import top.egon.mario.nutrition.dto.response.ShoppingListResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
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
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.ShoppingListService;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies shopping list generation and price recording for family meal plans.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ShoppingListServiceTests {

    private static final Long COOK_USER_ID = 9301L;

    @Autowired
    private ShoppingListService shoppingListService;
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

    @BeforeEach
    void setUp() {
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
    void shoppingListAggregatesIngredientsByStandardFoodAndUnit() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionStandardFoodPo tomato = standardFood("Tomato", "VEGETABLE");
        NutritionRecipePo pasta = recipe(family.getId(), "Tomato Pasta", 2);
        recipeIngredient(family.getId(), pasta.getId(), tomato.getId(), "Tomato", new BigDecimal("100.000"), "g");
        NutritionRecipePo soup = recipe(family.getId(), "Tomato Soup", 1);
        recipeIngredient(family.getId(), soup.getId(), tomato.getId(), "Tomatoes", new BigDecimal("50.000"), "g");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.CONFIRM_CLOSED);
        NutritionMealPlanItemPo pastaItem = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER, pasta.getId(),
                "Tomato Pasta", new BigDecimal("2.000"), 0);
        NutritionMealPlanItemPo soupItem = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.LUNCH, soup.getId(),
                "Tomato Soup", new BigDecimal("1.000"), 1);
        NutritionMealConfirmationPo first = confirmation(family.getId(), mealPlan.getId(), 101L, "[]");
        NutritionMealConfirmationPo second = confirmation(family.getId(), mealPlan.getId(), 102L, "[\"DINNER\"]");
        confirmationItem(family.getId(), first.getId(), pastaItem, true, "2.000");
        confirmationItem(family.getId(), first.getId(), soupItem, true, "1.000");
        confirmationItem(family.getId(), second.getId(), pastaItem, true, "2.000");

        ShoppingListResponse response = shoppingListService.generateFinalShoppingList(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.familyId()).isEqualTo(family.getId());
        assertThat(response.mealPlanId()).isEqualTo(mealPlan.getId());
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.standardFoodId()).isEqualTo(tomato.getId());
            assertThat(item.rawFoodName()).isEqualTo("Tomato");
            assertThat(item.category()).isEqualTo("VEGETABLE");
            assertThat(item.plannedAmount()).isEqualByComparingTo("250.000");
            assertThat(item.plannedUnit()).isEqualTo("g");
            assertThat(item.itemStatus()).isEqualTo("PLANNED");
        });
        assertThat(shoppingListRepository.findAll()).singleElement()
                .satisfies(saved -> assertThat(saved.getMealPlanId()).isEqualTo(mealPlan.getId()));
        assertThat(shoppingListItemRepository.findAll()).singleElement()
                .satisfies(saved -> assertThat(saved.getPlannedAmount()).isEqualByComparingTo("250.000"));
    }

    @Test
    void shoppingListScalesConfirmedServingsByMealItemServingCount() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionStandardFoodPo potato = standardFood("Potato", "VEGETABLE");
        NutritionRecipePo stew = recipe(family.getId(), "Potato Stew", 2);
        recipeIngredient(family.getId(), stew.getId(), potato.getId(), "Potato", new BigDecimal("75.000"), "g");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.CONFIRM_CLOSED);
        NutritionMealPlanItemPo stewItem = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER, stew.getId(),
                "Potato Stew", new BigDecimal("2.000"), 0);
        NutritionMealConfirmationPo first = confirmation(family.getId(), mealPlan.getId(), 101L, "[]");
        NutritionMealConfirmationPo second = confirmation(family.getId(), mealPlan.getId(), 102L, "[\"DINNER\"]");
        NutritionMealConfirmationPo third = confirmation(family.getId(), mealPlan.getId(), 103L, "[\"LUNCH\"]");
        confirmationItem(family.getId(), first.getId(), stewItem, true, "2.000");
        confirmationItem(family.getId(), second.getId(), stewItem, true, "2.000");
        confirmationItem(family.getId(), third.getId(), stewItem, false, "2.000");

        ShoppingListResponse response = shoppingListService.generateFinalShoppingList(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.standardFoodId()).isEqualTo(potato.getId());
            assertThat(item.plannedAmount()).isEqualByComparingTo("150.000");
        });
    }

    @Test
    void generatingShoppingListTwiceReturnsExistingListWithoutDuplicatingItems() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionStandardFoodPo carrot = standardFood("Carrot", "VEGETABLE");
        NutritionRecipePo soup = recipe(family.getId(), "Carrot Soup", 1);
        recipeIngredient(family.getId(), soup.getId(), carrot.getId(), "Carrot", new BigDecimal("120.000"), "g");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.CONFIRM_CLOSED);
        NutritionMealPlanItemPo soupItem = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.LUNCH, soup.getId(),
                "Carrot Soup", new BigDecimal("1.000"), 0);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), mealPlan.getId(), 101L, "[]");
        confirmationItem(family.getId(), confirmation.getId(), soupItem, true, "1.000");

        ShoppingListResponse first = shoppingListService.generateFinalShoppingList(
                family.getId(), mealPlan.getId(), COOK_USER_ID);
        ShoppingListResponse second = shoppingListService.generateFinalShoppingList(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.status()).isEqualTo(NutritionShoppingListStatus.DRAFT);
        assertThat(second.generatedSnapshot()).contains("mealPlanVersion", "confirmations");
        assertThat(second.items()).hasSize(1);
        assertThat(shoppingListRepository.findAll()).hasSize(1);
        assertThat(shoppingListItemRepository.findAll()).hasSize(1);
        assertThat(shoppingListService.listShoppingLists(
                family.getId(), mealPlan.getId(), COOK_USER_ID)).singleElement()
                .extracting(ShoppingListResponse::id).isEqualTo(first.id());
    }

    @Test
    void shoppingListRejectsPrivateRecipeFromAnotherFamily() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionFamilyPo otherFamily = family("Peach Family", 9302L);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionStandardFoodPo peach = standardFood("Peach", "FRUIT");
        NutritionRecipePo otherRecipe = recipe(otherFamily.getId(), "Peach Dessert", 1);
        recipeIngredient(otherFamily.getId(), otherRecipe.getId(), peach.getId(), "Peach",
                new BigDecimal("100.000"), "g");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.CONFIRM_CLOSED);
        NutritionMealPlanItemPo item = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER, otherRecipe.getId(),
                "Peach Dessert", new BigDecimal("1.000"), 0);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), mealPlan.getId(), 101L, "[]");
        confirmationItem(family.getId(), confirmation.getId(), item, true, "1.000");

        assertThatThrownBy(() -> shoppingListService.generateFinalShoppingList(
                family.getId(), mealPlan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_NOT_FOUND");
        assertThat(shoppingListRepository.findAll()).isEmpty();
        assertThat(shoppingListItemRepository.findAll()).isEmpty();
    }

    @Test
    void shoppingListAllowsPlatformPublicRecipe() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionStandardFoodPo rice = standardFood("Rice", "GRAIN");
        NutritionRecipePo publicRecipe = platformRecipe("Rice Bowl", 1);
        recipeIngredient(null, publicRecipe.getId(), rice.getId(), "Rice", new BigDecimal("150.000"), "g");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.CONFIRM_CLOSED);
        NutritionMealPlanItemPo item = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.LUNCH, publicRecipe.getId(),
                "Rice Bowl", new BigDecimal("1.000"), 0);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), mealPlan.getId(), 101L, "[]");
        confirmationItem(family.getId(), confirmation.getId(), item, true, "1.000");

        ShoppingListResponse response = shoppingListService.generateFinalShoppingList(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.items()).singleElement().satisfies(shoppingItem -> {
            assertThat(shoppingItem.standardFoodId()).isEqualTo(rice.getId());
            assertThat(shoppingItem.plannedAmount()).isEqualByComparingTo("150.000");
            assertThat(shoppingItem.plannedUnit()).isEqualTo("g");
        });
    }

    @Test
    void pendingReviewMealPlanCannotGenerateShoppingList() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> shoppingListService.generateFinalShoppingList(
                family.getId(), mealPlan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_PLAN_STATUS_INVALID");
        assertThat(shoppingListRepository.findAll()).isEmpty();
        assertThat(shoppingListItemRepository.findAll()).isEmpty();
    }

    @Test
    void priceEntryCreatesFamilyPriceRecordWithNormalizedUnitPrice() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionShoppingListPo shoppingList = shoppingList(family.getId(), null, LocalDate.of(2026, 7, 6));
        NutritionShoppingListItemPo item = shoppingListItem(family.getId(), shoppingList.getId(),
                null, "Tomato", new BigDecimal("500.000"), "g");

        FoodPriceRecordResponse response = shoppingListService.createPriceRecord(family.getId(),
                new CreateFoodPriceRecordRequest(item.getId(), null, "Tomato", LocalDate.of(2026, 7, 6),
                        "Supermarket", "Mario Farm", new BigDecimal("250.000"), "g",
                        new BigDecimal("2.000"), new BigDecimal("20.00"), "SHOPPING_LIST", "receipt"),
                COOK_USER_ID);

        assertThat(response.familyId()).isEqualTo(family.getId());
        assertThat(response.shoppingListItemId()).isEqualTo(item.getId());
        assertThat(response.normalizedUnitPrice()).isEqualByComparingTo("0.0400");
        assertThat(priceRecordRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getRawFoodName()).isEqualTo("Tomato");
            assertThat(saved.getTotalPrice()).isEqualByComparingTo("20.00");
            assertThat(saved.getNormalizedUnitPrice()).isEqualByComparingTo("0.0400");
        });
        assertThat(shoppingListItemRepository.findById(item.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getTotalPrice()).isEqualByComparingTo("20.00");
            assertThat(saved.getNormalizedUnitPrice()).isEqualByComparingTo("0.0400");
            assertThat(saved.getChannel()).isEqualTo("Supermarket");
        });
        assertThat(shoppingListRepository.findById(shoppingList.getId()).orElseThrow().getActualTotalPrice())
                .isEqualByComparingTo("20.00");
        assertThat(shoppingListService.listPriceRecords(family.getId(), null, COOK_USER_ID))
                .singleElement().extracting(FoodPriceRecordResponse::brand).isEqualTo("Mario Farm");
    }

    @Test
    void previewBeforeCloseDoesNotPersistAndFinalGenerationRequiresClosedConfirmation() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionStandardFoodPo tomato = standardFood("Tomato", "VEGETABLE");
        NutritionRecipePo recipe = recipe(family.getId(), "Tomato Soup", 2);
        recipeIngredient(family.getId(), recipe.getId(), tomato.getId(), "Tomato",
                new BigDecimal("300.000"), "g");
        NutritionMealPlanPo plan = mealPlan(family.getId(), NutritionMealPlanStatus.CONFIRMING);
        NutritionMealPlanItemPo item = mealPlanItem(family.getId(), plan.getId(), NutritionMealType.DINNER,
                recipe.getId(), "Tomato Soup", new BigDecimal("2.000"), 0);
        NutritionMealConfirmationPo confirmation = confirmation(family.getId(), plan.getId(), 101L, "[]");
        confirmationItem(family.getId(), confirmation.getId(), item, true, "3.000");

        ShoppingListResponse preview = shoppingListService.previewShoppingList(
                family.getId(), plan.getId(), COOK_USER_ID);

        assertThat(preview.id()).isNull();
        assertThat(preview.items()).singleElement()
                .satisfies(shoppingItem -> assertThat(shoppingItem.plannedAmount())
                        .isEqualByComparingTo("450.000"));
        assertThat(shoppingListRepository.findAll()).isEmpty();
        assertThatThrownBy(() -> shoppingListService.generateFinalShoppingList(
                family.getId(), plan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_PLAN_STATUS_INVALID");
    }

    @Test
    void shoppingListTransitionsFollowPurchasingLifecycle() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionShoppingListPo list = shoppingList(family.getId(), null, LocalDate.of(2026, 7, 6));
        list.setStatus(NutritionShoppingListStatus.DRAFT);
        shoppingListRepository.saveAndFlush(list);

        ShoppingListResponse active = shoppingListService.transitionShoppingList(family.getId(), list.getId(),
                new TransitionShoppingListRequest(NutritionShoppingListStatus.ACTIVE), COOK_USER_ID);
        ShoppingListResponse purchasing = shoppingListService.transitionShoppingList(family.getId(), list.getId(),
                new TransitionShoppingListRequest(NutritionShoppingListStatus.PURCHASING), COOK_USER_ID);

        assertThat(active.status()).isEqualTo(NutritionShoppingListStatus.ACTIVE);
        assertThat(purchasing.status()).isEqualTo(NutritionShoppingListStatus.PURCHASING);
        assertThatThrownBy(() -> shoppingListService.transitionShoppingList(family.getId(), list.getId(),
                new TransitionShoppingListRequest(NutritionShoppingListStatus.CLOSED), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_SHOPPING_LIST_STATUS_INVALID");
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setStatus(NutritionStatus.ACTIVE);
        return familyRepository.saveAndFlush(family);
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

    private NutritionRecipePo platformRecipe(String name, int servingCount) {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(null);
        recipe.setSourceType(NutritionRecipeSourceType.PLATFORM_PUBLIC);
        recipe.setName(name);
        recipe.setCategory("LUNCH");
        recipe.setDescription("");
        recipe.setServingCount(servingCount);
        recipe.setStatus(NutritionStatus.ACTIVE);
        return recipeRepository.saveAndFlush(recipe);
    }

    private NutritionRecipeIngredientPo recipeIngredient(Long familyId, Long recipeId, Long standardFoodId,
                                                         String rawFoodName, BigDecimal amount, String unit) {
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(familyId);
        ingredient.setRecipeId(recipeId);
        ingredient.setStandardFoodId(standardFoodId);
        ingredient.setRawFoodName(rawFoodName);
        ingredient.setAmount(amount);
        ingredient.setUnit(unit);
        ingredient.setMappingStatus("MAPPED");
        return recipeIngredientRepository.saveAndFlush(ingredient);
    }

    private NutritionMealPlanPo mealPlan(Long familyId, NutritionMealPlanStatus status) {
        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(familyId);
        mealPlan.setPlanDate(LocalDate.of(2026, 7, 6));
        mealPlan.setTitle("Family meals");
        mealPlan.setStatus(status);
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

    private NutritionShoppingListPo shoppingList(Long familyId, Long mealPlanId, LocalDate listDate) {
        NutritionShoppingListPo shoppingList = new NutritionShoppingListPo();
        shoppingList.setFamilyId(familyId);
        shoppingList.setMealPlanId(mealPlanId);
        shoppingList.setListDate(listDate);
        shoppingList.setStatus(NutritionShoppingListStatus.ACTIVE);
        shoppingList.setTitle("Family shopping");
        return shoppingListRepository.saveAndFlush(shoppingList);
    }

    private NutritionShoppingListItemPo shoppingListItem(Long familyId, Long shoppingListId, Long standardFoodId,
                                                         String rawFoodName, BigDecimal plannedAmount,
                                                         String plannedUnit) {
        NutritionShoppingListItemPo item = new NutritionShoppingListItemPo();
        item.setFamilyId(familyId);
        item.setShoppingListId(shoppingListId);
        item.setStandardFoodId(standardFoodId);
        item.setRawFoodName(rawFoodName);
        item.setPlannedAmount(plannedAmount);
        item.setPlannedUnit(plannedUnit);
        item.setItemStatus("PLANNED");
        return shoppingListItemRepository.saveAndFlush(item);
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
