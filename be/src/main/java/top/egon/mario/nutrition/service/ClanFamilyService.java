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
import top.egon.mario.nutrition.dto.request.CreateClanRequest;
import top.egon.mario.nutrition.dto.request.CreateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateScopedRoleBindingRequest;
import top.egon.mario.nutrition.dto.request.UpdateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.UpdateFamilySettingsRequest;
import top.egon.mario.nutrition.dto.response.ClanResponse;
import top.egon.mario.nutrition.dto.response.ClanFamilyRelationResponse;
import top.egon.mario.nutrition.dto.response.DataGrantResponse;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.ScopedRoleBindingResponse;
import top.egon.mario.nutrition.po.NutritionClanFamilyPo;
import top.egon.mario.nutrition.po.NutritionClanPo;
import top.egon.mario.nutrition.po.NutritionDataGrantPo;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.repository.UserRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Application service for nutrition clans, families and scoped grants.
 */
@Service
@RequiredArgsConstructor
@Validated
public class ClanFamilyService {

    private static final String GRANTEE_TYPE_USER = "USER";
    private static final String GRANTEE_TYPE_CLAN = "CLAN";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final Set<NutritionRoleCode> CLAN_READ_ROLES = Set.copyOf(EnumSet.of(
            NutritionRoleCode.CLAN_ADMIN,
            NutritionRoleCode.CLAN_MEMBER
    ));

