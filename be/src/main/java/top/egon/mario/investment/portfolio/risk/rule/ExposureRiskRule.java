package top.egon.mario.investment.portfolio.risk.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskContext;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRule;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
public class ExposureRiskRule implements InvestmentRiskRule {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<InvestmentRiskCheckResult> evaluate(
            InvestmentRiskContext context, InvestmentRiskLimits limits, Instant checkedAt) {
        return List.of(
                ceiling(InvestmentRiskRuleCode.ORDER_NOTIONAL_LIMIT, context.order().orderNotional(),
                        limits.maxOrderNotional(), "Order notional is within limit",
                        "Order notional exceeds limit", checkedAt),
                ceiling(InvestmentRiskRuleCode.POSITION_NOTIONAL_LIMIT,
                        context.portfolio().resultingPositionNotional(), limits.maxPositionNotional(),
                        "Position notional is within limit", "Position notional exceeds limit", checkedAt),
                ceiling(InvestmentRiskRuleCode.GROSS_EXPOSURE_LIMIT,
                        context.portfolio().resultingGrossExposureNotional(), limits.maxGrossExposureNotional(),
                        "Gross exposure is within limit", "Gross exposure exceeds limit", checkedAt),
                ceiling(InvestmentRiskRuleCode.MAX_LEVERAGE_LIMIT, context.order().leverage(),
                        limits.maxLeverage(), "Leverage is within limit", "Leverage exceeds limit", checkedAt),
                ceiling(InvestmentRiskRuleCode.MAX_OPEN_POSITIONS_LIMIT,
                        BigDecimal.valueOf(context.portfolio().resultingOpenPositions()),
                        BigDecimal.valueOf(limits.maxOpenPositions()),
                        "Open position count is within limit", "Open position count exceeds limit", checkedAt));
    }

    private static InvestmentRiskCheckResult ceiling(
            InvestmentRiskRuleCode code, BigDecimal observed, BigDecimal limit,
            String success, String failure, Instant checkedAt) {
        boolean passed = observed.signum() >= 0 && observed.compareTo(limit) <= 0;
        return RiskRuleResults.result(code, passed, observed, limit, success, failure, checkedAt);
    }
}
