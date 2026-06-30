package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.NutritionRecordPo;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;

/**
 * Nutrition values returned by record and report APIs.
 */
public record NutritionNutrientsResponse(
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal carbs,
        BigDecimal sugar,
        BigDecimal sodium,
        BigDecimal fiber,
        BigDecimal cholesterol
) {

    public static NutritionNutrientsResponse from(NutritionRecordPo record) {
        return new NutritionNutrientsResponse(record.getCalories(), record.getProtein(), record.getFat(),
                record.getCarbs(), record.getSugar(), record.getSodium(), record.getFiber(),
                record.getCholesterol());
    }

    public static NutritionNutrientsResponse from(NutritionTotals totals) {
        return new NutritionNutrientsResponse(totals.calories(), totals.protein(), totals.fat(), totals.carbs(),
                totals.sugar(), totals.sodium(), totals.fiber(), totals.cholesterol());
    }
}
