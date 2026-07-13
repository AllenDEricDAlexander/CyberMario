package top.egon.mario.nutrition.service.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects recipes repeated in the recent family meal history.
 */
@Component
public class RepetitionRuleChecker implements NutritionRuleChecker {

    public static final String RULE_CODE = "REPETITION";

    @Override
    public int order() {
        return 50;
    }

    @Override
    public List<RuleCheckResult> check(RuleCheckRequest request) {
        Set<Long> recent = new LinkedHashSet<>(request.recentRecipeIds());
        return request.ingredients().stream()
                .map(RuleIngredient::recipeId)
                .filter(java.util.Objects::nonNull)
                .filter(recent::contains)
                .findFirst()
                .map(recipeId -> List.of(new RuleCheckResult(
                        null, RULE_CODE, NutritionRiskLevel.MEDIUM,
                        "Recipe %s appeared in recent family meals".formatted(recipeId),
                        false, false)))
                .orElseGet(List::of);
    }
}
