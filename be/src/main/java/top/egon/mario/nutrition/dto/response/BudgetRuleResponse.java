package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Family budget rule response DTO.
 */
public record BudgetRuleResponse(
        Long id,
        Long familyId,
        String ruleName,
        String periodType,
        BigDecimal amountLimit,
        String currency,
        BigDecimal warningThreshold,
        boolean enabled,
        NutritionStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
