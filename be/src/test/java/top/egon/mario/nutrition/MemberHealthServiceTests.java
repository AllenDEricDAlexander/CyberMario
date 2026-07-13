package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateMemberProfileRequest;
import top.egon.mario.nutrition.dto.request.AssignProfileGuardianRequest;
import top.egon.mario.nutrition.dto.request.BindMemberUserRequest;
import top.egon.mario.nutrition.dto.request.UpdateHealthProfileRequest;
import top.egon.mario.nutrition.dto.request.UpdateMemberProfileRequest;
import top.egon.mario.nutrition.dto.response.HealthProfileResponse;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
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
import top.egon.mario.nutrition.service.MemberHealthService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies member health profiles stay protected by family access.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class MemberHealthServiceTests {

    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private MemberHealthService memberHealthService;
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
    @Autowired
    private UserRepository userRepository;

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
    void familyMemberCanReadAllFamilyHealthProfiles() {
        Long ownerUserId = 2001L;
        Long memberUserId = user("nutrition-luigi-reader").getId();
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var luigi = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                memberUserId, "Luigi", "MALE", LocalDate.of(1990, 1, 1),
                new BigDecimal("178.00"), new BigDecimal("72.50"), NutritionMemberType.ADULT,
                true, null), ownerUserId);

        memberHealthService.updateHealthProfile(family.id(), family.ownerMemberProfileId(), new UpdateHealthProfileRequest(
                "LIGHT", List.of("MAINTAIN_WEIGHT"), List.of("PEANUT"), List.of(), List.of(),
                new BigDecimal("2100.00"), null, null, null, null, null), ownerUserId);
        memberHealthService.updateHealthProfile(family.id(), luigi.id(), new UpdateHealthProfileRequest(
                "ACTIVE", List.of("GAIN_MUSCLE"), List.of(), List.of("CELERY"), List.of("LOW_SODIUM"),
                new BigDecimal("2400.00"), new BigDecimal("120.00"), null, null, null, null), ownerUserId);

        List<HealthProfileResponse> profiles = memberHealthService.listFamilyHealthProfiles(family.id(), memberUserId);

        assertThat(profiles)
                .extracting(HealthProfileResponse::memberProfileId)
                .containsExactlyInAnyOrder(family.ownerMemberProfileId(), luigi.id());
    }

    @Test
    void unrelatedUserCannotReadFamilyHealthProfile() {
        Long ownerUserId = 3001L;
        Long unrelatedUserId = 3999L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        memberHealthService.updateHealthProfile(family.id(), family.ownerMemberProfileId(), new UpdateHealthProfileRequest(
                "LIGHT", List.of("MAINTAIN_WEIGHT"), List.of(), List.of(), List.of(),
                new BigDecimal("2100.00"), null, null, null, null, null), ownerUserId);

        assertThatThrownBy(() -> memberHealthService.listFamilyHealthProfiles(family.id(), unrelatedUserId))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void updatingSoftDeletedHealthProfileReactivatesExistingRow() {
        Long ownerUserId = 4001L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        memberHealthService.updateHealthProfile(family.id(), family.ownerMemberProfileId(), new UpdateHealthProfileRequest(
                "LIGHT", List.of("MAINTAIN_WEIGHT"), List.of(), List.of(), List.of(),
                new BigDecimal("2100.00"), null, null, null, null, null), ownerUserId);
        NutritionHealthProfilePo softDeleted = healthProfileRepository.findAll().getFirst();
        softDeleted.setDeleted(true);
        healthProfileRepository.save(softDeleted);

        HealthProfileResponse updated = memberHealthService.updateHealthProfile(
                family.id(), family.ownerMemberProfileId(), new UpdateHealthProfileRequest(
                        "ACTIVE", List.of("GAIN_MUSCLE"), List.of(), List.of(), List.of(),
                        new BigDecimal("2400.00"), null, null, null, null, null), ownerUserId);

        assertThat(updated.id()).isEqualTo(softDeleted.getId());
        assertThat(healthProfileRepository.findAll()).hasSize(1);
        assertThat(healthProfileRepository.findAll().getFirst().isDeleted()).isFalse();
        assertThat(updated.targetCalories()).isEqualByComparingTo("2400.00");
    }

    @Test
    void familyHealthProfilesExcludeInactiveAndDeletedMembers() {
        Long ownerUserId = 5001L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var active = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                null, "Luigi", "MALE", LocalDate.of(1990, 1, 1),
                null, null, NutritionMemberType.ADULT, true, null), ownerUserId);
        var disabled = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                null, "Peach", "FEMALE", LocalDate.of(1991, 2, 2),
                null, null, NutritionMemberType.ADULT, true, null), ownerUserId);
        var deleted = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                null, "Toad", "MALE", LocalDate.of(1992, 3, 3),
                null, null, NutritionMemberType.ADULT, true, null), ownerUserId);
        memberHealthService.updateHealthProfile(family.id(), active.id(), basicHealth("ACTIVE"), ownerUserId);
        memberHealthService.updateHealthProfile(family.id(), disabled.id(), basicHealth("LIGHT"), ownerUserId);
        memberHealthService.updateHealthProfile(family.id(), deleted.id(), basicHealth("SEDENTARY"), ownerUserId);
        memberProfileRepository.findById(disabled.id()).ifPresent(member -> {
            member.setStatus(NutritionStatus.DISABLED);
            memberProfileRepository.save(member);
        });
        memberProfileRepository.findById(deleted.id()).ifPresent(member -> {
            member.setDeleted(true);
            memberProfileRepository.save(member);
        });

        List<HealthProfileResponse> profiles = memberHealthService.listFamilyHealthProfiles(family.id(), ownerUserId);

        assertThat(profiles)
                .extracting(HealthProfileResponse::memberProfileId)
                .containsExactly(active.id());
    }

    @Test
    void memberProfileCanBeUpdatedAndDeactivatedWithoutDeletingAuditRow() {
        Long ownerUserId = 6001L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var member = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                null, "Luigi", null, null, null, null,
                NutritionMemberType.ADULT, false, null), ownerUserId);

        var updated = memberHealthService.updateMemberProfile(family.id(), member.id(),
                new UpdateMemberProfileRequest("Luigi Mario", "MALE", LocalDate.of(1990, 1, 1),
                        new BigDecimal("178.00"), new BigDecimal("72.50"),
                        NutritionMemberType.ADULT, true, null), ownerUserId);

        assertThat(updated.nickname()).isEqualTo("Luigi Mario");
        assertThat(updated.loginEnabled()).isTrue();

        var disabled = memberHealthService.deactivateMemberProfile(family.id(), member.id(), ownerUserId);

        assertThat(disabled.status()).isEqualTo(NutritionStatus.DISABLED);
        assertThat(memberProfileRepository.findById(member.id()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void bindingValidatesUserAndUnbindingDeactivatesGeneratedRoles() {
        Long ownerUserId = 6002L;
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var member = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                null, "Luigi", null, null, null, null,
                NutritionMemberType.ADULT, false, null), ownerUserId);

        assertThatThrownBy(() -> memberHealthService.bindMemberUser(family.id(), member.id(),
                new BindMemberUserRequest(Long.MAX_VALUE), ownerUserId))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_USER_NOT_FOUND");

        Long boundUserId = user("nutrition-luigi-bound").getId();
        var bound = memberHealthService.bindMemberUser(family.id(), member.id(),
                new BindMemberUserRequest(boundUserId), ownerUserId);

        assertThat(bound.boundUserId()).isEqualTo(boundUserId);
        assertThat(bound.loginEnabled()).isTrue();
        assertThat(roleBindingRepository.existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, boundUserId, List.of(NutritionRoleCode.MEMBER),
                NutritionScopeType.FAMILY, family.id(), NutritionStatus.ACTIVE)).isTrue();
        assertThat(roleBindingRepository.existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, boundUserId, List.of(NutritionRoleCode.PROFILE_OWNER),
                NutritionScopeType.MEMBER_PROFILE, member.id(), NutritionStatus.ACTIVE)).isTrue();

        var unbound = memberHealthService.unbindMemberUser(family.id(), member.id(), ownerUserId);

        assertThat(unbound.boundUserId()).isNull();
        assertThat(unbound.loginEnabled()).isFalse();
        assertThat(roleBindingRepository.existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
                NutritionSubjectType.USER, boundUserId, List.of(NutritionRoleCode.PROFILE_OWNER),
                NutritionScopeType.MEMBER_PROFILE, member.id(), NutritionStatus.ACTIVE)).isFalse();
    }

    @Test
    void profileGuardianCanBeAssignedAndRevoked() {
        Long ownerUserId = 6003L;
        Long guardianUserId = user("nutrition-profile-guardian").getId();
        var family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var member = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                null, "Toad", null, null, null, null,
                NutritionMemberType.CHILD, false, null), ownerUserId);

        var guardian = memberHealthService.assignProfileGuardian(family.id(), member.id(),
                new AssignProfileGuardianRequest(guardianUserId), ownerUserId);

        assertThat(guardian.subjectId()).isEqualTo(guardianUserId);
        assertThat(guardian.roleCode()).isEqualTo(NutritionRoleCode.PROFILE_GUARDIAN);
        assertThat(guardian.scopeType()).isEqualTo(NutritionScopeType.MEMBER_PROFILE);
        assertThat(guardian.scopeId()).isEqualTo(member.id());

        memberHealthService.revokeProfileGuardian(
                family.id(), member.id(), guardian.id(), ownerUserId);

        assertThat(roleBindingRepository.findById(guardian.id()).orElseThrow().getStatus())
                .isEqualTo(NutritionStatus.DISABLED);
    }

    private UpdateHealthProfileRequest basicHealth(String activityLevel) {
        return new UpdateHealthProfileRequest(activityLevel, List.of(), List.of(), List.of(), List.of(),
                new BigDecimal("2000.00"), null, null, null, null, null);
    }

    private UserPo user(String username) {
        UserPo user = new UserPo();
        user.setUsername(username);
        user.setPasswordHash("{noop}nutrition-test");
        return userRepository.save(user);
    }
}
