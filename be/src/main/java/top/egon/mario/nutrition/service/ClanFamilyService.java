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
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.response.ClanResponse;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
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

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
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
        ownerProfile.setNickname(StringUtils.hasText(request.ownerNickname())
                ? request.ownerNickname().trim()
                : savedFamily.getName());
        ownerProfile.setMemberType(NutritionMemberType.ADULT);
        ownerProfile.setLoginEnabled(true);
        ownerProfile.setStatus(NutritionStatus.ACTIVE);
        NutritionMemberProfilePo savedOwnerProfile = memberProfileRepository.save(ownerProfile);
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

    private ClanResponse toClanResponse(NutritionClanPo clan) {
        return new ClanResponse(clan.getId(), clan.getName(), clan.getOwnerUserId(), clan.getStatus(),
                clan.getCreatedAt(), clan.getUpdatedAt());
    }

    private FamilyResponse toFamilyResponse(NutritionFamilyPo family) {
        Long ownerMemberProfileId = memberProfileRepository
                .findByFamilyIdAndBoundUserIdAndStatusAndDeletedFalse(
                        family.getId(), family.getOwnerUserId(), NutritionStatus.ACTIVE)
                .map(NutritionMemberProfilePo::getId)
                .orElse(null);
        return toFamilyResponse(family, ownerMemberProfileId);
    }

    private FamilyResponse toFamilyResponse(NutritionFamilyPo family, Long ownerMemberProfileId) {
        return new FamilyResponse(family.getId(), family.getName(), family.getOwnerUserId(), family.getRegion(),
                family.getCurrency(), readStringList(family.getDefaultMealTypes()), family.isAiEnabled(),
                family.getAiGenerateTime(), family.isHealthAlertEnabled(), family.isBudgetEnabled(),
                family.getStatus(), ownerMemberProfileId, family.getCreatedAt(), family.getUpdatedAt());
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
