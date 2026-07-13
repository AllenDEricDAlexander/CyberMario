package top.egon.mario.nutrition.dto.response;

import java.time.LocalDate;

/**
 * One day in a nutrition report trend series.
 */
public record NutritionTrendPointResponse(
        LocalDate date,
        NutritionNutrientsResponse nutrients
) {
}
