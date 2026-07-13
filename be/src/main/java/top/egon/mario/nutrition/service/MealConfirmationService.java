package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.request.MealConfirmationRequest;
import top.egon.mario.nutrition.dto.response.MealConfirmationResponse;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Application service for member meal confirmations.
 */
@Service
@RequiredArgsConstructor
@Validated
public class MealConfirmationService {

    private static final String MEAL_PLAN_SOURCE_TYPE = "MEAL_PLAN";
    private static final TypeReference<List<NutritionMealType>> MEAL_TYPE_LIST_TYPE = new TypeReference<>() {
    };

    private final NutritionMealConfirmationRepository confirmationRepository;
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
        enforceRiskGate(familyId, mealPlan.getId(), memberProfile.getId(), Boolean.TRUE.equals(request.riskConfirmed()));
        NutritionMealConfirmationPo confirmation = confirmationRepository
                .findByMealPlanIdAndMemberProfileIdAndDeletedFalse(mealPlan.getId(), memberProfile.getId())
                .orElseGet(NutritionMealConfirmationPo::new);
        applyConfirmation(confirmation, familyId, mealPlan, memberProfile, request, userId);
        mealPlanService.markConfirmingForConfirmation(mealPlan, userId);
        NutritionMealConfirmationPo saved = confirmationRepository.saveAndFlush(confirmation);
        mealPlanService.refreshConfirmedMemberCount(mealPlan);
        return toResponse(saved);
    }

    @Transactional
    public MealConfirmationResponse updateConfirmation(@NotNull Long familyId, @NotNull Long confirmationId,
                                                       @Valid @NotNull MealConfirmationRequest request,
                                                       Long actorId) {
        Long userId = requireActor(actorId);
        NutritionMealConfirmationPo confirmation = confirmationRepository
                .findByIdAndFamilyIdAndDeletedFalse(confirmationId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEAL_CONFIRMATION_NOT_FOUND", "nutrition meal confirmation not found"));
        if (!Objects.equals(confirmation.getMemberProfileId(), request.memberProfileId())) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_MEMBER_MISMATCH", "nutrition meal confirmation member mismatch");
        }
        NutritionMealPlanPo mealPlan = mealPlanService.getMealPlan(familyId, confirmation.getMealPlanId());
        validateConfirmationWindow(mealPlan);
        NutritionMemberProfilePo memberProfile = getActiveMemberProfile(familyId, confirmation.getMemberProfileId());
        accessService.requireConfirmMemberProfile(userId, familyId, memberProfile.getId());
        enforceRiskGate(familyId, mealPlan.getId(), memberProfile.getId(), Boolean.TRUE.equals(request.riskConfirmed()));
        applyConfirmation(confirmation, familyId, mealPlan, memberProfile, request, userId);
        mealPlanService.markConfirmingForConfirmation(mealPlan, userId);
        NutritionMealConfirmationPo saved = confirmationRepository.saveAndFlush(confirmation);
        mealPlanService.refreshConfirmedMemberCount(mealPlan);
        return toResponse(saved);
    }

    private void validateConfirmationWindow(NutritionMealPlanPo mealPlan) {
        if (mealPlan.getStatus() != NutritionMealPlanStatus.PUBLISHED
                && mealPlan.getStatus() != NutritionMealPlanStatus.CONFIRMING) {
            throw new NutritionException(
                    "NUTRITION_MEAL_PLAN_STATUS_INVALID", "nutrition meal plan status transition is invalid");
        }
        if (mealPlan.getConfirmationCutoffAt() != null
                && Instant.now().isAfter(mealPlan.getConfirmationCutoffAt())) {
            throw new NutritionException(
                    "NUTRITION_MEAL_CONFIRMATION_CLOSED", "nutrition meal confirmation is closed");
        }
    }

    private void applyConfirmation(NutritionMealConfirmationPo confirmation, Long familyId,
                                   NutritionMealPlanPo mealPlan, NutritionMemberProfilePo memberProfile,
                                   MealConfirmationRequest request, Long actorId) {
        boolean eatAtHome = !Boolean.FALSE.equals(request.eatAtHome());
        confirmation.setFamilyId(familyId);
        confirmation.setMealPlanId(mealPlan.getId());
        confirmation.setMemberProfileId(memberProfile.getId());
        confirmation.setConfirmedByUserId(actorId);
        confirmation.setProxyByUserId(actorId.equals(memberProfile.getBoundUserId()) ? null : actorId);
        confirmation.setConfirmationStatus(eatAtHome
                ? NutritionConfirmationStatus.CONFIRMED
                : NutritionConfirmationStatus.AWAY);
        confirmation.setEatAtHome(eatAtHome);
        confirmation.setSelectedMealTypes(writeMealTypes(normalizeMealTypes(request.selectedMealTypes())));
        confirmation.setRiskConfirmed(Boolean.TRUE.equals(request.riskConfirmed()));
        confirmation.setRiskConfirmationNote(trimToNull(request.riskConfirmationNote()));
        confirmation.setRemark(trimToNull(request.remark()));
        confirmation.setConfirmedAt(Instant.now());
        confirmation.setDeleted(false);
    }

    private void enforceRiskGate(Long familyId, Long mealPlanId, Long memberProfileId, boolean riskConfirmed) {
        List<NutritionRiskCheckResultPo> risks = riskCheckResultRepository
                .findByFamilyIdAndMemberProfileIdAndSourceTypeAndSourceIdAndStatusAndResolvedFalseAndDeletedFalseOrderByIdAsc(
                        familyId, memberProfileId, MEAL_PLAN_SOURCE_TYPE, mealPlanId, NutritionStatus.ACTIVE);
        boolean blocked = risks.stream()
                .map(NutritionRiskCheckResultPo::getRiskLevel)
                .anyMatch(riskLevel -> riskLevel == NutritionRiskLevel.HIGH
                        || riskLevel == NutritionRiskLevel.BLOCKING);
        if (blocked) {
            throw new NutritionException(
                    "NUTRITION_MEAL_RISK_BLOCKED", "nutrition meal risk blocks confirmation");
        }
        boolean requiresConfirmation = risks.stream()
                .map(NutritionRiskCheckResultPo::getRiskLevel)
                .anyMatch(riskLevel -> riskLevel == NutritionRiskLevel.MEDIUM);
        if (requiresConfirmation && !riskConfirmed) {
            throw new NutritionException(
                    "NUTRITION_MEAL_RISK_CONFIRMATION_REQUIRED",
                    "nutrition meal risk confirmation is required");
        }
    }

    private NutritionMemberProfilePo getActiveMemberProfile(Long familyId, Long memberProfileId) {
        return memberProfileRepository.findByIdAndFamilyIdAndStatusAndDeletedFalse(
                        memberProfileId, familyId, NutritionStatus.ACTIVE)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEMBER_PROFILE_NOT_FOUND", "nutrition member profile not found"));
    }

    private MealConfirmationResponse toResponse(NutritionMealConfirmationPo confirmation) {
        return new MealConfirmationResponse(confirmation.getId(), confirmation.getFamilyId(),
                confirmation.getMealPlanId(), confirmation.getMemberProfileId(),
                confirmation.getConfirmedByUserId(), confirmation.getProxyByUserId(),
                confirmation.getConfirmationStatus(), confirmation.isEatAtHome(),
                readMealTypes(confirmation.getSelectedMealTypes()), confirmation.isRiskConfirmed(),
                confirmation.getRiskConfirmationNote(), confirmation.getRemark(), confirmation.getConfirmedAt(),
                confirmation.getCreatedAt(), confirmation.getUpdatedAt());
    }

    private List<NutritionMealType> normalizeMealTypes(List<NutritionMealType> mealTypes) {
        return mealTypes == null ? List.of() : mealTypes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String writeMealTypes(List<NutritionMealType> mealTypes) {
        try {
            return objectMapper.writeValueAsString(mealTypes == null ? List.of() : mealTypes);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition meal type JSON is invalid");
        }
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
