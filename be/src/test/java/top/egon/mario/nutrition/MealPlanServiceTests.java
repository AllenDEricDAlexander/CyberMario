package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
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
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.service.MealPlanService;
import top.egon.mario.nutrition.service.NutritionException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies cook-owned meal plan review state transitions.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class MealPlanServiceTests {

    private static final Long COOK_USER_ID = 9101L;
    private static final Long MEMBER_USER_ID = 9102L;

    @Autowired
    private MealPlanService mealPlanService;
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
    private NutritionRiskCheckResultRepository riskCheckResultRepository;
    @Autowired
    private NutritionMealPlanRepository mealPlanRepository;
    @Autowired
    private NutritionMealPlanItemRepository mealPlanItemRepository;
    @Autowired
    private NutritionMealConfirmationRepository confirmationRepository;
    @Autowired
    private NutritionMealConfirmationItemRepository confirmationItemRepository;

    @BeforeEach
    void setUp() {
        confirmationItemRepository.deleteAll();
        confirmationRepository.deleteAll();
        mealPlanItemRepository.deleteAll();
        mealPlanRepository.deleteAll();
        riskCheckResultRepository.deleteAll();
        dataGrantRepository.deleteAll();
        roleBindingRepository.deleteAll();
        clanFamilyRepository.deleteAll();
        healthProfileRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
        clanRepository.deleteAll();
    }

    @Test
    void cookCanPublishPendingReviewMenu() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);

        MealPlanResponse response = mealPlanService.publishMealPlan(family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.PUBLISHED);
        assertThat(response.publishedAt()).isNotNull();
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionMealPlanStatus.PUBLISHED);
            assertThat(saved.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void ordinaryMemberCannotPublishMenu() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), MEMBER_USER_ID);
        roleBinding(MEMBER_USER_ID, NutritionRoleCode.MEMBER, NutritionScopeType.FAMILY, family.getId());
        roleBinding(MEMBER_USER_ID, NutritionRoleCode.PROFILE_OWNER,
                NutritionScopeType.MEMBER_PROFILE, member.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> mealPlanService.publishMealPlan(
                family.getId(), mealPlan.getId(), MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_FORBIDDEN");
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionMealPlanStatus.PENDING_REVIEW);
    }

    @Test
    void cookCanClosePublishedMenuWithoutConfirmations() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED);

        MealPlanResponse response = mealPlanService.closeConfirmation(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.CONFIRM_CLOSED);
        assertThat(response.confirmedMemberCount()).isZero();
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionMealPlanStatus.CONFIRM_CLOSED);
            assertThat(saved.getConfirmedMemberCount()).isZero();
        });
    }

    @Test
    void cookCanCompleteClosedConfirmationMenuThroughPreparingTransition() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PENDING_REVIEW);
        mealPlanService.publishMealPlan(family.getId(), mealPlan.getId(), COOK_USER_ID);
        mealPlanService.closeConfirmation(family.getId(), mealPlan.getId(), COOK_USER_ID);

        MealPlanResponse response = mealPlanService.completeMealPlan(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionMealPlanStatus.COMPLETED);
    }

    @Test
    void cookCanCompletePreparingMenuDirectly() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        roleBinding(COOK_USER_ID, NutritionRoleCode.COOK, NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PREPARING);

        MealPlanResponse response = mealPlanService.completeMealPlan(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(response.status()).isEqualTo(NutritionMealPlanStatus.COMPLETED);
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getStatus())
                .isEqualTo(NutritionMealPlanStatus.COMPLETED);
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setStatus(NutritionStatus.ACTIVE);
        return familyRepository.saveAndFlush(family);
    }

    private NutritionMealPlanPo mealPlan(Long familyId, NutritionMealPlanStatus status) {
        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(familyId);
        mealPlan.setPlanDate(LocalDate.of(2026, 7, 1));
        mealPlan.setTitle("Family dinner");
        mealPlan.setStatus(status);
        return mealPlanRepository.saveAndFlush(mealPlan);
    }

    private NutritionMemberProfilePo memberProfile(Long familyId, Long boundUserId) {
        NutritionMemberProfilePo memberProfile = new NutritionMemberProfilePo();
        memberProfile.setFamilyId(familyId);
        memberProfile.setBoundUserId(boundUserId);
        memberProfile.setNickname("Mario");
        memberProfile.setMemberType(NutritionMemberType.ADULT);
        memberProfile.setStatus(NutritionStatus.ACTIVE);
        return memberProfileRepository.saveAndFlush(memberProfile);
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
        return roleBindingRepository.saveAndFlush(binding);
    }
}
