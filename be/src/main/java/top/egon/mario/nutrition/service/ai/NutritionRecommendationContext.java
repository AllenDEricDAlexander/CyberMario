package top.egon.mario.nutrition.service.ai;

import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable, family-scoped input snapshot for one AI recommendation job.
 */
public record NutritionRecommendationContext(
        Long familyId,
        String familyName,
        FamilySettingsContext settings,
        LocalDate plannedDate,
        List<NutritionMealType> mealTypes,
        List<MemberContext> members,
        List<RecipeContext> recipes,
        List<StandardFoodContext> standardFoods,
        List<HealthTagContext> healthTags,
        List<RecentMealContext> recentMeals,
        List<BudgetRuleContext> budgetRules,
        List<PriceContext> recentPrices,
        Long actorId,
        NutritionAiTriggerType triggerType,
        String auditCorrelationId
) {

    public NutritionRecommendationContext {
        mealTypes = immutable(mealTypes);
        members = immutable(members);
        recipes = immutable(recipes);
        standardFoods = immutable(standardFoods);
        healthTags = immutable(healthTags);
        recentMeals = immutable(recentMeals);
        budgetRules = immutable(budgetRules);
        recentPrices = immutable(recentPrices);
    }

    public record FamilySettingsContext(
            String region,
            String currency,
            List<NutritionMealType> defaultMealTypes,
            boolean aiEnabled,
            LocalTime aiGenerateTime,
            boolean healthAlertEnabled,
            boolean budgetEnabled
    ) {

        public FamilySettingsContext {
            defaultMealTypes = immutable(defaultMealTypes);
        }
    }

    public record MemberContext(
            Long memberProfileId,
            NutritionMemberType memberType,
            String activityLevel,
            List<String> dietGoals,
            List<String> allergyTags,
            List<String> dislikeTags,
            List<String> restrictionTags,
            NutritionTotals targets
    ) {

        public MemberContext {
            dietGoals = immutable(dietGoals);
            allergyTags = immutable(allergyTags);
            dislikeTags = immutable(dislikeTags);
            restrictionTags = immutable(restrictionTags);
        }
    }

    public record RecipeContext(
            Long recipeId,
            NutritionRecipeSourceType sourceType,
            String name,
            int servingCount,
            NutritionTotals nutrients,
            BigDecimal estimatedCost
    ) {
    }

    public record StandardFoodContext(
            Long foodId,
            String name,
            String category,
            NutritionTotals nutrients,
            List<String> allergenTags,
            List<String> suitableTags
    ) {

        public StandardFoodContext {
            allergenTags = immutable(allergenTags);
            suitableTags = immutable(suitableTags);
        }
    }

    public record HealthTagContext(String tagType, String tagCode, String name) {
    }

    public record RecentMealContext(
            LocalDate planDate,
            NutritionMealType mealType,
            Long recipeId,
            String dishName
    ) {
    }

    public record BudgetRuleContext(String periodType, BigDecimal amountLimit, String currency) {
    }

    public record PriceContext(
            Long standardFoodId,
            LocalDate priceDate,
            BigDecimal normalizedUnitPrice,
            String channel,
            String brand
    ) {
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
