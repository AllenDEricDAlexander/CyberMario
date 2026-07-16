package top.egon.mario.investment.trading.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueCommand;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentSourceRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.repository.InvestmentPaperOrderRepository;
import top.egon.mario.investment.trading.service.PaperFundingSettlementService;
import top.egon.mario.investment.trading.service.model.PaperFundingJobInput;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckJobInput;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Reconciles durable paper maintenance jobs and reacts to committed market facts. */
@Component
@Slf4j
public class PaperMaintenanceJobPlanner implements SmartLifecycle {

    private static final int PRIORITY = 60;
    private static final int MAX_ATTEMPTS = 8;

    private final boolean enabled;
    private final Duration delay;
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentInstrumentSourceRepository sourceRepository;
    private final InvestmentPaperOrderRepository orderRepository;
    private final FundingRateJdbcRepository fundingRepository;
    private final InvestmentJobEnqueueService enqueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile boolean running;
    private ScheduledExecutorService executorService;

    public PaperMaintenanceJobPlanner(
            @Value("${mario.investment.paper-maintenance-planner-enabled:false}") boolean enabled,
            @Value("${mario.investment.paper-maintenance-planner-delay:PT30S}") Duration delay,
            InvestmentPositionRepository positionRepository,
            InvestmentPaperAccountRepository accountRepository,
            InvestmentInstrumentSourceRepository sourceRepository,
            InvestmentPaperOrderRepository orderRepository,
            FundingRateJdbcRepository fundingRepository,
            InvestmentJobEnqueueService enqueueService,
            ObjectMapper objectMapper,
            Clock clock) {
        if (delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException("paper-maintenance planner delay must be positive");
        }
        this.enabled = enabled;
        this.delay = delay;
        this.positionRepository = positionRepository;
        this.accountRepository = accountRepository;
        this.sourceRepository = sourceRepository;
        this.orderRepository = orderRepository;
        this.fundingRepository = fundingRepository;
        this.enqueueService = enqueueService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public synchronized int tick() {
        if (!enabled) {
            return 0;
        }
        Instant now = clock.instant();
        List<InvestmentPositionPo> positions = positionRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(InvestmentPositionPo::getAccountId)
                        .thenComparing(InvestmentPositionPo::getInstrumentId)).toList();
        Map<Long, InvestmentPaperAccountPo> accounts = accountRepository
                .findAllById(positions.stream().map(InvestmentPositionPo::getAccountId).distinct().toList())
                .stream().collect(Collectors.toMap(InvestmentPaperAccountPo::getId, Function.identity()));
        int planned = 0;
        for (InvestmentPositionPo position : positions) {
            InvestmentPaperAccountPo account = accounts.get(position.getAccountId());
            InvestmentInstrumentSourcePo source = source(position.getInstrumentId());
            if (account == null || source == null || account.isDeleted()) {
                continue;
            }
            planMargin(account, position, source.getSourceId(), now);
            planned++;
            Instant from = position.getCreatedAt() == null ? now.minus(Duration.ofDays(1)) : position.getCreatedAt();
            if (now.isAfter(from)) {
                int offset = 0;
                while (true) {
                    var page = fundingRepository.findCurrent(
                            source.getSourceId(), position.getInstrumentId(), from, now.plusNanos(1), offset, 1000);
                    for (var funding : page) {
                        planFunding(account, position, source.getSourceId(), funding.fundingTime());
                        planned++;
                    }
                    if (page.size() < 1000) {
                        break;
                    }
                    offset += page.size();
                }
            }
        }
        return planned;
    }

    public int onMarketDataCommitted(InvestmentMarketDataCommittedEvent event) {
        int planned = 0;
        Instant now = clock.instant();
        List<InvestmentPositionPo> positions = positionRepository
                .findByInstrumentIdOrderByAccountIdAscInstrumentIdAsc(event.instrumentId());
        Map<Long, InvestmentPaperAccountPo> accounts = accountRepository
                .findAllById(positions.stream().map(InvestmentPositionPo::getAccountId).distinct().toList())
                .stream().collect(Collectors.toMap(InvestmentPaperAccountPo::getId, Function.identity()));
        for (InvestmentPositionPo position : positions) {
            InvestmentPaperAccountPo account = accounts.get(position.getAccountId());
            if (account == null || account.isDeleted()) {
                continue;
            }
            if ("FUNDING_RATE".equals(event.dataType())) {
                planFunding(account, position, event.sourceId(), event.dataAsOf());
                planned++;
            }
            if (List.of("BAR", "QUOTE", "POSITION_TIER", "CONTRACT_METADATA")
                    .contains(event.dataType())) {
                planMargin(account, position, event.sourceId(), now);
                planned++;
            }
        }
        if ("BAR".equals(event.dataType())) {
            for (var order : orderRepository
                    .findByInstrumentIdAndStatusAndDeletedFalseOrderByIdAsc(event.instrumentId(), "PENDING_MATCH")) {
                if (enqueueService.wakePending("paper-match:" + order.getId(), now)) {
                    planned++;
                }
            }
        }
        return planned;
    }

    private void planMargin(
            InvestmentPaperAccountPo account, InvestmentPositionPo position,
            long sourceId, Instant dataAsOf) {
        PaperMarginCheckJobInput input = new PaperMarginCheckJobInput(
                account.getWorkspaceId(), account.getId(), position.getId(),
                position.getInstrumentId(), sourceId, dataAsOf);
        String key = "paper-margin:%d:%d:%d".formatted(
                account.getId(), position.getInstrumentId(), dataAsOf.getEpochSecond() / 60L);
        enqueueService.enqueueOrWake(new InvestmentJobEnqueueCommand(
                account.getWorkspaceId(), InvestmentJobType.PAPER_MARGIN_CHECK, PRIORITY,
                dataAsOf, MAX_ATTEMPTS, key, json(input)));
    }

    private void planFunding(
            InvestmentPaperAccountPo account, InvestmentPositionPo position,
            long sourceId, Instant fundingTime) {
        PaperFundingJobInput input = new PaperFundingJobInput(
                account.getWorkspaceId(), account.getId(), position.getId(),
                position.getInstrumentId(), sourceId, fundingTime);
        enqueueService.enqueueOrWake(new InvestmentJobEnqueueCommand(
                account.getWorkspaceId(), InvestmentJobType.PAPER_FUNDING_SETTLE, PRIORITY,
                fundingTime, MAX_ATTEMPTS, PaperFundingSettlementService.idempotencyKey(input), json(input)));
    }

    private InvestmentInstrumentSourcePo source(Long instrumentId) {
        return sourceRepository
                .findFirstByInstrumentIdAndSourceStatusAndDeletedFalseOrderByIdAsc(instrumentId, "ACTIVE")
                .orElse(null);
    }

    private String json(Object input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize paper maintenance input", exception);
        }
    }

    @Override
    public synchronized void start() {
        if (running || !enabled) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "investment-paper-maintenance-planner");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::tickSafely, 0L, delay.toMillis(), TimeUnit.MILLISECONDS);
        running = true;
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException exception) {
            LogUtil.warn(log).log("paper maintenance planner tick failed, error={}",
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
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

    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 80; }

    @PreDestroy
    void shutdown() {
        stop();
    }
}
