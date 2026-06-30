package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.response.MealPlanItemResponse;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.dto.response.MealPlanSummaryResponse;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application service for meal-plan review, publishing, and summaries.
 */
@Service
@RequiredArgsConstructor
@Validated
public class MealPlanService {

    private static final TypeReference<List<NutritionMealType>> MEAL_TYPE_LIST_TYPE = new TypeReference<>() {
    };

    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionMealConfirmationRepository confirmationRepository;
    private final NutritionAccessService accessService;
    private final NutritionRecordService nutritionRecordService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<MealPlanResponse> listMealPlans(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionMealPlanPo> mealPlans = mealPlanRepository
                .findByFamilyIdAndDeletedFalseOrderByPlanDateDescIdDesc(familyId);
        return toResponses(mealPlans);
    }

    @Transactional(readOnly = true)
    public List<MealPlanResponse> listTodayMealPlans(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionMealPlanPo> mealPlans = mealPlanRepository
                .findByFamilyIdAndPlanDateAndDeletedFalseOrderByIdDesc(familyId, LocalDate.now());
        return toResponses(mealPlans);
    }

    @Transactional
    public MealPlanResponse publishMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        transition(mealPlan, NutritionMealPlanStatus.PUBLISHED);
        if (mealPlan.getPublishedAt() == null) {
            mealPlan.setPublishedAt(Instant.now());
        }
        return toResponse(mealPlanRepository.saveAndFlush(mealPlan));
    }

    @Transactional
    public MealPlanResponse markAdjustedMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        transition(mealPlan, NutritionMealPlanStatus.ADJUSTED);
        return toResponse(mealPlanRepository.saveAndFlush(mealPlan));
    }

    @Transactional
    public MealPlanResponse closeConfirmation(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        refreshConfirmedMemberCount(mealPlan);
        transition(mealPlan, NutritionMealPlanStatus.CONFIRM_CLOSED);
        return toResponse(mealPlanRepository.saveAndFlush(mealPlan));
    }

    @Transactional
    public MealPlanResponse startPreparing(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        transition(mealPlan, NutritionMealPlanStatus.PREPARING);
        return toResponse(mealPlanRepository.saveAndFlush(mealPlan));
    }

    @Transactional
    public MealPlanResponse completeMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        if (mealPlan.getStatus() == NutritionMealPlanStatus.COMPLETED) {
            nutritionRecordService.generateForCompletedMealPlan(familyId, mealPlan.getId());
            return toResponse(mealPlan);
        }
        if (mealPlan.getStatus() == NutritionMealPlanStatus.CONFIRM_CLOSED) {
            transition(mealPlan, NutritionMealPlanStatus.PREPARING);
        }
        transition(mealPlan, NutritionMealPlanStatus.COMPLETED);
        NutritionMealPlanPo saved = mealPlanRepository.saveAndFlush(mealPlan);
        nutritionRecordService.generateForCompletedMealPlan(familyId, saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public MealPlanResponse cancelMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        transition(mealPlan, NutritionMealPlanStatus.CANCELLED);
        return toResponse(mealPlanRepository.saveAndFlush(mealPlan));
    }

    @Transactional(readOnly = true)
    public MealPlanSummaryResponse summary(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        List<NutritionMealPlanItemPo> items = activeItems(mealPlan.getId());
        List<NutritionMealConfirmationPo> confirmations = confirmationRepository
                .findByMealPlanIdAndConfirmationStatusAndDeletedFalse(
                        mealPlan.getId(), NutritionConfirmationStatus.CONFIRMED);
        List<MealPlanSummaryResponse.DishSummary> dishes = items.stream()
                .map(item -> toDishSummary(item, confirmations))
                .toList();
        return new MealPlanSummaryResponse(mealPlan.getId(), confirmations.size(), dishes);
    }

    NutritionMealPlanPo markConfirmingForConfirmation(NutritionMealPlanPo mealPlan) {
        if (mealPlan.getStatus() == NutritionMealPlanStatus.CONFIRMING) {
            return mealPlan;
        }
        transition(mealPlan, NutritionMealPlanStatus.CONFIRMING);
        return mealPlanRepository.saveAndFlush(mealPlan);
    }

    void refreshConfirmedMemberCount(NutritionMealPlanPo mealPlan) {
        long confirmedCount = confirmationRepository.countByMealPlanIdAndConfirmationStatusAndDeletedFalse(
                mealPlan.getId(), NutritionConfirmationStatus.CONFIRMED);
        mealPlan.setConfirmedMemberCount(Math.toIntExact(confirmedCount));
        mealPlanRepository.saveAndFlush(mealPlan);
    }

    NutritionMealPlanPo getMealPlan(Long familyId, Long mealPlanId) {
        return mealPlanRepository.findByIdAndFamilyIdAndDeletedFalse(mealPlanId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEAL_PLAN_NOT_FOUND", "nutrition meal plan not found"));
    }

    NutritionMealPlanPo getLockedMealPlan(Long familyId, Long mealPlanId) {
        return mealPlanRepository.findLockedByIdAndFamilyIdAndDeletedFalse(mealPlanId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEAL_PLAN_NOT_FOUND", "nutrition meal plan not found"));
    }

    private void transition(NutritionMealPlanPo mealPlan, NutritionMealPlanStatus targetStatus) {
        NutritionMealPlanStatus sourceStatus = mealPlan.getStatus();
        if (!canTransition(sourceStatus, targetStatus)) {
            throw new NutritionException(
                    "NUTRITION_MEAL_PLAN_STATUS_INVALID", "nutrition meal plan status transition is invalid");
        }
        mealPlan.setStatus(targetStatus);
    }

    private boolean canTransition(NutritionMealPlanStatus sourceStatus, NutritionMealPlanStatus targetStatus) {
        if (sourceStatus == null || targetStatus == null || sourceStatus == targetStatus) {
            return false;
        }
        return switch (sourceStatus) {
            case PENDING_REVIEW -> EnumSet.of(
                    NutritionMealPlanStatus.ADJUSTED,
                    NutritionMealPlanStatus.PUBLISHED).contains(targetStatus);
            case ADJUSTED -> targetStatus == NutritionMealPlanStatus.PUBLISHED;
            case PUBLISHED -> EnumSet.of(
                    NutritionMealPlanStatus.CONFIRMING,
                    NutritionMealPlanStatus.CONFIRM_CLOSED,
                    NutritionMealPlanStatus.CANCELLED).contains(targetStatus);
            case CONFIRMING -> EnumSet.of(
                    NutritionMealPlanStatus.CONFIRM_CLOSED,
                    NutritionMealPlanStatus.CANCELLED).contains(targetStatus);
            case CONFIRM_CLOSED -> EnumSet.of(
                    NutritionMealPlanStatus.PREPARING,
                    NutritionMealPlanStatus.CANCELLED).contains(targetStatus);
            case PREPARING -> EnumSet.of(
                    NutritionMealPlanStatus.COMPLETED,
                    NutritionMealPlanStatus.CANCELLED).contains(targetStatus);
            default -> false;
        };
    }

    private MealPlanSummaryResponse.DishSummary toDishSummary(
            NutritionMealPlanItemPo item, List<NutritionMealConfirmationPo> confirmations) {
        long selectedCount = confirmations.stream()
                .filter(confirmation -> selectsMealType(confirmation, item.getMealType()))
                .count();
        BigDecimal confirmedServingTotal = item.getServingCount().multiply(BigDecimal.valueOf(selectedCount));
        return new MealPlanSummaryResponse.DishSummary(item.getId(), item.getDishName(), item.getMealType(),
                item.getServingCount(), confirmedServingTotal);
    }

    private boolean selectsMealType(NutritionMealConfirmationPo confirmation, NutritionMealType mealType) {
        List<NutritionMealType> selectedMealTypes = readMealTypes(confirmation.getSelectedMealTypes());
        return selectedMealTypes.isEmpty() || selectedMealTypes.contains(mealType);
    }

    private List<MealPlanResponse> toResponses(List<NutritionMealPlanPo> mealPlans) {
        if (mealPlans.isEmpty()) {
            return List.of();
        }
        List<Long> mealPlanIds = mealPlans.stream().map(NutritionMealPlanPo::getId).toList();
        Map<Long, List<NutritionMealPlanItemPo>> itemsByMealPlanId = mealPlanItemRepository
                .findByMealPlanIdInAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        mealPlanIds, NutritionStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(NutritionMealPlanItemPo::getMealPlanId));
        return mealPlans.stream()
                .map(mealPlan -> toResponse(mealPlan, itemsByMealPlanId.getOrDefault(mealPlan.getId(), List.of())))
                .toList();
    }

    private MealPlanResponse toResponse(NutritionMealPlanPo mealPlan) {
        return toResponse(mealPlan, activeItems(mealPlan.getId()));
    }

    private MealPlanResponse toResponse(NutritionMealPlanPo mealPlan, List<NutritionMealPlanItemPo> items) {
        return new MealPlanResponse(mealPlan.getId(), mealPlan.getFamilyId(), mealPlan.getAiRecommendationId(),
                mealPlan.getPlanDate(), mealPlan.getStatus(), mealPlan.getTitle(), mealPlan.getPublishedAt(),
                mealPlan.getConfirmationCutoffAt(), mealPlan.getConfirmedMemberCount(),
                mealPlan.getEstimatedCost(), items.stream().map(this::toItemResponse).toList(),
                mealPlan.getCreatedAt(), mealPlan.getUpdatedAt());
    }

    private MealPlanItemResponse toItemResponse(NutritionMealPlanItemPo item) {
        return new MealPlanItemResponse(item.getId(), item.getMealPlanId(), item.getMealType(),
                item.getRecipeId(), item.getDishName(), item.getServingCount(), item.getSortOrder());
    }

    private List<NutritionMealPlanItemPo> activeItems(Long mealPlanId) {
        return mealPlanItemRepository.findByMealPlanIdAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                mealPlanId, NutritionStatus.ACTIVE);
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

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }
}
