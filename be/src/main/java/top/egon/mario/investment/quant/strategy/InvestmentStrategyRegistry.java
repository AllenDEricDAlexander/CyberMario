package top.egon.mario.investment.quant.strategy;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-fast registry for fixed Java strategies and their required market capabilities.
 */
@Component
public class InvestmentStrategyRegistry {

    private final Map<StrategyKey, InvestmentStrategy> strategies;
    private final Map<String, InvestmentStrategy> currentByCode;
    private final List<InvestmentStrategy> strategyView;

    public InvestmentStrategyRegistry(List<InvestmentStrategy> candidates,
                                      InvestmentMarketSubscriptionRegistry subscriptionRegistry) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(subscriptionRegistry, "subscriptionRegistry");
        AvailableMarketShape available = availableShape(subscriptionRegistry.subscriptions());
        Map<StrategyKey, InvestmentStrategy> registered = new LinkedHashMap<>();
        Map<String, InvestmentStrategy> byCode = new LinkedHashMap<>();
        for (InvestmentStrategy candidate : candidates) {
            InvestmentStrategy strategy = Objects.requireNonNull(candidate, "strategy");
            StrategyDescriptor descriptor = Objects.requireNonNull(strategy.descriptor(), "strategy descriptor");
            validateMarketShape(descriptor, available);
            StrategyKey key = new StrategyKey(descriptor.strategyCode(), descriptor.strategyVersion());
            InvestmentStrategy previous = registered.putIfAbsent(key, strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Investment strategy: "
                        + descriptor.strategyCode() + "/" + descriptor.strategyVersion());
            }
            InvestmentStrategy previousVersion = byCode.putIfAbsent(descriptor.strategyCode(), strategy);
            if (previousVersion != null) {
                throw new IllegalStateException("Multiple active Investment strategy versions: "
                        + descriptor.strategyCode());
            }
        }
        this.strategies = Map.copyOf(registered);
        this.currentByCode = Map.copyOf(byCode);
        this.strategyView = registered.values().stream()
                .sorted(Comparator.comparing((InvestmentStrategy strategy) -> strategy.descriptor().strategyCode())
                        .thenComparing(strategy -> strategy.descriptor().strategyVersion()))
                .toList();
    }

    public List<InvestmentStrategy> strategies() {
        return strategyView;
    }

    public List<StrategyDescriptor> descriptors() {
        return strategyView.stream().map(InvestmentStrategy::descriptor).toList();
    }

    public InvestmentStrategy require(String strategyCode) {
        InvestmentStrategy strategy = strategyCode == null ? null : currentByCode.get(strategyCode);
        if (strategy == null) {
            throw unavailable(strategyCode);
        }
        return strategy;
    }

    public InvestmentStrategy require(String strategyCode, String strategyVersion) {
        InvestmentStrategy strategy = strategyCode == null || strategyVersion == null
                ? null : strategies.get(new StrategyKey(strategyCode, strategyVersion));
        if (strategy == null) {
            throw unavailable(strategyCode + "/" + strategyVersion);
        }
        return strategy;
    }

    private AvailableMarketShape availableShape(Collection<MarketSubscription> subscriptions) {
        Objects.requireNonNull(subscriptions, "subscriptions");
        EnumSet<DataCapability> capabilities = EnumSet.noneOf(DataCapability.class);
        EnumSet<BarInterval> intervals = EnumSet.noneOf(BarInterval.class);
        for (MarketSubscription subscription : subscriptions) {
            MarketSubscription required = Objects.requireNonNull(subscription, "subscription");
            capabilities.addAll(required.capabilities());
            intervals.addAll(required.intervals());
        }
        return new AvailableMarketShape(Set.copyOf(capabilities), Set.copyOf(intervals));
    }

    private void validateMarketShape(StrategyDescriptor descriptor, AvailableMarketShape available) {
        EnumSet<DataCapability> missingCapabilities = EnumSet.copyOf(descriptor.requiredCapabilities());
        missingCapabilities.removeAll(available.capabilities());
        if (!missingCapabilities.isEmpty()) {
            throw new IllegalStateException("Investment strategy required capabilities are not subscribed: "
                    + descriptor.strategyCode() + "/" + missingCapabilities);
        }
        EnumSet<BarInterval> missingIntervals = EnumSet.copyOf(descriptor.supportedIntervals());
        missingIntervals.removeAll(available.intervals());
        if (!missingIntervals.isEmpty()) {
            throw new IllegalStateException("Investment strategy intervals are not subscribed: "
                    + descriptor.strategyCode() + "/" + missingIntervals);
        }
    }

    private InvestmentException unavailable(String strategy) {
        return new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                "Investment strategy is not installed in code: " + strategy);
    }

    private record StrategyKey(String strategyCode, String strategyVersion) {
    }

    private record AvailableMarketShape(Set<DataCapability> capabilities, Set<BarInterval> intervals) {
    }
}
