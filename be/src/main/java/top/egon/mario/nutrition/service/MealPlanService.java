package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.request.AcknowledgeMealRiskRequest;
import top.egon.mario.nutrition.dto.request.MealPlanItemRequest;
import top.egon.mario.nutrition.dto.request.UpdateMealPlanRequest;
import top.egon.mario.nutrition.dto.response.MealPlanItemResponse;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.dto.response.MealPlanSummaryResponse;
import top.egon.mario.nutrition.dto.response.MealRiskResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealOperationLogPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealOperationLogRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.nutrition.service.ai.NutritionAiService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for versioned meal-plan review, publishing, and summaries.
 */
@Service
@RequiredArgsConstructor
@Validated
public class MealPlanService {

    private static final String MEAL_PLAN_SOURCE_TYPE = "MEAL_PLAN";
    private static final Set<NutritionMealPlanStatus> EDITABLE_STATUSES = EnumSet.of(
            NutritionMealPlanStatus.PENDING_REVIEW, NutritionMealPlanStatus.ADJUSTED);

    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionMealConfirmationRepository confirmationRepository;
    private final NutritionMealConfirmationItemRepository confirmationItemRepository;
    private final NutritionMemberProfileRepository memberProfileRepository;
    private final NutritionRiskCheckResultRepository riskCheckResultRepository;
    private final NutritionMealOperationLogRepository operationLogRepository;
    private final NutritionAccessService accessService;
    private final NutritionRecordService nutritionRecordService;
    private final RecipeService recipeService;
    private final NutritionMealValidationService mealValidationService;
    private final NutritionAiService aiService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<MealPlanResponse> listMealPlans(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return toResponses(familyId, mealPlanRepository
                .findByFamilyIdAndDeletedFalseOrderByPlanDateDescIdDesc(familyId));
    }

    @Transactional(readOnly = true)
    public List<MealPlanResponse> listTodayMealPlans(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return toResponses(familyId, mealPlanRepository
                .findByFamilyIdAndPlanDateAndDeletedFalseOrderByIdDesc(familyId, LocalDate.now()));
    }

