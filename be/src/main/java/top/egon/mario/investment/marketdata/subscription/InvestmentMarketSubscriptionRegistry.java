package top.egon.mario.investment.marketdata.subscription;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only runtime gate backed exclusively by Java subscription providers.
 */
@Component
public class InvestmentMarketSubscriptionRegistry {

    private static final Set<DataCapability> CANDLE_CAPABILITIES = Set.of(
            DataCapability.MARKET_CANDLE, DataCapability.MARK_CANDLE, DataCapability.INDEX_CANDLE);

    private final Map<SubscriptionKey, MarketSubscription> subscriptions;
    private final List<MarketSubscription> subscriptionView;

    public InvestmentMarketSubscriptionRegistry(List<InvestmentMarketSubscriptionProvider> subscriptionProviders,
                                                ProviderRegistry providerRegistry) {
        Objects.requireNonNull(subscriptionProviders, "subscriptionProviders");
        Objects.requireNonNull(providerRegistry, "providerRegistry");
        Map<SubscriptionKey, MarketSubscription> registered = new LinkedHashMap<>();
        for (InvestmentMarketSubscriptionProvider subscriptionProvider : subscriptionProviders) {
            registerProvider(registered, Objects.requireNonNull(subscriptionProvider, "subscriptionProvider"),
                    providerRegistry);
        }
        this.subscriptions = Map.copyOf(registered);
        this.subscriptionView = registered.values().stream()
                .sorted(Comparator.comparing(MarketSubscription::sourceCode)
                        .thenComparing(subscription -> subscription.productType().name())
                        .thenComparing(MarketSubscription::symbol))
                .toList();
    }

    public Collection<MarketSubscription> subscriptions() {
        return subscriptionView;
    }

    public MarketSubscription requireCapability(String sourceCode, ProductType productType, String symbol,
                                                DataCapability capability) {
        Objects.requireNonNull(capability, "capability");
        MarketSubscription subscription = requireSubscription(sourceCode, productType, symbol);
        if (!subscription.capabilities().contains(capability)) {
            throw rejected(sourceCode, productType, symbol, capability.toString());
        }
        return subscription;
    }

    public MarketSubscription requireCandle(String sourceCode, ProductType productType, String symbol,
                                            BarInterval interval, PriceType priceType) {
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(priceType, "priceType");
        if (interval == BarInterval.NONE || priceType == PriceType.NONE) {
            throw rejected(sourceCode, productType, symbol, interval + "/" + priceType);
        }
        MarketSubscription subscription = requireCapability(sourceCode, productType, symbol,
                candleCapability(priceType));
        if (!subscription.intervals().contains(interval) || !subscription.priceTypes().contains(priceType)) {
            throw rejected(sourceCode, productType, symbol, interval + "/" + priceType);
        }
        return subscription;
    }

    private void registerProvider(Map<SubscriptionKey, MarketSubscription> registered,
                                  InvestmentMarketSubscriptionProvider subscriptionProvider,
                                  ProviderRegistry providerRegistry) {
        Collection<MarketSubscription> provided = Objects.requireNonNull(subscriptionProvider.subscriptions(),
                "subscriptions");
        for (MarketSubscription subscription : provided) {
            MarketSubscription required = Objects.requireNonNull(subscription, "subscription");
            validate(required, providerRegistry);
            SubscriptionKey key = SubscriptionKey.of(required.sourceCode(), required.productType(),
                    required.symbol());
            MarketSubscription previous = registered.putIfAbsent(key, required);
            if (previous != null) {
                throw new IllegalStateException("Duplicate market subscription key: " + key);
            }
        }
    }

    private void validate(MarketSubscription subscription, ProviderRegistry providerRegistry) {
        if (subscription.capabilities().isEmpty()) {
            throw invalid(subscription, "at least one capability is required");
        }
        for (DataCapability capability : subscription.capabilities()) {
            if (!providerRegistry.supports(subscription.sourceCode(), capability)) {
                throw invalid(subscription, "provider does not support capability " + capability);
            }
        }
        validateCandleDimensions(subscription);
        validateSchedule(subscription);
        validateRetention(subscription);
    }

