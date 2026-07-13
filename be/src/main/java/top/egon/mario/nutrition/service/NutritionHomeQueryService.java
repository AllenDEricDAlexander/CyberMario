package top.egon.mario.nutrition.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.dto.response.MealPlanSummaryResponse;
import top.egon.mario.nutrition.dto.response.NutritionHomeOverviewResponse;
import top.egon.mario.nutrition.dto.response.ShoppingListResponse;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionRecordRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only projection of the current family nutrition workflow.
 */
@Service
@RequiredArgsConstructor
@Validated
public class NutritionHomeQueryService {

    private static final String SOURCE_TYPE_MEAL_PLAN = "MEAL_PLAN";

    private final MealPlanService mealPlanService;
    private final ShoppingListService shoppingListService;
    private final BudgetService budgetService;
    private final NutritionRecordRepository recordRepository;
    private final NutritionAccessService accessService;

    @Transactional(readOnly = true)
    public NutritionHomeOverviewResponse overview(@NotNull Long familyId, LocalDate overviewDate, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        LocalDate date = overviewDate == null ? LocalDate.now() : overviewDate;
        List<MealPlanResponse> mealPlans = mealPlanService.listMealPlans(familyId, userId)
                .stream().filter(plan -> date.equals(plan.planDate())).toList();
        List<MealPlanSummaryResponse> summaries = mealPlans.stream()
                .map(plan -> mealPlanService.summary(familyId, plan.id(), userId))
                .toList();
        Map<NutritionRiskLevel, Long> riskCounts = new EnumMap<>(NutritionRiskLevel.class);
        summaries.forEach(summary -> summary.riskCounts()
                .forEach((level, count) -> riskCounts.merge(level, count, Long::sum)));
        List<ShoppingListResponse> shoppingLists = mealPlans.stream()
                .flatMap(plan -> shoppingListService.listShoppingLists(familyId, plan.id(), userId).stream())
                .toList();
        NutritionShoppingListStatus shoppingState = shoppingLists.stream()
                .max(Comparator.comparing(ShoppingListResponse::id))
                .map(ShoppingListResponse::status)
                .orElse(null);
        BudgetSummaryResponse budget = budgetService.weeklyBudget(familyId, date, userId);
        BudgetSummaryResponse.DailySummary daily = budget.dailySummaries().stream()
                .filter(summary -> date.equals(summary.date()))
                .findFirst().orElse(null);
        List<MealPlanResponse> completed = mealPlans.stream()
                .filter(plan -> plan.status() == NutritionMealPlanStatus.COMPLETED)
                .toList();
        boolean recordReady = !completed.isEmpty() && completed.stream().allMatch(plan ->
                recordRepository.existsByFamilyIdAndMealPlanIdAndSourceTypeAndStatusAndDeletedFalse(
                        familyId, plan.id(), SOURCE_TYPE_MEAL_PLAN, NutritionStatus.ACTIVE));
        return new NutritionHomeOverviewResponse(familyId, date, mealPlans,
                summaries.stream().mapToInt(MealPlanSummaryResponse::confirmedMemberCount).sum(),
                summaries.stream().mapToInt(MealPlanSummaryResponse::awayMemberCount).sum(),
                summaries.stream().mapToInt(MealPlanSummaryResponse::unconfirmedMemberCount).sum(),
                riskCounts, shoppingState,
                daily == null ? money(BigDecimal.ZERO) : daily.actualAmount(),
                daily == null ? money(BigDecimal.ZERO) : daily.estimatedAmount(),
                budget.usageRate(), recordReady);
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2);
    }
}
