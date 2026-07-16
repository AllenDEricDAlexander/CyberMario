package top.egon.mario.investment.quant.strategy.fixture;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.subscription.RetentionPolicy;
import top.egon.mario.investment.marketdata.subscription.SubscriptionSchedule;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestMarketSubscriptionFixtures {

    private TestMarketSubscriptionFixtures() {
    }

    public static InvestmentMarketSubscriptionRegistry subscriptions(Set<DataCapability> capabilities,
                                                                     Set<BarInterval> intervals) {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        if (capabilities.isEmpty() && intervals.isEmpty()) {
            when(registry.subscriptions()).thenReturn(List.of());
            return registry;
        }
        MarketSubscription subscription = new MarketSubscription(
                "TEST", ProductType.USDT_FUTURES, "BTCUSDT", intervals, Set.of(), capabilities,
                new SubscriptionSchedule(Map.of(), Map.of()), new RetentionPolicy(intervals, Map.of()));
        when(registry.subscriptions()).thenReturn(List.of(subscription));
        return registry;
    }
}
