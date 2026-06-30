package top.egon.mario.nutrition.service.access;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.service.NutritionException;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Default nutrition access service backed by scoped bindings and explicit data grants.
 */
@Service
@RequiredArgsConstructor
@Validated
public class NutritionAccessServiceImpl implements NutritionAccessService {

    private static final String FORBIDDEN_CODE = "NUTRITION_FORBIDDEN";
    private static final String FORBIDDEN_MESSAGE = "Nutrition family access is required";
    private static final String GRANTEE_TYPE_USER = "USER";
    private static final String GRANTEE_TYPE_CLAN = "CLAN";

    private static final Set<NutritionRoleCode> READ_FAMILY_ROLES = Set.copyOf(EnumSet.of(
            NutritionRoleCode.FAMILY_ADMIN,
            NutritionRoleCode.COOK,
            NutritionRoleCode.MEMBER,
            NutritionRoleCode.GUARDIAN,
            NutritionRoleCode.PROFILE_OWNER,
            NutritionRoleCode.PROFILE_GUARDIAN
    ));
    private static final Set<NutritionRoleCode> MANAGE_FAMILY_ROLES = Set.of(NutritionRoleCode.FAMILY_ADMIN);
    private static final Set<NutritionRoleCode> COOK_FAMILY_ROLES = Set.copyOf(EnumSet.of(
            NutritionRoleCode.FAMILY_ADMIN,
            NutritionRoleCode.COOK
    ));
    private static final Set<NutritionRoleCode> CONFIRM_FAMILY_ROLES = Set.copyOf(EnumSet.of(
            NutritionRoleCode.FAMILY_ADMIN,
            NutritionRoleCode.COOK,
            NutritionRoleCode.GUARDIAN
    ));
    private static final Set<NutritionRoleCode> CONFIRM_MEMBER_PROFILE_ROLES = Set.of(
            NutritionRoleCode.PROFILE_GUARDIAN
    );
    private static final Set<NutritionRoleCode> READ_CLAN_ROLES = Set.copyOf(EnumSet.of(
            NutritionRoleCode.CLAN_ADMIN,
            NutritionRoleCode.CLAN_MEMBER
    ));
    private static final Set<NutritionGrantPermissionLevel> READ_GRANT_LEVELS = Set.copyOf(EnumSet.of(
            NutritionGrantPermissionLevel.READ,
            NutritionGrantPermissionLevel.WRITE,
            NutritionGrantPermissionLevel.MANAGE
    ));

    private final NutritionFamilyRepository familyRepository;
    private final NutritionScopedRoleBindingRepository roleBindingRepository;
    private final NutritionDataGrantRepository dataGrantRepository;
    private final NutritionClanFamilyRepository clanFamilyRepository;
    private final NutritionMemberProfileRepository memberProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean canReadFamily(Long userId, Long familyId) {
        return canReadFamilyScope(userId, familyId, NutritionGrantDataScope.FAMILY);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireReadFamily(Long userId, Long familyId) {
        if (!canReadFamily(userId, familyId)) {
            throw forbidden();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireManageFamily(Long userId, Long familyId) {
        if (!isActiveFamily(familyId)) {
            throw forbidden();
        }
        if (!hasFamilyRole(userId, familyId, MANAGE_FAMILY_ROLES) && !isFamilyOwner(userId, familyId)) {
            throw forbidden();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireCookFamily(Long userId, Long familyId) {
        if (!isActiveFamily(familyId)) {
            throw forbidden();
        }
        if (!hasFamilyRole(userId, familyId, COOK_FAMILY_ROLES) && !isFamilyOwner(userId, familyId)) {
            throw forbidden();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireConfirmMemberProfile(Long userId, Long familyId, Long memberProfileId) {
        if (!isActiveFamily(familyId)) {
            throw forbidden();
        }
        NutritionMemberProfilePo memberProfile = memberProfileRepository
                .findByIdAndFamilyIdAndStatusAndDeletedFalse(memberProfileId, familyId, NutritionStatus.ACTIVE)
                .orElseThrow(NutritionAccessServiceImpl::forbidden);
        if (userId != null && userId.equals(memberProfile.getBoundUserId())) {
            return;
        }
        if (hasFamilyRole(userId, familyId, CONFIRM_FAMILY_ROLES) || isFamilyOwner(userId, familyId)
                || hasMemberProfileRole(userId, memberProfileId, CONFIRM_MEMBER_PROFILE_ROLES)) {
            return;
        }
        throw forbidden();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canReadFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope) {
        if (userId == null || familyId == null || scope == null) {
            return false;
        }
        if (!isActiveFamily(familyId)) {
            return false;
        }
        if (hasFamilyRole(userId, familyId, READ_FAMILY_ROLES) || isFamilyOwner(userId, familyId)) {
            return true;
        }
        if (hasReadableDataGrant(familyId, GRANTEE_TYPE_USER, userId, scope)) {
            return true;
        }
        return clanRoleBindings(userId).stream()
                .map(NutritionScopedRoleBindingPo::getScopeId)
                .anyMatch(clanId -> canReadThroughClanGrant(userId, familyId, scope, clanId));
    }

    private boolean hasFamilyRole(Long userId, Long familyId, Collection<NutritionRoleCode> roleCodes) {
        if (userId == null || familyId == null || roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleBindingRepository
                .existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                        NutritionSubjectType.USER, userId, roleCodes, NutritionScopeType.FAMILY, familyId,
                        NutritionStatus.ACTIVE);
    }

    private boolean hasMemberProfileRole(Long userId, Long memberProfileId, Collection<NutritionRoleCode> roleCodes) {
        if (userId == null || memberProfileId == null || roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleBindingRepository
                .existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                        NutritionSubjectType.USER, userId, roleCodes, NutritionScopeType.MEMBER_PROFILE,
                        memberProfileId, NutritionStatus.ACTIVE);
    }

    private boolean isActiveFamily(Long familyId) {
        return familyId != null && familyRepository.existsByIdAndStatusAndDeletedFalse(
                familyId, NutritionStatus.ACTIVE);
    }

    private boolean isFamilyOwner(Long userId, Long familyId) {
        return userId != null && familyId != null
                && familyRepository.existsByIdAndOwnerUserIdAndStatusAndDeletedFalse(
                familyId, userId, NutritionStatus.ACTIVE);
    }

    private Collection<NutritionScopedRoleBindingPo> clanRoleBindings(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return roleBindingRepository.findBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, userId, READ_CLAN_ROLES, NutritionScopeType.CLAN, NutritionStatus.ACTIVE);
    }

    private boolean canReadThroughClanGrant(Long userId, Long familyId, NutritionGrantDataScope scope, Long clanId) {
        return clanId != null
                && clanFamilyRepository.existsByClanIdAndFamilyIdAndRelationStatusAndDeletedFalse(
                clanId, familyId, NutritionStatus.ACTIVE)
                && hasReadableDataGrant(familyId, GRANTEE_TYPE_CLAN, clanId, scope);
    }

    private boolean hasReadableDataGrant(Long familyId, String granteeType, Long granteeId,
                                         NutritionGrantDataScope scope) {
        return dataGrantRepository.existsReadableGrant(familyId, granteeType, granteeId, scope,
                READ_GRANT_LEVELS, NutritionStatus.ACTIVE, Instant.now());
    }

    private static NutritionException forbidden() {
        return new NutritionException(FORBIDDEN_CODE, FORBIDDEN_MESSAGE);
    }
}
