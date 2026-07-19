package top.egon.mario.investment.marketdata.provider;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.provider.model.ExternalContract;
import top.egon.mario.investment.marketdata.provider.model.ExternalContractTicker;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestmentProviderRegistryTests {

    @Test
    void productionRegistryCanRemainEmpty() {
        ProviderRegistry registry = new ProviderRegistry(List.of());

        assertThat(registry.providers()).isEmpty();
    }

    @Test
    void resolvesARegisteredProviderOnlyForItsDeclaredCapabilityAndContract() {
        MetadataProvider provider = new MetadataProvider("TEST", Set.of(DataCapability.CONTRACT_METADATA));
        ProviderRegistry registry = new ProviderRegistry(List.of(provider));

        assertThat(registry.require("TEST", DataCapability.CONTRACT_METADATA, ContractMetadataProvider.class))
                .isSameAs(provider);
        assertThat(registry.supports("TEST", DataCapability.CONTRACT_METADATA)).isTrue();

        assertThatThrownBy(() -> registry.require("TEST", DataCapability.FUNDING_RATE, FundingRateProvider.class))
                .isInstanceOf(InvestmentException.class)
                .extracting(exception -> ((InvestmentException) exception).getErrorCode())
                .isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE);
    }

    @Test
    void registersCurrentFundingTickerWithoutClaimingLatestTicker() {
        CurrentFundingProvider provider = new CurrentFundingProvider();
        ProviderRegistry registry = new ProviderRegistry(List.of(provider));

        assertThat(registry.require("TEST", DataCapability.CURRENT_FUNDING_RATE,
                ContractTickerProvider.class)).isSameAs(provider);
        assertThat(registry.supports("TEST", DataCapability.CURRENT_FUNDING_RATE)).isTrue();
        assertThat(registry.supports("TEST", DataCapability.LATEST_TICKER)).isFalse();
    }

    @Test
    void rejectsDuplicateProviderCodes() {
        MetadataProvider first = new MetadataProvider("TEST", Set.of(DataCapability.CONTRACT_METADATA));
        MetadataProvider second = new MetadataProvider("TEST", Set.of(DataCapability.CONTRACT_METADATA));

        assertThatThrownBy(() -> new ProviderRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("TEST");
    }

    @Test
    void rejectsAnImplementedSpiWithoutItsRequiredCapability() {
        MetadataProvider provider = new MetadataProvider("TEST", Set.of(DataCapability.LATEST_TICKER));

        assertThatThrownBy(() -> new ProviderRegistry(List.of(provider)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTRACT_METADATA");
    }

    @Test
    void rejectsCapabilitiesThatHaveNoMatchingProviderSpi() {
        MetadataProvider provider = new MetadataProvider("TEST",
                Set.of(DataCapability.CONTRACT_METADATA, DataCapability.FUNDING_RATE));

        assertThatThrownBy(() -> new ProviderRegistry(List.of(provider)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FUNDING_RATE");
    }

    @Test
    void rejectsUnknownProviderAtRuntime() {
        ProviderRegistry registry = new ProviderRegistry(List.of());

        assertThatThrownBy(() -> registry.require("UNKNOWN", DataCapability.CONTRACT_METADATA,
                        ContractMetadataProvider.class))
                .isInstanceOf(InvestmentException.class)
                .extracting(exception -> ((InvestmentException) exception).getErrorCode())
                .isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE);
        assertThat(registry.supports(null, DataCapability.CONTRACT_METADATA)).isFalse();
        assertThatThrownBy(() -> registry.require(null, DataCapability.CONTRACT_METADATA,
                        ContractMetadataProvider.class))
                .isInstanceOf(InvestmentException.class)
                .extracting(exception -> ((InvestmentException) exception).getErrorCode())
                .isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE);
    }

    @Test
    void snapshotsCapabilitiesAtRegistrationTime() {
        java.util.EnumSet<DataCapability> capabilities = java.util.EnumSet.of(DataCapability.CONTRACT_METADATA);
        MetadataProvider provider = new MetadataProvider("TEST", capabilities);
        ProviderRegistry registry = new ProviderRegistry(List.of(provider));

        capabilities.clear();

        assertThat(registry.supports("TEST", DataCapability.CONTRACT_METADATA)).isTrue();
    }

    @Test
    void providerFailuresExposeOneOfFourStableCategories() {
        assertThat(ProviderErrorCategory.values()).containsExactly(
                ProviderErrorCategory.RETRYABLE,
                ProviderErrorCategory.NON_RETRYABLE,
                ProviderErrorCategory.RATE_LIMITED,
                ProviderErrorCategory.INVALID_DATA);
        assertThat(new MarketDataProviderException("TEST", ProviderErrorCategory.RETRYABLE, "timeout")
                .isRetryable()).isTrue();
        assertThat(new MarketDataProviderException("TEST", ProviderErrorCategory.RATE_LIMITED, "rate limited")
                .isRetryable()).isTrue();
        assertThat(new MarketDataProviderException("TEST", ProviderErrorCategory.INVALID_DATA, "bad payload")
                .isRetryable()).isFalse();
    }

    private static final class MetadataProvider implements ContractMetadataProvider {

        private final String providerCode;
        private final Set<DataCapability> capabilities;

        private MetadataProvider(String providerCode, Set<DataCapability> capabilities) {
            this.providerCode = providerCode;
            this.capabilities = capabilities;
        }

        @Override
        public String providerCode() {
            return providerCode;
        }

        @Override
        public Set<DataCapability> capabilities() {
            return capabilities;
        }

        @Override
        public List<ExternalContract> contracts(ProductType productType, Set<String> symbols) {
            return List.of();
        }
    }

    private static final class CurrentFundingProvider implements ContractTickerProvider {

        @Override
        public String providerCode() {
            return "TEST";
        }

        @Override
        public Set<DataCapability> capabilities() {
            return Set.of(DataCapability.CURRENT_FUNDING_RATE);
        }

        @Override
        public List<ExternalContractTicker> tickers(ProductType productType, Set<String> symbols) {
            return List.of();
        }
    }
}
