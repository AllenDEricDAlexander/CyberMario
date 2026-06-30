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
import top.egon.mario.nutrition.dto.request.CreateMemberProfileRequest;
import top.egon.mario.nutrition.dto.request.UpdateHealthProfileRequest;
import top.egon.mario.nutrition.dto.response.HealthProfileResponse;
import top.egon.mario.nutrition.dto.response.MemberProfileResponse;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.util.List;

/**
 * Application service for family members and health profiles.
 */
@Service
@RequiredArgsConstructor
@Validated
public class MemberHealthService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final NutritionMemberProfileRepository memberProfileRepository;
    private final NutritionHealthProfileRepository healthProfileRepository;
    private final NutritionScopedRoleBindingRepository roleBindingRepository;
    private final NutritionAccessService accessService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MemberProfileResponse createMemberProfile(@NotNull Long familyId,
                                                     @Valid @NotNull CreateMemberProfileRequest request,
                                                     Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        if (request.guardianMemberId() != null) {
            getActiveMemberProfile(familyId, request.guardianMemberId());
        }
        NutritionMemberProfilePo memberProfile = new NutritionMemberProfilePo();
        memberProfile.setFamilyId(familyId);
        memberProfile.setBoundUserId(request.boundUserId());
        memberProfile.setNickname(request.nickname().trim());
        memberProfile.setGender(trimToNull(request.gender()));
        memberProfile.setBirthDate(request.birthDate());
        memberProfile.setHeightCm(request.heightCm());
        memberProfile.setWeightKg(request.weightKg());
        memberProfile.setMemberType(request.memberType());
        memberProfile.setLoginEnabled(Boolean.TRUE.equals(request.loginEnabled()));
        memberProfile.setGuardianMemberId(request.guardianMemberId());
        memberProfile.setStatus(NutritionStatus.ACTIVE);
        NutritionMemberProfilePo saved = memberProfileRepository.save(memberProfile);
        if (saved.getBoundUserId() != null) {
            upsertRoleBinding(saved.getBoundUserId(), NutritionRoleCode.MEMBER, NutritionScopeType.FAMILY,
                    familyId);
            upsertRoleBinding(saved.getBoundUserId(), NutritionRoleCode.PROFILE_OWNER,
                    NutritionScopeType.MEMBER_PROFILE, saved.getId());
        }
        return toMemberProfileResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MemberProfileResponse> listMemberProfiles(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return memberProfileRepository.findByFamilyIdAndStatusAndDeletedFalse(familyId, NutritionStatus.ACTIVE)
                .stream()
                .map(this::toMemberProfileResponse)
                .toList();
    }

    @Transactional
    public HealthProfileResponse updateHealthProfile(@NotNull Long familyId, @NotNull Long memberProfileId,
                                                     @Valid @NotNull UpdateHealthProfileRequest request,
                                                     Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveMemberProfile(familyId, memberProfileId);
        NutritionHealthProfilePo healthProfile = healthProfileRepository
                .findByFamilyIdAndMemberProfileId(familyId, memberProfileId)
                .orElseGet(NutritionHealthProfilePo::new);
        healthProfile.setFamilyId(familyId);
        healthProfile.setMemberProfileId(memberProfileId);
        healthProfile.setActivityLevel(trimToNull(request.activityLevel()));
        healthProfile.setDietGoals(writeStringList(request.dietGoals()));
        healthProfile.setAllergyTags(writeStringList(request.allergyTags()));
        healthProfile.setDislikeTags(writeStringList(request.dislikeTags()));
        healthProfile.setRestrictionTags(writeStringList(request.restrictionTags()));
        healthProfile.setTargetCalories(request.targetCalories());
        healthProfile.setTargetProtein(request.targetProtein());
        healthProfile.setTargetFat(request.targetFat());
        healthProfile.setTargetCarbs(request.targetCarbs());
        healthProfile.setTargetSodium(request.targetSodium());
        healthProfile.setTargetSugar(request.targetSugar());
        healthProfile.setDeleted(false);
        return toHealthProfileResponse(healthProfileRepository.save(healthProfile));
    }

    @Transactional(readOnly = true)
    public List<HealthProfileResponse> listFamilyHealthProfiles(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return healthProfileRepository.findActiveMemberHealthProfiles(familyId, NutritionStatus.ACTIVE).stream()
                .map(this::toHealthProfileResponse)
                .toList();
    }

    private NutritionMemberProfilePo getActiveMemberProfile(Long familyId, Long memberProfileId) {
        return memberProfileRepository.findByIdAndFamilyIdAndStatusAndDeletedFalse(
                        memberProfileId, familyId, NutritionStatus.ACTIVE)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_MEMBER_PROFILE_NOT_FOUND", "nutrition member profile not found"));
    }

    private void upsertRoleBinding(Long userId, NutritionRoleCode roleCode, NutritionScopeType scopeType,
                                   Long scopeId) {
        NutritionScopedRoleBindingPo binding = roleBindingRepository
                .findBySubjectTypeAndSubjectIdAndRoleCodeAndScopeTypeAndScopeId(
                        NutritionSubjectType.USER, userId, roleCode, scopeType, scopeId)
                .orElseGet(NutritionScopedRoleBindingPo::new);
        binding.setSubjectType(NutritionSubjectType.USER);
        binding.setSubjectId(userId);
        binding.setRoleCode(roleCode);
        binding.setScopeType(scopeType);
        binding.setScopeId(scopeId);
        binding.setStatus(NutritionStatus.ACTIVE);
        binding.setDeleted(false);
        roleBindingRepository.save(binding);
    }

    private MemberProfileResponse toMemberProfileResponse(NutritionMemberProfilePo memberProfile) {
        return new MemberProfileResponse(memberProfile.getId(), memberProfile.getFamilyId(),
                memberProfile.getBoundUserId(), memberProfile.getNickname(), memberProfile.getGender(),
                memberProfile.getBirthDate(), memberProfile.getHeightCm(), memberProfile.getWeightKg(),
                memberProfile.getMemberType(), memberProfile.isLoginEnabled(), memberProfile.getGuardianMemberId(),
                memberProfile.getStatus(), memberProfile.getCreatedAt(), memberProfile.getUpdatedAt());
    }

    private HealthProfileResponse toHealthProfileResponse(NutritionHealthProfilePo healthProfile) {
        return new HealthProfileResponse(healthProfile.getId(), healthProfile.getFamilyId(),
                healthProfile.getMemberProfileId(), healthProfile.getActivityLevel(),
                readStringList(healthProfile.getDietGoals()), readStringList(healthProfile.getAllergyTags()),
                readStringList(healthProfile.getDislikeTags()), readStringList(healthProfile.getRestrictionTags()),
                healthProfile.getTargetCalories(), healthProfile.getTargetProtein(), healthProfile.getTargetFat(),
                healthProfile.getTargetCarbs(), healthProfile.getTargetSodium(), healthProfile.getTargetSugar(),
                healthProfile.getCreatedAt(), healthProfile.getUpdatedAt());
    }

    private String writeStringList(List<String> values) {
        List<String> normalized = values == null ? List.of() : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition JSON value is invalid");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw forbidden();
        }
        return actorId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static NutritionException forbidden() {
        return new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
    }
}
