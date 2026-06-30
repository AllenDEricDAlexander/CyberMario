package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListItemRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read service for family meal budget summaries.
 */
@Service
@RequiredArgsConstructor
@Validated
public class BudgetService {

    private static final String PERIOD_WEEKLY = "WEEKLY";
    private static final String PERIOD_MONTHLY = "MONTHLY";
    private static final TypeReference<List<NutritionMealType>> MEAL_TYPE_LIST_TYPE = new TypeReference<>() {
    };

    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionMealConfirmationRepository confirmationRepository;
    private final NutritionShoppingListRepository shoppingListRepository;
    private final NutritionShoppingListItemRepository shoppingListItemRepository;
    private final NutritionAccessService accessService;
    private final ObjectMapper objectMapper;

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
                .stream()
                .collect(Collectors.groupingBy(NutritionMealPlanItemPo::getMealPlanId));
        Map<Long, List<NutritionMealConfirmationPo>> confirmationsByPlanId = mealPlanIds.isEmpty() ? Map.of()
                : confirmationRepository.findByMealPlanIdInAndConfirmationStatusAndDeletedFalse(
                        mealPlanIds, NutritionConfirmationStatus.CONFIRMED)
                .stream()
                .collect(Collectors.groupingBy(NutritionMealConfirmationPo::getMealPlanId));

        List<NutritionShoppingListPo> shoppingLists = shoppingListRepository
                .findByFamilyIdAndListDateBetweenAndDeletedFalseOrderByListDateAscIdAsc(
                        familyId, periodStart, periodEnd);
        List<Long> shoppingListIds = shoppingLists.stream().map(NutritionShoppingListPo::getId).toList();
        List<NutritionShoppingListItemPo> shoppingItems = shoppingListIds.isEmpty() ? List.of()
                : shoppingListItemRepository.findByShoppingListIdInAndDeletedFalseOrderByIdAsc(shoppingListIds);
        Map<Long, NutritionShoppingListPo> shoppingListsById = shoppingLists.stream()
                .collect(Collectors.toMap(NutritionShoppingListPo::getId, Function.identity()));
        Map<Long, List<NutritionShoppingListItemPo>> shoppingItemsByListId = shoppingItems.stream()
                .collect(Collectors.groupingBy(NutritionShoppingListItemPo::getShoppingListId));
        Map<Long, List<NutritionShoppingListPo>> shoppingListsByMealPlanId = shoppingLists.stream()
                .filter(shoppingList -> shoppingList.getMealPlanId() != null)
                .collect(Collectors.groupingBy(NutritionShoppingListPo::getMealPlanId));

        Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
        List<BudgetSummaryResponse.DishSummary> dishSummaries = mealPlans.stream()
                .flatMap(mealPlan -> {
                    List<NutritionMealPlanItemPo> mealItems = mealItemsByPlanId
                            .getOrDefault(mealPlan.getId(), List.of());
                    List<NutritionMealConfirmationPo> confirmations = confirmationsByPlanId
                            .getOrDefault(mealPlan.getId(), List.of());
                    CostChoice cost = costChoice(mealPlan,
                            shoppingListsByMealPlanId.getOrDefault(mealPlan.getId(), List.of()),
                            shoppingItemsByListId);
                    daily.computeIfAbsent(mealPlan.getPlanDate(), DailyAccumulator::new)
                            .add(cost, 1, mealItems.size(), mealPlan.getConfirmedMemberCount());
                    BigDecimal dishAmount = mealItems.isEmpty() ? BigDecimal.ZERO
                            : money(cost.totalAmount()
                            .divide(BigDecimal.valueOf(mealItems.size()), 2, RoundingMode.HALF_UP));
                    return mealItems.stream().map(item -> new BudgetSummaryResponse.DishSummary(
                            mealPlan.getId(), item.getId(), mealPlan.getPlanDate(), item.getMealType(),
                            item.getDishName(), item.getServingCount(),
                            confirmedServingCount(item, confirmations),
                            dishAmount));
                })
                .toList();

