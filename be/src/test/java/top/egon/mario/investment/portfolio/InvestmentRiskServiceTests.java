package top.egon.mario.investment.portfolio;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskContext;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskService;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;
import top.egon.mario.investment.portfolio.risk.rule.ExposureRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.LossRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.MarketRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.OrderRiskRule;
import top.egon.mario.investment.portfolio.risk.rule.SwitchRiskRule;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentRiskServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    private final InvestmentRiskService service = new InvestmentRiskService(List.of(
            new OrderRiskRule(), new MarketRiskRule(), new SwitchRiskRule(),
            new LossRiskRule(), new ExposureRiskRule()), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void evaluatesEveryMandatoryRuleInStableOrderForAValidIntent() {
        var evaluation = service.evaluate(validContext());

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.results()).extracting(result -> result.ruleCode())
                .containsExactly(InvestmentRiskRuleCode.values());
        assertThat(evaluation.results()).allSatisfy(result -> {
            assertThat(result.passed()).isTrue();
            assertThat(result.checkedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void failsClosedWithAnIndependentResultForEveryMandatoryRule() {
        InvestmentRiskLimits limits = limits();
        InvestmentRiskContext context = new InvestmentRiskContext(
                InvestmentRiskSource.AGENT,
                new InvestmentRiskContext.AccountState(false, false),
                new InvestmentRiskContext.MarketState(false, false, null, null, false, false),
                new InvestmentRiskContext.OrderState(
                        bd("2000"), bd("20"), 20L, 0L, bd("2000"), bd("50"), true, false),
                new InvestmentRiskContext.PortfolioState(
                        bd("6000"), bd("12000"), 6L, bd("600"), bd("0.30"), bd("1000")),
                limits,
                null);

        var evaluation = service.evaluate(context);

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.results()).hasSize(InvestmentRiskRuleCode.values().length);
        assertThat(evaluation.results()).filteredOn(result -> !result.passed())
                .extracting(result -> result.ruleCode())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(InvestmentRiskRuleCode.class));
    }

    @Test
    void callerLimitsMayTightenButCanNeverWidenAccountLimits() {
        InvestmentRiskLimits attemptedWidening = new InvestmentRiskLimits(
                bd("100"), bd("100000"), bd("100000"), bd("100000"), 100L,
                bd("100000"), bd("0.90"), 1000L, 0L, 3600L, bd("500"));
        InvestmentRiskContext widened = contextWithOrderNotional(bd("1500"), attemptedWidening);

        var widenedEvaluation = service.evaluate(widened);

        assertThat(result(widenedEvaluation, InvestmentRiskRuleCode.ORDER_NOTIONAL_LIMIT).passed()).isFalse();
        assertThat(result(widenedEvaluation, InvestmentRiskRuleCode.ORDER_NOTIONAL_LIMIT).limitValue())
                .isEqualByComparingTo("1000");

        InvestmentRiskLimits tightened = new InvestmentRiskLimits(
                bd("5"), bd("500"), bd("2500"), bd("8000"), 3L,
                bd("250"), bd("0.10"), 10L, 60L, 30L, bd("10"));
        var tightenedEvaluation = service.evaluate(contextWithOrderNotional(bd("750"), tightened));

        assertThat(result(tightenedEvaluation, InvestmentRiskRuleCode.ORDER_NOTIONAL_LIMIT).passed()).isFalse();
        assertThat(result(tightenedEvaluation, InvestmentRiskRuleCode.ORDER_NOTIONAL_LIMIT).limitValue())
                .isEqualByComparingTo("500");
    }

    private static InvestmentRiskContext validContext() {
        return contextWithOrderNotional(bd("500"), null);
    }

    private static InvestmentRiskContext contextWithOrderNotional(
            BigDecimal orderNotional, InvestmentRiskLimits callerLimits) {
        return new InvestmentRiskContext(
                InvestmentRiskSource.AGENT,
                new InvestmentRiskContext.AccountState(true, true),
                new InvestmentRiskContext.MarketState(true, true, 10L, bd("100"), true, true),
                new InvestmentRiskContext.OrderState(
                        orderNotional, bd("5"), 2L, 90L, bd("100"), bd("5"), false, true),
                new InvestmentRiskContext.PortfolioState(
                        bd("2000"), bd("4000"), 2L, bd("100"), bd("0.05"), bd("5000")),
                limits(),
                callerLimits);
    }

    private static InvestmentRiskLimits limits() {
        return new InvestmentRiskLimits(
                bd("10"), bd("1000"), bd("5000"), bd("10000"), 5L,
                bd("500"), bd("0.20"), 20L, 30L, 60L, bd("25"));
    }

    private static top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult result(
            top.egon.mario.investment.portfolio.risk.InvestmentRiskEvaluation evaluation,
            InvestmentRiskRuleCode ruleCode) {
        return evaluation.results().stream()
                .filter(result -> result.ruleCode() == ruleCode)
                .findFirst()
                .orElseThrow();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
