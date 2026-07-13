package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionBudgetRuleRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;
import top.egon.mario.nutrition.service.rule.BudgetContext;
import top.egon.mario.nutrition.service.rule.MemberRuleProfile;
import top.egon.mario.nutrition.service.rule.NutritionRuleCheckService;
import top.egon.mario.nutrition.service.rule.RuleCheckRequest;
import top.egon.mario.nutrition.service.rule.RuleCheckResult;
import top.egon.mario.nutrition.service.rule.RuleIngredient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recalculates meal-plan snapshots and persists deterministic family risks.
 */
@Service
@RequiredArgsConstructor
public class NutritionMealValidationService {

    private static final String SOURCE_TYPE_MEAL_PLAN = "MEAL_PLAN";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionRecipeIngredientRepository recipeIngredientRepository;
    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionMemberProfileRepository memberProfileRepository;
    private final NutritionHealthProfileRepository healthProfileRepository;
    private final NutritionBudgetRuleRepository budgetRuleRepository;
    private final NutritionRuleCheckService ruleCheckService;
    private final NutritionAccessService accessService;
    private final RecipeService recipeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MealValidationResult validateAndPersist(Long familyId, Long mealPlanId, Long actorId) {
        accessService.requireCookFamily(actorId, familyId);
        NutritionMealPlanPo plan = mealPlanRepository.findByIdAndFamilyIdAndDeletedFalse(mealPlanId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEAL_PLAN_NOT_FOUND", "nutrition meal plan not found"));
        List<NutritionMealPlanItemPo> items = mealPlanItemRepository
                .findByMealPlanIdAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        mealPlanId, NutritionStatus.ACTIVE);
        NutritionTotals planTotals = NutritionTotals.zero();
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean completeCost = !items.isEmpty();
        Set<String> errors = new LinkedHashSet<>();
        List<Long> recipeIds = new ArrayList<>();
        for (NutritionMealPlanItemPo item : items) {
            if (item.getRecipeId() == null) {
                errors.add("NUTRITION_MEAL_RECIPE_REQUIRED");
                completeCost = false;
                continue;
            }
            RecipeResponse recipe = recipeService.getRecipe(familyId, item.getRecipeId(), actorId);
            recipeIds.add(recipe.id());
            var recipeValidation = recipeService.validateRecipe(familyId, recipe.id(), actorId);
            errors.addAll(recipeValidation.errors());
            BigDecimal factor = item.getServingCount().divide(
                    BigDecimal.valueOf(recipe.servingCount()), 9, RoundingMode.HALF_UP);
            NutritionTotals itemTotals = recipe.nutritionSnapshot().multiply(factor);
            item.setNutritionSnapshot(writeJson(itemTotals));
            planTotals = planTotals.plus(itemTotals);
            if (recipe.estimatedCost() == null) {
                completeCost = false;
                item.setCostSnapshot("{}");
            } else {
                BigDecimal itemCost = recipe.estimatedCost().multiply(factor).setScale(2, RoundingMode.HALF_UP);
                totalCost = totalCost.add(itemCost);
                item.setCostSnapshot(writeJson(Map.of("estimatedCost", itemCost)));
            }
        }
        mealPlanItemRepository.saveAll(items);
        BigDecimal estimatedCost = completeCost ? totalCost.setScale(2, RoundingMode.HALF_UP) : null;
        plan.setNutritionSnapshot(writeJson(planTotals));
        plan.setEstimatedCost(estimatedCost);
        mealPlanRepository.save(plan);

        List<RuleIngredient> ruleIngredients = ruleIngredients(recipeIds);
        RuleCheckRequest request = new RuleCheckRequest(
                familyId, SOURCE_TYPE_MEAL_PLAN, mealPlanId, memberProfiles(familyId), ruleIngredients,
                planTotals, estimatedCost, budgetContext(familyId), recentRecipeIds(familyId, plan));
        List<RuleCheckResult> risks = ruleCheckService.check(request);
        boolean blockingRisk = risks.stream().anyMatch(risk -> risk.blocking()
                || risk.riskLevel() == NutritionRiskLevel.HIGH
                || risk.riskLevel() == NutritionRiskLevel.BLOCKING);
        return new MealValidationResult(
                !items.isEmpty() && errors.isEmpty() && !blockingRisk,
                planTotals, estimatedCost, risks, List.copyOf(errors));
    }

