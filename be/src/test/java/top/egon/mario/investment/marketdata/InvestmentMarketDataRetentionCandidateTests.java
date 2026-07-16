package top.egon.mario.investment.marketdata;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.service.MarketDataRetentionCandidateService;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.subscription.RetentionPolicy;
import top.egon.mario.investment.marketdata.subscription.SubscriptionSchedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvestmentMarketDataRetentionCandidateTests {

    @Test
    void computesTwoYearIntradayCandidatesWithoutPhysicalDeletion() {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        MarketSubscription subscription = new MarketSubscription("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                Set.of(BarInterval.M1, BarInterval.D1), Set.of(PriceType.MARKET),
                Set.of(DataCapability.MARKET_CANDLE),
                new SubscriptionSchedule(Map.of(DataCapability.MARKET_CANDLE, Duration.ofMinutes(1)), Map.of()),
                new RetentionPolicy(Set.of(BarInterval.D1), Map.of(BarInterval.M1, Duration.ofDays(730))));
        when(registry.subscriptions()).thenReturn(List.of(subscription));
        Instant now = Instant.parse("2026-07-16T00:00:00Z");

        var candidates = new MarketDataRetentionCandidateService(registry,
                Clock.fixed(now, ZoneOffset.UTC)).candidates();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().interval()).isEqualTo(BarInterval.M1);
        assertThat(candidates.getFirst().toExclusive()).isEqualTo(now.minus(Duration.ofDays(730)));
        assertThat(candidates.getFirst().physicalDeletionEnabled()).isFalse();
    }
}
