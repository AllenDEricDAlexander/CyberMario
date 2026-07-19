package top.egon.mario.investment.marketdata.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.common.utils.LogUtil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Expands immutable code subscriptions into idempotent durable market-data jobs.
 */
@Component
@Slf4j
public class InvestmentMarketJobPlanner implements SmartLifecycle {

    private static final int PRIORITY = 100;
    private static final int MAX_ATTEMPTS = 5;

    private final boolean enabled;
    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;
    private final InvestmentJobEnqueueService enqueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration plannerDelay;
    private volatile boolean running;
    private ScheduledExecutorService executorService;

    public InvestmentMarketJobPlanner(
                                      @Value("${mario.investment.market-data-planner-enabled:false}") boolean enabled,
                                      InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                      InvestmentJobEnqueueService enqueueService,
                                      ObjectMapper objectMapper,
                                      Clock clock,
                                      @Value("${mario.investment.market-data-planner-delay:PT30S}")
                                      Duration plannerDelay) {
        this.enabled = enabled;
        this.subscriptionRegistry = subscriptionRegistry;
        this.enqueueService = enqueueService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (plannerDelay.isZero() || plannerDelay.isNegative()) {
            throw new IllegalArgumentException("market-data planner delay must be positive");
        }
        this.plannerDelay = plannerDelay;
    }

