package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Confirmed member and dish serving summary for one meal plan.
 */
public record MealPlanSummaryResponse(
        Long mealPlanId,
        int activeMemberCount,
        int confirmedMemberCount,
        int awayMemberCount,
        int unconfirmedMemberCount,
        Map<NutritionRiskLevel, Long> riskCounts,
        List<String> remarks,
        boolean readyForShopping,
        List<DishSummary> dishes
) {

    public record DishSummary(
            Long itemId,
            String dishName,
            NutritionMealType mealType,
            BigDecimal servingCount,
            int selectedMemberCount,
            BigDecimal confirmedServingTotal
    ) {
    }
}
