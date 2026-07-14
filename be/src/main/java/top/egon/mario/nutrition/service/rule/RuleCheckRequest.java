package top.egon.mario.nutrition.service.rule;

import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;
import java.util.List;

public record RuleCheckRequest(
        Long familyId,
        String sourceType,
        Long sourceId,
        List<MemberRuleProfile> memberProfiles,
        List<RuleIngredient> ingredients,
        NutritionTotals nutritionTotals,
        BigDecimal estimatedCost,
        BudgetContext budgetContext,
        List<Long> recentRecipeIds
) {

    public RuleCheckRequest {
        memberProfiles = memberProfiles == null ? List.of() : List.copyOf(memberProfiles);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        nutritionTotals = nutritionTotals == null ? NutritionTotals.zero() : nutritionTotals;
        recentRecipeIds = recentRecipeIds == null ? List.of() : List.copyOf(recentRecipeIds);
    }

    public RuleCheckRequest(Long familyId, String sourceType, Long sourceId,
                            List<MemberRuleProfile> memberProfiles, List<RuleIngredient> ingredients,
                            NutritionTotals nutritionTotals, BigDecimal estimatedCost,
                            BudgetContext budgetContext) {
        this(familyId, sourceType, sourceId, memberProfiles, ingredients,
                nutritionTotals, estimatedCost, budgetContext, List.of());
    }
}
