package top.egon.mario.investment.portfolio.risk;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable snapshot evaluated by the risk gate. It performs no I/O and owns no persistence.
 */
public record InvestmentRiskContext(
        InvestmentRiskSource source,
        AccountState account,
        MarketState market,
        OrderState order,
        PortfolioState portfolio,
        InvestmentRiskLimits accountLimits,
        InvestmentRiskLimits callerLimits
) {
    public InvestmentRiskContext {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(portfolio, "portfolio");
        Objects.requireNonNull(accountLimits, "accountLimits");
    }

    public InvestmentRiskLimits effectiveLimits() {
        return accountLimits.tightenedBy(callerLimits);
    }

    public record AccountState(boolean tradingEnabled, boolean agentAutoTradeEnabled) {
    }

    public record MarketState(
            boolean instrumentSubscribed,
            boolean instrumentTradable,
            Long marketDataAgeSeconds,
            BigDecimal markPrice,
            boolean positionTierAvailable,
            boolean fundingDataAvailable
    ) {
    }

    public record OrderState(
            BigDecimal orderNotional,
            BigDecimal leverage,
            Long ordersLastHour,
            Long secondsSinceLastOrder,
            BigDecimal requiredMargin,
            BigDecimal slippageBps,
            boolean reduceOnly,
            boolean reduceOnlyValid
    ) {
        public OrderState {
            Objects.requireNonNull(orderNotional, "orderNotional");
            Objects.requireNonNull(leverage, "leverage");
            Objects.requireNonNull(ordersLastHour, "ordersLastHour");
            Objects.requireNonNull(requiredMargin, "requiredMargin");
            Objects.requireNonNull(slippageBps, "slippageBps");
        }
    }

    public record PortfolioState(
            BigDecimal resultingPositionNotional,
            BigDecimal resultingGrossExposureNotional,
            Long resultingOpenPositions,
            BigDecimal dailyLossAmount,
            BigDecimal drawdownRatio,
            BigDecimal availableMargin
    ) {
        public PortfolioState {
            Objects.requireNonNull(resultingPositionNotional, "resultingPositionNotional");
            Objects.requireNonNull(resultingGrossExposureNotional, "resultingGrossExposureNotional");
            Objects.requireNonNull(resultingOpenPositions, "resultingOpenPositions");
            Objects.requireNonNull(dailyLossAmount, "dailyLossAmount");
            Objects.requireNonNull(drawdownRatio, "drawdownRatio");
            Objects.requireNonNull(availableMargin, "availableMargin");
        }
    }
}
