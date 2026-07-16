package top.egon.mario.investment.marketdata.subscription;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.ContractCandleProvider;
import top.egon.mario.investment.marketdata.provider.ContractMetadataProvider;
import top.egon.mario.investment.marketdata.provider.ContractTickerProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.CandleQuery;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.provider.model.ExternalContract;
import top.egon.mario.investment.marketdata.provider.model.ExternalContractTicker;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentMarketSubscriptionRegistryTests {

    private static final Set<DataCapability> CAPABILITIES = Set.of(
            DataCapability.CONTRACT_METADATA, DataCapability.LATEST_TICKER,
            DataCapability.MARKET_CANDLE, DataCapability.MARK_CANDLE);

    @Test
    void productionRegistryCanRemainEmpty() {
        InvestmentMarketSubscriptionRegistry registry = new InvestmentMarketSubscriptionRegistry(
                List.of(), new ProviderRegistry(List.of()));

        assertThat(registry.subscriptions()).isEmpty();
    }

    @Test
    void registeredCodeSubscriptionAllowsOnlyItsExactRuntimeDimensions() {
        InvestmentMarketSubscriptionRegistry registry = registry(validSubscription());

        assertThat(registry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.CONTRACT_METADATA)).isEqualTo(validSubscription());
        assertThat(registry.requireCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                BarInterval.M1, PriceType.MARK)).isEqualTo(validSubscription());

        assertSubscriptionRejected(() -> registry.requireCandle("TEST", ProductType.USDT_FUTURES,
                "BTCUSDT", BarInterval.H1, PriceType.MARK));
        assertSubscriptionRejected(() -> registry.requireCandle("TEST", ProductType.USDT_FUTURES,
                "BTCUSDT", BarInterval.M1, PriceType.INDEX));
        assertSubscriptionRejected(() -> registry.requireCapability("TEST", ProductType.USDT_FUTURES,
                "ETHUSDT", DataCapability.CONTRACT_METADATA));
        assertSubscriptionRejected(() -> registry.requireCapability(null, ProductType.USDT_FUTURES,
                "BTCUSDT", DataCapability.CONTRACT_METADATA));
    }

    @Test
    void rejectsDuplicateSubscriptionKeysAcrossCodeProviders() {
        MarketSubscription subscription = validSubscription();
        InvestmentMarketSubscriptionProvider first = () -> List.of(subscription);
        InvestmentMarketSubscriptionProvider second = () -> List.of(subscription);

        assertThatThrownBy(() -> new InvestmentMarketSubscriptionRegistry(List.of(first, second),
                providerRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("BTCUSDT");
    }

    @Test
    void rejectsCapabilitiesPriceTypesAndIntervalsNotBackedByCodeRegistries() {
        MarketSubscription unsupportedCapability = subscription(
                Set.of(DataCapability.CONTRACT_METADATA, DataCapability.FUNDING_RATE),
                Set.of(), Set.of(), schedule(DataCapability.CONTRACT_METADATA, DataCapability.FUNDING_RATE),
                new RetentionPolicy(Set.of(), Map.of()));
        assertThatThrownBy(() -> registry(unsupportedCapability))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FUNDING_RATE");

        MarketSubscription unsupportedPrice = subscription(CAPABILITIES, Set.of(BarInterval.M1),
                Set.of(PriceType.MARKET, PriceType.INDEX), schedule(CAPABILITIES.toArray(DataCapability[]::new)),
                new RetentionPolicy(Set.of(BarInterval.M1), Map.of()));
        assertThatThrownBy(() -> registry(unsupportedPrice))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INDEX");

        MarketSubscription missingRetention = subscription(CAPABILITIES, Set.of(BarInterval.M1),
                Set.of(PriceType.MARKET, PriceType.MARK), schedule(CAPABILITIES.toArray(DataCapability[]::new)),
                new RetentionPolicy(Set.of(), Map.of()));
        assertThatThrownBy(() -> registry(missingRetention))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("M1");
    }

    private InvestmentMarketSubscriptionRegistry registry(MarketSubscription subscription) {
        return new InvestmentMarketSubscriptionRegistry(List.of(() -> List.of(subscription)), providerRegistry());
    }

    private ProviderRegistry providerRegistry() {
        return new ProviderRegistry(List.of(new TestProvider()));
    }

    private MarketSubscription validSubscription() {
        return subscription(CAPABILITIES, Set.of(BarInterval.M1), Set.of(PriceType.MARKET, PriceType.MARK),
                schedule(CAPABILITIES.toArray(DataCapability[]::new)),
                new RetentionPolicy(Set.of(BarInterval.M1), Map.of()));
    }

    private MarketSubscription subscription(Set<DataCapability> capabilities, Set<BarInterval> intervals,
                                              Set<PriceType> priceTypes, SubscriptionSchedule schedule,
                                              RetentionPolicy retentionPolicy) {
        return new MarketSubscription("TEST", ProductType.USDT_FUTURES, "BTCUSDT", intervals, priceTypes,
                capabilities, schedule, retentionPolicy);
    }

    private SubscriptionSchedule schedule(DataCapability... capabilities) {
        java.util.EnumMap<DataCapability, Duration> refreshIntervals = new java.util.EnumMap<>(DataCapability.class);
        for (DataCapability capability : capabilities) {
            refreshIntervals.put(capability, Duration.ofMinutes(1));
        }
        return new SubscriptionSchedule(refreshIntervals, Map.of());
    }

    private void assertSubscriptionRejected(Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(InvestmentException.class)
                .extracting(exception -> ((InvestmentException) exception).getErrorCode())
                .isEqualTo(InvestmentErrorCode.SUBSCRIPTION_REJECTED);
    }

    private static final class TestProvider implements ContractMetadataProvider, ContractTickerProvider,
            ContractCandleProvider {

        @Override
        public String providerCode() {
            return "TEST";
        }

        @Override
        public Set<DataCapability> capabilities() {
            return CAPABILITIES;
        }

        @Override
        public List<ExternalContract> contracts(ProductType productType, Set<String> symbols) {
            return List.of();
        }

        @Override
        public List<ExternalContractTicker> tickers(ProductType productType, Set<String> symbols) {
            return List.of();
        }

        @Override
        public List<ExternalCandle> candles(CandleQuery query) {
            return List.of();
        }
    }
}
