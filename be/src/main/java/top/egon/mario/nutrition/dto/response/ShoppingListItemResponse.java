package top.egon.mario.nutrition.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Shopping list item response DTO.
 */
public record ShoppingListItemResponse(
        Long id,
        Long shoppingListId,
        Long standardFoodId,
        String rawFoodName,
        String category,
        BigDecimal plannedAmount,
        String plannedUnit,
        BigDecimal purchasedAmount,
        String purchasedUnit,
        String channel,
        String brand,
        BigDecimal specAmount,
        String specUnit,
        BigDecimal purchaseQuantity,
        BigDecimal totalPrice,
        BigDecimal normalizedUnitPrice,
        String itemStatus,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
