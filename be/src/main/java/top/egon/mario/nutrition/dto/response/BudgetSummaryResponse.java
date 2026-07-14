package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionMealType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Compact family budget summary for meal, daily, dish, ingredient, and channel costs.
 */
public record BudgetSummaryResponse(
        String periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalAmount,
        BigDecimal totalActualAmount,
        BigDecimal totalEstimatedAmount,
        int mealPlanCount,
        int mealCount,
        int confirmedMemberCount,
        BigDecimal perPersonCost,
        BigDecimal budgetLimit,
        BigDecimal usageRate,
        BigDecimal shoppingCompletionRate,
        List<DailySummary> dailySummaries,
        List<DishSummary> dishSummaries,
        List<IngredientSummary> ingredientSummaries,
        List<ChannelSummary> channelSummaries
) {

    public record DailySummary(
            LocalDate date,
            BigDecimal totalAmount,
            BigDecimal actualAmount,
            BigDecimal estimatedAmount,
            int mealPlanCount,
            int mealCount,
            int confirmedMemberCount,
            BigDecimal perPersonCost
    ) {
    }

    public record DishSummary(
            Long mealPlanId,
            Long itemId,
            LocalDate planDate,
            NutritionMealType mealType,
            String dishName,
            BigDecimal servingCount,
            BigDecimal confirmedServingCount,
            BigDecimal amount
    ) {
    }

    public record IngredientSummary(
            Long standardFoodId,
            String rawFoodName,
            String unit,
            BigDecimal plannedAmount,
            BigDecimal purchasedAmount,
            BigDecimal totalAmount
    ) {
    }

    public record ChannelSummary(
            String channel,
            BigDecimal totalAmount,
            int itemCount
    ) {
    }
}
