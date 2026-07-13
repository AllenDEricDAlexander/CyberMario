package top.egon.mario.nutrition.service.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces explicit standard-food population suitability tags.
 */
@Component
public class PopulationRuleChecker implements NutritionRuleChecker {

    public static final String RULE_CODE = "POPULATION";

    @Override
    public int order() {
        return 25;
    }

    @Override
    public List<RuleCheckResult> check(RuleCheckRequest request) {
        List<RuleCheckResult> results = new ArrayList<>();
        boolean adultOnly = request.ingredients().stream()
                .flatMap(ingredient -> ingredient.suitableTags().stream())
                .anyMatch(tag -> "ADULT_ONLY".equalsIgnoreCase(tag));
        if (!adultOnly) {
            return List.of();
        }
        request.memberProfiles().stream()
                .filter(member -> NutritionMemberType.ADULT != member.memberType())
                .forEach(member -> results.add(new RuleCheckResult(
                        member.memberProfileId(), RULE_CODE, NutritionRiskLevel.HIGH,
                        "Adult-only ingredient is not suitable for this family member", true, false)));
        return results;
    }
}
