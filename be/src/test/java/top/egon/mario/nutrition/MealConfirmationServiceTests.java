package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.MealConfirmationRequest;
import top.egon.mario.nutrition.dto.response.MealConfirmationResponse;
import top.egon.mario.nutrition.dto.response.MealPlanSummaryResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRiskCheckResultPo;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
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
import top.egon.mario.nutrition.service.MealConfirmationService;
import top.egon.mario.nutrition.service.MealPlanService;
import top.egon.mario.nutrition.service.NutritionException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies member meal confirmations, risk gates, and dish serving summaries.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class MealConfirmationServiceTests {

    private static final String MEAL_PLAN_SOURCE_TYPE = "MEAL_PLAN";
    private static final Long COOK_USER_ID = 9201L;
    private static final Long FIRST_MEMBER_USER_ID = 9202L;
    private static final Long SECOND_MEMBER_USER_ID = 9203L;
    private static final Long FAMILY_GUARDIAN_USER_ID = 9204L;
    private static final Long PROFILE_GUARDIAN_USER_ID = 9205L;

    @Autowired
    private MealConfirmationService confirmationService;
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
    void boundUserCanConfirmOwnProfileBeforeCutoff() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED,
                Instant.now().plusSeconds(3600));
        mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Tomato Pasta",
                new BigDecimal("2.000"), 0);

        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(NutritionMealType.DINNER),
                        false, null, "home dinner"), FIRST_MEMBER_USER_ID);

        assertThat(response.confirmationStatus()).isEqualTo(NutritionConfirmationStatus.CONFIRMED);
        assertThat(response.confirmedByUserId()).isEqualTo(FIRST_MEMBER_USER_ID);
        assertThat(response.proxyByUserId()).isNull();
        assertThat(response.selectedMealTypes()).containsExactly(NutritionMealType.DINNER);
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionMealPlanStatus.CONFIRMING);
            assertThat(saved.getConfirmedMemberCount()).isEqualTo(1);
        });
        assertThat(confirmationRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getMemberProfileId()).isEqualTo(member.getId());
            assertThat(saved.getConfirmationStatus()).isEqualTo(NutritionConfirmationStatus.CONFIRMED);
            assertThat(saved.getConfirmedAt()).isNotNull();
        });
    }

    @Test
    void familyGuardianCanProxyConfirmAnotherMemberBeforeCutoff() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        roleBinding(FAMILY_GUARDIAN_USER_ID, NutritionRoleCode.GUARDIAN,
                NutritionScopeType.FAMILY, family.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED,
                Instant.now().plusSeconds(3600));

        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(NutritionMealType.DINNER),
                        false, null, "guardian confirmed"), FAMILY_GUARDIAN_USER_ID);

        assertThat(response.confirmationStatus()).isEqualTo(NutritionConfirmationStatus.CONFIRMED);
        assertThat(response.confirmedByUserId()).isEqualTo(FAMILY_GUARDIAN_USER_ID);
        assertThat(response.proxyByUserId()).isEqualTo(FAMILY_GUARDIAN_USER_ID);
        assertThat(response.memberProfileId()).isEqualTo(member.getId());
    }

    @Test
    void profileGuardianCanProxyConfirmAssignedMemberBeforeCutoff() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        roleBinding(PROFILE_GUARDIAN_USER_ID, NutritionRoleCode.PROFILE_GUARDIAN,
                NutritionScopeType.MEMBER_PROFILE, member.getId());
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED,
                Instant.now().plusSeconds(3600));

        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(NutritionMealType.DINNER),
                        false, null, "profile guardian confirmed"), PROFILE_GUARDIAN_USER_ID);

        assertThat(response.confirmationStatus()).isEqualTo(NutritionConfirmationStatus.CONFIRMED);
        assertThat(response.confirmedByUserId()).isEqualTo(PROFILE_GUARDIAN_USER_ID);
        assertThat(response.proxyByUserId()).isEqualTo(PROFILE_GUARDIAN_USER_ID);
        assertThat(response.memberProfileId()).isEqualTo(member.getId());
    }

    @Test
    void repeatedConfirmForSameMemberUpdatesExistingRow() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED,
                Instant.now().plusSeconds(3600));

        MealConfirmationResponse first = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(NutritionMealType.DINNER),
                        false, null, "first"), FIRST_MEMBER_USER_ID);
        MealConfirmationResponse second = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(NutritionMealType.LUNCH),
                        false, null, "updated"), FIRST_MEMBER_USER_ID);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.selectedMealTypes()).containsExactly(NutritionMealType.LUNCH);
        assertThat(second.remark()).isEqualTo("updated");
        assertThat(confirmationRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getId()).isEqualTo(first.id());
            assertThat(saved.getSelectedMealTypes()).contains("LUNCH");
            assertThat(saved.getRemark()).isEqualTo("updated");
        });
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getConfirmedMemberCount())
                .isEqualTo(1);
    }

    @Test
    void highRiskAllergyBlocksConfirmation() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED,
                Instant.now().plusSeconds(3600));
        risk(family.getId(), member.getId(), mealPlan.getId(), NutritionRiskLevel.HIGH, "ALLERGY");

        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(), true,
                        "I understand", "still want it"), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_RISK_BLOCKED");
        assertThat(confirmationRepository.findAll()).isEmpty();
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow().getConfirmedMemberCount())
                .isZero();
    }

    @Test
    void mediumRiskRequiresExplicitRiskConfirmation() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED,
                Instant.now().plusSeconds(3600));
        risk(family.getId(), member.getId(), mealPlan.getId(), NutritionRiskLevel.MEDIUM, "DISLIKE");

        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(), false,
                        null, null), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_MEAL_RISK_CONFIRMATION_REQUIRED");

        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(member.getId(), true, List.of(), true,
                        null, null), FIRST_MEMBER_USER_ID);

        assertThat(response.confirmationStatus()).isEqualTo(NutritionConfirmationStatus.CONFIRMED);
        assertThat(response.riskConfirmed()).isTrue();
        assertThat(confirmationRepository.findAll()).hasSize(1);
    }

    @Test
    void summaryAggregatesConfirmedServingsByDish() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMemberProfilePo luigi = memberProfile(family.getId(), SECOND_MEMBER_USER_ID, "Luigi");
        NutritionMealPlanPo mealPlan = mealPlan(family.getId(), NutritionMealPlanStatus.PUBLISHED,
                Instant.now().plusSeconds(3600));
        NutritionMealPlanItemPo lunch = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.LUNCH,
                "Vegetable Curry", new BigDecimal("1.500"), 0);
        NutritionMealPlanItemPo dinner = mealPlanItem(family.getId(), mealPlan.getId(), NutritionMealType.DINNER,
                "Tomato Pasta", new BigDecimal("2.000"), 1);
        confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(mario.getId(), true, List.of(NutritionMealType.DINNER),
                        false, null, null), FIRST_MEMBER_USER_ID);
        confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                new MealConfirmationRequest(luigi.getId(), true, List.of(), false, null, null),
                SECOND_MEMBER_USER_ID);

        MealPlanSummaryResponse summary = mealPlanService.summary(family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(summary.mealPlanId()).isEqualTo(mealPlan.getId());
        assertThat(summary.confirmedMemberCount()).isEqualTo(2);
        assertThat(summary.dishes()).hasSize(2);
        assertThat(summary.dishes()).filteredOn(dish -> dish.itemId().equals(lunch.getId()))
                .singleElement()
                .satisfies(dish -> {
                    assertThat(dish.dishName()).isEqualTo("Vegetable Curry");
                    assertThat(dish.mealType()).isEqualTo(NutritionMealType.LUNCH);
                    assertThat(dish.servingCount()).isEqualByComparingTo("1.500");
                    assertThat(dish.confirmedServingTotal()).isEqualByComparingTo("1.500");
                });
        assertThat(summary.dishes()).filteredOn(dish -> dish.itemId().equals(dinner.getId()))
                .singleElement()
                .satisfies(dish -> {
                    assertThat(dish.dishName()).isEqualTo("Tomato Pasta");
                    assertThat(dish.mealType()).isEqualTo(NutritionMealType.DINNER);
                    assertThat(dish.servingCount()).isEqualByComparingTo("2.000");
                    assertThat(dish.confirmedServingTotal()).isEqualByComparingTo("4.000");
                });
    }

    private NutritionFamilyPo family(String name, Long ownerUserId) {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setName(name);
        family.setOwnerUserId(ownerUserId);
        family.setStatus(NutritionStatus.ACTIVE);
        return familyRepository.saveAndFlush(family);
    }

    private NutritionMemberProfilePo memberProfile(Long familyId, Long boundUserId, String nickname) {
        NutritionMemberProfilePo memberProfile = new NutritionMemberProfilePo();
        memberProfile.setFamilyId(familyId);
        memberProfile.setBoundUserId(boundUserId);
        memberProfile.setNickname(nickname);
        memberProfile.setMemberType(NutritionMemberType.ADULT);
        memberProfile.setStatus(NutritionStatus.ACTIVE);
        return memberProfileRepository.saveAndFlush(memberProfile);
    }

    private NutritionMealPlanPo mealPlan(Long familyId, NutritionMealPlanStatus status, Instant cutoffAt) {
        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(familyId);
        mealPlan.setPlanDate(LocalDate.of(2026, 7, 1));
        mealPlan.setTitle("Family dinner");
        mealPlan.setStatus(status);
        mealPlan.setConfirmationCutoffAt(cutoffAt);
        return mealPlanRepository.saveAndFlush(mealPlan);
    }

    private NutritionMealPlanItemPo mealPlanItem(Long familyId, Long mealPlanId, NutritionMealType mealType,
                                                 String dishName, BigDecimal servingCount, int sortOrder) {
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(familyId);
        item.setMealPlanId(mealPlanId);
        item.setMealType(mealType);
        item.setDishName(dishName);
        item.setServingCount(servingCount);
        item.setSortOrder(sortOrder);
        item.setStatus(NutritionStatus.ACTIVE);
        return mealPlanItemRepository.saveAndFlush(item);
    }

    private NutritionRiskCheckResultPo risk(Long familyId, Long memberProfileId, Long mealPlanId,
                                            NutritionRiskLevel riskLevel, String ruleCode) {
        NutritionRiskCheckResultPo risk = new NutritionRiskCheckResultPo();
        risk.setFamilyId(familyId);
        risk.setMemberProfileId(memberProfileId);
        risk.setSourceType(MEAL_PLAN_SOURCE_TYPE);
        risk.setSourceId(mealPlanId);
        risk.setRuleCode(ruleCode);
        risk.setRiskLevel(riskLevel);
        risk.setRiskMessage(ruleCode + " risk");
        risk.setResolved(false);
        risk.setStatus(NutritionStatus.ACTIVE);
        return riskCheckResultRepository.saveAndFlush(risk);
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
