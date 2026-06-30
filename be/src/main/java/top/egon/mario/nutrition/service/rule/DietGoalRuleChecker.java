package top.egon.mario.nutrition.service.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DietGoalRuleChecker implements NutritionRuleChecker {

    public static final String RULE_CODE = "DIET_GOAL";

    private static final String LOW_SODIUM = "LOW_SODIUM";
    private static final BigDecimal DEFAULT_LOW_SODIUM_LIMIT = new BigDecimal("600.000");

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<RuleCheckResult> check(RuleCheckRequest request) {
        List<RuleCheckResult> results = new ArrayList<>();
        for (MemberRuleProfile memberProfile : request.memberProfiles()) {
            if (!memberProfile.dietGoals().contains(LOW_SODIUM)) {
                continue;
            }
            BigDecimal sodiumLimit = memberProfile.targetSodium() == null
                    ? DEFAULT_LOW_SODIUM_LIMIT : memberProfile.targetSodium();
            if (request.nutritionTotals().sodium().compareTo(sodiumLimit) > 0) {
                results.add(new RuleCheckResult(memberProfile.memberProfileId(), RULE_CODE,
                        NutritionRiskLevel.MEDIUM,
                        "LOW_SODIUM goal exceeded: sodium %s > %s".formatted(
                                request.nutritionTotals().sodium(), sodiumLimit),
                        false, true,
                        Map.of("dietGoal", LOW_SODIUM,
                                "actualSodium", request.nutritionTotals().sodium(),
                                "sodiumLimit", sodiumLimit)));
            }
        }
        return results;
    }
}
