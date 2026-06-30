package top.egon.mario.nutrition.service.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DislikeRuleChecker implements NutritionRuleChecker {

    public static final String RULE_CODE = "DISLIKE";

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<RuleCheckResult> check(RuleCheckRequest request) {
        List<RuleCheckResult> results = new ArrayList<>();
        for (MemberRuleProfile memberProfile : request.memberProfiles()) {
            findMatchedIngredient(memberProfile.dislikeTags(), request.ingredients()).ifPresent(matched ->
                    results.add(new RuleCheckResult(memberProfile.memberProfileId(), RULE_CODE,
                            NutritionRiskLevel.MEDIUM,
                            "Dislike tag %s matched recipe ingredient".formatted(matched.tag()),
                            false, true,
                            Map.of("matchedTag", matched.tag(), "matchedIngredient", matched.ingredientName()))));
        }
        return results;
    }

    private Optional<IngredientMatch> findMatchedIngredient(List<String> tags, List<RuleIngredient> ingredients) {
        for (String tag : tags) {
            for (RuleIngredient ingredient : ingredients) {
                if (ingredient.matches(tag)) {
                    return Optional.of(new IngredientMatch(tag, ingredient.rawFoodName()));
                }
            }
        }
        return Optional.empty();
    }

    private record IngredientMatch(String tag, String ingredientName) {
    }
}
