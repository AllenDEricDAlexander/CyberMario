package top.egon.mario.nutrition.service.rule;

import java.util.List;

public interface NutritionRuleChecker {

    int order();

    List<RuleCheckResult> check(RuleCheckRequest request);
}
