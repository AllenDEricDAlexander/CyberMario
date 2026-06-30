package top.egon.mario.nutrition.service.rule;

import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.util.Map;

public record RuleCheckResult(
        Long memberProfileId,
        String ruleCode,
        NutritionRiskLevel riskLevel,
        String riskMessage,
        boolean blocking,
        boolean requiresConfirmation,
        Map<String, Object> evidence
) {

    public RuleCheckResult {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }

    public RuleCheckResult(Long memberProfileId, String ruleCode, NutritionRiskLevel riskLevel,
                           String riskMessage, boolean blocking, boolean requiresConfirmation) {
        this(memberProfileId, ruleCode, riskLevel, riskMessage, blocking, requiresConfirmation, Map.of());
    }
}
