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
import top.egon.mario.nutrition.dto.request.MealConfirmationItemRequest;
import top.egon.mario.nutrition.dto.request.MealConfirmationRequest;
import top.egon.mario.nutrition.dto.response.MealConfirmationItemResponse;
import top.egon.mario.nutrition.dto.response.MealConfirmationResponse;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for locked, dish-level member meal confirmations.
 */
@Service
@RequiredArgsConstructor
@Validated
public class MealConfirmationService {

    private static final String MEAL_PLAN_SOURCE_TYPE = "MEAL_PLAN";
    private static final String MEAL_PLAN_ITEM_SOURCE_TYPE = "MEAL_PLAN_ITEM";

    private final NutritionMealConfirmationRepository confirmationRepository;
    private final NutritionMealConfirmationItemRepository confirmationItemRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionMemberProfileRepository memberProfileRepository;
    private final NutritionRiskCheckResultRepository riskCheckResultRepository;
    private final NutritionAccessService accessService;
    private final MealPlanService mealPlanService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MealConfirmationResponse confirmMeal(@NotNull Long familyId, @NotNull Long mealPlanId,
                                                @Valid @NotNull MealConfirmationRequest request,
                                                Long actorId) {
        Long userId = requireActor(actorId);
        NutritionMealPlanPo mealPlan = mealPlanService.getLockedMealPlan(familyId, mealPlanId);
        validateConfirmationWindow(mealPlan);
        NutritionMemberProfilePo memberProfile = getActiveMemberProfile(familyId, request.memberProfileId());
        accessService.requireConfirmMemberProfile(userId, familyId, memberProfile.getId());
        ValidatedItems validatedItems = validateItems(familyId, mealPlan, memberProfile, request);
        NutritionMealConfirmationPo confirmation = confirmationRepository
                .findByMealPlanIdAndMemberProfileIdAndDeletedFalse(mealPlan.getId(), memberProfile.getId())
                .orElseGet(NutritionMealConfirmationPo::new);
        return saveConfirmation(confirmation, familyId, mealPlan, memberProfile,
                request, validatedItems, userId);
    }

