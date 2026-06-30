package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for updating one shopping list item during purchasing.
 */
public record UpdateShoppingListItemRequest(
        @DecimalMin(value = "0.000", inclusive = false) BigDecimal purchasedAmount,
        @Size(max = 32) String purchasedUnit,
        Boolean checked,
        @Size(max = 32) String itemStatus,
        @Size(max = 128) String channel,
        @Size(max = 128) String brand,
        @DecimalMin(value = "0.000", inclusive = false) BigDecimal specAmount,
        @Size(max = 32) String specUnit,
        @DecimalMin(value = "0.000", inclusive = false) BigDecimal purchaseQuantity,
        @DecimalMin(value = "0.00", inclusive = false) BigDecimal totalPrice,
        @Size(max = 512) String note
) {
}
