package top.egon.mario.nutrition.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.RecipeStepRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeStepRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.ai.NutritionAiIngredientDraft;
import top.egon.mario.nutrition.service.ai.NutritionAiMenuDraft;
import top.egon.mario.nutrition.service.ai.NutritionAiRecipeDraft;
import top.egon.mario.nutrition.service.ai.NutritionAiRecipeMaterializationService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionAiRecipeMaterializationServiceTests {

    @Autowired
    private NutritionAiRecipeMaterializationService materializationService;
    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private NutritionStandardFoodRepository standardFoodRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionRecipeIngredientRepository ingredientRepository;
    @Autowired
    private NutritionRecipeStepRepository stepRepository;
    @Autowired
    private NutritionDataGrantRepository dataGrantRepository;
    @Autowired
    private NutritionScopedRoleBindingRepository roleBindingRepository;
    @Autowired
    private NutritionHealthProfileRepository healthProfileRepository;
    @Autowired
    private NutritionMemberProfileRepository memberProfileRepository;
    @Autowired
    private NutritionClanFamilyRepository clanFamilyRepository;
    @Autowired
    private NutritionFamilyRepository familyRepository;
    @Autowired
    private NutritionClanRepository clanRepository;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        ingredientRepository.deleteAll();
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
    void generatedDraftCreatesAiRecipeWithMappedIngredientsAndSteps() {
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), 8901L);
        NutritionStandardFoodPo food = standardFood("Tomato");
        food = standardFoodRepository.save(food);
        NutritionAiMenuDraft draft = new NutritionAiMenuDraft("Dinner", "Balanced",
                List.of(NutritionMealType.DINNER), List.of(new NutritionAiRecipeDraft(
                NutritionMealType.DINNER, null, "Tomato Soup", new BigDecimal("2"),
                List.of(new NutritionAiIngredientDraft("Tomato", "VEGETABLE", food.getId(),
                        new BigDecimal("200"), "g", null, false)),
                List.of(new RecipeStepRequest(1, "Cook", "Simmer tomato")), "Simple")),
                new BigDecimal("10"));

        var materialized = materializationService.materialize(family.id(), draft, 8901L);

        assertThat(materialized).singleElement().satisfies(recipe -> {
            assertThat(recipe.recipeId()).isNotNull();
            assertThat(recipe.dishName()).isEqualTo("Tomato Soup");
            assertThat(recipe.nutrients().calories()).isEqualByComparingTo("36.000");
        });
        assertThat(recipeRepository.findAll()).singleElement()
                .satisfies(recipe -> assertThat(recipe.getSourceType())
                        .isEqualTo(NutritionRecipeSourceType.AI_GENERATED));
        assertThat(stepRepository.findAll()).singleElement();
    }

    @Test
    void existingRecipeFromAnotherFamilyIsRejected() {
        FamilyResponse mario = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), 8911L);
        FamilyResponse peach = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Peach Family", null, null, List.of(), "Peach"), 8912L);
        var foreign = new top.egon.mario.nutrition.po.NutritionRecipePo();
        foreign.setFamilyId(peach.id());
        foreign.setSourceType(NutritionRecipeSourceType.FAMILY_PRIVATE);
        foreign.setName("Peach Cake");
        foreign.setStatus(NutritionStatus.ACTIVE);
        foreign = recipeRepository.save(foreign);
        NutritionAiMenuDraft draft = new NutritionAiMenuDraft("Dinner", "",
                List.of(NutritionMealType.DINNER), List.of(new NutritionAiRecipeDraft(
                NutritionMealType.DINNER, foreign.getId(), null, BigDecimal.ONE,
                List.of(), List.of(), "Existing")), null);

        Long foreignId = foreign.getId();
        assertThatThrownBy(() -> materializationService.materialize(mario.id(), draft, 8911L))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_NOT_FOUND");
        assertThat(recipeRepository.findById(foreignId)).isPresent();
    }

    @Test
    void existingPublicAndFamilyRecipesAreResolvedWithoutCopying() {
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), 8921L);
        NutritionStandardFoodPo food = standardFoodRepository.save(standardFood("Tomato"));
        NutritionRecipePo publicRecipe = recipeRepository.save(recipe(
                null, NutritionRecipeSourceType.PLATFORM_PUBLIC, "Public Soup"));
        NutritionRecipePo familyRecipe = recipeRepository.save(recipe(
                family.id(), NutritionRecipeSourceType.FAMILY_PRIVATE, "Family Soup"));
        ingredientRepository.save(ingredient(null, publicRecipe.getId(), food.getId()));
        ingredientRepository.save(ingredient(family.id(), familyRecipe.getId(), food.getId()));
        NutritionAiMenuDraft draft = new NutritionAiMenuDraft("Dinner", "",
                List.of(NutritionMealType.DINNER), List.of(
                existingDraft(publicRecipe.getId()), existingDraft(familyRecipe.getId())), null);

        var materialized = materializationService.materialize(family.id(), draft, 8921L);

        assertThat(materialized).extracting(recipe -> recipe.recipeId())
                .containsExactly(publicRecipe.getId(), familyRecipe.getId());
        assertThat(recipeRepository.findAll()).hasSize(2);
    }

    private NutritionAiRecipeDraft existingDraft(Long recipeId) {
        return new NutritionAiRecipeDraft(NutritionMealType.DINNER, recipeId, null, BigDecimal.ONE,
                List.of(), List.of(), "Existing");
    }

    private NutritionRecipePo recipe(Long familyId, NutritionRecipeSourceType sourceType, String name) {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(familyId);
        recipe.setSourceType(sourceType);
        recipe.setName(name);
        recipe.setStatus(NutritionStatus.ACTIVE);
        return recipe;
    }

    private NutritionRecipeIngredientPo ingredient(Long familyId, Long recipeId, Long foodId) {
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(familyId);
        ingredient.setRecipeId(recipeId);
        ingredient.setStandardFoodId(foodId);
        ingredient.setRawFoodName("Tomato");
        ingredient.setAmount(new BigDecimal("100"));
        ingredient.setUnit("g");
        ingredient.setMappingStatus("MAPPED");
        return ingredient;
    }

    private NutritionStandardFoodPo standardFood(String name) {
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn(name);
        food.setCategory("VEGETABLE");
        food.setCaloriesPer100g(new BigDecimal("18"));
        food.setDataQuality("TEST");
        food.setStatus(NutritionStatus.ACTIVE);
        return food;
    }
}
