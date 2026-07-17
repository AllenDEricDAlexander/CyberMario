package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.request.UpsertBudgetRuleRequest;
import top.egon.mario.nutrition.dto.response.BudgetRuleResponse;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.po.NutritionBudgetRulePo;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionBudgetRuleRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListItemRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.nutrition.service.calculation.NutritionCalculationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Family budget-rule administration and cost summaries.
 */
@Service
@RequiredArgsConstructor
@Validated
public class BudgetService {

    private static final String PERIOD_DAILY = "DAILY";
    private static final String PERIOD_WEEKLY = "WEEKLY";
    private static final String PERIOD_MONTHLY = "MONTHLY";
    private static final String PERIOD_PER_MEAL = "PER_MEAL";
    private static final Set<String> PERIOD_TYPES = Set.of(
            PERIOD_DAILY, PERIOD_WEEKLY, PERIOD_MONTHLY, PERIOD_PER_MEAL);

    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionMealConfirmationRepository confirmationRepository;
    private final NutritionMealConfirmationItemRepository confirmationItemRepository;
    private final NutritionShoppingListRepository shoppingListRepository;
    private final NutritionShoppingListItemRepository shoppingListItemRepository;
    private final NutritionBudgetRuleRepository budgetRuleRepository;
    private final NutritionFoodPriceRecordRepository priceRecordRepository;
    private final NutritionRecipeRepository recipeRepository;
    private final NutritionRecipeIngredientRepository recipeIngredientRepository;
    private final NutritionCalculationService calculationService;
    private final NutritionAccessService accessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<BudgetRuleResponse> listBudgetRules(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return budgetRuleRepository.findByFamilyIdAndDeletedFalseOrderByIdAsc(familyId)
                .stream().map(this::toRuleResponse).toList();
    }

    @Transactional
    public BudgetRuleResponse createBudgetRule(@NotNull Long familyId,
                                               @Valid @NotNull UpsertBudgetRuleRequest request,
                                               Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionBudgetRulePo rule = new NutritionBudgetRulePo();
        rule.setFamilyId(familyId);
        applyRule(rule, request);
        return toRuleResponse(budgetRuleRepository.saveAndFlush(rule));
    }

    @Transactional
    public BudgetRuleResponse updateBudgetRule(@NotNull Long familyId, @NotNull Long ruleId,
                                               @Valid @NotNull UpsertBudgetRuleRequest request,
                                               Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionBudgetRulePo rule = getRule(familyId, ruleId);
        applyRule(rule, request);
        return toRuleResponse(budgetRuleRepository.saveAndFlush(rule));
    }

    @Transactional
    public void deactivateBudgetRule(@NotNull Long familyId, @NotNull Long ruleId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionBudgetRulePo rule = getRule(familyId, ruleId);
        rule.setEnabled(false);
        rule.setStatus(NutritionStatus.DISABLED);
        budgetRuleRepository.saveAndFlush(rule);
    }

    @Transactional(readOnly = true)
    public BudgetSummaryResponse weeklyBudget(@NotNull Long familyId, LocalDate weekStart, Long actorId) {
        LocalDate start = (weekStart == null ? LocalDate.now() : weekStart)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return summarize(PERIOD_WEEKLY, familyId, start, start.plusDays(6), actorId);
    }

    @Transactional(readOnly = true)
    public BudgetSummaryResponse monthlyBudget(@NotNull Long familyId, LocalDate monthDate, Long actorId) {
        LocalDate start = (monthDate == null ? LocalDate.now() : monthDate).withDayOfMonth(1);
        return summarize(PERIOD_MONTHLY, familyId, start, start.withDayOfMonth(start.lengthOfMonth()), actorId);
    }

