package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.response.NutritionHomeOverviewResponse;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionMealConfirmationPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.NutritionRecordPo;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;
import top.egon.mario.nutrition.po.enums.NutritionConfirmationStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionShoppingListStatus;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionBudgetRuleRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealConfirmationRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecordRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListItemRepository;
import top.egon.mario.nutrition.repository.NutritionShoppingListRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.service.NutritionHomeQueryService;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the home overview is a read-only projection over persisted workflow data.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionHomeQueryServiceTests {

    private static final Long OWNER_ID = 9601L;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 13);

    @Autowired
    private NutritionHomeQueryService homeQueryService;
    @Autowired
    private NutritionRecordRepository recordRepository;
    @Autowired
    private NutritionShoppingListItemRepository shoppingListItemRepository;
    @Autowired
    private NutritionShoppingListRepository shoppingListRepository;
    @Autowired
    private NutritionMealConfirmationItemRepository confirmationItemRepository;
    @Autowired
    private NutritionMealConfirmationRepository confirmationRepository;
    @Autowired
    private NutritionMealPlanItemRepository mealPlanItemRepository;
    @Autowired
    private NutritionMealPlanRepository mealPlanRepository;
    @Autowired
    private NutritionRiskCheckResultRepository riskRepository;
    @Autowired
    private NutritionBudgetRuleRepository budgetRuleRepository;
    @Autowired
    private NutritionMemberProfileRepository memberProfileRepository;
    @Autowired
    private NutritionFamilyRepository familyRepository;

    @BeforeEach
    void setUp() {
        recordRepository.deleteAll();
        shoppingListItemRepository.deleteAll();
        shoppingListRepository.deleteAll();
        confirmationItemRepository.deleteAll();
        confirmationRepository.deleteAll();
        riskRepository.deleteAll();
        mealPlanItemRepository.deleteAll();
        mealPlanRepository.deleteAll();
        budgetRuleRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
    }

    @Test
    void overviewUsesPersistedPlanConfirmationShoppingCostAndRecordState() {
        NutritionFamilyPo family = new NutritionFamilyPo();
        family.setOwnerUserId(OWNER_ID);
        family.setName("Mario Family");
        family.setStatus(NutritionStatus.ACTIVE);
        family = familyRepository.saveAndFlush(family);
        NutritionMemberProfilePo member = new NutritionMemberProfilePo();
        member.setFamilyId(family.getId());
        member.setBoundUserId(OWNER_ID);
        member.setNickname("Mario");
        member.setMemberType(NutritionMemberType.ADULT);
        member.setStatus(NutritionStatus.ACTIVE);
        member = memberProfileRepository.saveAndFlush(member);
        NutritionMealPlanPo plan = new NutritionMealPlanPo();
        plan.setFamilyId(family.getId());
        plan.setPlanDate(DATE);
        plan.setTitle("Sunday dinner");
        plan.setStatus(NutritionMealPlanStatus.COMPLETED);
        plan.setEstimatedCost(new BigDecimal("50.00"));
        plan = mealPlanRepository.saveAndFlush(plan);
        NutritionMealConfirmationPo confirmation = new NutritionMealConfirmationPo();
        confirmation.setFamilyId(family.getId());
        confirmation.setMealPlanId(plan.getId());
        confirmation.setMemberProfileId(member.getId());
        confirmation.setConfirmedByUserId(OWNER_ID);
        confirmation.setConfirmationStatus(NutritionConfirmationStatus.CONFIRMED);
        confirmation.setEatAtHome(true);
        confirmationRepository.saveAndFlush(confirmation);
        NutritionShoppingListPo shoppingList = new NutritionShoppingListPo();
        shoppingList.setFamilyId(family.getId());
        shoppingList.setMealPlanId(plan.getId());
        shoppingList.setListDate(DATE);
        shoppingList.setStatus(NutritionShoppingListStatus.PURCHASED);
        shoppingList.setTitle("Sunday shopping");
        shoppingList.setEstimatedTotalPrice(new BigDecimal("50.00"));
        shoppingList.setActualTotalPrice(new BigDecimal("42.00"));
        shoppingListRepository.saveAndFlush(shoppingList);
        NutritionRecordPo record = new NutritionRecordPo();
        record.setFamilyId(family.getId());
        record.setMemberProfileId(member.getId());
        record.setMealPlanId(plan.getId());
        record.setRecordDate(DATE);
        record.setMealType(NutritionMealType.DINNER);
        record.setSourceType("MEAL_PLAN");
        record.setCalories(new BigDecimal("450.000"));
        record.setStatus(NutritionStatus.ACTIVE);
        recordRepository.saveAndFlush(record);

        NutritionHomeOverviewResponse overview = homeQueryService.overview(family.getId(), DATE, OWNER_ID);

        assertThat(overview.mealPlans()).extracting(planResponse -> planResponse.id()).containsExactly(plan.getId());
        assertThat(overview.confirmedMemberCount()).isEqualTo(1);
        assertThat(overview.awayMemberCount()).isZero();
        assertThat(overview.unconfirmedMemberCount()).isZero();
        assertThat(overview.shoppingState()).isEqualTo(NutritionShoppingListStatus.PURCHASED);
        assertThat(overview.actualCost()).isEqualByComparingTo("42.00");
        assertThat(overview.estimatedCost()).isEqualByComparingTo("50.00");
        assertThat(overview.nutritionRecordReady()).isTrue();
    }
}
