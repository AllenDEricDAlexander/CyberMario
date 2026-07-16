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
public class MarketRiskRule implements InvestmentRiskRule {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<InvestmentRiskCheckResult> evaluate(
            InvestmentRiskContext context, InvestmentRiskLimits limits, Instant checkedAt) {
        InvestmentRiskContext.MarketState market = context.market();
        boolean fresh = market.marketDataAgeSeconds() != null
                && market.marketDataAgeSeconds() >= 0
                && market.marketDataAgeSeconds() <= limits.maxMarketDataAgeSeconds();
        boolean markAvailable = market.markPrice() != null && market.markPrice().signum() > 0;
        return List.of(
                booleanResult(InvestmentRiskRuleCode.INSTRUMENT_SUBSCRIBED, market.instrumentSubscribed(),
                        "Instrument is subscribed", "Instrument is outside the code subscription", checkedAt),
                booleanResult(InvestmentRiskRuleCode.INSTRUMENT_TRADABLE, market.instrumentTradable(),
                        "Instrument is tradable", "Instrument is not tradable", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.MARKET_DATA_FRESH, fresh,
                        market.marketDataAgeSeconds() == null ? null
                                : BigDecimal.valueOf(market.marketDataAgeSeconds()),
                        BigDecimal.valueOf(limits.maxMarketDataAgeSeconds()),
                        "Market data is fresh", "Market data is missing or stale", checkedAt),
                RiskRuleResults.result(InvestmentRiskRuleCode.MARK_PRICE_AVAILABLE, markAvailable,
                        market.markPrice(), null, "Mark price is available",
                        "Positive mark price is unavailable", checkedAt),
                booleanResult(InvestmentRiskRuleCode.POSITION_TIER_AVAILABLE, market.positionTierAvailable(),
                        "Position tier is available", "Position tier is unavailable", checkedAt),
                booleanResult(InvestmentRiskRuleCode.FUNDING_DATA_AVAILABLE, market.fundingDataAvailable(),
                        "Funding data is available", "Funding data is unavailable", checkedAt));
    }

    private static InvestmentRiskCheckResult booleanResult(
            InvestmentRiskRuleCode code, boolean passed, String success, String failure, Instant checkedAt) {
        return RiskRuleResults.result(code, passed, RiskRuleResults.booleanValue(passed), BigDecimal.ONE,
                success, failure, checkedAt);
    }
}
