package top.egon.mario.nutrition.dto.response;

import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.time.Instant;

/**
 * Active deterministic risk attached to a meal plan.
 */
public record MealRiskResponse(
        Long id,
        Long memberProfileId,
        String ruleCode,
        NutritionRiskLevel riskLevel,
        String riskMessage,
        boolean blocking,
        boolean requiresConfirmation,
        boolean acknowledged,
        Long acknowledgedBy,
        String acknowledgementNote,
        Instant acknowledgedAt
) {
}
