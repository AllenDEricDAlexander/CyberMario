package top.egon.mario.nutrition.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.po.NutritionBudgetRulePo;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionHealthTagPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionBudgetRuleRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.ai.NutritionRecommendationContext;
import top.egon.mario.nutrition.service.ai.NutritionRecommendationContextService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionRecommendationContextServiceTests {

    @Autowired
    private NutritionRecommendationContextService contextService;
    @Autowired
    private NutritionFamilyRepository familyRepository;
    @Autowired
    private NutritionMemberProfileRepository memberProfileRepository;
    @Autowired
    private NutritionHealthProfileRepository healthProfileRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionStandardFoodRepository standardFoodRepository;
    @Autowired
    private NutritionHealthTagRepository healthTagRepository;
    @Autowired
    private NutritionBudgetRuleRepository budgetRuleRepository;
    @Autowired
    private NutritionFoodPriceRecordRepository priceRepository;
    @Autowired
    private NutritionMealPlanRepository mealPlanRepository;
    @Autowired
    private NutritionMealPlanItemRepository mealPlanItemRepository;

    @BeforeEach
    void setUp() {
        mealPlanItemRepository.deleteAll();
        mealPlanRepository.deleteAll();
        priceRepository.deleteAll();
        budgetRuleRepository.deleteAll();
        healthTagRepository.deleteAll();
        recipeRepository.deleteAll();
        standardFoodRepository.deleteAll();
        healthProfileRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
    }

    @Test
    void contextContainsHealthRecipesCatalogBudgetPricesAndRecentMeals() {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName("Mario Family");
        family.setOwnerUserId(8801L);
        family.setCurrency("CNY");
        family.setDefaultMealTypes("[\"DINNER\"]");
        family.setAiEnabled(true);
        family.setBudgetEnabled(true);
        family.setStatus(NutritionStatus.ACTIVE);
        family = familyRepository.save(family);

        NutritionMemberProfilePo member = new NutritionMemberProfilePo();
        member.setFamilyId(family.getId());
        member.setNickname("Mario");
        member.setMemberType(NutritionMemberType.ADULT);
        member.setStatus(NutritionStatus.ACTIVE);
        member = memberProfileRepository.save(member);
        NutritionHealthProfilePo health = new NutritionHealthProfilePo();
        health.setFamilyId(family.getId());
        health.setMemberProfileId(member.getId());
        health.setAllergyTags("[\"PEANUT\"]");
        health.setDietGoals("[\"LOW_SODIUM\"]");
        healthProfileRepository.save(health);

        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn("Tomato");
        food.setCategory("VEGETABLE");
        food.setCaloriesPer100g(new BigDecimal("18"));
        food.setDataQuality("TEST");
        food.setStatus(NutritionStatus.ACTIVE);
        food = standardFoodRepository.save(food);
        saveRecipe(null, NutritionRecipeSourceType.PLATFORM_PUBLIC, "Public Soup");
        saveRecipe(family.getId(), NutritionRecipeSourceType.FAMILY_PRIVATE, "Tomato Pasta");

        NutritionHealthTagPo tag = new NutritionHealthTagPo();
        tag.setTagType("ALLERGY_TAG");
        tag.setTagCode("PEANUT");
        tag.setName("Peanut");
        tag.setStatus(NutritionStatus.ACTIVE);
        healthTagRepository.save(tag);
        NutritionBudgetRulePo budget = new NutritionBudgetRulePo();
        budget.setFamilyId(family.getId());
        budget.setRuleName("Dinner");
        budget.setPeriodType("PER_MEAL");
        budget.setAmountLimit(new BigDecimal("30"));
        budget.setCurrency("CNY");
        budget.setEnabled(true);
        budget.setStatus(NutritionStatus.ACTIVE);
        budgetRuleRepository.save(budget);
        NutritionFoodPriceRecordPo price = new NutritionFoodPriceRecordPo();
        price.setFamilyId(family.getId());
        price.setStandardFoodId(food.getId());
        price.setRawFoodName("Tomato");
        price.setPriceDate(LocalDate.of(2026, 7, 10));
        price.setTotalPrice(new BigDecimal("5"));
        price.setNormalizedUnitPrice(new BigDecimal("0.01"));
        price.setSourceType("TEST");
        priceRepository.save(price);
        NutritionMealPlanPo plan = new NutritionMealPlanPo();
        plan.setFamilyId(family.getId());
        plan.setPlanDate(LocalDate.of(2026, 7, 12));
        plan.setStatus(NutritionMealPlanStatus.COMPLETED);
        plan.setTitle("Recent dinner");
        plan = mealPlanRepository.save(plan);
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(family.getId());
        item.setMealPlanId(plan.getId());
        item.setMealType(NutritionMealType.DINNER);
        item.setDishName("Tomato Pasta");
        item.setStatus(NutritionStatus.ACTIVE);
        mealPlanItemRepository.save(item);

        NutritionRecommendationContext context = contextService.build(
                family.getId(), LocalDate.of(2026, 7, 20), List.of(NutritionMealType.DINNER),
                8801L, NutritionAiTriggerType.MANUAL);

        assertThat(context.members()).extracting(NutritionRecommendationContext.MemberContext::allergyTags)
                .contains(List.of("PEANUT"));
        assertThat(context.recipes()).extracting(NutritionRecommendationContext.RecipeContext::name)
                .contains("Public Soup", "Tomato Pasta");
        assertThat(context.standardFoods()).extracting(NutritionRecommendationContext.StandardFoodContext::foodId)
                .contains(food.getId());
        assertThat(context.healthTags()).extracting(NutritionRecommendationContext.HealthTagContext::tagCode)
                .contains("PEANUT");
        assertThat(context.budgetRules()).isNotEmpty();
        assertThat(context.recentPrices()).isNotEmpty();
        assertThat(context.recentMeals()).extracting(NutritionRecommendationContext.RecentMealContext::dishName)
                .contains("Tomato Pasta");
        assertThatThrownBy(() -> context.members().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.members().getFirst().allergyTags().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void saveRecipe(Long familyId, NutritionRecipeSourceType sourceType, String name) {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(familyId);
        recipe.setSourceType(sourceType);
        recipe.setName(name);
        recipe.setStatus(NutritionStatus.ACTIVE);
        recipeRepository.save(recipe);
    }
}
