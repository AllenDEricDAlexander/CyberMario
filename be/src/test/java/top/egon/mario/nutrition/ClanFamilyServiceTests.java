package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateClanRequest;
import top.egon.mario.nutrition.dto.request.CreateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateScopedRoleBindingRequest;
import top.egon.mario.nutrition.dto.request.UpdateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.UpdateFamilySettingsRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.po.NutritionDataGrantPo;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies clan and family bootstrap access bindings.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ClanFamilyServiceTests {

    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private NutritionAccessService accessService;
    @Autowired
    private NutritionClanRepository clanRepository;
    @Autowired
    private NutritionFamilyRepository familyRepository;
    @Autowired
    private NutritionClanFamilyRepository clanFamilyRepository;
    @Autowired
    private NutritionMemberProfileRepository memberProfileRepository;
    @Autowired
    private NutritionHealthProfileRepository healthProfileRepository;
    @Autowired
    private NutritionScopedRoleBindingRepository roleBindingRepository;
    @Autowired
    private NutritionDataGrantRepository dataGrantRepository;

    @BeforeEach
    void setUp() {
        dataGrantRepository.deleteAll();
        roleBindingRepository.deleteAll();
        clanFamilyRepository.deleteAll();
        healthProfileRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
        clanRepository.deleteAll();
    }

    @Test
    void creatingFamilyBindsOwnerAsFamilyAdminAndMemberProfileOwner() {
        Long ownerUserId = 1001L;

        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", "Shanghai", "CNY", List.of("BREAKFAST", "DINNER"), "ignored"),
                ownerUserId, "mario-login");

        assertThat(roleBindingRepository.existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, ownerUserId, List.of(NutritionRoleCode.FAMILY_ADMIN),
                NutritionScopeType.FAMILY, family.id(), NutritionStatus.ACTIVE)).isTrue();
        var ownerMember = memberProfileRepository.findAll().stream()
                .filter(member -> family.id().equals(member.getFamilyId()))
                .filter(member -> ownerUserId.equals(member.getBoundUserId()))
                .findFirst();
        assertThat(ownerMember).isPresent();
        assertThat(ownerMember.orElseThrow().getNickname()).isEqualTo("mario-login");
        assertThat(family.ownerMemberProfileId()).isEqualTo(ownerMember.orElseThrow().getId());
        assertThat(familyRepository.findById(family.id()).orElseThrow().getOwnerMemberProfileId())
                .isEqualTo(ownerMember.orElseThrow().getId());
        assertThat(roleBindingRepository.existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, ownerUserId, List.of(NutritionRoleCode.PROFILE_OWNER),
                NutritionScopeType.MEMBER_PROFILE, ownerMember.orElseThrow().getId(), NutritionStatus.ACTIVE)).isTrue();
    }

    @Test
    void clanFamilyAssociationDoesNotCreateFamilyReadBinding() {
        Long ownerUserId = 1002L;
        var clan = clanFamilyService.createClan(new CreateClanRequest("Mario Clan"), ownerUserId);
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);

        clanFamilyService.associateClanFamily(clan.id(), family.id(), ownerUserId);

        assertThat(clanFamilyRepository.existsByClanIdAndFamilyIdAndRelationStatusAndDeletedFalse(
                clan.id(), family.id(), NutritionStatus.ACTIVE)).isTrue();
        assertThat(roleBindingRepository.findAll().stream()
                .filter(binding -> NutritionScopeType.FAMILY == binding.getScopeType())
                .filter(binding -> family.id().equals(binding.getScopeId()))
                .filter(binding -> !binding.isDeleted())
                .count()).isEqualTo(1);
        assertThat(dataGrantRepository.findAll()).isEmpty();
    }

    @Test
    void regrantingExpiredDataGrantClearsExpiration() {
        Long ownerUserId = 1003L;
        Long granteeUserId = 1004L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        NutritionDataGrantPo expiredGrant = new NutritionDataGrantPo();
        expiredGrant.setFamilyId(family.id());
        expiredGrant.setGranteeType("USER");
        expiredGrant.setGranteeId(granteeUserId);
        expiredGrant.setDataScope(NutritionGrantDataScope.HEALTH_PROFILE);
        expiredGrant.setPermissionLevel(NutritionGrantPermissionLevel.READ);
        expiredGrant.setStatus(NutritionStatus.ACTIVE);
        expiredGrant.setExpiresAt(Instant.now().minusSeconds(60));
        dataGrantRepository.save(expiredGrant);

        clanFamilyService.grantFamilyDataToUser(family.id(), granteeUserId,
                NutritionGrantDataScope.HEALTH_PROFILE, NutritionGrantPermissionLevel.READ, ownerUserId);

        assertThat(dataGrantRepository.findAll()).hasSize(1);
        assertThat(dataGrantRepository.findAll().getFirst().getExpiresAt()).isNull();
        assertThat(accessService.canReadFamilyScope(
                granteeUserId, family.id(), NutritionGrantDataScope.HEALTH_PROFILE)).isTrue();
    }

    @Test
    void familySettingsCanBeReadAndUpdatedByFamilyAdministrator() {
        Long ownerUserId = 1005L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);

        FamilyResponse updated = clanFamilyService.updateFamilySettings(family.id(),
                new UpdateFamilySettingsRequest("Shanghai", "USD",
                        List.of(NutritionMealType.BREAKFAST, NutritionMealType.DINNER),
                        true, LocalTime.of(6, 30), false, true), ownerUserId);

        assertThat(updated.region()).isEqualTo("Shanghai");
        assertThat(updated.currency()).isEqualTo("USD");
        assertThat(updated.defaultMealTypes()).containsExactly("BREAKFAST", "DINNER");
        assertThat(updated.aiEnabled()).isTrue();
        assertThat(updated.aiGenerateTime()).isEqualTo(LocalTime.of(6, 30));
        assertThat(updated.healthAlertEnabled()).isFalse();
        assertThat(updated.budgetEnabled()).isTrue();
        FamilyResponse settings = clanFamilyService.getFamilySettings(family.id(), ownerUserId);
        assertThat(settings.id()).isEqualTo(updated.id());
        assertThat(settings.region()).isEqualTo(updated.region());
        assertThat(settings.currency()).isEqualTo(updated.currency());
        assertThat(settings.defaultMealTypes()).isEqualTo(updated.defaultMealTypes());
        assertThat(settings.aiEnabled()).isEqualTo(updated.aiEnabled());
        assertThat(settings.aiGenerateTime()).isEqualTo(updated.aiGenerateTime());
        assertThat(settings.healthAlertEnabled()).isEqualTo(updated.healthAlertEnabled());
        assertThat(settings.budgetEnabled()).isEqualTo(updated.budgetEnabled());
    }

    @Test
    void roleBindingCannotRemoveLastFamilyAdministrator() {
        Long ownerUserId = 1006L;
        Long secondAdminUserId = 1007L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var secondAdmin = clanFamilyService.createRoleBinding(family.id(),
                new CreateScopedRoleBindingRequest(NutritionSubjectType.USER, secondAdminUserId,
                        NutritionRoleCode.FAMILY_ADMIN, NutritionScopeType.FAMILY, family.id()), ownerUserId);
        Long ownerBindingId = roleBindingRepository.findAll().stream()
                .filter(binding -> ownerUserId.equals(binding.getSubjectId()))
                .filter(binding -> NutritionRoleCode.FAMILY_ADMIN == binding.getRoleCode())
                .map(binding -> binding.getId())
                .findFirst()
                .orElseThrow();

        clanFamilyService.revokeRoleBinding(family.id(), ownerBindingId, ownerUserId);

        assertThat(roleBindingRepository.findById(ownerBindingId).orElseThrow().getStatus())
                .isEqualTo(NutritionStatus.DISABLED);
        assertThatThrownBy(() -> clanFamilyService.revokeRoleBinding(
                family.id(), secondAdmin.id(), ownerUserId))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_LAST_FAMILY_ADMIN");
    }

    @Test
    void roleBindingRejectsRoleOutsideItsSupportedScope() {
        Long ownerUserId = 1008L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);

        assertThatThrownBy(() -> clanFamilyService.createRoleBinding(family.id(),
                new CreateScopedRoleBindingRequest(NutritionSubjectType.USER, 1009L,
                        NutritionRoleCode.PROFILE_GUARDIAN, NutritionScopeType.FAMILY, family.id()), ownerUserId))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_ROLE_SCOPE_INVALID");
    }

    @Test
    void dataGrantUpdateEnforcesExpirationAndRevokeKeepsAuditRow() {
        Long ownerUserId = 1010L;
        Long granteeUserId = 1011L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var grant = clanFamilyService.createDataGrant(family.id(),
                new CreateDataGrantRequest(null, "USER", granteeUserId,
                        NutritionGrantDataScope.HEALTH_PROFILE, NutritionGrantPermissionLevel.WRITE,
                        Instant.now().plusSeconds(60)), ownerUserId);

        assertThat(accessService.canWriteFamilyScope(
                granteeUserId, family.id(), NutritionGrantDataScope.HEALTH_PROFILE)).isTrue();

        var expired = clanFamilyService.updateDataGrant(family.id(), grant.id(),
                new UpdateDataGrantRequest(NutritionGrantPermissionLevel.MANAGE,
                        Instant.now().minusSeconds(60)), ownerUserId);

        assertThat(expired.permissionLevel()).isEqualTo(NutritionGrantPermissionLevel.MANAGE);
        assertThat(accessService.canManageFamilyScope(
                granteeUserId, family.id(), NutritionGrantDataScope.HEALTH_PROFILE)).isFalse();

        clanFamilyService.revokeDataGrant(family.id(), grant.id(), ownerUserId);

        assertThat(dataGrantRepository.findAll()).hasSize(1);
        assertThat(dataGrantRepository.findById(grant.id()).orElseThrow().getStatus())
                .isEqualTo(NutritionStatus.DISABLED);
    }

    @Test
    void clanFamilyRelationCanBeListedAndDeactivated() {
        Long ownerUserId = 1012L;
        var clan = clanFamilyService.createClan(new CreateClanRequest("Mario Clan"), ownerUserId);
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        clanFamilyService.associateClanFamily(clan.id(), family.id(), ownerUserId);
        var relation = clanFamilyService.listClanFamilyRelations(family.id(), ownerUserId).getFirst();

        clanFamilyService.removeClanFamilyRelation(family.id(), relation.id(), ownerUserId);

        assertThat(clanFamilyRepository.findById(relation.id()).orElseThrow().getRelationStatus())
                .isEqualTo(NutritionStatus.DISABLED);
        assertThat(clanFamilyRepository.findById(relation.id()).orElseThrow().isDeleted()).isFalse();
    }
}
