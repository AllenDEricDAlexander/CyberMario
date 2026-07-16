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
import top.egon.mario.nutrition.dto.request.AssignProfileGuardianRequest;
import top.egon.mario.nutrition.dto.request.BindMemberUserRequest;
import top.egon.mario.nutrition.dto.request.UpdateHealthProfileRequest;
import top.egon.mario.nutrition.dto.request.UpdateMemberProfileRequest;
import top.egon.mario.nutrition.dto.response.HealthProfileResponse;
import top.egon.mario.nutrition.dto.response.MemberProfileResponse;
import top.egon.mario.nutrition.dto.response.ScopedRoleBindingResponse;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.rbac.repository.UserRepository;

import java.util.List;
import java.util.Objects;

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
    private final NutritionFamilyRepository familyRepository;
    private final NutritionHealthProfileRepository healthProfileRepository;
    private final NutritionHealthTagRepository healthTagRepository;
    private final NutritionScopedRoleBindingRepository roleBindingRepository;
    private final UserRepository userRepository;
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
        if (request.boundUserId() != null) {
            requireExistingUser(request.boundUserId());
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

    @Transactional
    public MemberProfileResponse updateMemberProfile(@NotNull Long familyId, @NotNull Long memberProfileId,
                                                     @Valid @NotNull UpdateMemberProfileRequest request,
                                                     Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionMemberProfilePo memberProfile = getActiveMemberProfile(familyId, memberProfileId);
        boolean ownerProfile = isOwnerProfile(familyId, memberProfile);
        if (request.guardianMemberId() != null) {
            getActiveMemberProfile(familyId, request.guardianMemberId());
            if (memberProfileId.equals(request.guardianMemberId())) {
                throw new NutritionException(
                        "NUTRITION_GUARDIAN_INVALID", "member profile cannot guard itself");
            }
        }
        memberProfile.setNickname(ownerProfile ? ownerUsername(familyId) : request.nickname().trim());
        memberProfile.setGender(trimToNull(request.gender()));
        memberProfile.setBirthDate(request.birthDate());
        memberProfile.setHeightCm(request.heightCm());
        memberProfile.setWeightKg(request.weightKg());
        memberProfile.setMemberType(request.memberType());
        memberProfile.setLoginEnabled(ownerProfile || Boolean.TRUE.equals(request.loginEnabled()));
        memberProfile.setGuardianMemberId(request.guardianMemberId());
        return toMemberProfileResponse(memberProfileRepository.save(memberProfile));
    }

    @Transactional
    public MemberProfileResponse deactivateMemberProfile(@NotNull Long familyId, @NotNull Long memberProfileId,
                                                         Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionMemberProfilePo memberProfile = getActiveMemberProfile(familyId, memberProfileId);
        requireMutableBinding(familyId, memberProfile);
        deactivateBoundUserRoles(memberProfile);
        roleBindingRepository.findByScopeTypeAndScopeIdAndDeletedFalseOrderByIdAsc(
                        NutritionScopeType.MEMBER_PROFILE, memberProfileId)
                .stream()
                .filter(binding -> NutritionStatus.ACTIVE == binding.getStatus())
                .forEach(binding -> binding.setStatus(NutritionStatus.DISABLED));
        memberProfile.setStatus(NutritionStatus.DISABLED);
        memberProfile.setLoginEnabled(false);
        return toMemberProfileResponse(memberProfileRepository.save(memberProfile));
    }

    @Transactional
    public MemberProfileResponse bindMemberUser(@NotNull Long familyId, @NotNull Long memberProfileId,
                                                @Valid @NotNull BindMemberUserRequest request,
                                                Long actorId) {
        Long actorUserId = requireActor(actorId);
        accessService.requireManageFamily(actorUserId, familyId);
        NutritionMemberProfilePo memberProfile = getActiveMemberProfile(familyId, memberProfileId);
        requireMutableBinding(familyId, memberProfile);
        requireExistingUser(request.userId());
        if (memberProfileRepository.existsByFamilyIdAndBoundUserIdAndIdNotAndStatusAndDeletedFalse(
                familyId, request.userId(), memberProfileId, NutritionStatus.ACTIVE)) {
            throw new NutritionException(
                    "NUTRITION_USER_ALREADY_BOUND", "user is already bound to another family member profile");
        }
        if (memberProfile.getBoundUserId() != null && !memberProfile.getBoundUserId().equals(request.userId())) {
            deactivateBoundUserRoles(memberProfile);
        }
        memberProfile.setBoundUserId(request.userId());
        memberProfile.setLoginEnabled(true);
        NutritionMemberProfilePo saved = memberProfileRepository.save(memberProfile);
        upsertRoleBinding(request.userId(), NutritionRoleCode.MEMBER, NutritionScopeType.FAMILY, familyId);
        upsertRoleBinding(request.userId(), NutritionRoleCode.PROFILE_OWNER,
                NutritionScopeType.MEMBER_PROFILE, memberProfileId);
        return toMemberProfileResponse(saved);
    }

    @Transactional
    public MemberProfileResponse unbindMemberUser(@NotNull Long familyId, @NotNull Long memberProfileId,
                                                  Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionMemberProfilePo memberProfile = getActiveMemberProfile(familyId, memberProfileId);
        requireMutableBinding(familyId, memberProfile);
        deactivateBoundUserRoles(memberProfile);
        memberProfile.setBoundUserId(null);
        memberProfile.setLoginEnabled(false);
        return toMemberProfileResponse(memberProfileRepository.save(memberProfile));
    }

    @Transactional
    public ScopedRoleBindingResponse assignProfileGuardian(@NotNull Long familyId, @NotNull Long memberProfileId,
                                                           @Valid @NotNull AssignProfileGuardianRequest request,
                                                           Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveMemberProfile(familyId, memberProfileId);
        requireExistingUser(request.userId());
        NutritionScopedRoleBindingPo binding = upsertRoleBinding(request.userId(),
                NutritionRoleCode.PROFILE_GUARDIAN, NutritionScopeType.MEMBER_PROFILE, memberProfileId);
        return toScopedRoleBindingResponse(binding);
    }

    @Transactional
    public void revokeProfileGuardian(@NotNull Long familyId, @NotNull Long memberProfileId,
                                      @NotNull Long bindingId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveMemberProfile(familyId, memberProfileId);
        NutritionScopedRoleBindingPo binding = roleBindingRepository.findByIdAndDeletedFalse(bindingId)
                .filter(candidate -> NutritionSubjectType.USER == candidate.getSubjectType())
                .filter(candidate -> NutritionRoleCode.PROFILE_GUARDIAN == candidate.getRoleCode())
                .filter(candidate -> NutritionScopeType.MEMBER_PROFILE == candidate.getScopeType())
                .filter(candidate -> memberProfileId.equals(candidate.getScopeId()))
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_PROFILE_GUARDIAN_NOT_FOUND", "nutrition profile guardian not found"));
        binding.setStatus(NutritionStatus.DISABLED);
        roleBindingRepository.save(binding);
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
        validateTags("DIET_GOAL", request.dietGoals());
        validateTags("ALLERGY_TAG", request.allergyTags());
        validateTags("DISLIKE_TAG", request.dislikeTags());
        validateTags("HEALTH_TAG", request.restrictionTags());
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

    private NutritionScopedRoleBindingPo upsertRoleBinding(Long userId, NutritionRoleCode roleCode,
                                                           NutritionScopeType scopeType, Long scopeId) {
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
        return roleBindingRepository.save(binding);
    }

    private void deactivateBoundUserRoles(NutritionMemberProfilePo memberProfile) {
        Long boundUserId = memberProfile.getBoundUserId();
        if (boundUserId == null) {
            return;
        }
        deactivateRoleBinding(boundUserId, NutritionRoleCode.PROFILE_OWNER,
                NutritionScopeType.MEMBER_PROFILE, memberProfile.getId());
        if (!memberProfileRepository.existsByFamilyIdAndBoundUserIdAndIdNotAndStatusAndDeletedFalse(
                memberProfile.getFamilyId(), boundUserId, memberProfile.getId(), NutritionStatus.ACTIVE)) {
            deactivateRoleBinding(boundUserId, NutritionRoleCode.MEMBER,
                    NutritionScopeType.FAMILY, memberProfile.getFamilyId());
        }
    }

    private void deactivateRoleBinding(Long userId, NutritionRoleCode roleCode,
                                       NutritionScopeType scopeType, Long scopeId) {
        roleBindingRepository.findBySubjectTypeAndSubjectIdAndRoleCodeAndScopeTypeAndScopeId(
                        NutritionSubjectType.USER, userId, roleCode, scopeType, scopeId)
                .ifPresent(binding -> {
                    binding.setStatus(NutritionStatus.DISABLED);
                    roleBindingRepository.save(binding);
                });
    }

    private void requireExistingUser(Long userId) {
        userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_USER_NOT_FOUND", "nutrition member login user not found"));
    }

    private void validateTags(String tagType, List<String> tagCodes) {
        if (tagCodes == null) {
            return;
        }
        tagCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .filter(tagCode -> healthTagRepository
                        .findByTagTypeIgnoreCaseAndTagCodeIgnoreCaseAndDeletedFalse(tagType, tagCode)
                        .filter(tag -> NutritionStatus.ACTIVE == tag.getStatus())
                        .isEmpty())
                .findFirst()
                .ifPresent(tagCode -> {
                    throw new NutritionException("NUTRITION_HEALTH_TAG_INVALID",
                            "active nutrition health tag is required: " + tagType + "/" + tagCode);
                });
    }

    private MemberProfileResponse toMemberProfileResponse(NutritionMemberProfilePo memberProfile) {
        String boundUsername = memberProfile.getBoundUserId() == null
                ? null
                : userRepository.findByIdAndDeletedFalse(memberProfile.getBoundUserId())
                .map(user -> user.getUsername())
                .orElse(null);
        return new MemberProfileResponse(memberProfile.getId(), memberProfile.getFamilyId(),
                memberProfile.getBoundUserId(), boundUsername,
                isOwnerProfile(memberProfile.getFamilyId(), memberProfile),
                memberProfile.getNickname(), memberProfile.getGender(),
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

    private ScopedRoleBindingResponse toScopedRoleBindingResponse(NutritionScopedRoleBindingPo binding) {
        return new ScopedRoleBindingResponse(binding.getId(), binding.getSubjectType(), binding.getSubjectId(),
                binding.getRoleCode(), binding.getScopeType(), binding.getScopeId(), binding.getStatus(),
                binding.getCreatedAt(), binding.getUpdatedAt());
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

    private boolean isOwnerProfile(Long familyId, NutritionMemberProfilePo memberProfile) {
        return familyRepository.findByIdAndDeletedFalse(familyId)
                .map(family -> Objects.equals(family.getOwnerMemberProfileId(), memberProfile.getId())
                        || family.getOwnerMemberProfileId() == null
                        && Objects.equals(family.getOwnerUserId(), memberProfile.getBoundUserId()))
                .orElse(false);
    }

    private void requireMutableBinding(Long familyId, NutritionMemberProfilePo memberProfile) {
        if (isOwnerProfile(familyId, memberProfile)) {
            throw new NutritionException(
                    "NUTRITION_OWNER_PROFILE_PROTECTED", "nutrition family owner profile binding is protected");
        }
    }

    private String ownerUsername(Long familyId) {
        Long ownerUserId = familyRepository.findByIdAndDeletedFalse(familyId)
                .map(family -> family.getOwnerUserId())
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_FAMILY_NOT_FOUND", "nutrition family not found"));
        return userRepository.findByIdAndDeletedFalse(ownerUserId)
                .map(user -> user.getUsername())
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_USER_NOT_FOUND", "nutrition family owner user not found"));
    }

    private static NutritionException forbidden() {
        return new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
    }
}
