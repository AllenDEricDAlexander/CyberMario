package top.egon.mario.investment.marketdata.subscription;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Production code subscription for the initial Bitget BTC and SOL perpetual universe.
 */
@Component
@ConditionalOnProperty(prefix = "mario.investment.bitget", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class BitgetInvestmentMarketSubscriptionProvider implements InvestmentMarketSubscriptionProvider {

    private static final Duration TWO_YEARS = Duration.ofDays(730);
    private static final Set<DataCapability> CAPABILITIES = Set.of(
            DataCapability.MARKET_CANDLE, DataCapability.FUNDING_RATE, DataCapability.CURRENT_FUNDING_RATE);

    @Override
    public Collection<MarketSubscription> subscriptions() {
        return List.of(subscription("BTCUSDT"), subscription("SOLUSDT"));
    }

    private MarketSubscription subscription(String symbol) {
        return new MarketSubscription("BITGET", ProductType.USDT_FUTURES, symbol,
                Set.of(BarInterval.M1, BarInterval.D1), Set.of(PriceType.MARKET), CAPABILITIES,
                new SubscriptionSchedule(Map.of(
                        DataCapability.MARKET_CANDLE, Duration.ofMinutes(1),
                        DataCapability.FUNDING_RATE, Duration.ofHours(8),
                        DataCapability.CURRENT_FUNDING_RATE, Duration.ofMinutes(1)
                ), Map.of(
                        DataCapability.MARKET_CANDLE, TWO_YEARS,
                        DataCapability.FUNDING_RATE, TWO_YEARS
                )),
                new RetentionPolicy(Set.of(BarInterval.D1), Map.of(BarInterval.M1, TWO_YEARS)));
    }
}
