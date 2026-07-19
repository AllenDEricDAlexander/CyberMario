package top.egon.mario.investment.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.job.InvestmentMarketJobPlanner;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class InvestmentMarketJobPlannerTests {

    private final Instant now = Instant.parse("2026-07-16T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void disabledOrEmptyProductionRegistryIsStrictNoOp() {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        when(registry.subscriptions()).thenReturn(List.of(subscription()));

        InvestmentMarketJobPlanner disabled = planner(false, registry, enqueueService);
        assertThat(disabled.tick()).isZero();
        disabled.start();
        assertThat(disabled.isRunning()).isFalse();
        verify(enqueueService, never()).enqueue(org.mockito.ArgumentMatchers.any());

        when(registry.subscriptions()).thenReturn(List.of());
        InvestmentMarketJobPlanner empty = planner(true, registry, enqueueService);
        assertThat(empty.tick()).isZero();
        empty.start();
        assertThat(empty.isRunning()).isFalse();
        verify(enqueueService, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void expandsCodeScheduleAndUsesStableKeysAcrossRestartTicks() {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        when(registry.subscriptions()).thenReturn(List.of(subscription()));
        InvestmentMarketJobPlanner first = planner(true, registry, enqueueService);
        InvestmentMarketJobPlanner restarted = planner(true, registry, enqueueService);

        assertThat(first.tick()).isEqualTo(15);
        assertThat(restarted.tick()).isEqualTo(15);

        ArgumentCaptor<InvestmentJobEnqueueCommand> captor = ArgumentCaptor.forClass(
                InvestmentJobEnqueueCommand.class);
        verify(enqueueService, times(30)).enqueue(captor.capture());
        List<String> keys = captor.getAllValues().stream().map(InvestmentJobEnqueueCommand::idempotencyKey).toList();
        assertThat(keys.subList(0, 15)).containsExactlyInAnyOrderElementsOf(keys.subList(15, 30));
        assertThat(keys).allSatisfy(key -> assertThat(key).startsWith("market:TEST:USDT_FUTURES:BTCUSDT:"));
        assertThat(captor.getAllValues()).filteredOn(command -> command.jobType() == InvestmentJobType.BAR_BACKFILL)
                .hasSize(14);
        assertThat(captor.getAllValues()).filteredOn(command -> command.jobType()
                == InvestmentJobType.FUNDING_RATE_BACKFILL).hasSize(2);
        assertThat(captor.getAllValues()).filteredOn(command -> command.jobType()
                == InvestmentJobType.DATA_QUALITY_CHECK).hasSize(2);
        assertThat(captor.getAllValues()).filteredOn(command -> command.jobType()
                == InvestmentJobType.QUOTE_REFRESH).extracting(InvestmentJobEnqueueCommand::inputJson)
                .anySatisfy(json -> assertThat(read(json).capability()).isEqualTo(DataCapability.OPEN_INTEREST));
    }

    @Test
    void concurrentPlannerInstancesConvergeOnTheSameDurableJobKeys() {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        when(registry.subscriptions()).thenReturn(List.of(subscription()));
        Set<String> persistedKeys = ConcurrentHashMap.newKeySet();
        doAnswer(invocation -> {
            InvestmentJobEnqueueCommand command = invocation.getArgument(0);
            persistedKeys.add(command.idempotencyKey());
            return (long) persistedKeys.size();
        }).when(enqueueService).enqueue(org.mockito.ArgumentMatchers.any());
        InvestmentMarketJobPlanner first = planner(true, registry, enqueueService);
        InvestmentMarketJobPlanner second = planner(true, registry, enqueueService);

        CompletableFuture.allOf(CompletableFuture.runAsync(first::tick),
                CompletableFuture.runAsync(second::tick)).join();

        assertThat(persistedKeys).hasSize(15);
    }

    @Test
    void alignsInputWindowsWithKeysAndCoversDailyIntervalsWithoutGaps() {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        when(registry.subscriptions()).thenReturn(List.of(subscription()));
        ArgumentCaptor<InvestmentJobEnqueueCommand> captor = ArgumentCaptor.forClass(
                InvestmentJobEnqueueCommand.class);

        planner(true, registry, enqueueService, now).tick();
        planner(true, registry, enqueueService, now.plus(Duration.ofMinutes(1))).tick();
        verify(enqueueService, times(30)).enqueue(captor.capture());

        List<MarketDataJobInput> m1 = inputs(captor.getAllValues(), InvestmentJobType.BAR_INCREMENTAL,
                BarInterval.M1);
        assertThat(m1).hasSize(2);
        assertThat(m1.get(0).endExclusive()).isEqualTo(m1.get(1).startInclusive());
        List<MarketDataJobInput> daily = inputs(captor.getAllValues(), InvestmentJobType.BAR_INCREMENTAL,
                BarInterval.D1);
        assertThat(Duration.between(daily.getFirst().startInclusive(), daily.getFirst().endExclusive()))
                .isEqualTo(Duration.ofDays(1));
        assertThat(daily.get(1).startInclusive()).isBefore(daily.getFirst().endExclusive());
    }

    @Test
    void backfillKeyChangesOnlyWhenConfiguredWindowChanges() {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        when(registry.subscriptions()).thenReturn(List.of(subscription(Duration.ofDays(30))));
        planner(true, registry, enqueueService).tick();
        when(registry.subscriptions()).thenReturn(List.of(subscription(Duration.ofDays(60))));
        planner(true, registry, enqueueService).tick();
        ArgumentCaptor<InvestmentJobEnqueueCommand> captor = ArgumentCaptor.forClass(
                InvestmentJobEnqueueCommand.class);
        verify(enqueueService, times(34)).enqueue(captor.capture());
        assertThat(captor.getAllValues().stream()
                .filter(command -> command.jobType() == InvestmentJobType.BAR_BACKFILL)
                .map(InvestmentJobEnqueueCommand::idempotencyKey).toList())
                .anySatisfy(key -> assertThat(key).contains("backfill:2592000"))
                .anySatisfy(key -> assertThat(key).contains("backfill:5184000"));
    }

    @Test
    void splitsLongMinuteBackfillsIntoNewestFirstBoundedJobs() {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        when(registry.subscriptions()).thenReturn(List.of(subscription()));
        ArgumentCaptor<InvestmentJobEnqueueCommand> captor = ArgumentCaptor.forClass(
                InvestmentJobEnqueueCommand.class);
        InvestmentMarketJobPlanner planner = planner(true, registry, enqueueService);

        assertThat(planner.tick()).isEqualTo(15);
        assertThat(planner.tick()).isEqualTo(7);

        verify(enqueueService, times(22)).enqueue(captor.capture());
        List<InvestmentJobEnqueueCommand> commands = captor.getAllValues().stream()
                .filter(command -> command.jobType() == InvestmentJobType.BAR_BACKFILL)
                .filter(command -> read(command.inputJson()).interval() == BarInterval.M1)
                .toList();
        assertThat(commands).hasSize(6);
        List<MarketDataJobInput> inputs = commands.stream().map(command -> read(command.inputJson())).toList();
        assertThat(inputs.getFirst().endExclusive()).isEqualTo(now);
        assertThat(Duration.between(inputs.getFirst().startInclusive(), inputs.getFirst().endExclusive()))
                .isEqualTo(Duration.ofDays(1));
        assertThat(inputs).allSatisfy(input -> assertThat(
                        Duration.between(input.startInclusive(), input.endExclusive()))
                .isLessThanOrEqualTo(Duration.ofMinutes(10_000)));
        for (int index = 1; index < inputs.size(); index++) {
            assertThat(inputs.get(index).endExclusive()).isEqualTo(inputs.get(index - 1).startInclusive());
        }
        assertThat(commands.getFirst().idempotencyKey()).endsWith("backfill:2592000");
        assertThat(commands.get(1).idempotencyKey()).endsWith("backfill:2592000:chunk:1");
        assertThat(commands.get(1).availableAt()).isAfter(commands.getFirst().availableAt());
    }

    private InvestmentMarketJobPlanner planner(boolean enabled, InvestmentMarketSubscriptionRegistry registry,
                                                InvestmentJobEnqueueService enqueueService) {
        return planner(enabled, registry, enqueueService, now);
    }

    private InvestmentMarketJobPlanner planner(boolean enabled, InvestmentMarketSubscriptionRegistry registry,
                                                InvestmentJobEnqueueService enqueueService, Instant instant) {
        return new InvestmentMarketJobPlanner(enabled, registry, enqueueService,
                objectMapper, Clock.fixed(instant, ZoneOffset.UTC), Duration.ofSeconds(30));
    }

    private MarketSubscription subscription() {
        return subscription(Duration.ofDays(30));
    }

    private MarketSubscription subscription(Duration backfill) {
        Set<DataCapability> capabilities = Set.of(DataCapability.CONTRACT_METADATA,
                DataCapability.LATEST_TICKER, DataCapability.OPEN_INTEREST,
                DataCapability.MARKET_CANDLE, DataCapability.FUNDING_RATE);
        return new MarketSubscription("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                Set.of(BarInterval.M1, BarInterval.D1), Set.of(PriceType.MARKET), capabilities,
                new SubscriptionSchedule(Map.of(
                        DataCapability.CONTRACT_METADATA, Duration.ofHours(1),
                        DataCapability.LATEST_TICKER, Duration.ofSeconds(10),
                        DataCapability.OPEN_INTEREST, Duration.ofSeconds(10),
                        DataCapability.FUNDING_RATE, Duration.ofHours(8),
                        DataCapability.MARKET_CANDLE, Duration.ofMinutes(1)), Map.of(
                        DataCapability.MARKET_CANDLE, backfill,
                        DataCapability.FUNDING_RATE, backfill)),
                new RetentionPolicy(Set.of(BarInterval.D1), Map.of(BarInterval.M1, Duration.ofDays(730))));
    }

    private List<MarketDataJobInput> inputs(List<InvestmentJobEnqueueCommand> commands, InvestmentJobType jobType,
                                            BarInterval interval) {
        return commands.stream().filter(command -> command.jobType() == jobType)
                .map(command -> read(command.inputJson())).filter(input -> input.interval() == interval).toList();
    }

    private MarketDataJobInput read(String json) {
        try {
            return objectMapper.readValue(json, MarketDataJobInput.class);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
