package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for recording a family food purchase price.
 */
public record CreateFoodPriceRecordRequest(
        @Min(1) Long shoppingListItemId,
        @Min(1) Long standardFoodId,
        @Size(max = 128) String rawFoodName,
        LocalDate priceDate,
        @Size(max = 128) String channel,
        @Size(max = 128) String brand,
        @DecimalMin(value = "0.000", inclusive = false) BigDecimal specAmount,
        @Size(max = 32) String specUnit,
        @DecimalMin(value = "0.000", inclusive = false) BigDecimal purchaseQuantity,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal totalPrice,
        @Size(max = 32) String sourceType,
        @Size(max = 512) String note
) {
}
