package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.MealConfirmationItemRequest;
import top.egon.mario.nutrition.dto.request.MealConfirmationRequest;
import top.egon.mario.nutrition.dto.response.MealConfirmationResponse;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
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
import top.egon.mario.nutrition.repository.NutritionMealOperationLogRepository;
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
 * Verifies dish-level confirmation, proxy authorization, cutoff, and exact summaries.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class MealConfirmationServiceTests {

    private static final String MEAL_PLAN_SOURCE_TYPE = "MEAL_PLAN";
    private static final String MEAL_PLAN_ITEM_SOURCE_TYPE = "MEAL_PLAN_ITEM";
    private static final Long COOK_USER_ID = 9201L;
    private static final Long FIRST_MEMBER_USER_ID = 9202L;
    private static final Long SECOND_MEMBER_USER_ID = 9203L;
    private static final Long FAMILY_GUARDIAN_USER_ID = 9204L;
    private static final Long PROFILE_GUARDIAN_USER_ID = 9205L;
    private static final Long UNRELATED_USER_ID = 9206L;

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
    @Autowired
    private NutritionMealOperationLogRepository operationLogRepository;

    @BeforeEach
    void setUp() {
        confirmationItemRepository.deleteAll();
        confirmationRepository.deleteAll();
        operationLogRepository.deleteAll();
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
    void boundUserCanConfirmOwnProfileAndReloadPersistedDishes() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo item = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Tomato Pasta", 0);

        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, "home dinner", selectedItem(item.getId(), "1.500")),
                FIRST_MEMBER_USER_ID);
        MealConfirmationResponse reloaded = confirmationService.getConfirmation(
                family.getId(), response.id(), FIRST_MEMBER_USER_ID);

        assertThat(response.confirmationStatus()).isEqualTo(NutritionConfirmationStatus.CONFIRMED);
        assertThat(response.confirmedByUserId()).isEqualTo(FIRST_MEMBER_USER_ID);
        assertThat(response.proxyByUserId()).isNull();
        assertThat(reloaded.items()).singleElement().satisfies(saved -> {
            assertThat(saved.mealPlanItemId()).isEqualTo(item.getId());
            assertThat(saved.servingCount()).isEqualByComparingTo("1.500");
            assertThat(saved.selected()).isTrue();
        });
        assertThat(mealPlanRepository.findById(mealPlan.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionMealPlanStatus.CONFIRMING);
            assertThat(saved.getConfirmedMemberCount()).isEqualTo(1);
        });
    }

    @Test
    void awayRequiresNoItemsAndHomeRequiresOneSelectedItem() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo item = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Tomato Pasta", 0);

        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), false, null, selectedItem(item.getId(), "1")), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_AWAY_ITEMS_INVALID");
        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_CONFIRMATION_ITEMS_REQUIRED");

        MealConfirmationResponse away = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), false, "travelling"), FIRST_MEMBER_USER_ID);

        assertThat(away.confirmationStatus()).isEqualTo(NutritionConfirmationStatus.AWAY);
        assertThat(away.items()).isEmpty();
    }

    @Test
    void itemMustBelongToTheSameFamilyAndMealPlan() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionFamilyPo otherFamily = family("Luigi Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanPo sameFamilyOtherPlan = publishedPlan(family.getId());
        NutritionMealPlanPo crossFamilyPlan = publishedPlan(otherFamily.getId());
        NutritionMealPlanItemPo mismatched = mealPlanItem(
                family.getId(), sameFamilyOtherPlan.getId(), NutritionMealType.DINNER, "Other Dinner", 0);
        NutritionMealPlanItemPo crossFamily = mealPlanItem(
                otherFamily.getId(), crossFamilyPlan.getId(), NutritionMealType.DINNER, "Private Dinner", 0);

        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null, selectedItem(mismatched.getId(), "1")), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_CONFIRMATION_ITEM_NOT_FOUND");
        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null, selectedItem(crossFamily.getId(), "1")), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_CONFIRMATION_ITEM_NOT_FOUND");
    }

    @Test
    void familyAndProfileGuardiansCanProxyButUnrelatedUsersCannot() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMemberProfilePo luigi = memberProfile(family.getId(), SECOND_MEMBER_USER_ID, "Luigi");
        roleBinding(FAMILY_GUARDIAN_USER_ID, NutritionRoleCode.GUARDIAN,
                NutritionScopeType.FAMILY, family.getId());
        roleBinding(PROFILE_GUARDIAN_USER_ID, NutritionRoleCode.PROFILE_GUARDIAN,
                NutritionScopeType.MEMBER_PROFILE, luigi.getId());
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo item = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Tomato Pasta", 0);

        MealConfirmationResponse familyProxy = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(mario.getId(), true, null, selectedItem(item.getId(), "1")), FAMILY_GUARDIAN_USER_ID);
        MealConfirmationResponse profileProxy = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(luigi.getId(), true, null, selectedItem(item.getId(), "1")), PROFILE_GUARDIAN_USER_ID);

        assertThat(familyProxy.proxyByUserId()).isEqualTo(FAMILY_GUARDIAN_USER_ID);
        assertThat(profileProxy.proxyByUserId()).isEqualTo(PROFILE_GUARDIAN_USER_ID);
        assertThatThrownBy(() -> confirmationService.updateConfirmation(family.getId(), familyProxy.id(),
                request(mario.getId(), true, null, selectedItem(item.getId(), "2")), UNRELATED_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_FORBIDDEN");
    }

    @Test
    void highRiskBlocksOnlyTheAffectedSelectedDish() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo safe = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Safe Soup", 0);
        NutritionMealPlanItemPo risky = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Peanut Soup", 1);
        risk(family.getId(), member.getId(), MEAL_PLAN_ITEM_SOURCE_TYPE, risky.getId(),
                NutritionRiskLevel.HIGH, "ALLERGY", true, false, false);

        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null, selectedItem(risky.getId(), "1")), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_RISK_BLOCKED");

        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null, selectedItem(safe.getId(), "1")), FIRST_MEMBER_USER_ID);
        assertThat(response.items()).singleElement()
                .extracting(item -> item.mealPlanItemId()).isEqualTo(safe.getId());
    }

    @Test
    void mediumItemRiskRequiresAcknowledgementAndConfiguredNote() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo item = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Cilantro Soup", 0);
        risk(family.getId(), member.getId(), MEAL_PLAN_ITEM_SOURCE_TYPE, item.getId(),
                NutritionRiskLevel.MEDIUM, "DISLIKE", false, true, true);

        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null, selectedItem(item.getId(), "1")), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_RISK_CONFIRMATION_REQUIRED");
        assertThatThrownBy(() -> confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null, acknowledgedItem(item.getId(), "1", null)),
                FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_RISK_NOTE_REQUIRED");

        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null,
                        acknowledgedItem(item.getId(), "1", "remove cilantro")), FIRST_MEMBER_USER_ID);

        assertThat(response.items()).singleElement().satisfies(saved -> {
            assertThat(saved.riskAcknowledged()).isTrue();
            assertThat(saved.adjustmentNote()).isEqualTo("remove cilantro");
        });
    }

    @Test
    void updateReplacesDishRowsBeforeCutoffAndRejectsAtOrAfterCutoff() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo firstDish = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.LUNCH, "Curry", 0);
        NutritionMealPlanItemPo secondDish = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Pasta", 1);
        MealConfirmationResponse created = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, "first", selectedItem(firstDish.getId(), "1")),
                FIRST_MEMBER_USER_ID);

        MealConfirmationResponse updated = confirmationService.updateConfirmation(family.getId(), created.id(),
                request(member.getId(), true, "updated", selectedItem(secondDish.getId(), "2")),
                FIRST_MEMBER_USER_ID);

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.items()).singleElement().satisfies(saved -> {
            assertThat(saved.mealPlanItemId()).isEqualTo(secondDish.getId());
            assertThat(saved.servingCount()).isEqualByComparingTo("2");
        });
        assertThat(confirmationItemRepository.findAll()).singleElement()
                .extracting(item -> item.getMealPlanItemId()).isEqualTo(secondDish.getId());

        NutritionMealPlanPo expired = mealPlanRepository.findById(mealPlan.getId()).orElseThrow();
        expired.setConfirmationCutoffAt(Instant.now().minusMillis(1));
        mealPlanRepository.saveAndFlush(expired);
        assertThatThrownBy(() -> confirmationService.updateConfirmation(family.getId(), created.id(),
                request(member.getId(), true, null, selectedItem(firstDish.getId(), "1")),
                FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_CONFIRMATION_CLOSED");
    }

    @Test
    void exactSummaryUsesPersistedSelectionsAndMemberStates() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMemberProfilePo luigi = memberProfile(family.getId(), SECOND_MEMBER_USER_ID, "Luigi");
        memberProfile(family.getId(), null, "Toad");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo lunch = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.LUNCH, "Vegetable Curry", 0);
        NutritionMealPlanItemPo dinner = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Tomato Pasta", 1);
        confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(mario.getId(), true, "less salt",
                        selectedItem(lunch.getId(), "1.500"), unselectedItem(dinner.getId())),
                FIRST_MEMBER_USER_ID);
        confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(luigi.getId(), true, "extra sauce", selectedItem(dinner.getId(), "2.000")),
                SECOND_MEMBER_USER_ID);
        risk(family.getId(), mario.getId(), MEAL_PLAN_SOURCE_TYPE, mealPlan.getId(),
                NutritionRiskLevel.MEDIUM, "DIET_GOAL", false, true, false);

        MealPlanSummaryResponse summary = mealPlanService.summary(family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(summary.activeMemberCount()).isEqualTo(3);
        assertThat(summary.confirmedMemberCount()).isEqualTo(2);
        assertThat(summary.awayMemberCount()).isZero();
        assertThat(summary.unconfirmedMemberCount()).isEqualTo(1);
        assertThat(summary.riskCounts()).containsEntry(NutritionRiskLevel.MEDIUM, 1L);
        assertThat(summary.remarks()).containsExactly("less salt", "extra sauce");
        assertThat(summary.readyForShopping()).isFalse();
        assertThat(summary.dishes()).filteredOn(dish -> dish.itemId().equals(lunch.getId()))
                .singleElement().satisfies(dish -> {
                    assertThat(dish.selectedMemberCount()).isEqualTo(1);
                    assertThat(dish.confirmedServingTotal()).isEqualByComparingTo("1.500");
                });
        assertThat(summary.dishes()).filteredOn(dish -> dish.itemId().equals(dinner.getId()))
                .singleElement().satisfies(dish -> {
                    assertThat(dish.selectedMemberCount()).isEqualTo(1);
                    assertThat(dish.confirmedServingTotal()).isEqualByComparingTo("2.000");
                });
    }

    @Test
    void closeRequiresExplicitEarlyFlagAndExpiresPendingMembers() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo mario = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMemberProfilePo luigi = memberProfile(family.getId(), SECOND_MEMBER_USER_ID, "Luigi");
        NutritionMemberProfilePo toad = memberProfile(family.getId(), null, "Toad");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo item = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Tomato Pasta", 0);
        MealConfirmationResponse confirmed = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(mario.getId(), true, null, selectedItem(item.getId(), "1")), FIRST_MEMBER_USER_ID);
        confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(luigi.getId(), false, "away"), SECOND_MEMBER_USER_ID);

        assertThatThrownBy(() -> mealPlanService.closeConfirmation(
                family.getId(), mealPlan.getId(), COOK_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_CONFIRMATION_CUTOFF_NOT_REACHED");

        MealPlanResponse closed = mealPlanService.closeConfirmation(
                family.getId(), mealPlan.getId(), true, COOK_USER_ID);
        MealPlanSummaryResponse summary = mealPlanService.summary(
                family.getId(), mealPlan.getId(), COOK_USER_ID);

        assertThat(closed.status()).isEqualTo(NutritionMealPlanStatus.CONFIRM_CLOSED);
        assertThat(summary.confirmedMemberCount()).isEqualTo(1);
        assertThat(summary.awayMemberCount()).isEqualTo(1);
        assertThat(summary.unconfirmedMemberCount()).isEqualTo(1);
        assertThat(summary.readyForShopping()).isTrue();
        assertThat(confirmationRepository.findByMealPlanIdAndMemberProfileIdAndDeletedFalse(
                mealPlan.getId(), toad.getId())).get().extracting(row -> row.getConfirmationStatus())
                .isEqualTo(NutritionConfirmationStatus.EXPIRED);
        assertThatThrownBy(() -> confirmationService.updateConfirmation(family.getId(), confirmed.id(),
                request(mario.getId(), true, null, selectedItem(item.getId(), "2")), FIRST_MEMBER_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_MEAL_PLAN_STATUS_INVALID");
    }

    @Test
    void cookCanListConfirmationsButUnrelatedUserCannotReadAnotherProfile() {
        NutritionFamilyPo family = family("Mario Family", COOK_USER_ID);
        NutritionMemberProfilePo member = memberProfile(family.getId(), FIRST_MEMBER_USER_ID, "Mario");
        NutritionMealPlanPo mealPlan = publishedPlan(family.getId());
        NutritionMealPlanItemPo item = mealPlanItem(
                family.getId(), mealPlan.getId(), NutritionMealType.DINNER, "Tomato Pasta", 0);
        MealConfirmationResponse response = confirmationService.confirmMeal(family.getId(), mealPlan.getId(),
                request(member.getId(), true, null, selectedItem(item.getId(), "1")), FIRST_MEMBER_USER_ID);

        assertThat(confirmationService.listConfirmations(family.getId(), mealPlan.getId(), COOK_USER_ID))
                .singleElement().extracting(MealConfirmationResponse::id).isEqualTo(response.id());
        assertThatThrownBy(() -> confirmationService.getConfirmation(
                family.getId(), response.id(), UNRELATED_USER_ID))
                .isInstanceOf(NutritionException.class)
                .extracting("code").isEqualTo("NUTRITION_FORBIDDEN");
    }

    private MealConfirmationRequest request(Long memberProfileId, boolean eatAtHome, String remark,
                                            MealConfirmationItemRequest... items) {
        return new MealConfirmationRequest(memberProfileId, eatAtHome, List.of(items), remark);
    }

    private MealConfirmationItemRequest selectedItem(Long itemId, String servings) {
        return new MealConfirmationItemRequest(
                itemId, true, new BigDecimal(servings), false, null);
    }

    private MealConfirmationItemRequest acknowledgedItem(Long itemId, String servings, String note) {
        return new MealConfirmationItemRequest(
                itemId, true, new BigDecimal(servings), true, note);
    }

    private MealConfirmationItemRequest unselectedItem(Long itemId) {
        return new MealConfirmationItemRequest(itemId, false, BigDecimal.ONE, false, null);
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

    private NutritionMealPlanPo publishedPlan(Long familyId) {
        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(familyId);
        mealPlan.setPlanDate(LocalDate.of(2026, 7, 1));
        mealPlan.setTitle("Family dinner");
        mealPlan.setStatus(NutritionMealPlanStatus.PUBLISHED);
        mealPlan.setConfirmationCutoffAt(Instant.now().plusSeconds(3600));
        return mealPlanRepository.saveAndFlush(mealPlan);
    }

    private NutritionMealPlanItemPo mealPlanItem(Long familyId, Long mealPlanId, NutritionMealType mealType,
                                                 String dishName, int sortOrder) {
        NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
        item.setFamilyId(familyId);
        item.setMealPlanId(mealPlanId);
        item.setMealType(mealType);
        item.setDishName(dishName);
        item.setServingCount(BigDecimal.ONE);
        item.setSortOrder(sortOrder);
        item.setStatus(NutritionStatus.ACTIVE);
        return mealPlanItemRepository.saveAndFlush(item);
    }

    private NutritionRiskCheckResultPo risk(Long familyId, Long memberProfileId, String sourceType,
                                            Long sourceId, NutritionRiskLevel riskLevel, String ruleCode,
                                            boolean blocking, boolean requiresConfirmation, boolean requiresNote) {
        NutritionRiskCheckResultPo risk = new NutritionRiskCheckResultPo();
        risk.setFamilyId(familyId);
        risk.setMemberProfileId(memberProfileId);
        risk.setSourceType(sourceType);
        risk.setSourceId(sourceId);
        risk.setRuleCode(ruleCode);
        risk.setRiskLevel(riskLevel);
        risk.setRiskMessage(ruleCode + " risk");
        risk.setRiskSnapshot("{\"blocking\":" + blocking
                + ",\"requiresConfirmation\":" + requiresConfirmation
                + ",\"requiresNote\":" + requiresNote + "}");
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