        BigDecimal totalAmount = daily.values().stream()
                .map(DailyAccumulator::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalActualAmount = daily.values().stream()
                .map(DailyAccumulator::actualAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEstimatedAmount = daily.values().stream()
                .map(DailyAccumulator::estimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int confirmedMemberCount = daily.values().stream()
                .mapToInt(DailyAccumulator::confirmedMemberCount)
                .sum();
        int mealCount = mealItemsByPlanId.values().stream()
                .mapToInt(List::size)
                .sum();

        return new BudgetSummaryResponse(periodType, periodStart, periodEnd, money(totalAmount),
                money(totalActualAmount), money(totalEstimatedAmount), mealPlans.size(), mealCount,
                confirmedMemberCount, perPersonCost(totalAmount, confirmedMemberCount), usageRate(shoppingItems),
                daily.values().stream().map(DailyAccumulator::toResponse).toList(),
                dishSummaries, ingredientSummaries(shoppingItems), channelSummaries(shoppingItems, shoppingListsById));
    }

    private CostChoice costChoice(NutritionMealPlanPo mealPlan, List<NutritionShoppingListPo> shoppingLists,
                                  Map<Long, List<NutritionShoppingListItemPo>> shoppingItemsByListId) {
        BigDecimal actual = null;
        BigDecimal estimated = null;
        for (NutritionShoppingListPo shoppingList : shoppingLists) {
            BigDecimal listActual = shoppingList.getActualTotalPrice();
            if (listActual == null) {
                listActual = shoppingItemsByListId.getOrDefault(shoppingList.getId(), List.of()).stream()
                        .map(NutritionShoppingListItemPo::getTotalPrice)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (listActual.compareTo(BigDecimal.ZERO) == 0) {
                    listActual = null;
                }
            }
            if (listActual != null) {
                actual = addNullable(actual, listActual);
            }
            BigDecimal listEstimated = shoppingList.getEstimatedTotalPrice();
            if (listEstimated == null) {
                listEstimated = shoppingItemsByListId.getOrDefault(shoppingList.getId(), List.of()).stream()
                        .map(item -> readDecimal(item.getMetadataJson(), "estimatedTotalPrice"))
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (listEstimated.compareTo(BigDecimal.ZERO) == 0) {
                    listEstimated = null;
                }
            }
            if (listEstimated != null) {
                estimated = addNullable(estimated, listEstimated);
            }
        }
        if (estimated == null) {
            estimated = mealPlan.getEstimatedCost();
        }
        BigDecimal total = actual == null ? zeroIfNull(estimated) : actual;
        return new CostChoice(money(total), money(zeroIfNull(actual)), money(zeroIfNull(estimated)));
    }

    private List<BudgetSummaryResponse.IngredientSummary> ingredientSummaries(
            List<NutritionShoppingListItemPo> shoppingItems) {
        Map<IngredientKey, IngredientAccumulator> accumulators = new LinkedHashMap<>();
        for (NutritionShoppingListItemPo item : shoppingItems) {
            IngredientKey key = IngredientKey.of(item.getStandardFoodId(), item.getRawFoodName(),
                    item.getPlannedUnit());
            accumulators.computeIfAbsent(key, ignored -> new IngredientAccumulator(
                            item.getStandardFoodId(), item.getRawFoodName(), item.getPlannedUnit()))
                    .add(item.getPlannedAmount(), item.getPurchasedAmount(), itemAmount(item));
        }
        return accumulators.values().stream()
                .map(IngredientAccumulator::toResponse)
                .toList();
    }

    private List<BudgetSummaryResponse.ChannelSummary> channelSummaries(
            List<NutritionShoppingListItemPo> shoppingItems,
            Map<Long, NutritionShoppingListPo> shoppingListsById) {
        Map<String, ChannelAccumulator> accumulators = new LinkedHashMap<>();
        for (NutritionShoppingListItemPo item : shoppingItems) {
            String channel = StringUtils.hasText(item.getChannel()) ? item.getChannel() : "UNKNOWN";
            NutritionShoppingListPo shoppingList = shoppingListsById.get(item.getShoppingListId());
            BigDecimal amount = itemAmount(item);
            if (amount == null && shoppingList != null) {
                amount = shoppingList.getActualTotalPrice() == null
                        ? shoppingList.getEstimatedTotalPrice()
                        : shoppingList.getActualTotalPrice();
            }
            accumulators.computeIfAbsent(channel, ChannelAccumulator::new).add(amount);
        }
        return accumulators.values().stream()
                .map(ChannelAccumulator::toResponse)
                .toList();
    }

    private BigDecimal itemAmount(NutritionShoppingListItemPo item) {
        return item.getTotalPrice() == null
                ? readDecimal(item.getMetadataJson(), "estimatedTotalPrice")
                : item.getTotalPrice();
    }

    private BigDecimal confirmedServingCount(NutritionMealPlanItemPo item,
                                             List<NutritionMealConfirmationPo> confirmations) {
        long selectedCount = confirmations.stream()
                .filter(confirmation -> selectsMealType(confirmation, item.getMealType()))
                .count();
        return item.getServingCount().multiply(BigDecimal.valueOf(selectedCount))
                .setScale(3, RoundingMode.HALF_UP);
    }

    private boolean selectsMealType(NutritionMealConfirmationPo confirmation, NutritionMealType mealType) {
        List<NutritionMealType> selectedMealTypes = readMealTypes(confirmation.getSelectedMealTypes());
        return selectedMealTypes.isEmpty() || selectedMealTypes.contains(mealType);
    }

    private List<NutritionMealType> readMealTypes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, MEAL_TYPE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition meal type JSON is invalid");
        }
    }

