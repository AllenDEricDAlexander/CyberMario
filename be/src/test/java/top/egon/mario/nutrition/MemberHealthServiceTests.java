package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateMemberProfileRequest;
import top.egon.mario.nutrition.dto.request.UpdateHealthProfileRequest;
import top.egon.mario.nutrition.dto.response.HealthProfileResponse;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
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
        Long memberUserId = 2002L;
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
                5002L, "Luigi", "MALE", LocalDate.of(1990, 1, 1),
                null, null, NutritionMemberType.ADULT, true, null), ownerUserId);
        var disabled = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                5003L, "Peach", "FEMALE", LocalDate.of(1991, 2, 2),
                null, null, NutritionMemberType.ADULT, true, null), ownerUserId);
        var deleted = memberHealthService.createMemberProfile(family.id(), new CreateMemberProfileRequest(
                5004L, "Toad", "MALE", LocalDate.of(1992, 3, 3),
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

    private UpdateHealthProfileRequest basicHealth(String activityLevel) {
        return new UpdateHealthProfileRequest(activityLevel, List.of(), List.of(), List.of(), List.of(),
                new BigDecimal("2000.00"), null, null, null, null, null);
    }
}
