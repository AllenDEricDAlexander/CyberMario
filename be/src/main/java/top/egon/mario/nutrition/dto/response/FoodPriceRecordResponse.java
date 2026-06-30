package top.egon.mario.nutrition.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Family food price record response DTO.
 */
public record FoodPriceRecordResponse(
        Long id,
        Long familyId,
        Long shoppingListItemId,
        Long standardFoodId,
        String rawFoodName,
        LocalDate priceDate,
        String channel,
        String brand,
        BigDecimal specAmount,
        String specUnit,
        BigDecimal purchaseQuantity,
        BigDecimal totalPrice,
        BigDecimal normalizedUnitPrice,
        String sourceType,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
