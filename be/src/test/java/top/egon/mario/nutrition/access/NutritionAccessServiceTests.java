package top.egon.mario.nutrition.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies family-scoped nutrition access rules stay independent from clan membership.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionAccessServiceTests {

    @Autowired
    private NutritionAccessService accessService;
    @Autowired
    private NutritionFamilyRepository familyRepository;
    @Autowired
    private NutritionClanRepository clanRepository;
    @Autowired
    private NutritionClanFamilyRepository clanFamilyRepository;
    @Autowired
    private NutritionScopedRoleBindingRepository roleBindingRepository;
    @Autowired
    private NutritionDataGrantRepository dataGrantRepository;
    @Autowired
    private NutritionMemberProfileRepository memberProfileRepository;

    @BeforeEach
    void setUp() {
        dataGrantRepository.deleteAll();
        roleBindingRepository.deleteAll();
        clanFamilyRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
        clanRepository.deleteAll();
    }

    @Test
    void familyScopedBindingGrantsReadManageAndCookAccess() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        roleBinding(101L, NutritionRoleCode.FAMILY_ADMIN, NutritionScopeType.FAMILY, family.getId());

        assertThat(accessService.canReadFamily(101L, family.getId())).isTrue();
        assertThatCode(() -> accessService.requireReadFamily(101L, family.getId())).doesNotThrowAnyException();
        assertThatCode(() -> accessService.requireManageFamily(101L, family.getId())).doesNotThrowAnyException();
        assertThatCode(() -> accessService.requireCookFamily(101L, family.getId())).doesNotThrowAnyException();
    }

    @Test
    void inactiveFamilyIsDeniedDespiteActiveRoleAndGrant() {
        NutritionFamilyPo disabledFamily = family("Disabled Family", 10L, NutritionStatus.DISABLED);
        NutritionFamilyPo archivedFamily = family("Archived Family", 10L, NutritionStatus.ARCHIVED);
        roleBinding(104L, NutritionRoleCode.FAMILY_ADMIN, NutritionScopeType.FAMILY, disabledFamily.getId());
        dataGrant(disabledFamily.getId(), "USER", 104L, NutritionGrantDataScope.FAMILY);
        roleBinding(104L, NutritionRoleCode.FAMILY_ADMIN, NutritionScopeType.FAMILY, archivedFamily.getId());
        dataGrant(archivedFamily.getId(), "USER", 104L, NutritionGrantDataScope.FAMILY);

        assertThat(accessService.canReadFamily(104L, disabledFamily.getId())).isFalse();
        assertThat(accessService.canReadFamilyScope(104L, disabledFamily.getId(), NutritionGrantDataScope.FAMILY))
                .isFalse();
        assertThatThrownBy(() -> accessService.requireReadFamily(104L, disabledFamily.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
        assertThat(accessService.canReadFamily(104L, archivedFamily.getId())).isFalse();
        assertThat(accessService.canReadFamilyScope(104L, archivedFamily.getId(), NutritionGrantDataScope.FAMILY))
                .isFalse();
        assertThatThrownBy(() -> accessService.requireReadFamily(104L, archivedFamily.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void clanFamilyMembershipAloneDoesNotGrantFamilyAccess() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        NutritionClanPo clan = clan("Mario Clan", 20L);
        clanFamily(clan.getId(), family.getId());
        roleBinding(102L, NutritionRoleCode.CLAN_MEMBER, NutritionScopeType.CLAN, clan.getId());

        assertThat(accessService.canReadFamily(102L, family.getId())).isFalse();
        assertThatThrownBy(() -> accessService.requireReadFamily(102L, family.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void clanDataGrantAllowsGrantedFamilyScopeOnly() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        NutritionClanPo clan = clan("Mario Clan", 20L);
        clanFamily(clan.getId(), family.getId());
        roleBinding(103L, NutritionRoleCode.CLAN_MEMBER, NutritionScopeType.CLAN, clan.getId());
        dataGrant(family.getId(), "CLAN", clan.getId(), NutritionGrantDataScope.HEALTH_PROFILE);

        assertThat(accessService.canReadFamilyScope(103L, family.getId(), NutritionGrantDataScope.HEALTH_PROFILE))
                .isTrue();
        assertThat(accessService.canReadFamilyScope(103L, family.getId(), NutritionGrantDataScope.BUDGET))
                .isFalse();
        assertThat(accessService.canReadFamily(103L, family.getId())).isFalse();
    }

    @Test
    void writeGrantCanEditScopedDataButCannotManageFamily() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        dataGrant(family.getId(), "USER", 106L, NutritionGrantDataScope.HEALTH_PROFILE,
                NutritionGrantPermissionLevel.WRITE, null);

        assertThat(accessService.canWriteFamilyScope(
                106L, family.getId(), NutritionGrantDataScope.HEALTH_PROFILE)).isTrue();
        assertThat(accessService.canManageFamilyScope(
                106L, family.getId(), NutritionGrantDataScope.HEALTH_PROFILE)).isFalse();
        assertThatCode(() -> accessService.requireWriteFamilyScope(
                106L, family.getId(), NutritionGrantDataScope.HEALTH_PROFILE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> accessService.requireManageFamily(106L, family.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void manageGrantCanManageOnlyItsActiveScope() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        dataGrant(family.getId(), "USER", 107L, NutritionGrantDataScope.BUDGET,
                NutritionGrantPermissionLevel.MANAGE, Instant.now().plusSeconds(60));
        dataGrant(family.getId(), "USER", 107L, NutritionGrantDataScope.HEALTH_PROFILE,
                NutritionGrantPermissionLevel.MANAGE, Instant.now().minusSeconds(60));

        assertThat(accessService.canManageFamilyScope(
                107L, family.getId(), NutritionGrantDataScope.BUDGET)).isTrue();
        assertThatCode(() -> accessService.requireManageFamilyScope(
                107L, family.getId(), NutritionGrantDataScope.BUDGET)).doesNotThrowAnyException();
        assertThat(accessService.canWriteFamilyScope(
                107L, family.getId(), NutritionGrantDataScope.BUDGET)).isTrue();
        assertThat(accessService.canManageFamilyScope(
                107L, family.getId(), NutritionGrantDataScope.HEALTH_PROFILE)).isFalse();
        assertThat(accessService.canManageFamilyScope(
                107L, family.getId(), NutritionGrantDataScope.NUTRITION_RECORD)).isFalse();
    }

    @Test
    void memberConfirmationAllowsBoundUserAndFamilyCookOnly() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        NutritionMemberProfilePo member = memberProfile(family.getId(), 201L);
        roleBinding(202L, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());

        assertThatCode(() -> accessService.requireConfirmMemberProfile(201L, family.getId(), member.getId()))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessService.requireConfirmMemberProfile(202L, family.getId(), member.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> accessService.requireConfirmMemberProfile(203L, family.getId(), member.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void memberConfirmationAllowsFamilyAndProfileGuardianProxyOnly() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        NutritionMemberProfilePo member = memberProfile(family.getId(), 401L);
        NutritionMemberProfilePo otherMember = memberProfile(family.getId(), 402L);
        roleBinding(403L, NutritionRoleCode.GUARDIAN, NutritionScopeType.FAMILY, family.getId());
        roleBinding(404L, NutritionRoleCode.PROFILE_GUARDIAN,
                NutritionScopeType.MEMBER_PROFILE, member.getId());
        roleBinding(405L, NutritionRoleCode.MEMBER, NutritionScopeType.FAMILY, family.getId());
        roleBinding(402L, NutritionRoleCode.PROFILE_OWNER,
                NutritionScopeType.MEMBER_PROFILE, otherMember.getId());

        assertThatCode(() -> accessService.requireConfirmMemberProfile(403L, family.getId(), member.getId()))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessService.requireConfirmMemberProfile(404L, family.getId(), member.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> accessService.requireConfirmMemberProfile(405L, family.getId(), member.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
        assertThatThrownBy(() -> accessService.requireConfirmMemberProfile(402L, family.getId(), member.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void inactiveMemberProfileCannotBeConfirmedByBoundUserOrFamilyCook() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        NutritionMemberProfilePo disabledMember = memberProfile(family.getId(), 301L, NutritionStatus.DISABLED);
        NutritionMemberProfilePo archivedMember = memberProfile(family.getId(), 302L, NutritionStatus.ARCHIVED);
        roleBinding(303L, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());

        assertThatThrownBy(() -> accessService.requireConfirmMemberProfile(
                301L, family.getId(), disabledMember.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
        assertThatThrownBy(() -> accessService.requireConfirmMemberProfile(
                303L, family.getId(), disabledMember.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
        assertThatThrownBy(() -> accessService.requireConfirmMemberProfile(
                302L, family.getId(), archivedMember.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void memberProfileWriteAllowsOwnerGuardianAndSpecificWriteGrant() {
        NutritionFamilyPo family = family("Mario Family", 10L);
        NutritionMemberProfilePo member = memberProfile(family.getId(), 501L);
        roleBinding(502L, NutritionRoleCode.PROFILE_GUARDIAN,
                NutritionScopeType.MEMBER_PROFILE, member.getId());
        NutritionDataGrantPo grant = dataGrant(family.getId(), "USER", 503L,
                NutritionGrantDataScope.MEMBER_PROFILE, NutritionGrantPermissionLevel.WRITE, null);
        grant.setMemberProfileId(member.getId());
        dataGrantRepository.save(grant);

        assertThatCode(() -> accessService.requireWriteMemberProfile(
                501L, family.getId(), member.getId())).doesNotThrowAnyException();
        assertThatCode(() -> accessService.requireWriteMemberProfile(
                502L, family.getId(), member.getId())).doesNotThrowAnyException();
        assertThatCode(() -> accessService.requireWriteMemberProfile(
                503L, family.getId(), member.getId())).doesNotThrowAnyException();
        assertThatThrownBy(() -> accessService.requireWriteMemberProfile(
                504L, family.getId(), member.getId()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        return family(name, ownerUserId, NutritionStatus.ACTIVE);
    }

    private NutritionFamilyPo family(String name, Long ownerUserId, NutritionStatus status) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setStatus(status);
        return familyRepository.save(family);
    }

    private NutritionClanPo clan(String name, Long ownerUserId) {
        NutritionClanPo clan = new NutritionClanPo();
        clan.setName(name);
        clan.setOwnerUserId(ownerUserId);
        clan.setStatus(NutritionStatus.ACTIVE);
        return clanRepository.save(clan);
    }

    private NutritionClanFamilyPo clanFamily(Long clanId, Long familyId) {
        NutritionClanFamilyPo clanFamily = new NutritionClanFamilyPo();
        clanFamily.setClanId(clanId);
        clanFamily.setFamilyId(familyId);
        clanFamily.setRelationStatus(NutritionStatus.ACTIVE);
        return clanFamilyRepository.save(clanFamily);
    }

    private NutritionScopedRoleBindingPo roleBinding(Long userId, NutritionRoleCode roleCode,
                                                     NutritionScopeType scopeType, Long scopeId) {
        NutritionScopedRoleBindingPo binding = new NutritionScopedRoleBindingPo();
        binding.setSubjectType(NutritionSubjectType.USER);
        binding.setSubjectId(userId);
        binding.setRoleCode(roleCode);
        binding.setScopeType(scopeType);
        binding.setScopeId(scopeId);
        binding.setStatus(NutritionStatus.ACTIVE);
        return roleBindingRepository.save(binding);
    }

    private NutritionDataGrantPo dataGrant(Long familyId, String granteeType, Long granteeId,
                                           NutritionGrantDataScope dataScope) {
        return dataGrant(familyId, granteeType, granteeId, dataScope,
                NutritionGrantPermissionLevel.READ, null);
    }

    private NutritionDataGrantPo dataGrant(Long familyId, String granteeType, Long granteeId,
                                           NutritionGrantDataScope dataScope,
                                           NutritionGrantPermissionLevel permissionLevel,
                                           Instant expiresAt) {
        NutritionDataGrantPo grant = new NutritionDataGrantPo();
        grant.setFamilyId(familyId);
        grant.setGranteeType(granteeType);
        grant.setGranteeId(granteeId);
        grant.setDataScope(dataScope);
        grant.setPermissionLevel(permissionLevel);
        grant.setExpiresAt(expiresAt);
        grant.setStatus(NutritionStatus.ACTIVE);
        return dataGrantRepository.save(grant);
    }

    private NutritionMemberProfilePo memberProfile(Long familyId, Long boundUserId) {
        return memberProfile(familyId, boundUserId, NutritionStatus.ACTIVE);
    }

    private NutritionMemberProfilePo memberProfile(Long familyId, Long boundUserId, NutritionStatus status) {
        NutritionMemberProfilePo member = new NutritionMemberProfilePo();
        member.setFamilyId(familyId);
        member.setBoundUserId(boundUserId);
        member.setNickname("Mario");
        member.setMemberType(NutritionMemberType.ADULT);
        member.setStatus(status);
        return memberProfileRepository.save(member);
    }
}
