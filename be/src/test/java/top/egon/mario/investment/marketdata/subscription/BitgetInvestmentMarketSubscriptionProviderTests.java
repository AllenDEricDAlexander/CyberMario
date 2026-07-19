package top.egon.mario.investment.marketdata.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.job.InvestmentMarketJobPlanner;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.bitget.BitgetHttpTransport;
import top.egon.mario.investment.marketdata.provider.bitget.BitgetMarketDataProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BitgetInvestmentMarketSubscriptionProviderTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Instant now = Instant.parse("2026-07-19T12:34:56Z");

    @Test
    void declaresOnlyBtcAndSolWithTheFrozenCapabilitiesAndRetention() {
        InvestmentMarketSubscriptionRegistry registry = registry();

        assertThat(registry.subscriptions()).extracting(MarketSubscription::symbol)
                .containsExactly("BTCUSDT", "SOLUSDT");
        assertThat(registry.subscriptions()).allSatisfy(subscription -> {
            assertThat(subscription.sourceCode()).isEqualTo("BITGET");
            assertThat(subscription.capabilities()).containsExactlyInAnyOrder(
                    DataCapability.MARKET_CANDLE, DataCapability.FUNDING_RATE,
                    DataCapability.CURRENT_FUNDING_RATE);
            assertThat(subscription.capabilities()).doesNotContain(DataCapability.LATEST_TICKER);
            assertThat(subscription.intervals()).containsExactlyInAnyOrder(BarInterval.M1, BarInterval.D1);
            assertThat(subscription.retentionPolicy().permanentIntervals()).containsExactly(BarInterval.D1);
            assertThat(subscription.retentionPolicy().retainedFor().get(BarInterval.M1))
                    .isEqualTo(Duration.ofDays(730));
        });
    }

    @Test
    void plannerUsesActualBarBoundariesAndBitgetPageSize() throws Exception {
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        InvestmentMarketJobPlanner planner = new InvestmentMarketJobPlanner(true, registry(), enqueueService,
                objectMapper, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30));

        assertThat(planner.tick()).isEqualTo(16);

        ArgumentCaptor<InvestmentJobEnqueueCommand> captor =
                ArgumentCaptor.forClass(InvestmentJobEnqueueCommand.class);
        verify(enqueueService, times(16)).enqueue(captor.capture());
        List<InvestmentJobEnqueueCommand> commands = captor.getAllValues();
        List<MarketDataJobInput> inputs = commands.stream()
                .filter(command -> command.jobType() == InvestmentJobType.BAR_INCREMENTAL)
                .map(command -> read(command.inputJson())).toList();
        assertThat(inputs).allSatisfy(input -> assertThat(input.pageSize()).isEqualTo(100));
        assertThat(inputs).filteredOn(input -> input.interval() == BarInterval.M1)
                .allSatisfy(input -> assertThat(input.endExclusive())
                        .isEqualTo(Instant.parse("2026-07-19T12:34:00Z")));
        assertThat(inputs).filteredOn(input -> input.interval() == BarInterval.D1)
                .allSatisfy(input -> assertThat(input.endExclusive())
                        .isEqualTo(Instant.parse("2026-07-19T00:00:00Z")));
        assertThat(commands).filteredOn(command -> command.jobType() == InvestmentJobType.QUOTE_REFRESH)
                .extracting(command -> read(command.inputJson()).capability())
                .containsExactly(DataCapability.CURRENT_FUNDING_RATE, DataCapability.CURRENT_FUNDING_RATE);
    }

    private InvestmentMarketSubscriptionRegistry registry() {
        BitgetHttpTransport noNetwork = (uri, timeout) -> {
            throw new AssertionError("Provider construction and planning must not call Bitget");
        };
        BitgetMarketDataProvider provider = new BitgetMarketDataProvider(objectMapper,
                Clock.fixed(now, ZoneOffset.UTC), "https://api.bitget.test", Duration.ofSeconds(1), noNetwork);
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        return new InvestmentMarketSubscriptionRegistry(
                List.of(new BitgetInvestmentMarketSubscriptionProvider()), providerRegistry);
    }

    private MarketDataJobInput read(String json) {
        try {
            return objectMapper.readValue(json, MarketDataJobInput.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