    public synchronized int tick() {
        Collection<MarketSubscription> subscriptions = subscriptionRegistry.subscriptions();
        if (!enabled || subscriptions.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        int planned = 0;
        for (MarketSubscription subscription : subscriptions) {
            for (DataCapability capability : subscription.capabilities().stream().sorted().toList()) {
                Duration refresh = subscription.schedule().refreshIntervals().get(capability);
                if (refresh == null) {
                    continue;
                }
                for (PlannedInput plannedInput : refreshInputs(subscription, capability, refresh, now)) {
                    enqueueService.enqueue(command(plannedInput.input(), jobType(capability),
                            "refresh:" + plannedInput.slot().number(), plannedInput.slot().start()));
                    planned++;
                }
            }
            for (var entry : subscription.schedule().backfillWindows().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                Duration refresh = subscription.schedule().refreshIntervals().get(entry.getKey());
                for (MarketDataJobInput input : backfillInputs(subscription, entry.getKey(), entry.getValue(),
                        refresh, now)) {
                    enqueueService.enqueue(command(input, backfillJobType(entry.getKey()),
                            "backfill:" + entry.getValue().toSeconds(), now));
                    planned++;
                }
            }
            Duration qualityRefresh = subscription.schedule().refreshIntervals().values().stream()
                    .min(Comparator.naturalOrder()).orElse(plannerDelay);
            Slot qualitySlot = slot(qualityRefresh, now);
            DataCapability qualityCapability = subscription.capabilities().stream().sorted().findFirst().orElseThrow();
            MarketDataJobInput qualityInput = input(subscription, qualityCapability, PriceType.NONE,
                    BarInterval.NONE, null, null);
            enqueueService.enqueue(command(qualityInput, InvestmentJobType.DATA_QUALITY_CHECK,
                    "quality:" + qualitySlot.number(), qualitySlot.start()));
            planned++;
        }
        return planned;
    }

    @Override
    public synchronized void start() {
        if (running || !enabled || subscriptionRegistry.subscriptions().isEmpty()) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "investment-market-job-planner");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::tickSafely, 0L, plannerDelay.toMillis(),
                TimeUnit.MILLISECONDS);
        running = true;
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 90;
    }

    @PreDestroy
    void shutdown() {
        stop();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException ex) {
            LogUtil.warn(log).log("investment market planner tick failed, error={}",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private List<PlannedInput> refreshInputs(MarketSubscription subscription, DataCapability capability,
                                             Duration refresh, Instant now) {
        if (isCandle(capability)) {
            PriceType priceType = priceType(capability);
            List<PlannedInput> inputs = new ArrayList<>();
            for (BarInterval interval : subscription.intervals().stream().sorted().toList()) {
                Duration width = max(refresh, intervalDuration(interval));
                Slot intervalSlot = slot(width, now);
                inputs.add(new PlannedInput(input(subscription, capability, priceType, interval,
                        intervalSlot.start().minus(width), intervalSlot.start()), intervalSlot));
            }
            return inputs;
        }
        Slot capabilitySlot = slot(refresh, now);
        Instant alignedEnd = capabilitySlot.start();
        Instant start = capability == DataCapability.FUNDING_RATE ? alignedEnd.minus(refresh) : null;
        Instant end = start == null ? null : alignedEnd;
        return List.of(new PlannedInput(
                input(subscription, capability, PriceType.NONE, BarInterval.NONE, start, end), capabilitySlot));
    }

    private List<MarketDataJobInput> backfillInputs(MarketSubscription subscription, DataCapability capability,
                                                    Duration window, Duration refresh, Instant now) {
        if (isCandle(capability)) {
            PriceType priceType = priceType(capability);
            return subscription.intervals().stream().sorted()
                    .map(interval -> {
                        Instant alignedEnd = slot(max(refresh, intervalDuration(interval)), now).start();
                        return input(subscription, capability, priceType, interval,
                                alignedEnd.minus(window), alignedEnd);
                    }).toList();
        }
        if (capability == DataCapability.FUNDING_RATE) {
            Instant alignedEnd = slot(refresh, now).start();
            return List.of(input(subscription, capability, PriceType.NONE, BarInterval.NONE,
                    alignedEnd.minus(window), alignedEnd));
        }
        throw new IllegalArgumentException("Backfill is supported only for candle and funding capabilities: "
                + capability);
    }

    private MarketDataJobInput input(MarketSubscription subscription, DataCapability capability,
                                     PriceType priceType, BarInterval interval,
                                     Instant startInclusive, Instant endExclusive) {
        return new MarketDataJobInput(subscription.sourceCode(), subscription.productType(), subscription.symbol(),
                capability, priceType, interval, startInclusive, endExclusive, 100);
    }

    private InvestmentJobEnqueueCommand command(MarketDataJobInput input, InvestmentJobType jobType,
                                                String scheduleKey, Instant availableAt) {
        String key = "market:" + input.sourceCode() + ":" + input.productType() + ":" + input.symbol()
                + ":" + input.capability() + ":" + input.priceType() + ":" + input.interval() + ":" + scheduleKey;
        return new InvestmentJobEnqueueCommand(null, jobType, PRIORITY, availableAt, MAX_ATTEMPTS,
                key, json(input));
    }

    private InvestmentJobType backfillJobType(DataCapability capability) {
        if (isCandle(capability)) {
            return InvestmentJobType.BAR_BACKFILL;
        }
        if (capability == DataCapability.FUNDING_RATE) {
            return InvestmentJobType.FUNDING_RATE_BACKFILL;
        }
        throw new IllegalArgumentException("Backfill job type is unavailable for " + capability);
    }

    private InvestmentJobType jobType(DataCapability capability) {
        return switch (capability) {
            case CONTRACT_METADATA -> InvestmentJobType.CONTRACT_SYNC;
            case POSITION_TIER -> InvestmentJobType.POSITION_TIER_SYNC;
            case MARKET_CANDLE, MARK_CANDLE, INDEX_CANDLE -> InvestmentJobType.BAR_INCREMENTAL;
            case LATEST_TICKER, CURRENT_FUNDING_RATE, OPEN_INTEREST -> InvestmentJobType.QUOTE_REFRESH;
            case FUNDING_RATE -> InvestmentJobType.FUNDING_RATE_INCREMENTAL;
        };
    }

    private boolean isCandle(DataCapability capability) {
        return capability == DataCapability.MARKET_CANDLE || capability == DataCapability.MARK_CANDLE
                || capability == DataCapability.INDEX_CANDLE;
    }

    private PriceType priceType(DataCapability capability) {
        return switch (capability) {
            case MARKET_CANDLE -> PriceType.MARKET;
            case MARK_CANDLE -> PriceType.MARK;
            case INDEX_CANDLE -> PriceType.INDEX;
            default -> throw new IllegalArgumentException("Not a candle capability: " + capability);
        };
    }

    private String json(MarketDataJobInput input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to encode market-data job input", ex);
        }
    }

    private Slot slot(Duration refresh, Instant now) {
        long seconds = Math.max(1L, refresh.toSeconds());
        long number = Math.floorDiv(now.getEpochSecond(), seconds);
        return new Slot(number, Instant.ofEpochSecond(number * seconds));
    }

    private Duration max(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private Duration intervalDuration(BarInterval interval) {
        return switch (interval) {
            case M1 -> Duration.ofMinutes(1);
            case M5 -> Duration.ofMinutes(5);
            case M15 -> Duration.ofMinutes(15);
            case M30 -> Duration.ofMinutes(30);
            case H1 -> Duration.ofHours(1);
            case H4 -> Duration.ofHours(4);
            case D1 -> Duration.ofDays(1);
            case NONE -> throw new IllegalArgumentException("Concrete interval is required");
        };
    }

    private record Slot(long number, Instant start) {
    }

    private record PlannedInput(MarketDataJobInput input, Slot slot) {
    }
}
