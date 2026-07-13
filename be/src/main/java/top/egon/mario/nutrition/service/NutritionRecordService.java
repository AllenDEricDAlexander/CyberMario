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
import top.egon.mario.nutrition.dto.request.CreateExtraFoodRecordRequest;
import top.egon.mario.nutrition.dto.request.NutritionNutrientsRequest;
import top.egon.mario.nutrition.dto.request.NutritionRecordAdjustmentRequest;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.dto.response.NutritionDailyOverviewResponse;
import top.egon.mario.nutrition.dto.response.NutritionNutrientsResponse;
import top.egon.mario.nutrition.dto.response.NutritionRecordResponse;
import top.egon.mario.nutrition.dto.response.NutritionReportResponse;
import top.egon.mario.nutrition.dto.response.NutritionTrendPointResponse;
import top.egon.mario.nutrition.po.NutritionExtraFoodRecordPo;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionRecordAdjustmentPo;
import top.egon.mario.nutrition.po.NutritionRecordPo;
import top.egon.mario.nutrition.po.NutritionReportSnapshotPo;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionExtraFoodRecordRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecordAdjustmentRepository;
import top.egon.mario.nutrition.repository.NutritionRecordRepository;
import top.egon.mario.nutrition.repository.NutritionReportSnapshotRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.nutrition.service.calculation.NutritionCalculationService;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;
import top.egon.mario.nutrition.service.calculation.NutritionUnitConversionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for nutrition records, manual corrections, and basic reports.
 */
@Service
@RequiredArgsConstructor
@Validated
public class NutritionRecordService {

    private static final String SOURCE_TYPE_MEAL_PLAN = "MEAL_PLAN";
    private static final String SOURCE_TYPE_ADJUSTMENT = "ADJUSTMENT";
    private static final String SOURCE_TYPE_EXTRA_FOOD = "EXTRA_FOOD";
    private static final String REPORT_TYPE_WEEKLY = "WEEKLY";
    private static final String REPORT_TYPE_MONTHLY = "MONTHLY";

    private final NutritionRecordRepository recordRepository;
    private final NutritionRecordAdjustmentRepository adjustmentRepository;
    private final NutritionExtraFoodRecordRepository extraFoodRecordRepository;
    private final NutritionReportSnapshotRepository reportSnapshotRepository;
    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionMealConfirmationRepository confirmationRepository;
    private final NutritionMealConfirmationItemRepository confirmationItemRepository;
    private final NutritionRecipeRepository recipeRepository;
    private final NutritionMemberProfileRepository memberProfileRepository;
    private final NutritionHealthProfileRepository healthProfileRepository;
    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionRiskCheckResultRepository riskCheckResultRepository;
    private final NutritionCalculationService calculationService;
    private final NutritionUnitConversionService conversionService;
    private final BudgetService budgetService;
    private final NutritionAccessService accessService;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<NutritionRecordResponse> generateForCompletedMealPlan(@NotNull Long familyId,
                                                                      @NotNull Long mealPlanId,
                                                                      Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = mealPlanRepository.findLockedByIdAndFamilyIdAndDeletedFalse(
                        mealPlanId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEAL_PLAN_NOT_FOUND", "nutrition meal plan not found"));
        if (mealPlan.getStatus() != NutritionMealPlanStatus.COMPLETED) {
            throw new NutritionException(
                    "NUTRITION_MEAL_PLAN_STATUS_INVALID", "nutrition meal plan status transition is invalid");
        }
        List<NutritionMealPlanItemPo> items = mealPlanItemRepository
                .findByMealPlanIdAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        mealPlanId, NutritionStatus.ACTIVE);
        List<NutritionMealConfirmationPo> confirmations = confirmationRepository
                .findByMealPlanIdAndConfirmationStatusAndDeletedFalse(
                        mealPlanId, NutritionConfirmationStatus.CONFIRMED)
                .stream()
                .filter(NutritionMealConfirmationPo::isEatAtHome)
                .toList();
        Map<Long, NutritionMealConfirmationPo> confirmationsById = confirmations.stream()
                .collect(Collectors.toMap(NutritionMealConfirmationPo::getId, Function.identity()));
        List<NutritionMealConfirmationItemPo> confirmationItems = confirmations.isEmpty() ? List.of()
                : confirmationItemRepository.findByConfirmationIdInAndDeletedFalseOrderByIdAsc(
                        confirmations.stream().map(NutritionMealConfirmationPo::getId).toList());
        Map<Long, NutritionMealPlanItemPo> itemsById = items.stream()
                .collect(Collectors.toMap(NutritionMealPlanItemPo::getId, Function.identity()));
        List<NutritionRiskCheckResultPo> risks = riskCheckResultRepository
                .findByFamilyIdAndSourceTypeAndSourceIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        familyId, SOURCE_TYPE_MEAL_PLAN, mealPlanId, NutritionStatus.ACTIVE);

