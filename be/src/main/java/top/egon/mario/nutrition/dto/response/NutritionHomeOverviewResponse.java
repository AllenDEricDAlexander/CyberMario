package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Live family nutrition workflow state for one day.
 */
public record NutritionHomeOverviewResponse(
        Long familyId,
        LocalDate date,
        List<MealPlanResponse> mealPlans,
        int confirmedMemberCount,
        int awayMemberCount,
        int unconfirmedMemberCount,
        Map<NutritionRiskLevel, Long> riskCounts,
        NutritionShoppingListStatus shoppingState,
        BigDecimal actualCost,
        BigDecimal estimatedCost,
        BigDecimal budgetUsageRate,
        boolean nutritionRecordReady
) {
}
