package top.egon.mario.investment.portfolio.risk.rule;

import top.egon.mario.investment.portfolio.risk.InvestmentRiskCheckResult;
import top.egon.mario.investment.portfolio.risk.InvestmentRiskRuleCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

final class RiskRuleResults {

    private RiskRuleResults() {
    }

    static InvestmentRiskCheckResult result(
            InvestmentRiskRuleCode code, boolean passed, BigDecimal observed, BigDecimal limit,
            String success, String failure, Instant checkedAt) {
        return new InvestmentRiskCheckResult(
                code, passed, observed, limit, passed ? success : failure,
                Map.of("group", group(code)), checkedAt);
    }

    static BigDecimal booleanValue(boolean value) {
        return value ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private static String group(InvestmentRiskRuleCode code) {
        return switch (code) {
            case TRADING_SWITCH_ENABLED, AGENT_AUTO_TRADE_ENABLED -> "SWITCH";
            case INSTRUMENT_SUBSCRIBED, INSTRUMENT_TRADABLE, MARKET_DATA_FRESH,
                    MARK_PRICE_AVAILABLE, POSITION_TIER_AVAILABLE, FUNDING_DATA_AVAILABLE -> "MARKET";
            case ORDER_NOTIONAL_LIMIT, POSITION_NOTIONAL_LIMIT, GROSS_EXPOSURE_LIMIT,
                    MAX_LEVERAGE_LIMIT, MAX_OPEN_POSITIONS_LIMIT -> "EXPOSURE";
            case DAILY_LOSS_LIMIT, MAX_DRAWDOWN_LIMIT -> "LOSS";
            default -> "ORDER";
        };
    }
}
