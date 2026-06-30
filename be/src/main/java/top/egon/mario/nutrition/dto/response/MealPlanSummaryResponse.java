package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Confirmed member and dish serving summary for one meal plan.
 */
public record MealPlanSummaryResponse(
        Long mealPlanId,
        int confirmedMemberCount,
        List<DishSummary> dishes
) {

    public record DishSummary(
            Long itemId,
            String dishName,
            NutritionMealType mealType,
            BigDecimal servingCount,
            BigDecimal confirmedServingTotal
    ) {
    }
}