        Map<RecordSourceKey, NutritionRecordPo> existingBySource = recordRepository
                .findByFamilyIdAndMealPlanIdAndSourceTypeAndStatusAndDeletedFalseOrderByIdAsc(
                        familyId, mealPlanId, SOURCE_TYPE_MEAL_PLAN, NutritionStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(record -> new RecordSourceKey(
                                record.getMemberProfileId(), sourceMealPlanItemId(record)),
                        Function.identity(), (left, right) -> left));

        List<NutritionRecordPo> results = new ArrayList<>();
        for (NutritionMealConfirmationItemPo confirmationItem : confirmationItems) {
            if (!confirmationItem.isSelected()) {
                continue;
            }
            NutritionMealConfirmationPo confirmation = confirmationsById.get(confirmationItem.getConfirmationId());
            NutritionMealPlanItemPo item = itemsById.get(confirmationItem.getMealPlanItemId());
            if (confirmation == null || item == null || !Objects.equals(confirmationItem.getFamilyId(), familyId)) {
                throw new NutritionException(
                        "NUTRITION_CONFIRMATION_ITEM_INVALID", "nutrition confirmation item is invalid");
            }
            RecordSourceKey key = new RecordSourceKey(confirmation.getMemberProfileId(), item.getId());
            NutritionRecordPo existing = existingBySource.get(key);
            if (existing != null) {
                results.add(existing);
                continue;
            }
            BigDecimal confirmedServing = confirmationItem.getServingCount();
            NutritionTotals totals = itemTotals(familyId, item, confirmedServing);
            NutritionRecordPo record = new NutritionRecordPo();
            record.setFamilyId(familyId);
            record.setMemberProfileId(confirmation.getMemberProfileId());
            record.setMealPlanId(mealPlanId);
            record.setMealConfirmationId(confirmation.getId());
            record.setRecordDate(mealPlan.getPlanDate());
            record.setMealType(item.getMealType());
            record.setSourceType(SOURCE_TYPE_MEAL_PLAN);
            apply(record, totals);
            record.setRiskTags(toJson(riskTags(risks, confirmation.getMemberProfileId()),
                    "nutrition record risk tags are invalid"));
            record.setCalculationSnapshot(toJson(calculationSnapshot(item, confirmedServing, totals),
                    "nutrition record calculation snapshot is invalid"));
            record.setStatus(NutritionStatus.ACTIVE);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceMealPlanItemId", item.getId());
            metadata.put("mealPlanItemId", item.getId());
            metadata.put("recipeId", item.getRecipeId());
            metadata.put("servingCount", confirmedServing);
            metadata.put("dishName", item.getDishName());
            record.setMetadataJson(toJson(metadata, "nutrition record metadata is invalid"));
            NutritionRecordPo saved = recordRepository.saveAndFlush(record);
            existingBySource.put(key, saved);
            results.add(saved);
        }
        return results.stream().map(this::toRecordResponse).toList();
    }

    @Transactional(readOnly = true)
    public NutritionDailyOverviewResponse dailyOverview(@NotNull Long familyId, LocalDate recordDate, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        LocalDate date = recordDate == null ? LocalDate.now() : recordDate;
        List<NutritionRecordPo> records = recordRepository
                .findByFamilyIdAndRecordDateAndStatusAndDeletedFalseOrderByMemberProfileIdAscMealTypeAscIdAsc(
                        familyId, date, NutritionStatus.ACTIVE);
        List<NutritionRecordPo> effective = effectiveRecords(records);
        NutritionAccumulator total = new NutritionAccumulator();
        effective.forEach(total::add);
        Map<Long, List<NutritionRecordPo>> recordsByMember = new LinkedHashMap<>();
        for (NutritionRecordPo record : effective) {
            recordsByMember.computeIfAbsent(record.getMemberProfileId(), ignored -> new ArrayList<>()).add(record);
        }
        Map<Long, NutritionHealthProfilePo> healthByMember = healthProfileRepository
                .findActiveMemberHealthProfiles(familyId, NutritionStatus.ACTIVE)
                .stream().collect(Collectors.toMap(
                        NutritionHealthProfilePo::getMemberProfileId, Function.identity()));
        List<NutritionMemberProfilePo> members = memberProfileRepository
                .findByFamilyIdAndStatusAndDeletedFalse(familyId, NutritionStatus.ACTIVE);
        NutritionAccumulator familyTarget = new NutritionAccumulator();
        List<NutritionDailyOverviewResponse.MemberSummary> memberSummaries = members.stream()
                .sorted(Comparator.comparing(NutritionMemberProfilePo::getId))
                .map(member -> {
                    List<NutritionRecordPo> memberRecords = recordsByMember
                            .getOrDefault(member.getId(), List.of());
                    NutritionAccumulator memberTotal = new NutritionAccumulator();
                    memberRecords.forEach(memberTotal::add);
                    NutritionTotals target = targetTotals(healthByMember.get(member.getId()));
                    familyTarget.add(target);
                    return new NutritionDailyOverviewResponse.MemberSummary(member.getId(),
                            NutritionNutrientsResponse.from(memberTotal.toTotals()),
                            NutritionNutrientsResponse.from(target),
                            NutritionNutrientsResponse.from(subtract(target, memberTotal.toTotals())),
                            memberRecords.stream().map(this::toRecordResponse).toList());
                })
                .toList();
        NutritionTotals totalValues = total.toTotals();
        NutritionTotals targetValues = familyTarget.toTotals();
        return new NutritionDailyOverviewResponse(familyId, date,
                NutritionNutrientsResponse.from(totalValues), NutritionNutrientsResponse.from(targetValues),
                NutritionNutrientsResponse.from(subtract(targetValues, totalValues)), memberSummaries);
    }

    @Transactional
    public NutritionRecordResponse adjustRecord(@NotNull Long familyId, @NotNull Long recordId,
                                                @Valid @NotNull NutritionRecordAdjustmentRequest request,
                                                Long actorId) {
        Long userId = requireActor(actorId);
        NutritionRecordPo requestedRecord = recordRepository.findByIdAndFamilyIdAndDeletedFalse(recordId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_RECORD_NOT_FOUND", "nutrition record not found"));
        NutritionRecordPo original = originalRecord(familyId, requestedRecord);
        requireRecordWrite(userId, familyId, original.getMemberProfileId());

        NutritionRecordAdjustmentPo adjustment = new NutritionRecordAdjustmentPo();
        adjustment.setFamilyId(familyId);
        adjustment.setNutritionRecordId(original.getId());
        adjustment.setMemberProfileId(original.getMemberProfileId());
        adjustment.setAdjustedByUserId(userId);
        adjustment.setAdjustmentType(SOURCE_TYPE_ADJUSTMENT);
        adjustment.setBeforeSnapshot(toJson(nutritionSnapshot(requestedRecord),
                "nutrition adjustment snapshot is invalid"));
        adjustment.setAfterSnapshot(toJson(request.nutrients(), "nutrition adjustment snapshot is invalid"));
        adjustment.setReason(trimToNull(request.reason()));
        adjustment.setAdjustedAt(Instant.now());
        adjustment.setMetadataJson(toJson(Map.of("originalRecordId", original.getId()),
                "nutrition adjustment metadata is invalid"));
        NutritionRecordAdjustmentPo savedAdjustment = adjustmentRepository.saveAndFlush(adjustment);

        NutritionRecordPo correction = new NutritionRecordPo();
        correction.setFamilyId(familyId);
        correction.setMemberProfileId(original.getMemberProfileId());
        correction.setMealPlanId(original.getMealPlanId());
        correction.setMealConfirmationId(original.getMealConfirmationId());
        correction.setRecordDate(original.getRecordDate());
        correction.setMealType(original.getMealType());
        correction.setSourceType(SOURCE_TYPE_ADJUSTMENT);
        apply(correction, request.nutrients());
        correction.setRiskTags("[]");
        correction.setCalculationSnapshot(toJson(request.nutrients(),
                "nutrition adjustment calculation snapshot is invalid"));
        correction.setStatus(NutritionStatus.ACTIVE);
        correction.setMetadataJson(toJson(Map.of(
                "originalRecordId", original.getId(),
                "adjustmentId", savedAdjustment.getId()),
                "nutrition adjustment record metadata is invalid"));
        return toRecordResponse(recordRepository.saveAndFlush(correction));
    }

    @Transactional
    public NutritionRecordResponse createExtraFoodRecord(@NotNull Long familyId,
                                                         @Valid @NotNull CreateExtraFoodRecordRequest request,
                                                         Long actorId) {
        Long userId = requireActor(actorId);
        NutritionMemberProfilePo memberProfile = memberProfileRepository
                .findByIdAndFamilyIdAndStatusAndDeletedFalse(
                        request.memberProfileId(), familyId, NutritionStatus.ACTIVE)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEMBER_PROFILE_NOT_FOUND", "nutrition member profile not found"));
        requireRecordWrite(userId, familyId, memberProfile.getId());
        NutritionTotals totals;
        String nutritionSource;
        if (request.standardFoodId() != null) {
            NutritionStandardFoodPo food = standardFoodRepository.findByIdAndDeletedFalse(request.standardFoodId())
                    .filter(candidate -> candidate.getStatus() == NutritionStatus.ACTIVE)
                    .orElseThrow(() -> new NutritionException(
                            "NUTRITION_STANDARD_FOOD_NOT_FOUND", "nutrition standard food not found"));
            BigDecimal grams = conversionService.toGrams(request.amount(), request.unit(), null);
            totals = standardFoodTotals(food, grams);
            nutritionSource = "STANDARD_FOOD_CALCULATED";
        } else {
            if (request.nutrients() == null) {
                throw new NutritionException(
                        "NUTRITION_NUTRIENTS_REQUIRED", "nutrition nutrients are required");
            }
            totals = requestTotals(request.nutrients());
            nutritionSource = "EXPLICIT";
        }

        NutritionExtraFoodRecordPo extraFood = new NutritionExtraFoodRecordPo();
        extraFood.setFamilyId(familyId);
        extraFood.setMemberProfileId(memberProfile.getId());
        extraFood.setRecordDate(request.recordDate());
        extraFood.setMealType(request.mealType());
        extraFood.setFoodName(request.foodName().trim());
        extraFood.setStandardFoodId(request.standardFoodId());
        extraFood.setAmount(request.amount());
        extraFood.setUnit(request.unit().trim());
        extraFood.setNutritionSnapshot(toJson(NutritionNutrientsResponse.from(totals),
                "nutrition extra food snapshot is invalid"));
        extraFood.setMetadataJson(toJson(Map.of("nutritionSource", nutritionSource),
                "nutrition extra food metadata is invalid"));
        extraFood.setNote(trimToNull(request.note()));
        extraFood.setStatus(NutritionStatus.ACTIVE);
        NutritionExtraFoodRecordPo savedExtraFood = extraFoodRecordRepository.saveAndFlush(extraFood);

        NutritionRecordPo record = new NutritionRecordPo();
        record.setFamilyId(familyId);
        record.setMemberProfileId(memberProfile.getId());
        record.setRecordDate(request.recordDate());
        record.setMealType(request.mealType());
        record.setSourceType(SOURCE_TYPE_EXTRA_FOOD);
        apply(record, totals);
        record.setRiskTags("[]");
        record.setCalculationSnapshot(toJson(Map.of(
                        "nutritionSource", nutritionSource,
                        "amount", request.amount(),
                        "unit", request.unit(),
                        "nutrients", NutritionNutrientsResponse.from(totals)),
                "nutrition extra food calculation snapshot is invalid"));
        record.setStatus(NutritionStatus.ACTIVE);
        record.setMetadataJson(toJson(Map.of(
                "extraFoodRecordId", savedExtraFood.getId(),
                "foodName", savedExtraFood.getFoodName(),
                "nutritionSource", nutritionSource),
                "nutrition extra food metadata is invalid"));
        return toRecordResponse(recordRepository.saveAndFlush(record));
    }

    @Transactional(readOnly = true)
    public NutritionReportResponse familyWeeklyReport(@NotNull Long familyId, LocalDate weekStart, Long actorId) {
        LocalDate start = (weekStart == null ? LocalDate.now() : weekStart)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return report(REPORT_TYPE_WEEKLY, familyId, start, start.plusDays(6), actorId);
    }

    @Transactional(readOnly = true)
    public NutritionReportResponse familyMonthlyReport(@NotNull Long familyId, LocalDate month, Long actorId) {
        LocalDate start = (month == null ? LocalDate.now() : month).withDayOfMonth(1);
        return report(REPORT_TYPE_MONTHLY, familyId, start, start.withDayOfMonth(start.lengthOfMonth()), actorId);
    }

    @Transactional
    public NutritionReportResponse generateFamilyWeeklyReport(@NotNull Long familyId, LocalDate weekStart,
                                                              Long actorId) {
        LocalDate start = (weekStart == null ? LocalDate.now() : weekStart)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return saveReportSnapshot(familyId, report(
                REPORT_TYPE_WEEKLY, familyId, start, start.plusDays(6), actorId));
    }

    @Transactional
    public NutritionReportResponse generateFamilyMonthlyReport(@NotNull Long familyId, LocalDate month,
                                                               Long actorId) {
        LocalDate start = (month == null ? LocalDate.now() : month).withDayOfMonth(1);
        return saveReportSnapshot(familyId, report(REPORT_TYPE_MONTHLY, familyId, start,
                start.withDayOfMonth(start.lengthOfMonth()), actorId));
    }

    private NutritionReportResponse report(String reportType, Long familyId, LocalDate periodStart,
                                           LocalDate periodEnd, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        BudgetSummaryResponse budget = REPORT_TYPE_WEEKLY.equals(reportType)
                ? budgetService.weeklyBudget(familyId, periodStart, userId)
                : budgetService.monthlyBudget(familyId, periodStart, userId);
        List<NutritionMealPlanPo> mealPlans = mealPlanRepository
                .findByFamilyIdAndPlanDateBetweenAndDeletedFalseOrderByPlanDateAscIdAsc(
                        familyId, periodStart, periodEnd);
        Set<Long> periodMealPlanIds = mealPlans.stream()
                .map(NutritionMealPlanPo::getId)
                .collect(Collectors.toSet());
        NutritionNutrientsResponse totalNutrients = totalNutrients(familyId, periodStart, periodEnd);
        return new NutritionReportResponse(null, reportType,
                periodStart, periodEnd, totalNutrients,
                riskCounts(familyId, periodStart, periodEnd, periodMealPlanIds), budget.totalAmount(),
                budget.totalActualAmount(), budget.totalEstimatedAmount(),
                budget.perPersonCost(), commonDishes(familyId, periodStart, periodEnd),
                nutrientReminders(familyId, periodStart, periodEnd, totalNutrients),
                trends(familyId, periodStart, periodEnd));
    }

    private NutritionReportResponse saveReportSnapshot(Long familyId, NutritionReportResponse responseWithoutId) {
        NutritionReportSnapshotPo snapshot = new NutritionReportSnapshotPo();
        snapshot.setFamilyId(familyId);
        snapshot.setReportType(responseWithoutId.periodType());
        snapshot.setPeriodStart(responseWithoutId.periodStart());
        snapshot.setPeriodEnd(responseWithoutId.periodEnd());
        snapshot.setGeneratedAt(Instant.now());
        snapshot.setReportSnapshot(toJson(responseWithoutId, "nutrition report snapshot is invalid"));
        snapshot.setStatus(NutritionStatus.ACTIVE);
        NutritionReportSnapshotPo saved = reportSnapshotRepository.saveAndFlush(snapshot);

        return new NutritionReportResponse(saved.getId(), responseWithoutId.periodType(),
                responseWithoutId.periodStart(), responseWithoutId.periodEnd(), responseWithoutId.totalNutrients(),
                responseWithoutId.riskCounts(), responseWithoutId.totalCost(), responseWithoutId.actualCost(),
                responseWithoutId.estimatedCost(), responseWithoutId.perPersonCost(),
                responseWithoutId.commonDishes(), responseWithoutId.nutrientReminders(), responseWithoutId.trends());
    }

    private NutritionNutrientsResponse totalNutrients(Long familyId, LocalDate periodStart, LocalDate periodEnd) {
        NutritionAccumulator accumulator = new NutritionAccumulator();
        List<NutritionRecordPo> records = recordRepository
                .findByFamilyIdAndRecordDateBetweenAndStatusAndDeletedFalseOrderByRecordDateAscIdAsc(
                        familyId, periodStart, periodEnd, NutritionStatus.ACTIVE);
        effectiveRecords(records)
                .forEach(accumulator::add);
        return NutritionNutrientsResponse.from(accumulator.toTotals());
    }

    private Map<NutritionRiskLevel, Long> riskCounts(Long familyId, LocalDate periodStart, LocalDate periodEnd,
                                                     Set<Long> periodMealPlanIds) {
        Map<NutritionRiskLevel, Long> counts = new EnumMap<>(NutritionRiskLevel.class);
        riskCheckResultRepository
                .findByFamilyIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        familyId, NutritionStatus.ACTIVE)
                .stream()
                .filter(risk -> belongsToPeriod(risk, periodStart, periodEnd, periodMealPlanIds))
                .forEach(risk -> counts.merge(risk.getRiskLevel(), 1L, Long::sum));
        return counts;
    }

    private boolean belongsToPeriod(NutritionRiskCheckResultPo risk, LocalDate periodStart, LocalDate periodEnd,
                                    Set<Long> periodMealPlanIds) {
        if (SOURCE_TYPE_MEAL_PLAN.equals(risk.getSourceType())) {
            return periodMealPlanIds.contains(risk.getSourceId());
        }
        if (risk.getCreatedAt() == null) {
            return false;
        }
        LocalDate createdDate = risk.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return !createdDate.isBefore(periodStart) && !createdDate.isAfter(periodEnd);
    }

    private List<NutritionReportResponse.CommonDish> commonDishes(Long familyId, LocalDate periodStart,
                                                                  LocalDate periodEnd) {
        Map<String, Long> counts = recordRepository
                .findByFamilyIdAndRecordDateBetweenAndStatusAndDeletedFalseOrderByRecordDateAscIdAsc(
                        familyId, periodStart, periodEnd, NutritionStatus.ACTIVE)
                .stream()
                .filter(record -> SOURCE_TYPE_MEAL_PLAN.equals(record.getSourceType()))
                .map(record -> readText(record.getMetadataJson(), "dishName"))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>, Long>comparing(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(entry -> new NutritionReportResponse.CommonDish(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<NutritionTrendPointResponse> trends(Long familyId, LocalDate periodStart, LocalDate periodEnd) {
        List<NutritionRecordPo> records = recordRepository
                .findByFamilyIdAndRecordDateBetweenAndStatusAndDeletedFalseOrderByRecordDateAscIdAsc(
                        familyId, periodStart, periodEnd, NutritionStatus.ACTIVE);
        Map<LocalDate, NutritionAccumulator> totalsByDate = new LinkedHashMap<>();
        effectiveRecords(records).forEach(record -> totalsByDate
                .computeIfAbsent(record.getRecordDate(), ignored -> new NutritionAccumulator())
                .add(record));
        return totalsByDate.entrySet().stream()
                .map(entry -> new NutritionTrendPointResponse(
                        entry.getKey(), NutritionNutrientsResponse.from(entry.getValue().toTotals())))
                .toList();
    }

    private List<String> nutrientReminders(Long familyId, LocalDate periodStart, LocalDate periodEnd,
                                           NutritionNutrientsResponse total) {
        long days = periodEnd.toEpochDay() - periodStart.toEpochDay() + 1;
        List<NutritionHealthProfilePo> profiles = healthProfileRepository
                .findActiveMemberHealthProfiles(familyId, NutritionStatus.ACTIVE);
        int memberCount = Math.max(memberProfileRepository
                .findByFamilyIdAndStatusAndDeletedFalse(familyId, NutritionStatus.ACTIVE).size(), 1);
        BigDecimal sodiumLimit = periodLimit(profiles.stream()
                .map(NutritionHealthProfilePo::getTargetSodium).toList(),
                new BigDecimal("2000.000"), days, memberCount);
        BigDecimal sugarLimit = periodLimit(profiles.stream()
                .map(NutritionHealthProfilePo::getTargetSugar).toList(),
                new BigDecimal("50.000"), days, memberCount);
        BigDecimal fatLimit = periodLimit(profiles.stream()
                .map(NutritionHealthProfilePo::getTargetFat).toList(),
                new BigDecimal("70.000"), days, memberCount);
        List<String> reminders = new ArrayList<>();
        if (total.sodium().compareTo(sodiumLimit) > 0) {
            reminders.add("HIGH_SODIUM");
        }
        if (total.sugar().compareTo(sugarLimit) > 0) {
            reminders.add("HIGH_SUGAR");
        }
        if (total.fat().compareTo(fatLimit) > 0) {
            reminders.add("HIGH_FAT");
        }
        return reminders;
    }

    private BigDecimal periodLimit(List<BigDecimal> configuredTargets, BigDecimal defaultTarget,
                                   long days, int memberCount) {
        BigDecimal configured = configuredTargets.stream().filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyLimit = configured.signum() > 0
                ? configured
                : defaultTarget.multiply(BigDecimal.valueOf(memberCount));
        return dailyLimit.multiply(BigDecimal.valueOf(days));
    }

    private List<NutritionRecordPo> effectiveRecords(List<NutritionRecordPo> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<Long, NutritionRecordPo> recordsById = records.stream()
                .filter(record -> record.getId() != null)
                .collect(Collectors.toMap(NutritionRecordPo::getId, Function.identity(), (left, right) -> left));
        Map<Long, NutritionRecordPo> latestAdjustmentByOriginalId = new LinkedHashMap<>();
        List<NutritionRecordPo> unlinkedAdjustments = new ArrayList<>();
        for (NutritionRecordPo record : records) {
            if (!SOURCE_TYPE_ADJUSTMENT.equals(record.getSourceType())) {
                continue;
            }
            Long originalRecordId = originalRecordId(record);
            if (originalRecordId == null || !recordsById.containsKey(originalRecordId)) {
                unlinkedAdjustments.add(record);
                continue;
            }
            latestAdjustmentByOriginalId.merge(originalRecordId, record, this::latestRecord);
        }
        List<NutritionRecordPo> effective = new ArrayList<>();
        for (NutritionRecordPo record : records) {
            if (SOURCE_TYPE_ADJUSTMENT.equals(record.getSourceType())) {
                continue;
            }
            effective.add(latestAdjustmentByOriginalId.getOrDefault(record.getId(), record));
        }
        effective.addAll(unlinkedAdjustments);
        return effective;
    }

    private NutritionRecordPo latestRecord(NutritionRecordPo left, NutritionRecordPo right) {
        Instant leftCreatedAt = left.getCreatedAt();
        Instant rightCreatedAt = right.getCreatedAt();
        if (leftCreatedAt != null && rightCreatedAt != null) {
            return rightCreatedAt.isAfter(leftCreatedAt) ? right : left;
        }
        if (rightCreatedAt != null && leftCreatedAt == null) {
            return right;
        }
        Long leftId = left.getId();
        Long rightId = right.getId();
        if (leftId == null) {
            return right;
        }
        if (rightId == null) {
            return left;
        }
        return rightId > leftId ? right : left;
    }

    private NutritionTotals itemTotals(Long familyId, NutritionMealPlanItemPo item,
                                       BigDecimal confirmedServingCount) {
        if (item.getRecipeId() == null) {
            return NutritionTotals.zero();
        }
        NutritionRecipePo recipe = recipeRepository.findByIdAndStatusAndDeletedFalse(
                        item.getRecipeId(), NutritionStatus.ACTIVE)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_RECIPE_NOT_FOUND", "nutrition recipe not found"));
        if (!isRecipeVisibleToFamily(familyId, recipe)) {
            throw new NutritionException("NUTRITION_RECIPE_NOT_FOUND", "nutrition recipe not found");
        }
        NutritionTotals recipeTotals = calculationService.calculateRecipe(recipe.getId());
        BigDecimal servingCount = confirmedServingCount == null ? BigDecimal.ZERO : confirmedServingCount;
        BigDecimal recipeServingCount = BigDecimal.valueOf(Math.max(recipe.getServingCount(), 1));
        BigDecimal scale = servingCount.divide(recipeServingCount, 6, RoundingMode.HALF_UP);
        return scale(recipeTotals, scale);
    }

    private boolean isRecipeVisibleToFamily(Long familyId, NutritionRecipePo recipe) {
        return NutritionRecipeSourceType.PLATFORM_PUBLIC == recipe.getSourceType()
                || Objects.equals(recipe.getFamilyId(), familyId);
    }

    private NutritionTotals scale(NutritionTotals totals, BigDecimal scale) {
        return new NutritionTotals(
                multiply(totals.calories(), scale),
                multiply(totals.protein(), scale),
                multiply(totals.fat(), scale),
                multiply(totals.carbs(), scale),
                multiply(totals.sugar(), scale),
                multiply(totals.sodium(), scale),
                multiply(totals.fiber(), scale),
                multiply(totals.cholesterol(), scale));
    }

    private NutritionTotals standardFoodTotals(NutritionStandardFoodPo food, BigDecimal grams) {
        BigDecimal scale = grams.divide(new BigDecimal("100.000"), 6, RoundingMode.HALF_UP);
        return new NutritionTotals(
                multiply(food.getCaloriesPer100g(), scale),
                multiply(food.getProteinPer100g(), scale),
                multiply(food.getFatPer100g(), scale),
                multiply(food.getCarbsPer100g(), scale),
                multiply(food.getSugarPer100g(), scale),
                multiply(food.getSodiumPer100g(), scale),
                multiply(food.getFiberPer100g(), scale),
                multiply(food.getCholesterolPer100g(), scale));
    }

    private NutritionTotals requestTotals(NutritionNutrientsRequest nutrients) {
        return new NutritionTotals(nutrients.calories(), nutrients.protein(), nutrients.fat(), nutrients.carbs(),
                nutrients.sugar(), nutrients.sodium(), nutrients.fiber(), nutrients.cholesterol());
    }

    private NutritionTotals targetTotals(NutritionHealthProfilePo profile) {
        if (profile == null) {
            return NutritionTotals.zero();
        }
        return new NutritionTotals(profile.getTargetCalories(), profile.getTargetProtein(), profile.getTargetFat(),
                profile.getTargetCarbs(), profile.getTargetSugar(), profile.getTargetSodium(), null, null);
    }

    private NutritionTotals subtract(NutritionTotals target, NutritionTotals actual) {
        return new NutritionTotals(
                target.calories().subtract(actual.calories()),
                target.protein().subtract(actual.protein()),
                target.fat().subtract(actual.fat()),
                target.carbs().subtract(actual.carbs()),
                target.sugar().subtract(actual.sugar()),
                target.sodium().subtract(actual.sodium()),
                target.fiber().subtract(actual.fiber()),
                target.cholesterol().subtract(actual.cholesterol()));
    }

    private BigDecimal multiply(BigDecimal value, BigDecimal scale) {
        return zeroIfNull(value).multiply(scale).setScale(3, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> riskTags(List<NutritionRiskCheckResultPo> risks, Long memberProfileId) {
        return risks.stream()
                .filter(risk -> risk.getMemberProfileId() == null
                        || Objects.equals(risk.getMemberProfileId(), memberProfileId))
                .map(risk -> {
                    Map<String, Object> tag = new LinkedHashMap<>();
                    tag.put("riskId", risk.getId());
                    tag.put("ruleCode", risk.getRuleCode());
                    tag.put("riskLevel", risk.getRiskLevel());
                    tag.put("riskMessage", risk.getRiskMessage());
                    return tag;
                })
                .toList();
    }

    private Map<String, Object> calculationSnapshot(NutritionMealPlanItemPo item,
                                                    BigDecimal confirmedServingCount,
                                                    NutritionTotals totals) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mealPlanItemId", item.getId());
        snapshot.put("recipeId", item.getRecipeId());
        snapshot.put("dishName", item.getDishName());
        snapshot.put("plannedServingCount", item.getServingCount());
        snapshot.put("confirmedServingCount", confirmedServingCount);
        snapshot.put("nutrients", NutritionNutrientsResponse.from(totals));
        return snapshot;
    }

    private Map<String, Object> nutritionSnapshot(NutritionRecordPo record) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recordId", record.getId());
        snapshot.put("sourceType", record.getSourceType());
        snapshot.put("nutrients", NutritionNutrientsResponse.from(record));
        return snapshot;
    }

    private Long originalRecordId(NutritionRecordPo record) {
        JsonNode value = readMetadataField(record.getMetadataJson(), "originalRecordId");
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isTextual() && StringUtils.hasText(value.textValue())) {
            try {
                return Long.parseLong(value.textValue());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private NutritionRecordPo originalRecord(Long familyId, NutritionRecordPo record) {
        NutritionRecordPo current = record;
        Set<Long> visited = new HashSet<>();
        while (SOURCE_TYPE_ADJUSTMENT.equals(current.getSourceType())) {
            Long originalId = originalRecordId(current);
            if (originalId == null || !visited.add(originalId)) {
                throw new NutritionException(
                        "NUTRITION_RECORD_INVALID", "nutrition adjustment source is invalid");
            }
            current = recordRepository.findByIdAndFamilyIdAndDeletedFalse(originalId, familyId)
                    .orElseThrow(() -> new NutritionException(
                            "NUTRITION_RECORD_NOT_FOUND", "nutrition record not found"));
        }
        return current;
    }

    private Long sourceMealPlanItemId(NutritionRecordPo record) {
        Long sourceId = readLong(record.getMetadataJson(), "sourceMealPlanItemId");
        return sourceId == null ? readLong(record.getMetadataJson(), "mealPlanItemId") : sourceId;
    }

    private Long readLong(String metadataJson, String fieldName) {
        JsonNode value = readMetadataField(metadataJson, fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isTextual() && StringUtils.hasText(value.textValue())) {
            try {
                return Long.parseLong(value.textValue());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readText(String metadataJson, String fieldName) {
        JsonNode value = readMetadataField(metadataJson, fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.textValue() : value.asText();
    }

    private JsonNode readMetadataField(String metadataJson, String fieldName) {
        if (!StringUtils.hasText(metadataJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            return root == null ? null : root.get(fieldName);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition record metadata is invalid");
        }
    }

    private void apply(NutritionRecordPo record, NutritionTotals totals) {
        record.setCalories(totals.calories());
        record.setProtein(totals.protein());
        record.setFat(totals.fat());
        record.setCarbs(totals.carbs());
        record.setSugar(totals.sugar());
        record.setSodium(totals.sodium());
        record.setFiber(totals.fiber());
        record.setCholesterol(totals.cholesterol());
    }

    private void apply(NutritionRecordPo record, NutritionNutrientsRequest nutrients) {
        record.setCalories(normalize(nutrients.calories()));
        record.setProtein(normalize(nutrients.protein()));
        record.setFat(normalize(nutrients.fat()));
        record.setCarbs(normalize(nutrients.carbs()));
        record.setSugar(normalize(nutrients.sugar()));
        record.setSodium(normalize(nutrients.sodium()));
        record.setFiber(normalize(nutrients.fiber()));
        record.setCholesterol(normalize(nutrients.cholesterol()));
    }

    private NutritionRecordResponse toRecordResponse(NutritionRecordPo record) {
        return new NutritionRecordResponse(record.getId(), record.getFamilyId(), record.getMemberProfileId(),
                record.getMealPlanId(), record.getMealConfirmationId(), sourceMealPlanItemId(record), record.getRecordDate(),
                record.getMealType(), record.getSourceType(), NutritionNutrientsResponse.from(record),
                record.getRiskTags(), record.getCalculationSnapshot(), record.getMetadataJson(),
                record.getCreatedAt(), record.getUpdatedAt());
    }

    private String toJson(Object value, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", message);
        }
    }

    private BigDecimal normalize(BigDecimal value) {
        return zeroIfNull(value).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private void requireRecordWrite(Long userId, Long familyId, Long memberProfileId) {
        if (!accessService.canCookFamily(userId, familyId)) {
            accessService.requireWriteMemberProfile(userId, familyId, memberProfileId);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record RecordSourceKey(Long memberProfileId, Long sourceMealPlanItemId) {
    }

    private static final class NutritionAccumulator {

        private NutritionTotals totals = NutritionTotals.zero();

        private void add(NutritionRecordPo record) {
            add(new NutritionTotals(record.getCalories(), record.getProtein(), record.getFat(),
                    record.getCarbs(), record.getSugar(), record.getSodium(), record.getFiber(),
                    record.getCholesterol()));
        }

        private void add(NutritionTotals values) {
            totals = totals.plus(values);
        }

        private NutritionTotals toTotals() {
            return totals;
        }
    }
}
