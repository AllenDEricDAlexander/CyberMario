package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Shopping list response DTO with aggregated items.
 */
public record ShoppingListResponse(
        Long id,
        Long familyId,
        Long mealPlanId,
        LocalDate listDate,
        NutritionShoppingListStatus status,
        String title,
        BigDecimal estimatedTotalPrice,
        BigDecimal actualTotalPrice,
        List<ShoppingListItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