    private BudgetSummaryResponse summarize(String periodType, Long familyId, LocalDate periodStart,
                                            LocalDate periodEnd, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionMealPlanPo> mealPlans = mealPlanRepository
                .findByFamilyIdAndPlanDateBetweenAndDeletedFalseOrderByPlanDateAscIdAsc(
                        familyId, periodStart, periodEnd);
        List<Long> mealPlanIds = mealPlans.stream().map(NutritionMealPlanPo::getId).toList();
        Map<Long, List<NutritionMealPlanItemPo>> mealItemsByPlanId = mealPlanIds.isEmpty() ? Map.of()
                : mealPlanItemRepository
                .findByMealPlanIdInAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        mealPlanIds, NutritionStatus.ACTIVE)
                .stream().collect(Collectors.groupingBy(NutritionMealPlanItemPo::getMealPlanId));
        List<NutritionMealConfirmationPo> confirmations = mealPlanIds.isEmpty() ? List.of()
                : confirmationRepository.findByMealPlanIdInAndConfirmationStatusAndDeletedFalse(
                        mealPlanIds, NutritionConfirmationStatus.CONFIRMED);
        List<NutritionMealConfirmationItemPo> confirmationItems = confirmations.isEmpty() ? List.of()
                : confirmationItemRepository.findByConfirmationIdInAndDeletedFalseOrderByIdAsc(
                        confirmations.stream().map(NutritionMealConfirmationPo::getId).toList());
        Map<Long, BigDecimal> servingsByMealItemId = confirmedServings(confirmationItems);

        List<NutritionShoppingListPo> shoppingLists = shoppingListRepository
                .findByFamilyIdAndListDateBetweenAndDeletedFalseOrderByListDateAscIdAsc(
                        familyId, periodStart, periodEnd)
                .stream()
                .filter(list -> list.getStatus() != NutritionShoppingListStatus.CANCELLED)
                .toList();
        List<Long> shoppingListIds = shoppingLists.stream().map(NutritionShoppingListPo::getId).toList();
        List<NutritionShoppingListItemPo> shoppingItems = shoppingListIds.isEmpty() ? List.of()
                : shoppingListItemRepository.findByShoppingListIdInAndDeletedFalseOrderByIdAsc(shoppingListIds);
        Map<Long, NutritionShoppingListPo> shoppingListsById = shoppingLists.stream()
                .collect(Collectors.toMap(NutritionShoppingListPo::getId, Function.identity()));
        Map<Long, List<NutritionShoppingListItemPo>> shoppingItemsByListId = shoppingItems.stream()
                .collect(Collectors.groupingBy(NutritionShoppingListItemPo::getShoppingListId));
        Map<Long, List<NutritionShoppingListPo>> shoppingListsByMealPlanId = shoppingLists.stream()
                .filter(list -> list.getMealPlanId() != null)
                .collect(Collectors.groupingBy(NutritionShoppingListPo::getMealPlanId));

        Map<Long, NutritionRecipePo> recipesById = recipes(mealItemsByPlanId);
        Map<Long, List<NutritionRecipeIngredientPo>> ingredientsByRecipeId = recipeIngredients(recipesById);
        Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
        List<BudgetSummaryResponse.DishSummary> dishSummaries = mealPlans.stream()
                .flatMap(mealPlan -> {
                    List<NutritionMealPlanItemPo> mealItems = mealItemsByPlanId
                            .getOrDefault(mealPlan.getId(), List.of());
                    List<NutritionShoppingListPo> planLists = shoppingListsByMealPlanId
                            .getOrDefault(mealPlan.getId(), List.of());
                    CostChoice cost = costChoice(familyId, mealPlan, planLists, shoppingItemsByListId);
                    daily.computeIfAbsent(mealPlan.getPlanDate(), DailyAccumulator::new)
                            .add(cost, 1, mealItems.size(), mealPlan.getConfirmedMemberCount());
                    List<NutritionShoppingListItemPo> planShoppingItems = latestShoppingItems(
                            planLists, shoppingItemsByListId);
                    return mealItems.stream().map(item -> {
                        BigDecimal confirmedServings = servingsByMealItemId
                                .getOrDefault(item.getId(), BigDecimal.ZERO);
                        BigDecimal finalServings = finalServingCount(item, confirmedServings);
                        return new BudgetSummaryResponse.DishSummary(
                                mealPlan.getId(), item.getId(), mealPlan.getPlanDate(), item.getMealType(),
                                item.getDishName(), item.getServingCount(), amount(confirmedServings),
                                amount(finalServings), dishCost(familyId, item, finalServings,
                                recipesById, ingredientsByRecipeId, planShoppingItems));
                    });
                })
                .toList();

