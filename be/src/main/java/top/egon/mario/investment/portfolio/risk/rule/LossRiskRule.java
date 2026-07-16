package top.egon.mario.investment.portfolio.risk.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskContext;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRule;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;

import java.time.Instant;
import java.util.List;

@Component
public class LossRiskRule implements InvestmentRiskRule {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<InvestmentRiskCheckResult> evaluate(
            InvestmentRiskContext context, InvestmentRiskLimits limits, Instant checkedAt) {
        boolean dailyLoss = context.portfolio().dailyLossAmount().signum() >= 0
                && context.portfolio().dailyLossAmount().compareTo(limits.maxDailyLossAmount()) <= 0;
        boolean drawdown = context.portfolio().drawdownRatio().signum() >= 0
                && context.portfolio().drawdownRatio().compareTo(limits.maxDrawdownRatio()) <= 0;
        return List.of(
                RiskRuleResults.result(InvestmentRiskRuleCode.DAILY_LOSS_LIMIT, dailyLoss,
                        context.portfolio().dailyLossAmount(), limits.maxDailyLossAmount(),
                        "Daily loss is within limit", "Daily loss exceeds limit", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.MAX_DRAWDOWN_LIMIT, drawdown,
                        context.portfolio().drawdownRatio(), limits.maxDrawdownRatio(),
                        "Drawdown is within limit", "Drawdown exceeds limit", checkedAt));
    }
}
