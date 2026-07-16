package top.egon.mario.investment.portfolio.risk.rule;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskContext;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskLimits;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRule;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
public class SwitchRiskRule implements InvestmentRiskRule {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<InvestmentRiskCheckResult> evaluate(
            InvestmentRiskContext context, InvestmentRiskLimits limits, Instant checkedAt) {
        boolean trading = context.account().tradingEnabled();
        boolean agent = context.source() != InvestmentRiskSource.AGENT
                || context.account().agentAutoTradeEnabled();
        return List.of(
                RiskRuleResults.result(InvestmentRiskRuleCode.TRADING_SWITCH_ENABLED, trading,
                        RiskRuleResults.booleanValue(trading), BigDecimal.ONE,
                        "Paper trading is enabled", "Paper trading switch is disabled", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.AGENT_AUTO_TRADE_ENABLED, agent,
                        RiskRuleResults.booleanValue(agent), BigDecimal.ONE,
                        "Agent auto trade is enabled or not required",
                        "Agent auto trade switch is disabled", checkedAt));
    }
}
