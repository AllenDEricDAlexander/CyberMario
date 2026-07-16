package top.egon.mario.investment.quant.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;
import top.egon.mario.investment.quant.repository.InvestmentStrategyReleaseRepository;
import top.egon.mario.investment.quant.strategy.fixture.TestEmaCrossStrategy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static top.egon.mario.investment.quant.strategy.fixture.TestMarketSubscriptionFixtures.subscriptions;

class InvestmentStrategyReleaseSyncTests {

    private InvestmentStrategyReleaseRepository repository;
    private InvestmentStrategySourceHasher sourceHasher;
    private InvestmentStrategyReleaseSyncService service;
    private StrategyDescriptor descriptor;

    @BeforeEach
    void setUp() {
        TestEmaCrossStrategy strategy = new TestEmaCrossStrategy();
        descriptor = strategy.descriptor();
        InvestmentMarketSubscriptionRegistry subscriptions = subscriptions(
                descriptor.requiredCapabilities(), descriptor.supportedIntervals());
        InvestmentStrategyRegistry registry = new InvestmentStrategyRegistry(List.of(strategy), subscriptions);
        repository = mock(InvestmentStrategyReleaseRepository.class);
        sourceHasher = mock(InvestmentStrategySourceHasher.class);
        when(sourceHasher.fingerprint(TestEmaCrossStrategy.class))
                .thenReturn(new StrategySourceFingerprint("a".repeat(64), "build-123"));
        service = new InvestmentStrategyReleaseSyncService(repository, registry, new ObjectMapper(), sourceHasher,
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void insertsAnImmutableReleaseSnapshotOnce() {
        when(repository.findByStrategyCodeAndStrategyVersion("TEST_EMA_CROSS", "1.0.0"))
                .thenReturn(Optional.empty());

        service.synchronize();

        ArgumentCaptor<InvestmentStrategyReleasePo> captor =
                ArgumentCaptor.forClass(InvestmentStrategyReleasePo.class);
        verify(repository).saveAndFlush(captor.capture());
        InvestmentStrategyReleasePo release = captor.getValue();
        assertThat(release.getStrategyCode()).isEqualTo("TEST_EMA_CROSS");
        assertThat(release.getStrategyVersion()).isEqualTo("1.0.0");
        assertThat(release.getImplementationClass()).isEqualTo(TestEmaCrossStrategy.class.getName());
        assertThat(release.getEngineType()).isEqualTo("JAVA");
        assertThat(release.getSourceHash()).isEqualTo("a".repeat(64));
        assertThat(release.getBuildRevision()).isEqualTo("build-123");
        assertThat(release.getRequiredCapabilitiesJson()).contains("MARKET_CANDLE", "FUNDING_RATE");
        assertThat(release.getDescriptorSnapshotJson()).contains("TEST_EMA_CROSS", "defaultLeverage");
        assertThat(release.isActive()).isTrue();
        assertThat(release.getRegisteredAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void identicalStartupSyncIsIdempotent() {
        when(repository.findByStrategyCodeAndStrategyVersion("TEST_EMA_CROSS", "1.0.0"))
                .thenReturn(Optional.empty());
        service.synchronize();
        ArgumentCaptor<InvestmentStrategyReleasePo> captor =
                ArgumentCaptor.forClass(InvestmentStrategyReleasePo.class);
        verify(repository).saveAndFlush(captor.capture());

        InvestmentStrategyReleasePo existing = captor.getValue();
        when(repository.findByStrategyCodeAndStrategyVersion("TEST_EMA_CROSS", "1.0.0"))
                .thenReturn(Optional.of(existing));
        service.synchronize();

        verify(repository).saveAndFlush(any(InvestmentStrategyReleasePo.class));
    }

    @Test
    void changedSourceHashForTheSameCodeVersionBlocksScheduling() {
        InvestmentStrategyReleasePo existing = existingRelease("b".repeat(64));
        when(repository.findByStrategyCodeAndStrategyVersion("TEST_EMA_CROSS", "1.0.0"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(service::synchronize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source hash changed")
                .hasMessageContaining("TEST_EMA_CROSS")
                .hasMessageContaining("1.0.0");
        verify(repository, never()).saveAndFlush(any());
    }

    private InvestmentStrategyReleasePo existingRelease(String sourceHash) {
        InvestmentStrategyReleasePo release = new InvestmentStrategyReleasePo();
        release.setStrategyCode(descriptor.strategyCode());
        release.setStrategyVersion(descriptor.strategyVersion());
        release.setImplementationClass(TestEmaCrossStrategy.class.getName());
        release.setEngineType(descriptor.engineType().name());
        release.setSourceHash(sourceHash);
        release.setBuildRevision("build-123");
        release.setRequiredCapabilitiesJson("[]");
        release.setDescriptorSnapshotJson("{}");
        release.setActive(true);
        return release;
    }
}
