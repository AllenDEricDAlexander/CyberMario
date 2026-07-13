package top.egon.mario.nutrition.service.calculation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.NutritionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calculates recipe nutrition from mapped standard-food values.
 */
@Service
@RequiredArgsConstructor
@Validated
public class NutritionCalculationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.000");

    private final NutritionRecipeRepository recipeRepository;
    private final NutritionRecipeIngredientRepository recipeIngredientRepository;
    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionUnitConversionService conversionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public NutritionTotals calculateRecipe(@NotNull Long recipeId) {
        recipeRepository.findByIdAndStatusAndDeletedFalse(recipeId, NutritionStatus.ACTIVE)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_RECIPE_NOT_FOUND", "nutrition recipe not found"));
        List<NutritionRecipeIngredientPo> ingredients = recipeIngredientRepository
                .findByRecipeIdAndDeletedFalseOrderByIdAsc(recipeId);
        if (ingredients.isEmpty()) {
            return NutritionTotals.zero();
        }
        Map<Long, NutritionStandardFoodPo> standardFoods = standardFoodRepository.findAllById(ingredients.stream()
                        .map(NutritionRecipeIngredientPo::getStandardFoodId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .filter(food -> !food.isDeleted())
                .filter(food -> food.getStatus() == NutritionStatus.ACTIVE)
                .collect(Collectors.toMap(NutritionStandardFoodPo::getId, Function.identity()));
        return calculate(ingredients, standardFoods);
    }

    public NutritionTotals calculate(List<NutritionRecipeIngredientPo> ingredients,
                                     Map<Long, NutritionStandardFoodPo> standardFoods) {
        if (ingredients == null || ingredients.isEmpty()) {
            return NutritionTotals.zero();
        }
        NutritionTotals totals = NutritionTotals.zero();
        Map<Long, NutritionStandardFoodPo> foods = standardFoods == null ? Map.of() : standardFoods;
        for (NutritionRecipeIngredientPo ingredient : ingredients) {
            if (ingredient.getStandardFoodId() == null) {
                continue;
            }
            NutritionStandardFoodPo food = foods.get(ingredient.getStandardFoodId());
            if (food == null || ingredient.getAmount() == null) {
                continue;
            }
            try {
                totals = totals.plus(calculateIngredient(ingredient, food));
            } catch (NutritionException ignored) {
                // Validation reports missing required mappings or conversions; calculations stay deterministic.
            }
        }
        return totals;
    }

    public NutritionTotals calculateIngredient(NutritionRecipeIngredientPo ingredient,
                                               NutritionStandardFoodPo food) {
        BigDecimal grams = ingredientGrams(ingredient);
        return new NutritionTotals(
                contribution(food.getCaloriesPer100g(), grams),
                contribution(food.getProteinPer100g(), grams),
                contribution(food.getFatPer100g(), grams),
                contribution(food.getCarbsPer100g(), grams),
                contribution(food.getSugarPer100g(), grams),
                contribution(food.getSodiumPer100g(), grams),
                contribution(food.getFiberPer100g(), grams),
                contribution(food.getCholesterolPer100g(), grams));
    }

    public BigDecimal ingredientGrams(NutritionRecipeIngredientPo ingredient) {
        return conversionService.toGrams(
                ingredient.getAmount(), ingredient.getUnit(), readGramsPerUnit(ingredient.getMetadataJson()));
    }

    private BigDecimal contribution(BigDecimal per100g, BigDecimal amountInGrams) {
        if (per100g == null || amountInGrams == null) {
            return BigDecimal.ZERO;
        }
        return per100g.multiply(amountInGrams).divide(ONE_HUNDRED, 3, RoundingMode.HALF_UP);
    }

    private BigDecimal readGramsPerUnit(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(metadataJson).get("gramsPerUnit");
            return value == null || value.isNull() ? null : value.decimalValue();
        } catch (Exception ignored) {
            return null;
        }
    }
}