    private BigDecimal usageRate(List<NutritionShoppingListItemPo> shoppingItems) {
        if (shoppingItems.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        long usedCount = shoppingItems.stream().filter(this::isUsed).count();
        return BigDecimal.valueOf(usedCount)
                .divide(BigDecimal.valueOf(shoppingItems.size()), 4, RoundingMode.HALF_UP);
    }

    private boolean isUsed(NutritionShoppingListItemPo item) {
        String status = item.getItemStatus();
        if ("CHECKED".equalsIgnoreCase(status)
                || "PURCHASED".equalsIgnoreCase(status)
                || "BOUGHT".equalsIgnoreCase(status)
                || "DONE".equalsIgnoreCase(status)) {
            return true;
        }
        return positive(item.getPurchasedAmount()) || positive(item.getTotalPrice());
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal addNullable(BigDecimal current, BigDecimal value) {
        if (value == null) {
            return current;
        }
        return current == null ? value : current.add(value);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal perPersonCost(BigDecimal totalAmount, int confirmedMemberCount) {
        if (confirmedMemberCount <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return totalAmount.divide(BigDecimal.valueOf(confirmedMemberCount), 2, RoundingMode.HALF_UP);
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

        private void add(CostChoice cost, int mealPlanCount, int mealCount, int confirmedMemberCount) {
            this.totalAmount = this.totalAmount.add(cost.totalAmount());
            this.actualAmount = this.actualAmount.add(cost.actualAmount());
            this.estimatedAmount = this.estimatedAmount.add(cost.estimatedAmount());
            this.mealPlanCount += mealPlanCount;
            this.mealCount += mealCount;
            this.confirmedMemberCount += confirmedMemberCount;
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
                            : totalAmount.divide(BigDecimal.valueOf(confirmedMemberCount), 2, RoundingMode.HALF_UP));
        }

        private BigDecimal money(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private record IngredientKey(Long standardFoodId, String rawFoodName, String unit) {

        static IngredientKey of(Long standardFoodId, String rawFoodName, String unit) {
            if (standardFoodId != null) {
                return new IngredientKey(standardFoodId, null, unit);
            }
            return new IngredientKey(null, StringUtils.hasText(rawFoodName)
                    ? rawFoodName.trim().toLowerCase()
                    : "", unit);
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

        private void add(BigDecimal plannedAmount, BigDecimal purchasedAmount, BigDecimal totalAmount) {
            this.plannedAmount = this.plannedAmount.add(zeroIfNull(plannedAmount));
            this.purchasedAmount = this.purchasedAmount.add(zeroIfNull(purchasedAmount));
            this.totalAmount = this.totalAmount.add(zeroIfNull(totalAmount));
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
            this.totalAmount = this.totalAmount.add(zeroIfNull(amount));
            this.itemCount++;
        }

        private BudgetSummaryResponse.ChannelSummary toResponse() {
            return new BudgetSummaryResponse.ChannelSummary(channel, money(totalAmount), itemCount);
        }
    }
}