    private List<MemberRuleProfile> memberProfiles(Long familyId) {
        Map<Long, NutritionHealthProfilePo> healthByMemberId = healthProfileRepository
                .findActiveMemberHealthProfiles(familyId, NutritionStatus.ACTIVE).stream()
                .collect(Collectors.toMap(NutritionHealthProfilePo::getMemberProfileId, Function.identity()));
        return memberProfileRepository.findByFamilyIdAndStatusAndDeletedFalse(familyId, NutritionStatus.ACTIVE)
                .stream()
                .map(member -> {
                    NutritionHealthProfilePo health = healthByMemberId.get(member.getId());
                    return new MemberRuleProfile(member.getId(), member.getMemberType(),
                            health == null ? List.of() : readStrings(health.getAllergyTags()),
                            health == null ? List.of() : readStrings(health.getDislikeTags()),
                            health == null ? List.of() : readStrings(health.getDietGoals()),
                            health == null ? List.of() : readStrings(health.getRestrictionTags()),
                            health == null ? null : health.getTargetSodium());
                })
                .toList();
    }

    private List<RuleIngredient> ruleIngredients(List<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return List.of();
        }
        List<NutritionRecipeIngredientPo> ingredients = recipeIngredientRepository
                .findByRecipeIdInAndDeletedFalseOrderByIdAsc(recipeIds);
        Map<Long, NutritionStandardFoodPo> foods = standardFoodRepository.findByIdInAndStatusAndDeletedFalse(
                        ingredients.stream().map(NutritionRecipeIngredientPo::getStandardFoodId)
                                .filter(java.util.Objects::nonNull).distinct().toList(), NutritionStatus.ACTIVE)
                .stream().collect(Collectors.toMap(NutritionStandardFoodPo::getId, Function.identity()));
        return ingredients.stream().map(ingredient -> {
            NutritionStandardFoodPo food = ingredient.getStandardFoodId() == null
                    ? null : foods.get(ingredient.getStandardFoodId());
            return new RuleIngredient(ingredient.getRecipeId(), ingredient.getRawFoodName(),
                    food == null ? List.of() : readStrings(food.getAllergenTags()),
                    food == null ? List.of() : readStrings(food.getSuitableTags()));
        }).toList();
    }

    private BudgetContext budgetContext(Long familyId) {
        return budgetRuleRepository.findByFamilyIdAndEnabledTrueAndStatusAndDeletedFalseOrderByIdAsc(
                        familyId, NutritionStatus.ACTIVE).stream()
                .filter(rule -> "PER_MEAL".equalsIgnoreCase(rule.getPeriodType()))
                .findFirst()
                .map(rule -> new BudgetContext(rule.getAmountLimit()))
                .orElse(null);
    }

    private List<Long> recentRecipeIds(Long familyId, NutritionMealPlanPo currentPlan) {
        LocalDate end = currentPlan.getPlanDate().minusDays(1);
        List<NutritionMealPlanPo> plans = mealPlanRepository
                .findByFamilyIdAndPlanDateBetweenAndDeletedFalseOrderByPlanDateAscIdAsc(
                        familyId, currentPlan.getPlanDate().minusDays(30), end);
        if (plans.isEmpty()) {
            return List.of();
        }
        return mealPlanItemRepository.findByMealPlanIdInAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        plans.stream().map(NutritionMealPlanPo::getId).toList(), NutritionStatus.ACTIVE).stream()
                .map(NutritionMealPlanItemPo::getRecipeId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition rule JSON is invalid");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition snapshot JSON is invalid");
        }
    }
}
