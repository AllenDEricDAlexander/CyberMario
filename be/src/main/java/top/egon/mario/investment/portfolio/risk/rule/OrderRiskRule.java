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
public class OrderRiskRule implements InvestmentRiskRule {

    @Override
    public int order() {
        return 50;
    }

    @Override
    public List<InvestmentRiskCheckResult> evaluate(
            InvestmentRiskContext context, InvestmentRiskLimits limits, Instant checkedAt) {
        InvestmentRiskContext.OrderState order = context.order();
        long requestedHourlyCount = order.ordersLastHour() + 1;
        boolean rate = order.ordersLastHour() >= 0 && requestedHourlyCount <= limits.maxOrdersPerHour();
        boolean cooldown = order.secondsSinceLastOrder() == null
                || (order.secondsSinceLastOrder() >= 0
                && order.secondsSinceLastOrder() >= limits.cooldownSeconds());
        boolean margin = context.portfolio().availableMargin().signum() >= 0
                && order.requiredMargin().signum() >= 0
                && context.portfolio().availableMargin().compareTo(order.requiredMargin()) >= 0;
        boolean slippage = order.slippageBps().signum() >= 0
                && order.slippageBps().compareTo(limits.maxSlippageBps()) <= 0;
        boolean reduceOnly = !order.reduceOnly() || order.reduceOnlyValid();
        return List.of(
                RiskRuleResults.result(InvestmentRiskRuleCode.ORDER_RATE_LIMIT, rate,
                        BigDecimal.valueOf(requestedHourlyCount), BigDecimal.valueOf(limits.maxOrdersPerHour()),
                        "Hourly order rate is within limit", "Hourly order rate exceeds limit", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.COOLDOWN_LIMIT, cooldown,
                        order.secondsSinceLastOrder() == null ? null
                                : BigDecimal.valueOf(order.secondsSinceLastOrder()),
                        BigDecimal.valueOf(limits.cooldownSeconds()),
                        "Order cooldown has elapsed", "Order cooldown has not elapsed", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.AVAILABLE_MARGIN_LIMIT, margin,
                        order.requiredMargin(), context.portfolio().availableMargin(),
                        "Available margin covers the order", "Available margin is insufficient", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.MAX_SLIPPAGE_LIMIT, slippage,
                        order.slippageBps(), limits.maxSlippageBps(),
                        "Slippage is within limit", "Slippage exceeds limit", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.REDUCE_ONLY_VALIDATION, reduceOnly,
                        RiskRuleResults.booleanValue(reduceOnly), BigDecimal.ONE,
                        "Reduce-only semantics are valid", "Reduce-only order would increase exposure", checkedAt));
    }
}
