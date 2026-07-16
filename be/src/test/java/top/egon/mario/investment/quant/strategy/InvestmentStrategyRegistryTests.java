package top.egon.mario.investment.quant.strategy;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.quant.strategy.fixture.TestEmaCrossStrategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static top.egon.mario.investment.quant.strategy.fixture.TestMarketSubscriptionFixtures.subscriptions;

class InvestmentStrategyRegistryTests {

    @Test
    void supportsAnEmptyProductionRegistry() {
        InvestmentStrategyRegistry registry = new InvestmentStrategyRegistry(List.of(), subscriptions(Set.of(), Set.of()));

        assertThat(registry.strategies()).isEmpty();
        assertThatThrownBy(() -> registry.require("NOT_INSTALLED"))
                .isInstanceOfSatisfying(InvestmentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE));
    }

    @Test
    void registersCompleteFixedDescriptorsWhenCodeSubscriptionsCoverCapabilities() {
        TestEmaCrossStrategy strategy = new TestEmaCrossStrategy();
        StrategyDescriptor descriptor = strategy.descriptor();
        InvestmentStrategyRegistry registry = new InvestmentStrategyRegistry(List.of(strategy), subscriptions(
                descriptor.requiredCapabilities(), descriptor.supportedIntervals()));

        assertThat(registry.require("TEST_EMA_CROSS")).isSameAs(strategy);
        assertThat(registry.require("TEST_EMA_CROSS", "1.0.0")).isSameAs(strategy);
        assertThat(registry.descriptors()).containsExactly(descriptor);
    }

    @Test
    void rejectsDuplicateCodeAndVersion() {
        TestEmaCrossStrategy first = new TestEmaCrossStrategy();
        TestEmaCrossStrategy duplicate = new TestEmaCrossStrategy();
        StrategyDescriptor descriptor = first.descriptor();

        assertThatThrownBy(() -> new InvestmentStrategyRegistry(List.of(first, duplicate), subscriptions(
                descriptor.requiredCapabilities(), descriptor.supportedIntervals())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate Investment strategy")
                .hasMessageContaining("TEST_EMA_CROSS")
                .hasMessageContaining("1.0.0");
    }

    @Test
    void rejectsMissingCodeSubscriptionsAndUnsafeLeverage() {
        TestEmaCrossStrategy strategy = new TestEmaCrossStrategy();
        assertThatThrownBy(() -> new InvestmentStrategyRegistry(List.of(strategy),
                subscriptions(Set.of(DataCapability.MARKET_CANDLE), Set.of(BarInterval.M1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required capabilities")
                .hasMessageContaining("FUNDING_RATE");

        assertThatThrownBy(() -> new StrategyDescriptor(
                "BAD", "1", "Bad", "Bad descriptor", StrategyEngineType.JAVA,
                Set.of(DataCapability.MARKET_CANDLE), Set.of(BarInterval.M1), BarInterval.M1,
                top.egon.mario.investment.common.model.PriceType.MARKET,
                "ON_BAR_CLOSE", "FIXED", new BigDecimal("6"), new BigDecimal("5"),
                "FEE", "SLIPPAGE", "MATCHING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultLeverage");
    }

}