    private final NutritionClanRepository clanRepository;
    private final NutritionFamilyRepository familyRepository;
    private final NutritionClanFamilyRepository clanFamilyRepository;
    private final NutritionMemberProfileRepository memberProfileRepository;
    private final NutritionScopedRoleBindingRepository roleBindingRepository;
    private final NutritionDataGrantRepository dataGrantRepository;
    private final NutritionAccessService accessService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ClanResponse createClan(@Valid @NotNull CreateClanRequest request, Long actorId) {
        Long userId = requireActor(actorId);
        NutritionClanPo clan = new NutritionClanPo();
        clan.setName(request.name().trim());
        clan.setOwnerUserId(userId);
        clan.setStatus(NutritionStatus.ACTIVE);
        NutritionClanPo saved = clanRepository.save(clan);
        upsertRoleBinding(userId, NutritionRoleCode.CLAN_ADMIN, NutritionScopeType.CLAN, saved.getId());
        return toClanResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ClanResponse> listAccessibleClans(Long actorId) {
        Long userId = requireActor(actorId);
        List<NutritionClanPo> ownedClans = clanRepository
                .findByOwnerUserIdAndStatusAndDeletedFalse(userId, NutritionStatus.ACTIVE);
        List<Long> roleClanIds = roleBindingRepository
                .findBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndStatusAndDeletedFalse(
                        NutritionSubjectType.USER, userId, CLAN_READ_ROLES, NutritionScopeType.CLAN,
                        NutritionStatus.ACTIVE)
                .stream()
                .map(NutritionScopedRoleBindingPo::getScopeId)
                .toList();
        List<NutritionClanPo> roleClans = roleClanIds.isEmpty()
                ? List.of()
                : clanRepository.findByIdInAndStatusAndDeletedFalse(roleClanIds, NutritionStatus.ACTIVE);
        return Stream.concat(ownedClans.stream(), roleClans.stream())
                .collect(Collectors.toMap(NutritionClanPo::getId, clan -> clan, (left, right) -> left,
                        LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(NutritionClanPo::getId).reversed())
                .map(this::toClanResponse)
                .toList();
    }

    @Transactional
    public FamilyResponse createFamily(@Valid @NotNull CreateFamilyRequest request, Long actorId) {
        return createFamily(request, actorId, null);
    }

    @Transactional
    public FamilyResponse createFamily(@Valid @NotNull CreateFamilyRequest request, Long actorId,
                                       String actorUsername) {
        Long userId = requireActor(actorId);
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(request.name().trim());
        family.setOwnerUserId(userId);
        family.setRegion(trimToNull(request.region()));
        family.setCurrency(StringUtils.hasText(request.currency()) ? request.currency().trim() : "CNY");
        family.setDefaultMealTypes(writeStringList(request.defaultMealTypes()));
        family.setStatus(NutritionStatus.ACTIVE);
        NutritionFamilyPo savedFamily = familyRepository.save(family);
        upsertRoleBinding(userId, NutritionRoleCode.FAMILY_ADMIN, NutritionScopeType.FAMILY, savedFamily.getId());

        NutritionMemberProfilePo ownerProfile = new NutritionMemberProfilePo();
        ownerProfile.setFamilyId(savedFamily.getId());
        ownerProfile.setBoundUserId(userId);
        ownerProfile.setNickname(resolveOwnerUsername(userId, actorUsername, request));
        ownerProfile.setMemberType(NutritionMemberType.ADULT);
        ownerProfile.setLoginEnabled(true);
        ownerProfile.setStatus(NutritionStatus.ACTIVE);
        NutritionMemberProfilePo savedOwnerProfile = memberProfileRepository.save(ownerProfile);
        savedFamily.setOwnerMemberProfileId(savedOwnerProfile.getId());
        familyRepository.save(savedFamily);
        upsertRoleBinding(userId, NutritionRoleCode.PROFILE_OWNER, NutritionScopeType.MEMBER_PROFILE,
                savedOwnerProfile.getId());
        return toFamilyResponse(savedFamily, savedOwnerProfile.getId());
    }

    @Transactional(readOnly = true)
    public List<FamilyResponse> listAccessibleFamilies(Long actorId) {
        Long userId = requireActor(actorId);
        return familyRepository.findAll().stream()
                .filter(family -> !family.isDeleted())
                .filter(family -> NutritionStatus.ACTIVE == family.getStatus())
                .filter(family -> accessService.canReadFamily(userId, family.getId()))
                .sorted(Comparator.comparing(NutritionFamilyPo::getId).reversed())
                .map(this::toFamilyResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FamilyResponse getFamilySettings(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return toFamilyResponse(getActiveFamily(familyId));
    }

    @Transactional
    public FamilyResponse updateFamilySettings(@NotNull Long familyId,
                                               @Valid @NotNull UpdateFamilySettingsRequest request,
                                               Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionFamilyPo family = getLockedActiveFamily(familyId);
        family.setRegion(trimToNull(request.region()));
        family.setCurrency(request.currency() == null
                ? "CNY"
                : request.currency().trim().toUpperCase(Locale.ROOT));
        family.setDefaultMealTypes(writeStringList(request.defaultMealTypes() == null
                ? List.of()
                : request.defaultMealTypes().stream().map(Enum::name).toList()));
        family.setAiEnabled(Boolean.TRUE.equals(request.aiEnabled()));
        family.setAiGenerateTime(request.aiGenerateTime());
        family.setHealthAlertEnabled(Boolean.TRUE.equals(request.healthAlertEnabled()));
        family.setBudgetEnabled(Boolean.TRUE.equals(request.budgetEnabled()));
        return toFamilyResponse(familyRepository.save(family));
    }

    @Transactional(readOnly = true)
    public List<ScopedRoleBindingResponse> listRoleBindings(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveFamily(familyId);
        List<NutritionScopedRoleBindingPo> familyBindings = roleBindingRepository
                .findByScopeTypeAndScopeIdAndDeletedFalseOrderByIdAsc(NutritionScopeType.FAMILY, familyId);
        List<Long> memberProfileIds = memberProfileRepository.findByFamilyIdAndDeletedFalseOrderByIdAsc(familyId)
                .stream()
                .map(NutritionMemberProfilePo::getId)
                .toList();
        List<NutritionScopedRoleBindingPo> memberBindings = memberProfileIds.isEmpty()
                ? List.of()
                : roleBindingRepository.findByScopeTypeAndScopeIdInAndDeletedFalseOrderByIdAsc(
                NutritionScopeType.MEMBER_PROFILE, memberProfileIds);
        return Stream.concat(familyBindings.stream(), memberBindings.stream())
                .sorted(Comparator.comparing(NutritionScopedRoleBindingPo::getId))
                .map(this::toScopedRoleBindingResponse)
                .toList();
    }

    @Transactional
    public ScopedRoleBindingResponse createRoleBinding(@NotNull Long familyId,
                                                       @Valid @NotNull CreateScopedRoleBindingRequest request,
                                                       Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getLockedActiveFamily(familyId);
        validateRoleBinding(familyId, request);
        return toScopedRoleBindingResponse(upsertRoleBinding(request.subjectType(), request.subjectId(),
                request.roleCode(), request.scopeType(), request.scopeId()));
    }

    @Transactional
    public ScopedRoleBindingResponse updateRoleBinding(@NotNull Long familyId, @NotNull Long bindingId,
                                                       @Valid @NotNull CreateScopedRoleBindingRequest request,
                                                       Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getLockedActiveFamily(familyId);
        NutritionScopedRoleBindingPo binding = getFamilyRoleBinding(familyId, bindingId);
        validateRoleBinding(familyId, request);
        protectLastFamilyAdmin(binding, request.roleCode(), request.scopeType(), request.scopeId());
        binding.setSubjectType(request.subjectType());
        binding.setSubjectId(request.subjectId());
        binding.setRoleCode(request.roleCode());
        binding.setScopeType(request.scopeType());
        binding.setScopeId(request.scopeId());
        binding.setStatus(NutritionStatus.ACTIVE);
        binding.setDeleted(false);
        return toScopedRoleBindingResponse(roleBindingRepository.save(binding));
    }

    @Transactional
    public void revokeRoleBinding(@NotNull Long familyId, @NotNull Long bindingId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getLockedActiveFamily(familyId);
        NutritionScopedRoleBindingPo binding = getFamilyRoleBinding(familyId, bindingId);
        protectLastFamilyAdmin(binding, null, null, null);
        binding.setStatus(NutritionStatus.DISABLED);
        roleBindingRepository.save(binding);
    }

    @Transactional(readOnly = true)
    public List<DataGrantResponse> listDataGrants(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveFamily(familyId);
        return dataGrantRepository.findByFamilyIdAndDeletedFalseOrderByIdAsc(familyId).stream()
                .map(this::toDataGrantResponse)
                .toList();
    }

    @Transactional
    public DataGrantResponse createDataGrant(@NotNull Long familyId,
                                             @Valid @NotNull CreateDataGrantRequest request,
                                             Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveFamily(familyId);
        validateDataGrant(familyId, request.memberProfileId(), request.granteeType(), request.granteeId());
        NutritionDataGrantPo grant = new NutritionDataGrantPo();
        grant.setFamilyId(familyId);
        grant.setMemberProfileId(request.memberProfileId());
        grant.setGranteeType(request.granteeType());
        grant.setGranteeId(request.granteeId());
        grant.setDataScope(request.dataScope());
        grant.setPermissionLevel(request.permissionLevel());
        grant.setExpiresAt(request.expiresAt());
        grant.setStatus(NutritionStatus.ACTIVE);
        return toDataGrantResponse(dataGrantRepository.save(grant));
    }

    @Transactional
    public DataGrantResponse updateDataGrant(@NotNull Long familyId, @NotNull Long grantId,
                                             @Valid @NotNull UpdateDataGrantRequest request,
                                             Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveFamily(familyId);
        NutritionDataGrantPo grant = getActiveDataGrant(familyId, grantId);
        grant.setPermissionLevel(request.permissionLevel());
        grant.setExpiresAt(request.expiresAt());
        return toDataGrantResponse(dataGrantRepository.save(grant));
    }

    @Transactional
    public void revokeDataGrant(@NotNull Long familyId, @NotNull Long grantId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveFamily(familyId);
        NutritionDataGrantPo grant = getActiveDataGrant(familyId, grantId);
        grant.setStatus(NutritionStatus.DISABLED);
        dataGrantRepository.save(grant);
    }

    @Transactional(readOnly = true)
    public List<ClanFamilyRelationResponse> listClanFamilyRelations(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveFamily(familyId);
        return clanFamilyRepository.findByFamilyIdAndDeletedFalseOrderByIdAsc(familyId).stream()
                .map(this::toClanFamilyRelationResponse)
                .toList();
    }

    @Transactional
    public void removeClanFamilyRelation(@NotNull Long familyId, @NotNull Long relationId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        getActiveFamily(familyId);
        NutritionClanFamilyPo relation = clanFamilyRepository.findByIdAndFamilyIdAndDeletedFalse(relationId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_CLAN_RELATION_NOT_FOUND", "nutrition clan family relation not found"));
        relation.setRelationStatus(NutritionStatus.DISABLED);
        clanFamilyRepository.save(relation);
    }

    @Transactional
    public FamilyResponse associateClanFamily(@NotNull Long clanId, @NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        NutritionClanPo clan = getActiveClan(clanId);
        NutritionFamilyPo family = getActiveFamily(familyId);
        requireManageClan(userId, clan.getId());
        accessService.requireManageFamily(userId, family.getId());
        NutritionClanFamilyPo relation = clanFamilyRepository.findByClanIdAndFamilyId(clan.getId(), family.getId())
                .orElseGet(NutritionClanFamilyPo::new);
        relation.setClanId(clan.getId());
        relation.setFamilyId(family.getId());
        relation.setRelationStatus(NutritionStatus.ACTIVE);
        relation.setJoinedAt(Instant.now());
        relation.setDeleted(false);
        clanFamilyRepository.save(relation);
        return toFamilyResponse(family);
    }

    @Transactional
    public void grantFamilyDataToUser(@NotNull Long familyId, @NotNull Long granteeUserId,
                                      @NotNull NutritionGrantDataScope dataScope,
                                      NutritionGrantPermissionLevel permissionLevel,
                                      Long actorId) {
        Long userId = requireActor(actorId);
        getActiveFamily(familyId);
        accessService.requireManageFamily(userId, familyId);
        upsertDataGrant(familyId, null, GRANTEE_TYPE_USER, granteeUserId, dataScope, permissionLevel);
    }

    @Transactional
    public void grantFamilyDataToClan(@NotNull Long familyId, @NotNull Long clanId,
                                      @NotNull NutritionGrantDataScope dataScope,
                                      NutritionGrantPermissionLevel permissionLevel,
                                      Long actorId) {
        Long userId = requireActor(actorId);
        getActiveFamily(familyId);
        getActiveClan(clanId);
        accessService.requireManageFamily(userId, familyId);
        upsertDataGrant(familyId, null, GRANTEE_TYPE_CLAN, clanId, dataScope, permissionLevel);
    }

    private NutritionScopedRoleBindingPo upsertRoleBinding(Long userId, NutritionRoleCode roleCode,
                                                           NutritionScopeType scopeType, Long scopeId) {
        return upsertRoleBinding(NutritionSubjectType.USER, userId, roleCode, scopeType, scopeId);
    }

    private NutritionScopedRoleBindingPo upsertRoleBinding(NutritionSubjectType subjectType, Long subjectId,
                                                           NutritionRoleCode roleCode,
                                                           NutritionScopeType scopeType, Long scopeId) {
        NutritionScopedRoleBindingPo binding = roleBindingRepository
                .findBySubjectTypeAndSubjectIdAndRoleCodeAndScopeTypeAndScopeId(
                        subjectType, subjectId, roleCode, scopeType, scopeId)
                .orElseGet(NutritionScopedRoleBindingPo::new);
        binding.setSubjectType(subjectType);
        binding.setSubjectId(subjectId);
        binding.setRoleCode(roleCode);
        binding.setScopeType(scopeType);
        binding.setScopeId(scopeId);
        binding.setStatus(NutritionStatus.ACTIVE);
        binding.setDeleted(false);
        return roleBindingRepository.save(binding);
    }

    private void upsertDataGrant(Long familyId, Long memberProfileId, String granteeType, Long granteeId,
                                 NutritionGrantDataScope dataScope,
                                 NutritionGrantPermissionLevel permissionLevel) {
        NutritionDataGrantPo grant = dataGrantRepository
                .findByFamilyIdAndGranteeTypeAndGranteeIdAndDataScopeOrderByIdAsc(
                        familyId, granteeType, granteeId, dataScope)
                .stream()
                .findFirst()
                .orElseGet(NutritionDataGrantPo::new);
        grant.setFamilyId(familyId);
        grant.setMemberProfileId(memberProfileId);
        grant.setGranteeType(granteeType);
        grant.setGranteeId(granteeId);
        grant.setDataScope(dataScope);
        grant.setPermissionLevel(permissionLevel == null ? NutritionGrantPermissionLevel.READ : permissionLevel);
        grant.setExpiresAt(null);
        grant.setStatus(NutritionStatus.ACTIVE);
        grant.setDeleted(false);
        dataGrantRepository.save(grant);
    }

    private void requireManageClan(Long userId, Long clanId) {
        if (!clanRepository.existsByIdAndOwnerUserIdAndStatusAndDeletedFalse(
                clanId, userId, NutritionStatus.ACTIVE)
                && !roleBindingRepository.existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, userId, List.of(NutritionRoleCode.CLAN_ADMIN),
                NutritionScopeType.CLAN, clanId, NutritionStatus.ACTIVE)) {
            throw forbidden();
        }
    }

    private NutritionClanPo getActiveClan(Long clanId) {
        return clanRepository.findByIdAndDeletedFalse(clanId)
                .filter(clan -> NutritionStatus.ACTIVE == clan.getStatus())
                .orElseThrow(() -> new NutritionException("NUTRITION_CLAN_NOT_FOUND", "nutrition clan not found"));
    }

    private NutritionFamilyPo getActiveFamily(Long familyId) {
        return familyRepository.findByIdAndDeletedFalse(familyId)
                .filter(family -> NutritionStatus.ACTIVE == family.getStatus())
                .orElseThrow(() -> new NutritionException("NUTRITION_FAMILY_NOT_FOUND", "nutrition family not found"));
    }

    private NutritionFamilyPo getLockedActiveFamily(Long familyId) {
        return familyRepository.findLockedByIdAndDeletedFalse(familyId)
                .filter(family -> NutritionStatus.ACTIVE == family.getStatus())
                .orElseThrow(() -> new NutritionException("NUTRITION_FAMILY_NOT_FOUND", "nutrition family not found"));
    }

    private NutritionScopedRoleBindingPo getFamilyRoleBinding(Long familyId, Long bindingId) {
        NutritionScopedRoleBindingPo binding = roleBindingRepository.findByIdAndDeletedFalse(bindingId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_ROLE_BINDING_NOT_FOUND", "nutrition role binding not found"));
        if (NutritionScopeType.FAMILY == binding.getScopeType() && familyId.equals(binding.getScopeId())) {
            return binding;
        }
        if (NutritionScopeType.MEMBER_PROFILE == binding.getScopeType()
                && memberProfileRepository.findByIdAndFamilyIdAndDeletedFalse(binding.getScopeId(), familyId)
                .isPresent()) {
            return binding;
        }
        throw new NutritionException("NUTRITION_ROLE_BINDING_NOT_FOUND", "nutrition role binding not found");
    }

    private NutritionDataGrantPo getActiveDataGrant(Long familyId, Long grantId) {
        return dataGrantRepository.findByIdAndFamilyIdAndDeletedFalse(grantId, familyId)
                .filter(grant -> NutritionStatus.ACTIVE == grant.getStatus())
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_DATA_GRANT_NOT_FOUND", "nutrition data grant not found"));
    }

    private void validateRoleBinding(Long familyId, CreateScopedRoleBindingRequest request) {
        if (NutritionSubjectType.USER != request.subjectType()) {
            throw roleScopeInvalid();
        }
        boolean familyRole = Set.of(NutritionRoleCode.FAMILY_ADMIN, NutritionRoleCode.COOK,
                        NutritionRoleCode.MEMBER, NutritionRoleCode.GUARDIAN)
                .contains(request.roleCode());
        if (familyRole && NutritionScopeType.FAMILY == request.scopeType()
                && familyId.equals(request.scopeId())) {
            return;
        }
        boolean profileRole = Set.of(NutritionRoleCode.PROFILE_OWNER, NutritionRoleCode.PROFILE_GUARDIAN)
                .contains(request.roleCode());
        if (profileRole && NutritionScopeType.MEMBER_PROFILE == request.scopeType()
                && memberProfileRepository.findByIdAndFamilyIdAndStatusAndDeletedFalse(
                request.scopeId(), familyId, NutritionStatus.ACTIVE).isPresent()) {
            return;
        }
        throw roleScopeInvalid();
    }

    private void protectLastFamilyAdmin(NutritionScopedRoleBindingPo binding,
                                        NutritionRoleCode replacementRole,
                                        NutritionScopeType replacementScope,
                                        Long replacementScopeId) {
        boolean removesFamilyAdmin = NutritionRoleCode.FAMILY_ADMIN == binding.getRoleCode()
                && NutritionScopeType.FAMILY == binding.getScopeType()
                && NutritionStatus.ACTIVE == binding.getStatus()
                && !(NutritionRoleCode.FAMILY_ADMIN == replacementRole
                && NutritionScopeType.FAMILY == replacementScope
                && binding.getScopeId().equals(replacementScopeId));
        if (removesFamilyAdmin && roleBindingRepository
                .countByRoleCodeAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                        NutritionRoleCode.FAMILY_ADMIN, NutritionScopeType.FAMILY,
                        binding.getScopeId(), NutritionStatus.ACTIVE) <= 1) {
            throw new NutritionException(
                    "NUTRITION_LAST_FAMILY_ADMIN", "the last family administrator cannot be removed");
        }
    }

    private void validateDataGrant(Long familyId, Long memberProfileId, String granteeType, Long granteeId) {
        if (memberProfileId != null) {
            memberProfileRepository.findByIdAndFamilyIdAndStatusAndDeletedFalse(
                            memberProfileId, familyId, NutritionStatus.ACTIVE)
                    .orElseThrow(() -> new NutritionException(
                            "NUTRITION_MEMBER_PROFILE_NOT_FOUND", "nutrition member profile not found"));
        }
        if (GRANTEE_TYPE_USER.equals(granteeType)) {
            return;
        }
        if (GRANTEE_TYPE_CLAN.equals(granteeType)
                && clanRepository.existsByIdAndStatusAndDeletedFalse(granteeId, NutritionStatus.ACTIVE)
                && clanFamilyRepository.existsByClanIdAndFamilyIdAndRelationStatusAndDeletedFalse(
                granteeId, familyId, NutritionStatus.ACTIVE)) {
            return;
        }
        throw new NutritionException("NUTRITION_GRANTEE_INVALID", "nutrition data grant grantee is invalid");
    }

    private NutritionException roleScopeInvalid() {
        return new NutritionException("NUTRITION_ROLE_SCOPE_INVALID", "nutrition role scope is invalid");
    }

    private ClanResponse toClanResponse(NutritionClanPo clan) {
        return new ClanResponse(clan.getId(), clan.getName(), clan.getOwnerUserId(), clan.getStatus(),
                clan.getCreatedAt(), clan.getUpdatedAt());
    }

    private FamilyResponse toFamilyResponse(NutritionFamilyPo family) {
        Long ownerMemberProfileId = family.getOwnerMemberProfileId();
        if (ownerMemberProfileId == null) {
            ownerMemberProfileId = memberProfileRepository
                    .findByFamilyIdAndBoundUserIdAndStatusAndDeletedFalse(
                            family.getId(), family.getOwnerUserId(), NutritionStatus.ACTIVE)
                    .map(NutritionMemberProfilePo::getId)
                    .orElse(null);
        }
        return toFamilyResponse(family, ownerMemberProfileId);
    }

    private FamilyResponse toFamilyResponse(NutritionFamilyPo family, Long ownerMemberProfileId) {
        return new FamilyResponse(family.getId(), family.getName(), family.getOwnerUserId(), family.getRegion(),
                family.getCurrency(), readStringList(family.getDefaultMealTypes()), family.isAiEnabled(),
                family.getAiGenerateTime(), family.isHealthAlertEnabled(), family.isBudgetEnabled(),
                family.getStatus(), ownerMemberProfileId, family.getCreatedAt(), family.getUpdatedAt());
    }

    private ScopedRoleBindingResponse toScopedRoleBindingResponse(NutritionScopedRoleBindingPo binding) {
        return new ScopedRoleBindingResponse(binding.getId(), binding.getSubjectType(), binding.getSubjectId(),
                binding.getRoleCode(), binding.getScopeType(), binding.getScopeId(), binding.getStatus(),
                binding.getCreatedAt(), binding.getUpdatedAt());
    }

    private DataGrantResponse toDataGrantResponse(NutritionDataGrantPo grant) {
        return new DataGrantResponse(grant.getId(), grant.getFamilyId(), grant.getMemberProfileId(),
                grant.getGranteeType(), grant.getGranteeId(), grant.getDataScope(), grant.getPermissionLevel(),
                grant.getExpiresAt(), grant.getStatus(), grant.getCreatedAt(), grant.getUpdatedAt());
    }

    private ClanFamilyRelationResponse toClanFamilyRelationResponse(NutritionClanFamilyPo relation) {
        return new ClanFamilyRelationResponse(relation.getId(), relation.getClanId(), relation.getFamilyId(),
                relation.getRelationStatus(), relation.getJoinedAt(), relation.getCreatedAt(), relation.getUpdatedAt());
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

    private String resolveOwnerUsername(Long userId, String actorUsername, CreateFamilyRequest request) {
        if (StringUtils.hasText(actorUsername)) {
            return actorUsername.trim();
        }
        return userRepository.findByIdAndDeletedFalse(userId)
                .map(UserPo::getUsername)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElseGet(() -> StringUtils.hasText(request.ownerNickname())
                        ? request.ownerNickname().trim()
                        : request.name().trim());
    }

    private static NutritionException forbidden() {
        return new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
    }
}