    @Transactional
    public MealConfirmationResponse updateConfirmation(@NotNull Long familyId, @NotNull Long confirmationId,
                                                       @Valid @NotNull MealConfirmationRequest request,
                                                       Long actorId) {
        Long userId = requireActor(actorId);
        NutritionMealConfirmationPo existing = getConfirmation(familyId, confirmationId);
        NutritionMealPlanPo mealPlan = mealPlanService.getLockedMealPlan(familyId, existing.getMealPlanId());
        NutritionMealConfirmationPo confirmation = confirmationRepository
                .findLockedByIdAndFamilyIdAndDeletedFalse(confirmationId, familyId)
                .orElseThrow(this::confirmationNotFound);
        if (!Objects.equals(confirmation.getMemberProfileId(), request.memberProfileId())) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_MEMBER_MISMATCH", "nutrition meal confirmation member mismatch");
        }
        validateConfirmationWindow(mealPlan);
        NutritionMemberProfilePo memberProfile = getActiveMemberProfile(familyId, confirmation.getMemberProfileId());
        accessService.requireConfirmMemberProfile(userId, familyId, memberProfile.getId());
        ValidatedItems validatedItems = validateItems(familyId, mealPlan, memberProfile, request);
        return saveConfirmation(confirmation, familyId, mealPlan, memberProfile,
                request, validatedItems, userId);
    }

    @Transactional(readOnly = true)
    public List<MealConfirmationResponse> listConfirmations(
            @NotNull Long familyId, @NotNull Long mealPlanId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        mealPlanService.getMealPlan(familyId, mealPlanId);
        List<NutritionMealConfirmationPo> confirmations = confirmationRepository
                .findByMealPlanIdAndDeletedFalseOrderByIdAsc(mealPlanId);
        return toResponses(confirmations);
    }

    @Transactional(readOnly = true)
    public MealConfirmationResponse getConfirmation(
            @NotNull Long familyId, @NotNull Long confirmationId, Long actorId) {
        Long userId = requireActor(actorId);
        NutritionMealConfirmationPo confirmation = getConfirmation(familyId, confirmationId);
        accessService.requireConfirmMemberProfile(userId, familyId, confirmation.getMemberProfileId());
        return toResponse(confirmation, confirmationItemRepository
                .findByConfirmationIdAndDeletedFalseOrderByIdAsc(confirmationId));
    }

    private MealConfirmationResponse saveConfirmation(
            NutritionMealConfirmationPo confirmation, Long familyId, NutritionMealPlanPo mealPlan,
            NutritionMemberProfilePo memberProfile, MealConfirmationRequest request,
            ValidatedItems validatedItems, Long actorId) {
        applyConfirmation(confirmation, familyId, mealPlan, memberProfile, request, validatedItems, actorId);
        NutritionMealConfirmationPo saved = confirmationRepository.saveAndFlush(confirmation);
        List<NutritionMealConfirmationItemPo> savedItems = replaceItems(saved, validatedItems);
        mealPlanService.markConfirmingForConfirmation(mealPlan, actorId);
        mealPlanService.refreshConfirmedMemberCount(mealPlan);
        return toResponse(saved, savedItems);
    }

    private ValidatedItems validateItems(Long familyId, NutritionMealPlanPo mealPlan,
                                         NutritionMemberProfilePo memberProfile,
                                         MealConfirmationRequest request) {
        List<MealConfirmationItemRequest> requestedItems = request.items();
        if (Boolean.FALSE.equals(request.eatAtHome())) {
            if (!requestedItems.isEmpty()) {
                throw new NutritionException(
                        "NUTRITION_MEAL_AWAY_ITEMS_INVALID", "away nutrition confirmation cannot select dishes");
            }
            return new ValidatedItems(List.of(), Map.of());
        }
        if (requestedItems.stream().noneMatch(item -> Boolean.TRUE.equals(item.selected()))) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_ITEMS_REQUIRED", "at least one nutrition meal dish is required");
        }
        Set<Long> requestedIds = new LinkedHashSet<>();
        for (MealConfirmationItemRequest requestItem : requestedItems) {
            if (requestItem == null || requestItem.mealPlanItemId() == null
                    || !requestedIds.add(requestItem.mealPlanItemId())) {
                throw new NutritionException(
                        "NUTRITION_MEAL_CONFIRMATION_ITEM_INVALID", "nutrition meal confirmation item is invalid");
            }
        }
        Map<Long, NutritionMealPlanItemPo> planItems = mealPlanItemRepository
                .findByMealPlanIdAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                        mealPlan.getId(), NutritionStatus.ACTIVE).stream()
                .collect(Collectors.toMap(NutritionMealPlanItemPo::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        if (requestedIds.stream().anyMatch(id -> !planItems.containsKey(id))) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_ITEM_NOT_FOUND",
                    "nutrition meal confirmation item does not belong to the meal plan");
        }
        enforceRiskGate(familyId, mealPlan, memberProfile.getId(), requestedItems, planItems);
        return new ValidatedItems(List.copyOf(requestedItems), Map.copyOf(planItems));
    }

    private void enforceRiskGate(Long familyId, NutritionMealPlanPo mealPlan, Long memberProfileId,
                                 List<MealConfirmationItemRequest> requestedItems,
                                 Map<Long, NutritionMealPlanItemPo> planItems) {
        List<MealConfirmationItemRequest> selectedItems = requestedItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.selected())).toList();
        List<NutritionRiskCheckResultPo> planRisks = riskCheckResultRepository
                .findByFamilyIdAndMemberProfileIdAndSourceTypeAndSourceIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        familyId, memberProfileId, MEAL_PLAN_SOURCE_TYPE, mealPlan.getId(), NutritionStatus.ACTIVE);
        List<Long> selectedItemIds = selectedItems.stream()
                .map(MealConfirmationItemRequest::mealPlanItemId).toList();
        List<NutritionRiskCheckResultPo> itemRisks = selectedItemIds.isEmpty() ? List.of()
                : riskCheckResultRepository
                .findByFamilyIdAndMemberProfileIdAndSourceTypeAndSourceIdInAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        familyId, memberProfileId, MEAL_PLAN_ITEM_SOURCE_TYPE,
                        selectedItemIds, NutritionStatus.ACTIVE);
        Map<Long, List<NutritionRiskCheckResultPo>> itemRisksById = itemRisks.stream()
                .collect(Collectors.groupingBy(NutritionRiskCheckResultPo::getSourceId));
        for (MealConfirmationItemRequest itemRequest : selectedItems) {
            NutritionMealPlanItemPo planItem = planItems.get(itemRequest.mealPlanItemId());
            List<NutritionRiskCheckResultPo> applicable = new ArrayList<>(
                    itemRisksById.getOrDefault(planItem.getId(), List.of()));
            planRisks.stream().filter(risk -> appliesToItem(risk, planItem)).forEach(applicable::add);
            if (applicable.stream().anyMatch(this::blockingRisk)) {
                throw new NutritionException(
                        "NUTRITION_MEAL_RISK_BLOCKED", "nutrition meal risk blocks the selected dish");
            }
            if (applicable.stream().anyMatch(this::requiresAcknowledgement)
                    && !Boolean.TRUE.equals(itemRequest.riskAcknowledged())) {
                throw new NutritionException(
                        "NUTRITION_MEAL_RISK_CONFIRMATION_REQUIRED",
                        "nutrition meal risk confirmation is required for the selected dish");
            }
            if (applicable.stream().anyMatch(this::requiresNote)
                    && !StringUtils.hasText(itemRequest.adjustmentNote())) {
                throw new NutritionException(
                        "NUTRITION_MEAL_RISK_NOTE_REQUIRED",
                        "nutrition meal risk acknowledgement note is required");
            }
        }
    }

    private boolean appliesToItem(NutritionRiskCheckResultPo risk, NutritionMealPlanItemPo item) {
        JsonNode snapshot = readObject(risk.getRiskSnapshot());
        JsonNode evidence = snapshot.path("evidence");
        Set<Long> itemIds = longValues(snapshot, evidence, "mealPlanItemId", "mealPlanItemIds");
        Set<Long> recipeIds = longValues(snapshot, evidence, "recipeId", "recipeIds");
        if (!itemIds.isEmpty()) {
            return itemIds.contains(item.getId());
        }
        if (!recipeIds.isEmpty()) {
            return item.getRecipeId() != null && recipeIds.contains(item.getRecipeId());
        }
        return true;
    }

    private Set<Long> longValues(JsonNode snapshot, JsonNode evidence, String singleName, String listName) {
        Set<Long> values = new LinkedHashSet<>();
        addLong(values, snapshot.get(singleName));
        addLong(values, evidence.get(singleName));
        addLongs(values, snapshot.get(listName));
        addLongs(values, evidence.get(listName));
        return values;
    }

    private void addLong(Set<Long> values, JsonNode node) {
        if (node != null && node.canConvertToLong()) {
            values.add(node.longValue());
        }
    }

    private void addLongs(Set<Long> values, JsonNode node) {
        if (node != null && node.isArray()) {
            node.forEach(value -> addLong(values, value));
        }
    }

    private boolean blockingRisk(NutritionRiskCheckResultPo risk) {
        return risk.getRiskLevel() == NutritionRiskLevel.HIGH
                || risk.getRiskLevel() == NutritionRiskLevel.BLOCKING
                || readObject(risk.getRiskSnapshot()).path("blocking").asBoolean(false);
    }

    private boolean requiresAcknowledgement(NutritionRiskCheckResultPo risk) {
        return risk.getRiskLevel() == NutritionRiskLevel.MEDIUM
                || readObject(risk.getRiskSnapshot()).path("requiresConfirmation").asBoolean(false);
    }

    private boolean requiresNote(NutritionRiskCheckResultPo risk) {
        JsonNode snapshot = readObject(risk.getRiskSnapshot());
        return snapshot.path("requiresNote").asBoolean(false)
                || snapshot.path("evidence").path("requiresNote").asBoolean(false);
    }

    private void validateConfirmationWindow(NutritionMealPlanPo mealPlan) {
        if (mealPlan.getStatus() != NutritionMealPlanStatus.PUBLISHED
                && mealPlan.getStatus() != NutritionMealPlanStatus.CONFIRMING) {
            throw new NutritionException(
                    "NUTRITION_MEAL_PLAN_STATUS_INVALID", "nutrition meal plan status transition is invalid");
        }
        if (mealPlan.getConfirmationCutoffAt() != null
                && !Instant.now().isBefore(mealPlan.getConfirmationCutoffAt())) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_CLOSED", "nutrition meal confirmation is closed");
        }
    }

    private void applyConfirmation(NutritionMealConfirmationPo confirmation, Long familyId,
                                   NutritionMealPlanPo mealPlan, NutritionMemberProfilePo memberProfile,
                                   MealConfirmationRequest request, ValidatedItems validatedItems, Long actorId) {
        boolean eatAtHome = request.eatAtHome();
        List<NutritionMealPlanItemPo> selectedItems = validatedItems.requests().stream()
                .filter(item -> Boolean.TRUE.equals(item.selected()))
                .map(item -> validatedItems.planItems().get(item.mealPlanItemId()))
                .toList();
        confirmation.setFamilyId(familyId);
        confirmation.setMealPlanId(mealPlan.getId());
        confirmation.setMemberProfileId(memberProfile.getId());
        confirmation.setConfirmedByUserId(actorId);
        confirmation.setProxyByUserId(actorId.equals(memberProfile.getBoundUserId()) ? null : actorId);
        confirmation.setConfirmationStatus(eatAtHome
                ? NutritionConfirmationStatus.CONFIRMED : NutritionConfirmationStatus.AWAY);
        confirmation.setEatAtHome(eatAtHome);
        confirmation.setSelectedMealTypes(writeJson(selectedItems.stream()
                .map(NutritionMealPlanItemPo::getMealType).distinct().toList()));
        confirmation.setRiskConfirmed(validatedItems.requests().stream()
                .filter(item -> Boolean.TRUE.equals(item.selected()))
                .anyMatch(item -> Boolean.TRUE.equals(item.riskAcknowledged())));
        confirmation.setRiskConfirmationNote(validatedItems.requests().stream()
                .map(MealConfirmationItemRequest::adjustmentNote)
                .filter(StringUtils::hasText).map(String::trim).findFirst().orElse(null));
        confirmation.setRemark(trimToNull(request.remark()));
        confirmation.setConfirmedAt(Instant.now());
        confirmation.setDeleted(false);
    }

    private List<NutritionMealConfirmationItemPo> replaceItems(
            NutritionMealConfirmationPo confirmation, ValidatedItems validatedItems) {
        List<NutritionMealConfirmationItemPo> current = confirmationItemRepository
                .findByConfirmationIdAndDeletedFalseOrderByIdAsc(confirmation.getId());
        if (!current.isEmpty()) {
            confirmationItemRepository.deleteAllInBatch(current);
            confirmationItemRepository.flush();
        }
        List<NutritionMealConfirmationItemPo> replacements = validatedItems.requests().stream().map(request -> {
            NutritionMealPlanItemPo planItem = validatedItems.planItems().get(request.mealPlanItemId());
            NutritionMealConfirmationItemPo item = new NutritionMealConfirmationItemPo();
            item.setFamilyId(confirmation.getFamilyId());
            item.setConfirmationId(confirmation.getId());
            item.setMealPlanItemId(planItem.getId());
            item.setMealType(planItem.getMealType());
            item.setSelected(Boolean.TRUE.equals(request.selected()));
            item.setServingCount(request.servingCount());
            item.setRiskAcknowledged(Boolean.TRUE.equals(request.riskAcknowledged()));
            item.setAdjustmentNote(trimToNull(request.adjustmentNote()));
            item.setDeleted(false);
            return item;
        }).toList();
        return replacements.isEmpty() ? List.of() : confirmationItemRepository.saveAllAndFlush(replacements);
    }

    private List<MealConfirmationResponse> toResponses(List<NutritionMealConfirmationPo> confirmations) {
        if (confirmations.isEmpty()) {
            return List.of();
        }
        Map<Long, List<NutritionMealConfirmationItemPo>> itemsByConfirmationId = confirmationItemRepository
                .findByConfirmationIdInAndDeletedFalseOrderByIdAsc(
                        confirmations.stream().map(NutritionMealConfirmationPo::getId).toList()).stream()
                .collect(Collectors.groupingBy(NutritionMealConfirmationItemPo::getConfirmationId));
        return confirmations.stream().map(confirmation -> toResponse(confirmation,
                itemsByConfirmationId.getOrDefault(confirmation.getId(), List.of()))).toList();
    }

    private MealConfirmationResponse toResponse(
            NutritionMealConfirmationPo confirmation, List<NutritionMealConfirmationItemPo> items) {
        return new MealConfirmationResponse(confirmation.getId(), confirmation.getFamilyId(),
                confirmation.getMealPlanId(), confirmation.getMemberProfileId(),
                confirmation.getConfirmedByUserId(), confirmation.getProxyByUserId(),
                confirmation.getConfirmationStatus(), confirmation.isEatAtHome(),
                items.stream().map(this::toItemResponse).toList(), confirmation.getRemark(),
                confirmation.getConfirmedAt(), confirmation.getCreatedAt(), confirmation.getUpdatedAt());
    }

    private MealConfirmationItemResponse toItemResponse(NutritionMealConfirmationItemPo item) {
        return new MealConfirmationItemResponse(item.getId(), item.getConfirmationId(), item.getMealPlanItemId(),
                item.getMealType(), item.isSelected(), item.getServingCount(), item.isRiskAcknowledged(),
                item.getAdjustmentNote(), item.getVersion());
    }

    private NutritionMealConfirmationPo getConfirmation(Long familyId, Long confirmationId) {
        return confirmationRepository.findByIdAndFamilyIdAndDeletedFalse(confirmationId, familyId)
                .orElseThrow(this::confirmationNotFound);
    }

    private NutritionMemberProfilePo getActiveMemberProfile(Long familyId, Long memberProfileId) {
        return memberProfileRepository.findByIdAndFamilyIdAndStatusAndDeletedFalse(
                        memberProfileId, familyId, NutritionStatus.ACTIVE)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEMBER_PROFILE_NOT_FOUND", "nutrition member profile not found"));
    }

    private JsonNode readObject(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (JsonProcessingException error) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition risk JSON is invalid");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition confirmation JSON is invalid");
        }
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private NutritionException confirmationNotFound() {
        return new NutritionException(
                "NUTRITION_MEAL_CONFIRMATION_NOT_FOUND", "nutrition meal confirmation not found");
    }

    private record ValidatedItems(
            List<MealConfirmationItemRequest> requests,
            Map<Long, NutritionMealPlanItemPo> planItems
    ) {
    }
}
