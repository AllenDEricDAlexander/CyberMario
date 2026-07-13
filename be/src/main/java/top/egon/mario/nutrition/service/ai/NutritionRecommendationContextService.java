package top.egon.mario.nutrition.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionBudgetRuleRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the complete immutable context consumed by the AI model boundary.
 */
@Service
@RequiredArgsConstructor
public class NutritionRecommendationContextService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<NutritionMealType>> MEAL_TYPE_LIST_TYPE = new TypeReference<>() {
    };

    private final NutritionFamilyRepository familyRepository;
    private final NutritionMemberProfileRepository memberProfileRepository;
    private final NutritionHealthProfileRepository healthProfileRepository;
    private final NutritionRecipeRepository recipeRepository;
    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionHealthTagRepository healthTagRepository;
    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionBudgetRuleRepository budgetRuleRepository;
    private final NutritionFoodPriceRecordRepository priceRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public NutritionRecommendationContext build(Long familyId, LocalDate plannedDate,
                                                List<NutritionMealType> mealTypes, Long actorId,
                                                NutritionAiTriggerType triggerType) {
        NutritionFamilyPo family = familyRepository.findByIdAndDeletedFalse(familyId)
                .filter(candidate -> NutritionStatus.ACTIVE == candidate.getStatus())
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_FAMILY_NOT_FOUND", "nutrition family not found"));
        List<NutritionMemberProfilePo> memberProfiles = memberProfileRepository
                .findByFamilyIdAndStatusAndDeletedFalse(familyId, NutritionStatus.ACTIVE);
        Map<Long, NutritionHealthProfilePo> healthByMemberId = healthProfileRepository
                .findActiveMemberHealthProfiles(familyId, NutritionStatus.ACTIVE).stream()
                .collect(Collectors.toMap(NutritionHealthProfilePo::getMemberProfileId, Function.identity()));
        List<NutritionRecipePo> recipes = Stream.concat(
                        recipeRepository.findByFamilyIdIsNullAndSourceTypeAndStatusAndDeletedFalseOrderByIdDesc(
                                NutritionRecipeSourceType.PLATFORM_PUBLIC, NutritionStatus.ACTIVE).stream(),
                        recipeRepository.findByFamilyIdAndStatusAndDeletedFalseOrderByIdDesc(
                                familyId, NutritionStatus.ACTIVE).stream())
                .toList();
        List<NutritionRecommendationContext.RecentMealContext> recentMeals = recentMeals(familyId, plannedDate);
        return new NutritionRecommendationContext(
                familyId,
                family.getName(),
                new NutritionRecommendationContext.FamilySettingsContext(
                        family.getRegion(), family.getCurrency(), readMealTypes(family.getDefaultMealTypes()),
                        family.isAiEnabled(), family.getAiGenerateTime(), family.isHealthAlertEnabled(),
                        family.isBudgetEnabled()),
                plannedDate,
                mealTypes == null ? List.of() : List.copyOf(mealTypes),
                memberProfiles.stream().map(member -> memberContext(member, healthByMemberId.get(member.getId())))
                        .toList(),
                recipes.stream().map(this::recipeContext).toList(),
                standardFoodRepository.findByStatusAndDeletedFalseOrderByIdDesc(NutritionStatus.ACTIVE).stream()
                        .map(this::standardFoodContext).toList(),
                healthTagRepository.findByStatusAndDeletedFalseOrderByTagTypeAscSortOrderAscIdAsc(
                                NutritionStatus.ACTIVE).stream()
                        .map(tag -> new NutritionRecommendationContext.HealthTagContext(
                                tag.getTagType(), tag.getTagCode(), tag.getName()))
                        .toList(),
                recentMeals,
                budgetRuleRepository.findByFamilyIdAndEnabledTrueAndStatusAndDeletedFalseOrderByIdAsc(
                                familyId, NutritionStatus.ACTIVE).stream()
                        .map(rule -> new NutritionRecommendationContext.BudgetRuleContext(
                                rule.getPeriodType(), rule.getAmountLimit(), rule.getCurrency()))
                        .toList(),
                priceRepository.findTop50ByFamilyIdAndDeletedFalseOrderByPriceDateDescIdDesc(familyId).stream()
                        .map(price -> new NutritionRecommendationContext.PriceContext(
                                price.getStandardFoodId(), price.getPriceDate(), price.getNormalizedUnitPrice(),
                                price.getChannel(), price.getBrand()))
                        .toList(),
                actorId,
                triggerType,
                UUID.randomUUID().toString());
    }

    private NutritionRecommendationContext.MemberContext memberContext(
            NutritionMemberProfilePo member, NutritionHealthProfilePo health) {
        if (health == null) {
            return new NutritionRecommendationContext.MemberContext(
                    member.getId(), member.getMemberType(), null, List.of(), List.of(), List.of(), List.of(),
                    NutritionTotals.zero());
        }
        return new NutritionRecommendationContext.MemberContext(
                member.getId(), member.getMemberType(), health.getActivityLevel(), readStrings(health.getDietGoals()),
                readStrings(health.getAllergyTags()), readStrings(health.getDislikeTags()),
                readStrings(health.getRestrictionTags()),
                new NutritionTotals(health.getTargetCalories(), health.getTargetProtein(), health.getTargetFat(),
                        health.getTargetCarbs(), health.getTargetSugar(), health.getTargetSodium(), null, null));
    }

    private NutritionRecommendationContext.RecipeContext recipeContext(NutritionRecipePo recipe) {
        return new NutritionRecommendationContext.RecipeContext(
                recipe.getId(), recipe.getSourceType(), recipe.getName(), recipe.getServingCount(),
                readTotals(recipe.getNutritionSnapshot()), recipe.getEstimatedCost());
    }

    private NutritionRecommendationContext.StandardFoodContext standardFoodContext(NutritionStandardFoodPo food) {
        return new NutritionRecommendationContext.StandardFoodContext(
                food.getId(), food.getNameCn(), food.getCategory(),
                new NutritionTotals(food.getCaloriesPer100g(), food.getProteinPer100g(), food.getFatPer100g(),
                        food.getCarbsPer100g(), food.getSugarPer100g(), food.getSodiumPer100g(),
                        food.getFiberPer100g(), food.getCholesterolPer100g()),
                readStrings(food.getAllergenTags()), readStrings(food.getSuitableTags()));
    }

    private List<NutritionRecommendationContext.RecentMealContext> recentMeals(
            Long familyId, LocalDate plannedDate) {
        LocalDate end = plannedDate.minusDays(1);
        List<top.egon.mario.nutrition.po.NutritionMealPlanPo> plans = mealPlanRepository
                .findByFamilyIdAndPlanDateBetweenAndDeletedFalseOrderByPlanDateAscIdAsc(
                        familyId, plannedDate.minusDays(30), end);
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<Long, LocalDate> datesByPlanId = plans.stream().collect(Collectors.toMap(
                top.egon.mario.nutrition.po.NutritionMealPlanPo::getId,
                top.egon.mario.nutrition.po.NutritionMealPlanPo::getPlanDate));
        return mealPlanItemRepository.findByMealPlanIdInAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        datesByPlanId.keySet(), NutritionStatus.ACTIVE).stream()
                .map(item -> recentMeal(item, datesByPlanId.get(item.getMealPlanId())))
                .toList();
    }

    private NutritionRecommendationContext.RecentMealContext recentMeal(
            NutritionMealPlanItemPo item, LocalDate planDate) {
        return new NutritionRecommendationContext.RecentMealContext(
                planDate, item.getMealType(), item.getRecipeId(), item.getDishName());
    }

    private NutritionTotals readTotals(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return NutritionTotals.zero();
        }
        try {
            return objectMapper.readValue(json, NutritionTotals.class);
        } catch (JsonProcessingException ignored) {
            return NutritionTotals.zero();
        }
    }

    private List<String> readStrings(String json) {
        return readJsonList(json, STRING_LIST_TYPE);
    }

    private List<NutritionMealType> readMealTypes(String json) {
        return readJsonList(json, MEAL_TYPE_LIST_TYPE);
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition context JSON is invalid");
        }
    }
}