        BigDecimal totalAmount = sumDaily(daily, DailyAccumulator::totalAmount);
        BigDecimal totalActualAmount = sumDaily(daily, DailyAccumulator::actualAmount);
        BigDecimal totalEstimatedAmount = sumDaily(daily, DailyAccumulator::estimatedAmount);
        int confirmedMemberCount = daily.values().stream()
                .mapToInt(DailyAccumulator::confirmedMemberCount).sum();
        int mealCount = mealItemsByPlanId.values().stream().mapToInt(List::size).sum();
        BigDecimal budgetLimit = applicableBudgetLimit(
                familyId, periodType, periodStart, periodEnd, mealCount);

        return new BudgetSummaryResponse(periodType, periodStart, periodEnd, money(totalAmount),
                money(totalActualAmount), money(totalEstimatedAmount), mealPlans.size(), mealCount,
                confirmedMemberCount, perPersonCost(totalAmount, confirmedMemberCount), budgetLimit,
                usageRate(totalAmount, budgetLimit), shoppingCompletionRate(shoppingItems),
                daily.values().stream().map(DailyAccumulator::toResponse).toList(), dishSummaries,
                ingredientSummaries(familyId, shoppingItems), channelSummaries(familyId, shoppingItems,
                shoppingListsById));
    }

    private CostChoice costChoice(Long familyId, NutritionMealPlanPo mealPlan,
                                  List<NutritionShoppingListPo> shoppingLists,
                                  Map<Long, List<NutritionShoppingListItemPo>> shoppingItemsByListId) {
        Optional<NutritionShoppingListPo> latest = shoppingLists.stream()
                .max(Comparator.comparing(NutritionShoppingListPo::getId));
        if (latest.isEmpty()) {
            BigDecimal estimate = money(zeroIfNull(mealPlan.getEstimatedCost()));
            return new CostChoice(estimate, money(BigDecimal.ZERO), estimate);
        }
        NutritionShoppingListPo list = latest.orElseThrow();
        List<NutritionShoppingListItemPo> items = shoppingItemsByListId.getOrDefault(list.getId(), List.of());
        BigDecimal itemActual = positiveSum(items.stream()
                .map(NutritionShoppingListItemPo::getTotalPrice).toList());
        BigDecimal actual = itemActual == null ? list.getActualTotalPrice() : itemActual;
        BigDecimal itemEstimate = positiveSum(items.stream()
                .map(item -> estimatedItemAmount(familyId, item)).toList());
        BigDecimal estimated = itemEstimate == null ? list.getEstimatedTotalPrice() : itemEstimate;
        if (estimated == null) {
            estimated = mealPlan.getEstimatedCost();
        }
        BigDecimal total = actual == null ? zeroIfNull(estimated) : actual;
        return new CostChoice(money(total), money(zeroIfNull(actual)), money(zeroIfNull(estimated)));
    }

    private BigDecimal dishCost(Long familyId, NutritionMealPlanItemPo mealItem, BigDecimal confirmedServings,
                                Map<Long, NutritionRecipePo> recipesById,
                                Map<Long, List<NutritionRecipeIngredientPo>> ingredientsByRecipeId,
                                List<NutritionShoppingListItemPo> shoppingItems) {
        if (mealItem.getRecipeId() == null || confirmedServings.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }
        NutritionRecipePo recipe = recipesById.get(mealItem.getRecipeId());
        if (recipe == null) {
            return money(BigDecimal.ZERO);
        }
        BigDecimal scale = confirmedServings.divide(
                BigDecimal.valueOf(Math.max(recipe.getServingCount(), 1)), 6, RoundingMode.HALF_UP);
        BigDecimal ingredientCost = BigDecimal.ZERO;
        boolean priced = false;
        for (NutritionRecipeIngredientPo ingredient : ingredientsByRecipeId
                .getOrDefault(recipe.getId(), List.of())) {
            BigDecimal unitPrice = compatibleShoppingUnitPrice(ingredient, shoppingItems)
                    .or(() -> latestUnitPrice(familyId, ingredient.getStandardFoodId(),
                            ingredient.getRawFoodName(), "g"))
                    .orElse(null);
            if (unitPrice != null) {
                ingredientCost = ingredientCost.add(unitPrice
                        .multiply(calculationService.ingredientGrams(ingredient)).multiply(scale));
                priced = true;
            }
        }
        if (priced) {
            return money(ingredientCost);
        }
        return recipe.getEstimatedCost() == null ? money(BigDecimal.ZERO)
                : money(recipe.getEstimatedCost().multiply(scale));
    }

    private Optional<BigDecimal> compatibleShoppingUnitPrice(NutritionRecipeIngredientPo ingredient,
                                                              List<NutritionShoppingListItemPo> shoppingItems) {
        return shoppingItems.stream()
                .filter(item -> compatibleFood(ingredient.getStandardFoodId(), ingredient.getRawFoodName(), item))
                .map(item -> item.getNormalizedUnitPrice() != null ? item.getNormalizedUnitPrice()
                        : unitPriceFromTotal(item))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private BigDecimal unitPriceFromTotal(NutritionShoppingListItemPo item) {
        if (!positive(item.getTotalPrice()) || !positive(item.getPlannedAmount())) {
            return null;
        }
        return item.getTotalPrice().divide(item.getPlannedAmount(), 6, RoundingMode.HALF_UP);
    }

    private boolean compatibleFood(Long standardFoodId, String rawFoodName, NutritionShoppingListItemPo item) {
        if (standardFoodId != null) {
            return Objects.equals(standardFoodId, item.getStandardFoodId());
        }
        return StringUtils.hasText(rawFoodName) && StringUtils.hasText(item.getRawFoodName())
                && rawFoodName.trim().equalsIgnoreCase(item.getRawFoodName().trim());
    }

    private Map<Long, BigDecimal> confirmedServings(List<NutritionMealConfirmationItemPo> confirmationItems) {
        Map<Long, BigDecimal> servings = new LinkedHashMap<>();
        confirmationItems.stream().filter(NutritionMealConfirmationItemPo::isSelected)
                .forEach(item -> servings.merge(item.getMealPlanItemId(), item.getServingCount(), BigDecimal::add));
        return servings;
    }

    private BigDecimal finalServingCount(NutritionMealPlanItemPo item, BigDecimal confirmedServingCount) {
        BigDecimal adjusted = readDecimal(item.getMetadataJson(), "finalServingCount");
        return adjusted == null ? confirmedServingCount : adjusted;
    }

    private Map<Long, NutritionRecipePo> recipes(Map<Long, List<NutritionMealPlanItemPo>> mealItemsByPlanId) {
        List<Long> recipeIds = mealItemsByPlanId.values().stream().flatMap(List::stream)
                .map(NutritionMealPlanItemPo::getRecipeId).filter(Objects::nonNull).distinct().toList();
        return recipeIds.isEmpty() ? Map.of() : recipeRepository.findAllById(recipeIds).stream()
                .filter(recipe -> !recipe.isDeleted() && recipe.getStatus() == NutritionStatus.ACTIVE)
                .collect(Collectors.toMap(NutritionRecipePo::getId, Function.identity()));
    }

    private Map<Long, List<NutritionRecipeIngredientPo>> recipeIngredients(
            Map<Long, NutritionRecipePo> recipesById) {
        return recipesById.isEmpty() ? Map.of()
                : recipeIngredientRepository.findByRecipeIdInAndDeletedFalseOrderByIdAsc(recipesById.keySet())
                .stream().collect(Collectors.groupingBy(NutritionRecipeIngredientPo::getRecipeId));
    }

    private List<NutritionShoppingListItemPo> latestShoppingItems(
            List<NutritionShoppingListPo> shoppingLists,
            Map<Long, List<NutritionShoppingListItemPo>> shoppingItemsByListId) {
        return shoppingLists.stream().max(Comparator.comparing(NutritionShoppingListPo::getId))
                .map(list -> shoppingItemsByListId.getOrDefault(list.getId(), List.of()))
                .orElse(List.of());
    }

    private List<BudgetSummaryResponse.IngredientSummary> ingredientSummaries(
            Long familyId, List<NutritionShoppingListItemPo> shoppingItems) {
        Map<IngredientKey, IngredientAccumulator> accumulators = new LinkedHashMap<>();
        for (NutritionShoppingListItemPo item : shoppingItems) {
            IngredientKey key = IngredientKey.of(item.getStandardFoodId(), item.getRawFoodName(),
                    item.getPlannedUnit());
            accumulators.computeIfAbsent(key, ignored -> new IngredientAccumulator(
                            item.getStandardFoodId(), item.getRawFoodName(), item.getPlannedUnit()))
                    .add(item.getPlannedAmount(), item.getPurchasedAmount(), effectiveItemAmount(familyId, item));
        }
        return accumulators.values().stream().map(IngredientAccumulator::toResponse).toList();
    }

    private List<BudgetSummaryResponse.ChannelSummary> channelSummaries(
            Long familyId, List<NutritionShoppingListItemPo> shoppingItems,
            Map<Long, NutritionShoppingListPo> shoppingListsById) {
        Map<String, ChannelAccumulator> accumulators = new LinkedHashMap<>();
        for (NutritionShoppingListItemPo item : shoppingItems) {
            if (!shoppingListsById.containsKey(item.getShoppingListId())) {
                continue;
            }
            String channel = StringUtils.hasText(item.getChannel()) ? item.getChannel() : "UNKNOWN";
            accumulators.computeIfAbsent(channel, ChannelAccumulator::new)
                    .add(effectiveItemAmount(familyId, item));
        }
        return accumulators.values().stream().map(ChannelAccumulator::toResponse).toList();
    }

    private BigDecimal effectiveItemAmount(Long familyId, NutritionShoppingListItemPo item) {
        return item.getTotalPrice() == null ? estimatedItemAmount(familyId, item) : item.getTotalPrice();
    }

    private BigDecimal estimatedItemAmount(Long familyId, NutritionShoppingListItemPo item) {
        if (!positive(item.getPlannedAmount())) {
            return null;
        }
        BigDecimal latestPriceEstimate = latestUnitPrice(
                familyId, item.getStandardFoodId(), item.getRawFoodName(), item.getPlannedUnit())
                .map(price -> money(price.multiply(item.getPlannedAmount())))
                .orElse(null);
        return latestPriceEstimate == null
                ? readDecimal(item.getMetadataJson(), "estimatedTotalPrice")
                : latestPriceEstimate;
    }

    private Optional<BigDecimal> latestUnitPrice(Long familyId, Long standardFoodId,
                                                 String rawFoodName, String unit) {
        if (!StringUtils.hasText(unit)) {
            return Optional.empty();
        }
        if (standardFoodId == null && !StringUtils.hasText(rawFoodName)) {
            return Optional.empty();
        }
        Optional<NutritionFoodPriceRecordPo> price = standardFoodId == null
                ? priceRecordRepository
                .findFirstByFamilyIdAndRawFoodNameIgnoreCaseAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
                        familyId, rawFoodName, unit)
                : priceRecordRepository
                .findFirstByFamilyIdAndStandardFoodIdAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
                        familyId, standardFoodId, unit);
        return price.map(NutritionFoodPriceRecordPo::getNormalizedUnitPrice).filter(Objects::nonNull);
    }

    private BigDecimal applicableBudgetLimit(Long familyId, String periodType, LocalDate periodStart,
                                             LocalDate periodEnd, int mealCount) {
        Map<String, BigDecimal> limits = budgetRuleRepository
                .findByFamilyIdAndEnabledTrueAndStatusAndDeletedFalseOrderByIdAsc(
                        familyId, NutritionStatus.ACTIVE)
                .stream().collect(Collectors.groupingBy(NutritionBudgetRulePo::getPeriodType,
                        Collectors.reducing(BigDecimal.ZERO, NutritionBudgetRulePo::getAmountLimit,
                                BigDecimal::add)));
        BigDecimal exact = limits.get(periodType);
        if (positive(exact)) {
            return money(exact);
        }
        long days = periodEnd.toEpochDay() - periodStart.toEpochDay() + 1;
        BigDecimal daily = limits.get(PERIOD_DAILY);
        if (positive(daily)) {
            return money(daily.multiply(BigDecimal.valueOf(days)));
        }
        if (PERIOD_MONTHLY.equals(periodType) && positive(limits.get(PERIOD_WEEKLY))) {
            long weeks = (days + 6) / 7;
            return money(limits.get(PERIOD_WEEKLY).multiply(BigDecimal.valueOf(weeks)));
        }
        BigDecimal perMeal = limits.get(PERIOD_PER_MEAL);
        return positive(perMeal) ? money(perMeal.multiply(BigDecimal.valueOf(mealCount))) : null;
    }

    private BigDecimal usageRate(BigDecimal totalAmount, BigDecimal budgetLimit) {
        if (!positive(budgetLimit)) {
            return null;
        }
        return totalAmount.divide(budgetLimit, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal shoppingCompletionRate(List<NutritionShoppingListItemPo> shoppingItems) {
        if (shoppingItems.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        long completed = shoppingItems.stream().filter(this::isCompleted).count();
        return BigDecimal.valueOf(completed)
                .divide(BigDecimal.valueOf(shoppingItems.size()), 4, RoundingMode.HALF_UP);
    }

    private boolean isCompleted(NutritionShoppingListItemPo item) {
        String status = item.getItemStatus();
        return "CHECKED".equalsIgnoreCase(status) || "PURCHASED".equalsIgnoreCase(status)
                || "BOUGHT".equalsIgnoreCase(status) || "DONE".equalsIgnoreCase(status)
                || positive(item.getPurchasedAmount()) || positive(item.getTotalPrice());
    }

    private void applyRule(NutritionBudgetRulePo rule, UpsertBudgetRuleRequest request) {
        String periodType = request.periodType().trim().toUpperCase(Locale.ROOT);
        if (!PERIOD_TYPES.contains(periodType)) {
            throw new NutritionException("NUTRITION_BUDGET_PERIOD_INVALID",
                    "nutrition budget period type is invalid");
        }
        rule.setRuleName(request.ruleName().trim());
        rule.setPeriodType(periodType);
        rule.setAmountLimit(money(request.amountLimit()));
        rule.setCurrency(StringUtils.hasText(request.currency())
                ? request.currency().trim().toUpperCase(Locale.ROOT) : "CNY");
        rule.setWarningThreshold(request.warningThreshold() == null ? null
                : request.warningThreshold().setScale(4, RoundingMode.HALF_UP));
        rule.setEnabled(request.enabled());
        rule.setStatus(request.enabled() ? NutritionStatus.ACTIVE : NutritionStatus.DISABLED);
    }

    private NutritionBudgetRulePo getRule(Long familyId, Long ruleId) {
        return budgetRuleRepository.findByIdAndFamilyIdAndDeletedFalse(ruleId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_BUDGET_RULE_NOT_FOUND", "nutrition budget rule not found"));
    }

    private BudgetRuleResponse toRuleResponse(NutritionBudgetRulePo rule) {
        return new BudgetRuleResponse(rule.getId(), rule.getFamilyId(), rule.getRuleName(), rule.getPeriodType(),
                rule.getAmountLimit(), rule.getCurrency(), rule.getWarningThreshold(), rule.isEnabled(),
                rule.getStatus(), rule.getVersion(), rule.getCreatedAt(), rule.getUpdatedAt());
    }

    private BigDecimal sumDaily(Map<LocalDate, DailyAccumulator> daily,
                                Function<DailyAccumulator, BigDecimal> extractor) {
        return daily.values().stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal positiveSum(List<BigDecimal> values) {
        BigDecimal total = values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return positive(total) ? total : null;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal perPersonCost(BigDecimal totalAmount, int confirmedMemberCount) {
        return confirmedMemberCount <= 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalAmount.divide(BigDecimal.valueOf(confirmedMemberCount), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal readDecimal(String metadataJson, String field) {
        if (!StringUtils.hasText(metadataJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            JsonNode value = node == null ? null : node.get(field);
            return value == null || !value.isNumber() ? null : value.decimalValue();
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition budget metadata is invalid");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private record CostChoice(BigDecimal totalAmount, BigDecimal actualAmount, BigDecimal estimatedAmount) {
    }

    private static final class DailyAccumulator {

        private final LocalDate date;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private BigDecimal actualAmount = BigDecimal.ZERO;
        private BigDecimal estimatedAmount = BigDecimal.ZERO;
        private int mealPlanCount;
        private int mealCount;
        private int confirmedMemberCount;

        private DailyAccumulator(LocalDate date) {
            this.date = date;
        }

        private void add(CostChoice cost, int planCount, int dishCount, int memberCount) {
            totalAmount = totalAmount.add(cost.totalAmount());
            actualAmount = actualAmount.add(cost.actualAmount());
            estimatedAmount = estimatedAmount.add(cost.estimatedAmount());
            mealPlanCount += planCount;
            mealCount += dishCount;
            confirmedMemberCount += memberCount;
        }

        private BigDecimal totalAmount() {
            return totalAmount;
        }

        private BigDecimal actualAmount() {
            return actualAmount;
        }

        private BigDecimal estimatedAmount() {
            return estimatedAmount;
        }

        private int confirmedMemberCount() {
            return confirmedMemberCount;
        }

        private BudgetSummaryResponse.DailySummary toResponse() {
            return new BudgetSummaryResponse.DailySummary(date, money(totalAmount), money(actualAmount),
                    money(estimatedAmount), mealPlanCount, mealCount, confirmedMemberCount,
                    confirmedMemberCount <= 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                            : totalAmount.divide(BigDecimal.valueOf(confirmedMemberCount),
                            2, RoundingMode.HALF_UP));
        }

        private BigDecimal money(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private record IngredientKey(Long standardFoodId, String rawFoodName, String unit) {

        private static IngredientKey of(Long standardFoodId, String rawFoodName, String unit) {
            return standardFoodId != null ? new IngredientKey(standardFoodId, null, unit)
                    : new IngredientKey(null, StringUtils.hasText(rawFoodName)
                    ? rawFoodName.trim().toLowerCase(Locale.ROOT) : "", unit);
        }
    }

    private final class IngredientAccumulator {

        private final Long standardFoodId;
        private final String rawFoodName;
        private final String unit;
        private BigDecimal plannedAmount = BigDecimal.ZERO;
        private BigDecimal purchasedAmount = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private IngredientAccumulator(Long standardFoodId, String rawFoodName, String unit) {
            this.standardFoodId = standardFoodId;
            this.rawFoodName = rawFoodName;
            this.unit = unit;
        }

        private void add(BigDecimal planned, BigDecimal purchased, BigDecimal total) {
            plannedAmount = plannedAmount.add(zeroIfNull(planned));
            purchasedAmount = purchasedAmount.add(zeroIfNull(purchased));
            totalAmount = totalAmount.add(zeroIfNull(total));
        }

        private BudgetSummaryResponse.IngredientSummary toResponse() {
            return new BudgetSummaryResponse.IngredientSummary(standardFoodId, rawFoodName, unit,
                    amount(plannedAmount), amount(purchasedAmount), money(totalAmount));
        }
    }

    private final class ChannelAccumulator {

        private final String channel;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private int itemCount;

        private ChannelAccumulator(String channel) {
            this.channel = channel;
        }

        private void add(BigDecimal amount) {
            totalAmount = totalAmount.add(zeroIfNull(amount));
            itemCount++;
        }

        private BudgetSummaryResponse.ChannelSummary toResponse() {
            return new BudgetSummaryResponse.ChannelSummary(channel, money(totalAmount), itemCount);
        }
    }
}
