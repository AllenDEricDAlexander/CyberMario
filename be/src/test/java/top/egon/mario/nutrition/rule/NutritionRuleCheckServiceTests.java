package top.egon.mario.nutrition.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.po.enums.NutritionRiskLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;
import top.egon.mario.nutrition.service.rule.BudgetContext;
import top.egon.mario.nutrition.service.rule.MemberRuleProfile;
import top.egon.mario.nutrition.service.rule.NutritionRuleCheckService;
import top.egon.mario.nutrition.service.rule.RuleCheckRequest;
import top.egon.mario.nutrition.service.rule.RuleIngredient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies deterministic local nutrition risk rules.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionRuleCheckServiceTests {

    private static final Long FAMILY_ID = 9101L;
    private static final String SOURCE_TYPE = "RECIPE";
    private static final Long SOURCE_ID = 9201L;

    @Autowired
    private NutritionRuleCheckService ruleCheckService;
    @Autowired
    private NutritionRiskCheckResultRepository riskCheckResultRepository;

    @BeforeEach
    void setUp() {
        riskCheckResultRepository.deleteAll();
    }

    @Test
    void allergyRuleCreatesHighRiskBlockingResult() {
        RuleCheckRequest request = request(
                List.of(new MemberRuleProfile(9301L, List.of("PEANUT"), List.of(), List.of(), null)),
                List.of(new RuleIngredient("Peanut Butter", List.of("LEGUME"))),
                NutritionTotals.zero(),
                new BigDecimal("12.00"),
                new BudgetContext(new BigDecimal("30.00")));

        var results = ruleCheckService.check(request);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.memberProfileId()).isEqualTo(9301L);
            assertThat(result.ruleCode()).isEqualTo("ALLERGY");
            assertThat(result.riskLevel()).isEqualTo(NutritionRiskLevel.HIGH);
            assertThat(result.blocking()).isTrue();
            assertThat(result.requiresConfirmation()).isFalse();
            assertThat(result.riskMessage()).contains("PEANUT");
        });
        assertThat(riskCheckResultRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getFamilyId()).isEqualTo(FAMILY_ID);
            assertThat(saved.getMemberProfileId()).isEqualTo(9301L);
            assertThat(saved.getSourceType()).isEqualTo(SOURCE_TYPE);
            assertThat(saved.getSourceId()).isEqualTo(SOURCE_ID);
            assertThat(saved.getRuleCode()).isEqualTo("ALLERGY");
            assertThat(saved.getRiskLevel()).isEqualTo(NutritionRiskLevel.HIGH);
            assertThat(saved.getRiskSnapshot()).contains("\"blocking\":true");
            assertThat(saved.getRiskSnapshot()).contains("\"matchedTag\":\"PEANUT\"");
            assertThat(saved.getRiskSnapshot()).contains("\"matchedIngredient\":\"Peanut Butter\"");
        });
    }

    @Test
    void dislikeRuleCreatesMediumRiskConfirmableResult() {
        RuleCheckRequest request = request(
                List.of(new MemberRuleProfile(9302L, List.of(), List.of("CILANTRO"), List.of(), null)),
                List.of(new RuleIngredient("Fresh Cilantro", List.of("HERB"))),
                NutritionTotals.zero(),
                new BigDecimal("8.00"),
                new BudgetContext(new BigDecimal("30.00")));

        var results = ruleCheckService.check(request);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.memberProfileId()).isEqualTo(9302L);
            assertThat(result.ruleCode()).isEqualTo("DISLIKE");
            assertThat(result.riskLevel()).isEqualTo(NutritionRiskLevel.MEDIUM);
            assertThat(result.blocking()).isFalse();
            assertThat(result.requiresConfirmation()).isTrue();
            assertThat(result.riskMessage()).contains("CILANTRO");
        });
        assertThat(riskCheckResultRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getRiskSnapshot()).contains("\"matchedTag\":\"CILANTRO\"");
            assertThat(saved.getRiskSnapshot()).contains("\"matchedIngredient\":\"Fresh Cilantro\"");
        });
    }

    @Test
    void dietGoalMatchesLowercaseWhitespaceAndCreatesConfirmableSodiumRisk() {
        RuleCheckRequest request = request(
                List.of(new MemberRuleProfile(9303L, List.of(), List.of(), List.of(" low_sodium "), null)),
                List.of(new RuleIngredient("Soup", List.of())),
                new NutritionTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("700.000"), BigDecimal.ZERO, BigDecimal.ZERO),
                new BigDecimal("8.00"),
                new BudgetContext(new BigDecimal("30.00")));

        var results = ruleCheckService.check(request);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.memberProfileId()).isEqualTo(9303L);
            assertThat(result.ruleCode()).isEqualTo("DIET_GOAL");
            assertThat(result.riskLevel()).isEqualTo(NutritionRiskLevel.MEDIUM);
            assertThat(result.blocking()).isFalse();
            assertThat(result.requiresConfirmation()).isTrue();
            assertThat(result.riskMessage()).contains("LOW_SODIUM");
        });
        assertThat(riskCheckResultRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getRiskSnapshot()).contains("\"dietGoal\":\"LOW_SODIUM\"");
            assertThat(saved.getRiskSnapshot()).contains("\"actualSodium\":700.000");
            assertThat(saved.getRiskSnapshot()).contains("\"sodiumLimit\":600.000");
        });
    }

    @Test
    void budgetRuleCreatesWarningWhenEstimatedCostExceedsMealLimit() {
        RuleCheckRequest request = request(
                List.of(),
                List.of(new RuleIngredient("Salmon", List.of("FISH"))),
                NutritionTotals.zero(),
                new BigDecimal("45.00"),
                new BudgetContext(new BigDecimal("30.00")));

        var results = ruleCheckService.check(request);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.memberProfileId()).isNull();
            assertThat(result.ruleCode()).isEqualTo("BUDGET");
            assertThat(result.riskLevel()).isEqualTo(NutritionRiskLevel.MEDIUM);
            assertThat(result.blocking()).isFalse();
            assertThat(result.requiresConfirmation()).isFalse();
            assertThat(result.riskMessage()).contains("45.00", "30.00");
        });
        assertThat(riskCheckResultRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getRiskSnapshot()).contains("\"estimatedCost\":45.00");
            assertThat(saved.getRiskSnapshot()).contains("\"perMealLimit\":30.00");
        });
    }

    @Test
    void repeatedCheckArchivesPriorActiveRiskWhenAllergyDisappears() {
        RuleCheckRequest allergyRequest = request(
                List.of(new MemberRuleProfile(9304L, List.of("PEANUT"), List.of(), List.of(), null)),
                List.of(new RuleIngredient("Peanut Butter", List.of())),
                NutritionTotals.zero(),
                new BigDecimal("8.00"),
                new BudgetContext(new BigDecimal("30.00")));
        ruleCheckService.check(allergyRequest);

        RuleCheckRequest clearRequest = request(
                List.of(new MemberRuleProfile(9304L, List.of("PEANUT"), List.of(), List.of(), null)),
                List.of(new RuleIngredient("Rice", List.of())),
                NutritionTotals.zero(),
                new BigDecimal("8.00"),
                new BudgetContext(new BigDecimal("30.00")));
        var results = ruleCheckService.check(clearRequest);

        assertThat(results).isEmpty();
        assertThat(riskCheckResultRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getRuleCode()).isEqualTo("ALLERGY");
            assertThat(saved.isResolved()).isTrue();
            assertThat(saved.getStatus()).isEqualTo(NutritionStatus.ARCHIVED);
        });
    }

    private RuleCheckRequest request(List<MemberRuleProfile> memberProfiles, List<RuleIngredient> ingredients,
                                     NutritionTotals nutritionTotals, BigDecimal estimatedCost,
                                     BudgetContext budgetContext) {
        return new RuleCheckRequest(FAMILY_ID, SOURCE_TYPE, SOURCE_ID, memberProfiles, ingredients,
                nutritionTotals, estimatedCost, budgetContext);
    }
}
