package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateClanRequest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.po.NutritionDataGrantPo;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
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
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                "Mario Family", "Shanghai", "CNY", List.of("BREAKFAST", "DINNER"), "Mario"), ownerUserId);

        assertThat(roleBindingRepository.existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, ownerUserId, List.of(NutritionRoleCode.FAMILY_ADMIN),
                NutritionScopeType.FAMILY, family.id(), NutritionStatus.ACTIVE)).isTrue();
        var ownerMember = memberProfileRepository.findAll().stream()
                .filter(member -> family.id().equals(member.getFamilyId()))
                .filter(member -> ownerUserId.equals(member.getBoundUserId()))
                .findFirst();
        assertThat(ownerMember).isPresent();
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
}
