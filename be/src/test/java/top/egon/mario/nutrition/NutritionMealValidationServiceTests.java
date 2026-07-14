package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.NutritionMealValidationService;
import top.egon.mario.nutrition.service.RecipeService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionMealValidationServiceTests {

    @Autowired
    private NutritionMealValidationService validationService;
    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private RecipeService recipeService;
    @Autowired
    private NutritionStandardFoodRepository foodRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionRecipeIngredientRepository ingredientRepository;
    @Autowired
    private NutritionMealPlanRepository planRepository;
    @Autowired
    private NutritionMealPlanItemRepository itemRepository;
    @Autowired
    private NutritionRiskCheckResultRepository riskRepository;
    @Autowired
    private NutritionHealthProfileRepository healthRepository;
    @Autowired
    private NutritionMemberProfileRepository memberRepository;
    @Autowired
    private NutritionDataGrantRepository dataGrantRepository;
    @Autowired
    private NutritionScopedRoleBindingRepository roleBindingRepository;
    @Autowired
    private NutritionClanFamilyRepository clanFamilyRepository;
    @Autowired
    private NutritionFamilyRepository familyRepository;
    @Autowired
    private NutritionClanRepository clanRepository;

    @BeforeEach
    void setUp() {
        riskRepository.deleteAll();
        itemRepository.deleteAll();
        planRepository.deleteAll();
        ingredientRepository.deleteAll();
        recipeRepository.deleteAll();
        foodRepository.deleteAll();
        dataGrantRepository.deleteAll();
        roleBindingRepository.deleteAll();
        clanFamilyRepository.deleteAll();
        healthRepository.deleteAll();
        memberRepository.deleteAll();
        familyRepository.deleteAll();
        clanRepository.deleteAll();
    }

    @Test
    void allergyRiskIsPersistedAndMakesMealPlanUnpublishable() {
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), 8951L);
        NutritionHealthProfilePo health = new NutritionHealthProfilePo();
        health.setFamilyId(family.id());
        health.setMemberProfileId(family.ownerMemberProfileId());
        health.setAllergyTags("[\"PEANUT\"]");
        healthRepository.save(health);
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn("Peanut");
        food.setCategory("LEGUME");
        food.setCaloriesPer100g(new BigDecimal("500"));
        food.setAllergenTags("[\"PEANUT\"]");
        food.setDataQuality("TEST");
        food.setStatus(NutritionStatus.ACTIVE);
        food = foodRepository.save(food);
        var recipe = recipeService.createFamilyRecipe(family.id(), new CreateRecipeRequest(
                "Peanut Bowl", "DINNER", "", 1,
                List.of(new RecipeIngredientRequest(food.getId(), "Peanut", "LEGUME",
                        new BigDecimal("100"), "g", null, false))), 8951L);
        NutritionMealPlanPo plan = new NutritionMealPlanPo();
        plan.setFamilyId(family.id());
        plan.setPlanDate(LocalDate.of(2026, 7, 20));
        plan.setStatus(NutritionMealPlanStatus.PENDING_REVIEW);
        plan.setTitle("Dinner");
        plan = planRepository.save(plan);
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(family.id());
        item.setMealPlanId(plan.getId());
        item.setMealType(NutritionMealType.DINNER);
        item.setRecipeId(recipe.id());
        item.setDishName(recipe.name());
        item.setServingCount(BigDecimal.ONE);
        item.setStatus(NutritionStatus.ACTIVE);
        itemRepository.save(item);

        var result = validationService.validateAndPersist(family.id(), plan.getId(), 8951L);

        assertThat(result.publishable()).isFalse();
        assertThat(result.risks()).anySatisfy(risk -> {
            assertThat(risk.riskLevel()).isEqualTo(NutritionRiskLevel.HIGH);
            assertThat(risk.ruleCode()).isEqualTo("ALLERGY");
        });
        assertThat(riskRepository.findAll()).singleElement();
        assertThat(planRepository.findById(plan.getId())).hasValueSatisfying(saved ->
                assertThat(saved.getNutritionSnapshot()).contains("calories"));
    }
}
