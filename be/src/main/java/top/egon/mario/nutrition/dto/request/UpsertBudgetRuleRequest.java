package top.egon.mario.nutrition.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating or updating one family budget rule.
 */
public record UpsertBudgetRuleRequest(
        @NotBlank @Size(max = 128) String ruleName,
        @NotBlank @Size(max = 32) String periodType,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amountLimit,
        @Size(max = 16) String currency,
        @DecimalMin(value = "0.0000", inclusive = false)
        @DecimalMax(value = "1.0000") BigDecimal warningThreshold,
        boolean enabled
) {
}
