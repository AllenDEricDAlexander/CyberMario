package top.egon.mario.nutrition.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record NutritionTotals(
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal carbs,
        BigDecimal sugar,
        BigDecimal sodium,
        BigDecimal fiber,
        BigDecimal cholesterol
) {

    private static final int SCALE = 3;

    public NutritionTotals {
        calories = normalize(calories);
        protein = normalize(protein);
        fat = normalize(fat);
        carbs = normalize(carbs);
        sugar = normalize(sugar);
        sodium = normalize(sodium);
        fiber = normalize(fiber);
        cholesterol = normalize(cholesterol);
    }

    public static NutritionTotals zero() {
        return new NutritionTotals(null, null, null, null, null, null, null, null);
    }

    public NutritionTotals plus(NutritionTotals other) {
        if (other == null) {
            return this;
        }
        return new NutritionTotals(
                calories.add(other.calories),
                protein.add(other.protein),
                fat.add(other.fat),
                carbs.add(other.carbs),
                sugar.add(other.sugar),
                sodium.add(other.sodium),
                fiber.add(other.fiber),
                cholesterol.add(other.cholesterol));
    }

    public NutritionTotals multiply(BigDecimal factor) {
        if (factor == null) {
            return zero();
        }
        return new NutritionTotals(
                calories.multiply(factor), protein.multiply(factor), fat.multiply(factor),
                carbs.multiply(factor), sugar.multiply(factor), sodium.multiply(factor),
                fiber.multiply(factor), cholesterol.multiply(factor));
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
