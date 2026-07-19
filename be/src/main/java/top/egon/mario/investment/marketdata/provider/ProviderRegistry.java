package top.egon.mario.investment.marketdata.provider;

import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.DataCapability;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-fast registry for code-provided market-data adapters.
 */
@Component
public class ProviderRegistry {

    private static final Pattern PROVIDER_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_.:-]*");
    private static final Set<DataCapability> CANDLE_CAPABILITIES = Set.of(
            DataCapability.MARKET_CANDLE, DataCapability.MARK_CANDLE, DataCapability.INDEX_CANDLE);
    private static final Set<DataCapability> TICKER_CAPABILITIES = Set.of(
            DataCapability.LATEST_TICKER, DataCapability.CURRENT_FUNDING_RATE, DataCapability.OPEN_INTEREST);

    private final Map<String, RegisteredProvider> providers;

    public ProviderRegistry(List<MarketDataProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        Map<String, RegisteredProvider> registered = new LinkedHashMap<>();
        for (MarketDataProvider provider : providers) {
            register(registered, Objects.requireNonNull(provider, "provider"));
        }
        this.providers = Map.copyOf(registered);
    }

    public Collection<MarketDataProvider> providers() {
        return providers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().provider())
                .toList();
    }

    public boolean supports(String providerCode, DataCapability capability) {
        RegisteredProvider provider = providerCode == null ? null : providers.get(providerCode);
        return provider != null && capability != null && provider.capabilities().contains(capability);
    }

    public <T extends MarketDataProvider> T require(String providerCode, DataCapability capability,
                                                     Class<T> providerType) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(providerType, "providerType");
        RegisteredProvider registration = providerCode == null ? null : providers.get(providerCode);
        if (registration == null || !registration.capabilities().contains(capability)
                || !providerType.isInstance(registration.provider())) {
            throw new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                    "Market-data provider capability is not registered: " + providerCode + "/" + capability);
        }
        return providerType.cast(registration.provider());
    }

    private void register(Map<String, RegisteredProvider> registered, MarketDataProvider provider) {
        String providerCode = requireProviderCode(provider.providerCode());
        Set<DataCapability> capabilities = immutableCapabilities(provider.capabilities(), providerCode);
        validateSpiCapabilities(provider, providerCode, capabilities);
        RegisteredProvider previous = registered.putIfAbsent(providerCode,
                new RegisteredProvider(provider, capabilities));
        if (previous != null) {
            throw new IllegalStateException("Duplicate market-data provider code: " + providerCode);
        }
    }

    private String requireProviderCode(String providerCode) {
        if (providerCode == null || !PROVIDER_CODE.matcher(providerCode).matches()) {
            throw new IllegalStateException("Normalized market-data provider code is required: " + providerCode);
        }
        return providerCode;
    }

    private Set<DataCapability> immutableCapabilities(Set<DataCapability> capabilities, String providerCode) {
        if (capabilities == null || capabilities.isEmpty()
                || capabilities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("Market-data provider capabilities are required: " + providerCode);
        }
        return Set.copyOf(capabilities);
    }

    private void validateSpiCapabilities(MarketDataProvider provider, String providerCode,
                                         Set<DataCapability> capabilities) {
        EnumSet<DataCapability> supportedBySpis = EnumSet.noneOf(DataCapability.class);
        if (provider instanceof ContractMetadataProvider) {
            requireCapability(providerCode, capabilities, DataCapability.CONTRACT_METADATA);
            supportedBySpis.add(DataCapability.CONTRACT_METADATA);
        }
        if (provider instanceof ContractTickerProvider) {
            if (capabilities.stream().noneMatch(TICKER_CAPABILITIES::contains)) {
                throw new IllegalStateException("ContractTickerProvider requires a ticker capability: "
                        + providerCode);
            }
            supportedBySpis.addAll(TICKER_CAPABILITIES);
        }
        if (provider instanceof ContractCandleProvider) {
            if (capabilities.stream().noneMatch(CANDLE_CAPABILITIES::contains)) {
                throw new IllegalStateException("ContractCandleProvider requires a candle capability: "
                        + providerCode);
            }
            supportedBySpis.addAll(CANDLE_CAPABILITIES);
        }
        if (provider instanceof FundingRateProvider) {
            requireCapability(providerCode, capabilities, DataCapability.FUNDING_RATE);
            supportedBySpis.add(DataCapability.FUNDING_RATE);
        }
        if (provider instanceof PositionTierProvider) {
            requireCapability(providerCode, capabilities, DataCapability.POSITION_TIER);
            supportedBySpis.add(DataCapability.POSITION_TIER);
        }
        if (supportedBySpis.isEmpty()) {
            throw new IllegalStateException("Market-data provider does not implement a provider SPI: " + providerCode);
        }
        EnumSet<DataCapability> unsupported = EnumSet.copyOf(capabilities);
        unsupported.removeAll(supportedBySpis);
        if (!unsupported.isEmpty()) {
            throw new IllegalStateException("Market-data provider declares capabilities without a matching SPI: "
                    + providerCode + "/" + unsupported);
        }
    }

    private void requireCapability(String providerCode, Set<DataCapability> capabilities,
                                   DataCapability requiredCapability) {
        if (!capabilities.contains(requiredCapability)) {
            throw new IllegalStateException("Market-data provider " + providerCode
                    + " must declare capability " + requiredCapability);
        }
    }

    private record RegisteredProvider(MarketDataProvider provider, Set<DataCapability> capabilities) {
    }
}