    private void validateCandleDimensions(MarketSubscription subscription) {
        EnumSet<DataCapability> declaredCandleCapabilities = EnumSet.noneOf(DataCapability.class);
        declaredCandleCapabilities.addAll(subscription.capabilities());
        declaredCandleCapabilities.retainAll(CANDLE_CAPABILITIES);
        if (declaredCandleCapabilities.isEmpty()) {
            if (!subscription.intervals().isEmpty() || !subscription.priceTypes().isEmpty()) {
                throw invalid(subscription, "intervals and price types require a candle capability");
            }
            return;
        }
        if (subscription.intervals().isEmpty() || subscription.priceTypes().isEmpty()) {
            throw invalid(subscription, "candle capabilities require intervals and price types");
        }
        for (PriceType priceType : subscription.priceTypes()) {
            DataCapability capability = candleCapability(priceType);
            if (!declaredCandleCapabilities.contains(capability)) {
                throw invalid(subscription, "price type is not backed by capability " + priceType);
            }
        }
        for (DataCapability capability : declaredCandleCapabilities) {
            PriceType priceType = candlePriceType(capability);
            if (!subscription.priceTypes().contains(priceType)) {
                throw invalid(subscription, "candle capability is missing price type " + priceType);
            }
        }
    }

    private void validateSchedule(MarketSubscription subscription) {
        SubscriptionSchedule schedule = subscription.schedule();
        if (!schedule.refreshIntervals().keySet().equals(subscription.capabilities())) {
            throw invalid(subscription, "refresh schedule must cover exactly the subscribed capabilities");
        }
        if (!subscription.capabilities().containsAll(schedule.backfillWindows().keySet())) {
            throw invalid(subscription, "backfill schedule contains an unsupported capability");
        }
    }

    private void validateRetention(MarketSubscription subscription) {
        RetentionPolicy retention = subscription.retentionPolicy();
        Set<BarInterval> permanent = retention.permanentIntervals();
        Set<BarInterval> retained = retention.retainedFor().keySet();
        if (permanent.stream().anyMatch(retained::contains)) {
            throw invalid(subscription, "an interval cannot be both permanent and duration-retained");
        }
        List<BarInterval> configured = new ArrayList<>(permanent);
        configured.addAll(retained);
        if (!Set.copyOf(configured).equals(subscription.intervals())) {
            throw invalid(subscription, "retention policy must cover exactly the subscribed intervals: "
                    + subscription.intervals());
        }
    }

    private MarketSubscription requireSubscription(String sourceCode, ProductType productType, String symbol) {
        if (sourceCode == null || productType == null || symbol == null) {
            throw rejected(sourceCode, productType, symbol, "subscription");
        }
        MarketSubscription subscription = subscriptions.get(SubscriptionKey.of(sourceCode, productType, symbol));
        if (subscription == null) {
            throw rejected(sourceCode, productType, symbol, "subscription");
        }
        return subscription;
    }

    private DataCapability candleCapability(PriceType priceType) {
        return switch (priceType) {
            case MARKET -> DataCapability.MARKET_CANDLE;
            case MARK -> DataCapability.MARK_CANDLE;
            case INDEX -> DataCapability.INDEX_CANDLE;
            case NONE -> throw new IllegalArgumentException("Concrete price type is required");
        };
    }

    private PriceType candlePriceType(DataCapability capability) {
        return switch (capability) {
            case MARKET_CANDLE -> PriceType.MARKET;
            case MARK_CANDLE -> PriceType.MARK;
            case INDEX_CANDLE -> PriceType.INDEX;
            default -> throw new IllegalArgumentException("Not a candle capability: " + capability);
        };
    }

    private IllegalStateException invalid(MarketSubscription subscription, String reason) {
        return new IllegalStateException("Invalid market subscription " + subscription.sourceCode() + "/"
                + subscription.productType() + "/" + subscription.symbol() + ": " + reason);
    }

    private InvestmentException rejected(String sourceCode, ProductType productType, String symbol, String dimension) {
        return new InvestmentException(InvestmentErrorCode.SUBSCRIPTION_REJECTED,
                "Market-data request is outside the code subscription registry: " + sourceCode + "/"
                        + productType + "/" + symbol + "/" + dimension);
    }

    private record SubscriptionKey(String sourceCode, ProductType productType, String symbol) {

        private static SubscriptionKey of(String sourceCode, ProductType productType, String symbol) {
            return new SubscriptionKey(sourceCode, productType, symbol);
        }

        private SubscriptionKey {
            Objects.requireNonNull(sourceCode, "sourceCode");
            Objects.requireNonNull(productType, "productType");
            Objects.requireNonNull(symbol, "symbol");
        }
    }
}