    @Transactional
    public MealPlanResponse updateMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId,
                                           @Valid @NotNull UpdateMealPlanRequest request, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        requireEditable(mealPlan);
        if (!Objects.equals(mealPlan.getVersion(), request.expectedVersion())) {
            throw new NutritionException(
                    "NUTRITION_MEAL_VERSION_CONFLICT", "nutrition meal plan version is stale");
        }
        List<NutritionMealPlanItemPo> currentItems = activeItems(mealPlanId);
        String before = snapshot(mealPlan, currentItems, activeRisks(familyId, mealPlanId));
        replaceItems(familyId, mealPlan, currentItems, request.items(), userId);
        mealPlan.setConfirmationCutoffAt(request.confirmationCutoffAt());
        if (mealPlan.getStatus() == NutritionMealPlanStatus.PENDING_REVIEW) {
            transition(mealPlan, NutritionMealPlanStatus.ADJUSTED);
        }
        mealPlanRepository.saveAndFlush(mealPlan);
        MealValidationResult validation = mealValidationService.validateAndPersist(familyId, mealPlanId, userId);
        mealPlanRepository.flush();
        List<NutritionMealPlanItemPo> savedItems = activeItems(mealPlanId);
        List<NutritionRiskCheckResultPo> risks = activeRisks(familyId, mealPlanId);
        String after = snapshot(mealPlan, savedItems, risks);
        saveOperation(mealPlan, "EDIT", userId, before, after, null, Map.of());
        return toResponse(mealPlan, savedItems, risks, validation.publishable());
    }

    @Transactional
    public MealPlanResponse acknowledgeRisks(@NotNull Long familyId, @NotNull Long mealPlanId,
                                             @Valid @NotNull AcknowledgeMealRiskRequest request,
                                             Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        requireEditable(mealPlan);
        List<NutritionMealPlanItemPo> items = activeItems(mealPlanId);
        List<NutritionRiskCheckResultPo> activeRisks = activeRisks(familyId, mealPlanId);
        String before = snapshot(mealPlan, items, activeRisks);
        Set<Long> requestedIds = new LinkedHashSet<>(request.riskIds());
        if (requestedIds.size() != request.riskIds().size()) {
            throw new NutritionException("NUTRITION_MEAL_RISK_INVALID", "duplicate nutrition meal risk id");
        }
        Map<Long, NutritionRiskCheckResultPo> risksById = activeRisks.stream()
                .collect(Collectors.toMap(NutritionRiskCheckResultPo::getId, Function.identity()));
        List<NutritionRiskCheckResultPo> selected = requestedIds.stream().map(risksById::get).toList();
        if (selected.stream().anyMatch(Objects::isNull)) {
            throw new NutritionException("NUTRITION_MEAL_RISK_NOT_FOUND", "nutrition meal risk not found");
        }
        if (selected.stream().anyMatch(risk -> risk.getRiskLevel() == NutritionRiskLevel.HIGH
                || risk.getRiskLevel() == NutritionRiskLevel.BLOCKING)) {
            throw new NutritionException("NUTRITION_MEAL_RISK_BLOCKED", "high nutrition meal risk cannot be acknowledged");
        }
        if (selected.stream().anyMatch(risk -> risk.getRiskLevel() != NutritionRiskLevel.MEDIUM)) {
            throw new NutritionException(
                    "NUTRITION_MEAL_RISK_INVALID", "only medium nutrition meal risks can be acknowledged");
        }
        Instant acknowledgedAt = Instant.now();
        selected.forEach(risk -> risk.setMetadataJson(acknowledgementMetadata(
                risk.getMetadataJson(), userId, request.note().trim(), acknowledgedAt)));
        riskCheckResultRepository.saveAllAndFlush(selected);
        List<NutritionRiskCheckResultPo> savedRisks = activeRisks(familyId, mealPlanId);
        String after = snapshot(mealPlan, items, savedRisks);
        saveOperation(mealPlan, "ACKNOWLEDGE_RISK", userId, before, after,
                request.note().trim(), Map.of("riskIds", requestedIds));
        return toResponse(mealPlan, items, savedRisks, validationPublishable(mealPlan));
    }

    @Transactional
    public NutritionAiRecommendationJobResponse regenerateMealPlan(
            @NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        requireEditable(mealPlan);
        List<NutritionMealPlanItemPo> items = activeItems(mealPlanId);
        List<NutritionMealType> mealTypes = items.stream().map(NutritionMealPlanItemPo::getMealType)
                .distinct().toList();
        String snapshot = snapshot(mealPlan, items, activeRisks(familyId, mealPlanId));
        NutritionAiRecommendationJobResponse job = aiService.generateManualRecommendation(
                familyId, mealPlan.getPlanDate(), mealTypes, userId);
        saveOperation(mealPlan, "REGENERATE", userId, snapshot, snapshot, null, Map.of("aiJobId", job.id()));
        return job;
    }

    @Transactional
    public MealPlanResponse publishMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        requireEditable(mealPlan);
        requireFutureCutoff(mealPlan);
        List<NutritionMealPlanItemPo> beforeItems = activeItems(mealPlanId);
        String before = snapshot(mealPlan, beforeItems, activeRisks(familyId, mealPlanId));
        MealValidationResult validation = mealValidationService.validateAndPersist(familyId, mealPlanId, userId);
        List<NutritionMealPlanItemPo> items = activeItems(mealPlanId);
        List<NutritionRiskCheckResultPo> risks = activeRisks(familyId, mealPlanId);
        if (items.isEmpty() || items.stream().anyMatch(item -> item.getRecipeId() == null)
                || !validation.publishable()
                || risks.stream().anyMatch(this::blockingRisk)) {
            throw new NutritionException("NUTRITION_MEAL_NOT_PUBLISHABLE", "nutrition meal plan is not publishable");
        }
        if (risks.stream().filter(risk -> risk.getRiskLevel() == NutritionRiskLevel.MEDIUM)
                .anyMatch(risk -> !acknowledged(risk))) {
            throw new NutritionException(
                    "NUTRITION_MEAL_RISK_ACKNOWLEDGEMENT_REQUIRED",
                    "medium nutrition meal risks must be acknowledged before publishing");
        }
        transition(mealPlan, NutritionMealPlanStatus.PUBLISHED);
        mealPlan.setPublishedAt(Instant.now());
        mealPlanRepository.saveAndFlush(mealPlan);
        String after = snapshot(mealPlan, items, risks);
        saveOperation(mealPlan, "PUBLISH", userId, before, after, null, Map.of());
        return toResponse(mealPlan, items, risks, true);
    }

    @Transactional
    public MealPlanResponse markAdjustedMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        String before = snapshot(mealPlan);
        transition(mealPlan, NutritionMealPlanStatus.ADJUSTED);
        mealPlanRepository.saveAndFlush(mealPlan);
        saveOperation(mealPlan, "ADJUST", userId, before, snapshot(mealPlan), null, Map.of());
        return toResponse(mealPlan);
    }

    @Transactional
    public MealPlanResponse closeConfirmation(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        return closeConfirmation(familyId, mealPlanId, false, actorId);
    }

    @Transactional
    public MealPlanResponse closeConfirmation(@NotNull Long familyId, @NotNull Long mealPlanId,
                                              boolean closeEarly, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        if (!closeEarly && mealPlan.getConfirmationCutoffAt() != null
                && Instant.now().isBefore(mealPlan.getConfirmationCutoffAt())) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_CUTOFF_NOT_REACHED",
                    "nutrition meal confirmation cutoff has not been reached");
        }
        String before = snapshot(mealPlan);
        expireRemainingConfirmations(familyId, mealPlan, userId);
        refreshConfirmedMemberCount(mealPlan);
        transition(mealPlan, NutritionMealPlanStatus.CONFIRM_CLOSED);
        mealPlanRepository.saveAndFlush(mealPlan);
        saveOperation(mealPlan, "CLOSE_CONFIRMATION", userId, before, snapshot(mealPlan), null,
                Map.of("closeEarly", closeEarly));
        return toResponse(mealPlan);
    }

    @Transactional
    public MealPlanResponse startPreparing(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        String before = snapshot(mealPlan);
        transition(mealPlan, NutritionMealPlanStatus.PREPARING);
        mealPlanRepository.saveAndFlush(mealPlan);
        saveOperation(mealPlan, "START_PREPARING", userId, before, snapshot(mealPlan), null, Map.of());
        return toResponse(mealPlan);
    }

    @Transactional
    public MealPlanResponse completeMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        if (mealPlan.getStatus() == NutritionMealPlanStatus.COMPLETED) {
            nutritionRecordService.generateForCompletedMealPlan(familyId, mealPlan.getId(), userId);
            return toResponse(mealPlan);
        }
        String before = snapshot(mealPlan);
        if (mealPlan.getStatus() == NutritionMealPlanStatus.CONFIRM_CLOSED) {
            transition(mealPlan, NutritionMealPlanStatus.PREPARING);
        }
        transition(mealPlan, NutritionMealPlanStatus.COMPLETED);
        NutritionMealPlanPo saved = mealPlanRepository.saveAndFlush(mealPlan);
        nutritionRecordService.generateForCompletedMealPlan(familyId, saved.getId(), userId);
        saveOperation(saved, "COMPLETE", userId, before, snapshot(saved), null, Map.of());
        return toResponse(saved);
    }

    @Transactional
    public MealPlanResponse cancelMealPlan(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getLockedMealPlan(familyId, mealPlanId);
        String before = snapshot(mealPlan);
        transition(mealPlan, NutritionMealPlanStatus.CANCELLED);
        mealPlanRepository.saveAndFlush(mealPlan);
        saveOperation(mealPlan, "CANCEL", userId, before, snapshot(mealPlan), null, Map.of());
        return toResponse(mealPlan);
    }

    @Transactional(readOnly = true)
    public MealPlanSummaryResponse summary(@NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        NutritionMealPlanPo mealPlan = getMealPlan(familyId, mealPlanId);
        List<NutritionMealPlanItemPo> items = activeItems(mealPlan.getId());
        List<NutritionMealConfirmationPo> confirmations = confirmationRepository
                .findByMealPlanIdAndDeletedFalseOrderByIdAsc(mealPlan.getId());
        List<NutritionMealConfirmationPo> confirmed = confirmations.stream()
                .filter(confirmation -> confirmation.getConfirmationStatus() == NutritionConfirmationStatus.CONFIRMED)
                .toList();
        List<Long> confirmedIds = confirmed.stream().map(NutritionMealConfirmationPo::getId).toList();
        List<NutritionMealConfirmationItemPo> selectedItems = confirmedIds.isEmpty() ? List.of()
                : confirmationItemRepository.findByConfirmationIdInAndDeletedFalseOrderByIdAsc(confirmedIds).stream()
                .filter(NutritionMealConfirmationItemPo::isSelected).toList();
        Map<Long, List<NutritionMealConfirmationItemPo>> selectedByPlanItem = selectedItems.stream()
                .collect(Collectors.groupingBy(NutritionMealConfirmationItemPo::getMealPlanItemId));
        List<MealPlanSummaryResponse.DishSummary> dishes = items.stream()
                .map(item -> toDishSummary(item, selectedByPlanItem.getOrDefault(item.getId(), List.of())))
                .toList();
        int activeMemberCount = Math.toIntExact(memberProfileRepository
                .countByFamilyIdAndStatusAndDeletedFalse(familyId, NutritionStatus.ACTIVE));
        int confirmedMemberCount = confirmed.size();
        int awayMemberCount = Math.toIntExact(confirmations.stream()
                .filter(confirmation -> confirmation.getConfirmationStatus() == NutritionConfirmationStatus.AWAY)
                .count());
        int unconfirmedMemberCount = Math.max(activeMemberCount - confirmedMemberCount - awayMemberCount, 0);
        List<NutritionRiskCheckResultPo> risks = summaryRisks(familyId, mealPlanId, items);
        Map<NutritionRiskLevel, Long> riskCounts = risks.stream().collect(Collectors.groupingBy(
                NutritionRiskCheckResultPo::getRiskLevel, Collectors.counting()));
        List<String> remarks = confirmations.stream().map(NutritionMealConfirmationPo::getRemark)
                .filter(StringUtils::hasText).toList();
        boolean readyForShopping = EnumSet.of(NutritionMealPlanStatus.CONFIRM_CLOSED,
                NutritionMealPlanStatus.PREPARING, NutritionMealPlanStatus.COMPLETED)
                .contains(mealPlan.getStatus());
        return new MealPlanSummaryResponse(mealPlan.getId(), activeMemberCount, confirmedMemberCount,
                awayMemberCount, unconfirmedMemberCount, Map.copyOf(riskCounts), remarks,
                readyForShopping, dishes);
    }

    private void expireRemainingConfirmations(Long familyId, NutritionMealPlanPo mealPlan, Long actorId) {
        Map<Long, NutritionMealConfirmationPo> confirmationsByMemberId = confirmationRepository
                .findByMealPlanIdAndDeletedFalseOrderByIdAsc(mealPlan.getId()).stream()
                .collect(Collectors.toMap(NutritionMealConfirmationPo::getMemberProfileId, Function.identity()));
        List<NutritionMealConfirmationPo> expired = new ArrayList<>();
        for (NutritionMemberProfilePo member : memberProfileRepository
                .findByFamilyIdAndStatusAndDeletedFalse(familyId, NutritionStatus.ACTIVE)) {
            NutritionMealConfirmationPo confirmation = confirmationsByMemberId.get(member.getId());
            if (confirmation != null && (confirmation.getConfirmationStatus() == NutritionConfirmationStatus.CONFIRMED
                    || confirmation.getConfirmationStatus() == NutritionConfirmationStatus.AWAY)) {
                continue;
            }
            if (confirmation == null) {
                confirmation = new NutritionMealConfirmationPo();
                confirmation.setFamilyId(familyId);
                confirmation.setMealPlanId(mealPlan.getId());
                confirmation.setMemberProfileId(member.getId());
            }
            confirmation.setConfirmationStatus(NutritionConfirmationStatus.EXPIRED);
            confirmation.setEatAtHome(false);
            confirmation.setConfirmedByUserId(actorId);
            confirmation.setProxyByUserId(actorId);
            confirmation.setConfirmedAt(Instant.now());
            confirmation.setMetadataJson(writeJson(Map.of("expiredByClose", true)));
            confirmation.setDeleted(false);
            expired.add(confirmation);
        }
        if (!expired.isEmpty()) {
            confirmationRepository.saveAllAndFlush(expired);
        }
    }

    NutritionMealPlanPo markConfirmingForConfirmation(NutritionMealPlanPo mealPlan, Long actorId) {
        if (mealPlan.getStatus() == NutritionMealPlanStatus.CONFIRMING) {
            return mealPlan;
        }
        String before = snapshot(mealPlan);
        transition(mealPlan, NutritionMealPlanStatus.CONFIRMING);
        NutritionMealPlanPo saved = mealPlanRepository.saveAndFlush(mealPlan);
        saveOperation(saved, "START_CONFIRMING", actorId, before, snapshot(saved), null, Map.of());
        return saved;
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

    private void replaceItems(Long familyId, NutritionMealPlanPo mealPlan,
                              List<NutritionMealPlanItemPo> currentItems,
                              List<MealPlanItemRequest> requests, Long actorId) {
        validateItemRequests(currentItems, requests);
        Map<Long, RecipeResponse> recipes = new LinkedHashMap<>();
        for (MealPlanItemRequest request : requests) {
            RecipeResponse recipe = recipeService.getRecipe(familyId, request.recipeId(), actorId);
            var validation = recipeService.validateRecipe(familyId, recipe.id(), actorId);
            if (!validation.publishable()) {
                throw new NutritionException(
                        "NUTRITION_RECIPE_INVALID", "nutrition recipe is invalid: " + validation.errors());
            }
            recipes.put(recipe.id(), recipe);
        }
        Map<Long, NutritionMealPlanItemPo> currentById = currentItems.stream()
                .collect(Collectors.toMap(NutritionMealPlanItemPo::getId, Function.identity()));
        Set<Long> retainedIds = requests.stream().map(MealPlanItemRequest::id)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        currentItems.stream().filter(item -> !retainedIds.contains(item.getId()))
                .forEach(item -> item.setStatus(NutritionStatus.ARCHIVED));
        List<NutritionMealPlanItemPo> saved = new ArrayList<>(currentItems);
        for (MealPlanItemRequest request : requests) {
            NutritionMealPlanItemPo item = request.id() == null
                    ? new NutritionMealPlanItemPo() : currentById.get(request.id());
            RecipeResponse recipe = recipes.get(request.recipeId());
            item.setFamilyId(familyId);
            item.setMealPlanId(mealPlan.getId());
            item.setMealType(request.mealType());
            item.setRecipeId(recipe.id());
            item.setDishName(recipe.name());
            item.setServingCount(request.servingCount());
            item.setSortOrder(request.sortOrder());
            item.setNutritionSnapshot(writeJson(recipe.nutritionSnapshot()));
            item.setCostSnapshot(recipe.estimatedCost() == null
                    ? "{}" : writeJson(Map.of("estimatedCost", recipe.estimatedCost())));
            item.setStatus(NutritionStatus.ACTIVE);
            item.setDeleted(false);
            if (request.id() == null) {
                saved.add(item);
            }
        }
        mealPlanItemRepository.saveAllAndFlush(saved);
    }

    private void validateItemRequests(List<NutritionMealPlanItemPo> currentItems,
                                      List<MealPlanItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new NutritionException("NUTRITION_MEAL_ITEMS_REQUIRED", "nutrition meal items are required");
        }
        Set<Long> currentIds = currentItems.stream().map(NutritionMealPlanItemPo::getId).collect(Collectors.toSet());
        Set<Long> requestedIds = new LinkedHashSet<>();
        Set<Integer> sortOrders = new LinkedHashSet<>();
        Set<NutritionMealType> allowedMealTypes = currentItems.stream()
                .map(NutritionMealPlanItemPo::getMealType).collect(Collectors.toSet());
        for (MealPlanItemRequest request : requests) {
            if (request == null || request.mealType() == null || request.recipeId() == null
                    || request.servingCount() == null || request.servingCount().signum() <= 0
                    || request.sortOrder() == null || request.sortOrder() < 0) {
                throw new NutritionException("NUTRITION_MEAL_ITEM_INVALID", "nutrition meal item is invalid");
            }
            if (request.id() != null && (!currentIds.contains(request.id()) || !requestedIds.add(request.id()))) {
                throw new NutritionException("NUTRITION_MEAL_ITEM_NOT_FOUND", "nutrition meal item is not editable");
            }
            if (!sortOrders.add(request.sortOrder())) {
                throw new NutritionException(
                        "NUTRITION_MEAL_ITEM_SORT_DUPLICATE", "nutrition meal item sort order must be unique");
            }
            if (!allowedMealTypes.isEmpty() && !allowedMealTypes.contains(request.mealType())) {
                throw new NutritionException("NUTRITION_MEAL_TYPE_INVALID", "nutrition meal type was not requested");
            }
        }
    }

    private void requireEditable(NutritionMealPlanPo mealPlan) {
        if (!EDITABLE_STATUSES.contains(mealPlan.getStatus())) {
            throw new NutritionException("NUTRITION_MEAL_PLAN_IMMUTABLE", "nutrition meal plan is immutable");
        }
    }

    private void requireFutureCutoff(NutritionMealPlanPo mealPlan) {
        if (mealPlan.getConfirmationCutoffAt() == null
                || !mealPlan.getConfirmationCutoffAt().isAfter(Instant.now())) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_CUTOFF_INVALID",
                    "nutrition meal confirmation cutoff must be in the future");
        }
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
            NutritionMealPlanItemPo item, List<NutritionMealConfirmationItemPo> selections) {
        BigDecimal confirmedServingTotal = selections.stream()
                .map(NutritionMealConfirmationItemPo::getServingCount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MealPlanSummaryResponse.DishSummary(item.getId(), item.getDishName(), item.getMealType(),
                item.getServingCount(), selections.size(), confirmedServingTotal);
    }

    private List<NutritionRiskCheckResultPo> summaryRisks(
            Long familyId, Long mealPlanId, List<NutritionMealPlanItemPo> items) {
        List<NutritionRiskCheckResultPo> risks = new ArrayList<>(activeRisks(familyId, mealPlanId));
        if (!items.isEmpty()) {
            risks.addAll(riskCheckResultRepository
                    .findByFamilyIdAndSourceTypeAndSourceIdInAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                            familyId, "MEAL_PLAN_ITEM", items.stream().map(NutritionMealPlanItemPo::getId).toList(),
                            NutritionStatus.ACTIVE));
        }
        return risks;
    }

    private List<MealPlanResponse> toResponses(Long familyId, List<NutritionMealPlanPo> mealPlans) {
        if (mealPlans.isEmpty()) {
            return List.of();
        }
        List<Long> mealPlanIds = mealPlans.stream().map(NutritionMealPlanPo::getId).toList();
        Map<Long, List<NutritionMealPlanItemPo>> itemsByMealPlanId = mealPlanItemRepository
                .findByMealPlanIdInAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        mealPlanIds, NutritionStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(NutritionMealPlanItemPo::getMealPlanId));
        Map<Long, List<NutritionRiskCheckResultPo>> risksByMealPlanId = riskCheckResultRepository
                .findByFamilyIdAndSourceTypeAndSourceIdInAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        familyId, MEAL_PLAN_SOURCE_TYPE, mealPlanIds, NutritionStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(NutritionRiskCheckResultPo::getSourceId));
        return mealPlans.stream().map(mealPlan -> toResponse(
                mealPlan,
                itemsByMealPlanId.getOrDefault(mealPlan.getId(), List.of()),
                risksByMealPlanId.getOrDefault(mealPlan.getId(), List.of()),
                validationPublishable(mealPlan))).toList();
    }

    private MealPlanResponse toResponse(NutritionMealPlanPo mealPlan) {
        return toResponse(mealPlan, activeItems(mealPlan.getId()),
                activeRisks(mealPlan.getFamilyId(), mealPlan.getId()), validationPublishable(mealPlan));
    }

    private MealPlanResponse toResponse(NutritionMealPlanPo mealPlan, List<NutritionMealPlanItemPo> items,
                                        List<NutritionRiskCheckResultPo> risks, boolean validationPublishable) {
        boolean publishable = validationPublishable
                && !items.isEmpty()
                && items.stream().allMatch(item -> item.getRecipeId() != null)
                && mealPlan.getConfirmationCutoffAt() != null
                && mealPlan.getConfirmationCutoffAt().isAfter(Instant.now())
                && risks.stream().noneMatch(this::blockingRisk)
                && risks.stream().filter(risk -> risk.getRiskLevel() == NutritionRiskLevel.MEDIUM)
                .allMatch(this::acknowledged);
        return new MealPlanResponse(mealPlan.getId(), mealPlan.getFamilyId(), mealPlan.getAiRecommendationId(),
                mealPlan.getPlanDate(), mealPlan.getStatus(), mealPlan.getVersion(), mealPlan.getTitle(),
                mealPlan.getPublishedAt(), mealPlan.getConfirmationCutoffAt(), mealPlan.getConfirmedMemberCount(),
                mealPlan.getEstimatedCost(), mealPlan.getNutritionSnapshot(),
                risks.stream().map(this::toRiskResponse).toList(), publishable,
                items.stream().map(this::toItemResponse).toList(),
                mealPlan.getCreatedAt(), mealPlan.getUpdatedAt());
    }

    private MealPlanItemResponse toItemResponse(NutritionMealPlanItemPo item) {
        return new MealPlanItemResponse(item.getId(), item.getMealPlanId(), item.getMealType(),
                item.getRecipeId(), item.getDishName(), item.getServingCount(), item.getSortOrder(),
                item.getNutritionSnapshot(), item.getCostSnapshot(), item.getVersion());
    }

    private MealRiskResponse toRiskResponse(NutritionRiskCheckResultPo risk) {
        JsonNode riskSnapshot = readObject(risk.getRiskSnapshot());
        JsonNode metadata = readObject(risk.getMetadataJson());
        return new MealRiskResponse(risk.getId(), risk.getMemberProfileId(), risk.getRuleCode(),
                risk.getRiskLevel(), risk.getRiskMessage(), riskSnapshot.path("blocking").asBoolean(false),
                riskSnapshot.path("requiresConfirmation").asBoolean(false),
                metadata.path("acknowledged").asBoolean(false), longValue(metadata.get("acknowledgedBy")),
                textValue(metadata.get("acknowledgementNote")), instantValue(metadata.get("acknowledgedAt")));
    }

    private List<NutritionMealPlanItemPo> activeItems(Long mealPlanId) {
        return mealPlanItemRepository.findByMealPlanIdAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                mealPlanId, NutritionStatus.ACTIVE);
    }

    private List<NutritionRiskCheckResultPo> activeRisks(Long familyId, Long mealPlanId) {
        return riskCheckResultRepository
                .findByFamilyIdAndSourceTypeAndSourceIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        familyId, MEAL_PLAN_SOURCE_TYPE, mealPlanId, NutritionStatus.ACTIVE);
    }

    private boolean blockingRisk(NutritionRiskCheckResultPo risk) {
        return risk.getRiskLevel() == NutritionRiskLevel.HIGH
                || risk.getRiskLevel() == NutritionRiskLevel.BLOCKING
                || readObject(risk.getRiskSnapshot()).path("blocking").asBoolean(false);
    }

    private boolean acknowledged(NutritionRiskCheckResultPo risk) {
        return readObject(risk.getMetadataJson()).path("acknowledged").asBoolean(false);
    }

    private boolean validationPublishable(NutritionMealPlanPo mealPlan) {
        return readObject(mealPlan.getMetadataJson()).path("validationPublishable").asBoolean(false);
    }

    private String acknowledgementMetadata(String metadataJson, Long actorId, String note, Instant at) {
        ObjectNode metadata = objectNode(metadataJson);
        metadata.put("acknowledged", true);
        metadata.put("acknowledgedBy", actorId);
        metadata.put("acknowledgementNote", note);
        metadata.put("acknowledgedAt", at.toString());
        return writeJson(metadata);
    }

    private void saveOperation(NutritionMealPlanPo mealPlan, String operationType, Long actorId,
                               String before, String after, String note, Map<String, ?> metadata) {
        NutritionMealOperationLogPo log = new NutritionMealOperationLogPo();
        log.setFamilyId(mealPlan.getFamilyId());
        log.setMealPlanId(mealPlan.getId());
        log.setOperationType(operationType);
        log.setOperatorUserId(actorId);
        log.setBeforeSnapshot(before);
        log.setAfterSnapshot(after);
        log.setNote(note);
        log.setOperatedAt(Instant.now());
        log.setMetadataJson(writeJson(metadata));
        operationLogRepository.save(log);
    }

    private String snapshot(NutritionMealPlanPo mealPlan) {
        return snapshot(mealPlan, activeItems(mealPlan.getId()),
                activeRisks(mealPlan.getFamilyId(), mealPlan.getId()));
    }

    private String snapshot(NutritionMealPlanPo mealPlan, List<NutritionMealPlanItemPo> items,
                            List<NutritionRiskCheckResultPo> risks) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("id", mealPlan.getId());
        plan.put("status", mealPlan.getStatus());
        plan.put("version", mealPlan.getVersion());
        plan.put("confirmationCutoffAt", mealPlan.getConfirmationCutoffAt());
        plan.put("publishedAt", mealPlan.getPublishedAt());
        plan.put("nutritionSnapshot", mealPlan.getNutritionSnapshot());
        plan.put("estimatedCost", mealPlan.getEstimatedCost());
        plan.put("items", items.stream().map(this::itemSnapshot).toList());
        plan.put("risks", risks.stream().map(this::toRiskResponse).toList());
        return writeJson(plan);
    }

    private Map<String, Object> itemSnapshot(NutritionMealPlanItemPo item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", item.getId());
        snapshot.put("mealType", item.getMealType());
        snapshot.put("recipeId", item.getRecipeId());
        snapshot.put("servingCount", item.getServingCount());
        snapshot.put("sortOrder", item.getSortOrder());
        return snapshot;
    }

    private ObjectNode objectNode(String json) {
        JsonNode node = readObject(json);
        return node instanceof ObjectNode objectNode ? objectNode.deepCopy() : objectMapper.createObjectNode();
    }

    private JsonNode readObject(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (JsonProcessingException error) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition meal JSON is invalid");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition meal JSON is invalid");
        }
    }

    private Long longValue(JsonNode node) {
        return node != null && node.canConvertToLong() ? node.longValue() : null;
    }

    private String textValue(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private Instant instantValue(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(node.textValue());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }
}
