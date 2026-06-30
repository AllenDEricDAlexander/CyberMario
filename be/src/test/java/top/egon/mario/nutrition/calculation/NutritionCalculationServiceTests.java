package top.egon.mario.nutrition.calculation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.nutrition.service.calculation.NutritionCalculationService;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies recipe nutrition totals are calculated from mapped standard foods.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionCalculationServiceTests {

    @Autowired
    private NutritionCalculationService calculationService;
    @Autowired
    private NutritionStandardFoodRepository standardFoodRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionRecipeIngredientRepository recipeIngredientRepository;

    @BeforeEach
    void setUp() {
        recipeIngredientRepository.deleteAll();
        recipeRepository.deleteAll();
        standardFoodRepository.deleteAll();
    }

    @Test
    void calculatesNutritionByHundredGramFoodValuesAndServingAmount() {
        NutritionStandardFoodPo chicken = standardFoodRepository.save(standardFood(
                "Chicken Breast", "MEAT",
                "120.000", "10.000", "5.500", "20.000",
                "2.000", "300.000", "4.000", "30.000"));
        NutritionStandardFoodPo sauce = standardFoodRepository.save(standardFood(
                "Sauce", "CONDIMENT",
                "500.000", "1.000", "1.000", "1.000",
                "1.000", "1.000", "1.000", "1.000"));
        NutritionRecipePo recipe = recipeRepository.save(recipe());
        recipeIngredientRepository.save(ingredient(recipe.getId(), chicken.getId(), "Chicken Breast",
                new BigDecimal("150.000"), "g", RecipeService.MAPPING_STATUS_MAPPED));
        recipeIngredientRepository.save(ingredient(recipe.getId(), null, "Secret Spice",
                new BigDecimal("20.000"), "g", RecipeService.MAPPING_STATUS_UNMAPPED));
        recipeIngredientRepository.save(ingredient(recipe.getId(), sauce.getId(), "Sauce",
                new BigDecimal("1.000"), "piece", RecipeService.MAPPING_STATUS_MAPPED));

        NutritionTotals totals = calculationService.calculateRecipe(recipe.getId());

        assertThat(totals.calories()).isEqualByComparingTo("180.000");
        assertThat(totals.protein()).isEqualByComparingTo("15.000");
        assertThat(totals.fat()).isEqualByComparingTo("8.250");
        assertThat(totals.carbs()).isEqualByComparingTo("30.000");
        assertThat(totals.sugar()).isEqualByComparingTo("3.000");
        assertThat(totals.sodium()).isEqualByComparingTo("450.000");
        assertThat(totals.fiber()).isEqualByComparingTo("6.000");
        assertThat(totals.cholesterol()).isEqualByComparingTo("45.000");
    }

    @Test
    void missingRecipeCannotBeCalculatedAsZeroNutrition() {
        assertThatThrownBy(() -> calculationService.calculateRecipe(404L))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_NOT_FOUND");
    }

    private NutritionStandardFoodPo standardFood(String name, String category, String calories, String protein,
                                                 String fat, String carbs, String sugar, String sodium,
                                                 String fiber, String cholesterol) {
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn(name);
        food.setCategory(category);
        food.setCaloriesPer100g(new BigDecimal(calories));
        food.setProteinPer100g(new BigDecimal(protein));
        food.setFatPer100g(new BigDecimal(fat));
        food.setCarbsPer100g(new BigDecimal(carbs));
        food.setSugarPer100g(new BigDecimal(sugar));
        food.setSodiumPer100g(new BigDecimal(sodium));
        food.setFiberPer100g(new BigDecimal(fiber));
        food.setCholesterolPer100g(new BigDecimal(cholesterol));
        food.setDataQuality("TEST");
        food.setStatus(NutritionStatus.ACTIVE);
        return food;
    }

    private NutritionRecipePo recipe() {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(9001L);
        recipe.setSourceType(NutritionRecipeSourceType.FAMILY_PRIVATE);
        recipe.setName("Chicken Plate");
        recipe.setCategory("DINNER");
        recipe.setServingCount(2);
        recipe.setStatus(NutritionStatus.ACTIVE);
        return recipe;
    }

    private NutritionRecipeIngredientPo ingredient(Long recipeId, Long standardFoodId, String rawFoodName,
                                                   BigDecimal amount, String unit, String mappingStatus) {
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(9001L);
        ingredient.setRecipeId(recipeId);
        ingredient.setStandardFoodId(standardFoodId);
        ingredient.setRawFoodName(rawFoodName);
        ingredient.setAmount(amount);
        ingredient.setUnit(unit);
        ingredient.setMappingStatus(mappingStatus);
        return ingredient;
    }
}
