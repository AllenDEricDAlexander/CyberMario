package top.egon.mario.investment.quant.backtest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.quant.backtest.model.BacktestEquityPoint;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetHasher;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetSnapshotService;
import top.egon.mario.investment.quant.po.InvestmentBacktestEventPo;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.po.InvestmentBacktestTradePo;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestEventRepository;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;
import top.egon.mario.investment.quant.repository.InvestmentBacktestTradeRepository;
import top.egon.mario.investment.quant.repository.InvestmentStrategyReleaseRepository;
import top.egon.mario.investment.quant.repository.jdbc.BacktestEquityJdbcRepository;
import top.egon.mario.investment.quant.strategy.InvestmentStrategy;
import top.egon.mario.investment.quant.strategy.InvestmentStrategyRegistry;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestEquityResponse;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestEventResponse;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestRunResponse;
import top.egon.mario.investment.quant.web.dto.InvestmentBacktestTradeResponse;
import top.egon.mario.investment.quant.web.dto.SubmitInvestmentBacktestRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;

/**
 * Owner-scoped backtest submission and query facade.
 */
@Service
public class InvestmentBacktestService {

    private static final BigDecimal INITIAL_EQUITY = new BigDecimal("10000");

    private final InvestmentAccessService accessService;
    private final BacktestSourceResolver sourceResolver;
    private final InvestmentStrategyRegistry strategyRegistry;
    private final InvestmentStrategyReleaseRepository strategyReleaseRepository;
    private final InvestmentDatasetSnapshotService snapshotService;
    private final BacktestSubmissionPersistenceService submissionPersistenceService;
    private final InvestmentBacktestRunRepository runRepository;
    private final InvestmentBacktestTradeRepository tradeRepository;
    private final InvestmentBacktestEventRepository eventRepository;
    private final BacktestEquityJdbcRepository equityRepository;
    private final InvestmentDatasetHasher hasher;
    private final Clock clock;

    public InvestmentBacktestService(InvestmentAccessService accessService,
                                     BacktestSourceResolver sourceResolver,
                                     InvestmentStrategyRegistry strategyRegistry,
                                     InvestmentStrategyReleaseRepository strategyReleaseRepository,
                                     InvestmentDatasetSnapshotService snapshotService,
                                     BacktestSubmissionPersistenceService submissionPersistenceService,
                                     InvestmentBacktestRunRepository runRepository,
                                     InvestmentBacktestTradeRepository tradeRepository,
                                     InvestmentBacktestEventRepository eventRepository,
                                     BacktestEquityJdbcRepository equityRepository,
                                     InvestmentDatasetHasher hasher,
                                     Clock clock) {
        this.accessService = accessService;
        this.sourceResolver = sourceResolver;
        this.strategyRegistry = strategyRegistry;
        this.strategyReleaseRepository = strategyReleaseRepository;
        this.snapshotService = snapshotService;
        this.submissionPersistenceService = submissionPersistenceService;
        this.runRepository = runRepository;
        this.tradeRepository = tradeRepository;
        this.eventRepository = eventRepository;
        this.equityRepository = equityRepository;
        this.hasher = hasher;
        this.clock = clock;
    }

    public InvestmentBacktestRunResponse submit(long actorId, long workspaceId,
                                                SubmitInvestmentBacktestRequest request) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        validateRequest(request);
        InvestmentStrategy strategy = strategyRegistry.require(request.getStrategyCode());
        long sourceId = sourceResolver.resolve(request.getInstrumentIds());
        java.time.Instant dataAsOf = clock.instant();
        if (request.getEndTime().isAfter(dataAsOf)) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST,
                    "Backtest range must be complete at submission time");
        }
        InvestmentStrategyReleasePo release = strategyReleaseRepository
                .findByStrategyCodeAndStrategyVersion(strategy.descriptor().strategyCode(),
                        strategy.descriptor().strategyVersion())
                .filter(InvestmentStrategyReleasePo::isActive)
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE,
                        "Strategy release is not active"));
        var snapshot = snapshotService.create(new InvestmentDatasetSnapshotService.CreateCommand(
                actorId, workspaceId, sourceId, request.getInstrumentIds(),
                request.getStartTime(), request.getEndTime(), dataAsOf, strategy.descriptor()));
        String idempotencyKey = "backtest:" + hasher.hash(Map.of(
                "workspaceId", workspaceId,
                "strategy", strategy.descriptor().strategyCode() + "/" + strategy.descriptor().strategyVersion(),
                "datasetHash", snapshot.snapshot().getDatasetHash(),
                "initialEquity", INITIAL_EQUITY.toPlainString()));
        return response(submissionPersistenceService.submit(actorId, workspaceId, release.getId(),
                snapshot.snapshot().getId(), INITIAL_EQUITY, strategy.descriptor(), idempotencyKey));
    }

    public Page<InvestmentBacktestRunResponse> list(long actorId, long workspaceId, Pageable pageable) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        return runRepository.findByWorkspaceIdAndDeletedFalse(workspaceId, pageable)
                .map(InvestmentBacktestService::response);
    }

    public InvestmentBacktestRunResponse detail(long actorId, long runId) {
        return response(requireOwnedRun(actorId, runId));
    }

    public Page<InvestmentBacktestTradeResponse> trades(long actorId, long runId, Pageable pageable) {
        requireOwnedRun(actorId, runId);
        return tradeRepository.findByRunId(runId, pageable).map(InvestmentBacktestService::tradeResponse);
    }

    public Page<InvestmentBacktestEventResponse> events(long actorId, long runId, Pageable pageable) {
        requireOwnedRun(actorId, runId);
        return eventRepository.findByRunId(runId, pageable).map(InvestmentBacktestService::eventResponse);
    }

    public java.util.List<InvestmentBacktestEquityResponse> equity(long actorId, long runId) {
        requireOwnedRun(actorId, runId);
        return equityRepository.findByRunId(runId).stream().map(InvestmentBacktestService::equityResponse).toList();
    }

    private InvestmentBacktestRunPo requireOwnedRun(long actorId, long runId) {
        return runRepository.findOwnedRun(runId, actorId)
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                        "Backtest run access denied"));
    }

    private void validateRequest(SubmitInvestmentBacktestRequest request) {
        if (request == null || request.getStartTime() == null || request.getEndTime() == null
                || !request.getEndTime().isAfter(request.getStartTime())
                || request.getInstrumentIds() == null || request.getInstrumentIds().isEmpty()) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, "Backtest range is invalid");
        }
        if (request.getInstrumentIds().size() > 20) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST,
                    "Backtest supports at most 20 instruments");
        }
    }

    static InvestmentBacktestRunResponse response(InvestmentBacktestRunPo value) {
        return new InvestmentBacktestRunResponse(value.getId(), value.getWorkspaceId(), value.getJobId(),
                value.getStrategyReleaseId(), value.getDatasetSnapshotId(), value.getStatus(),
                text(value.getInitialEquity()), text(value.getTotalReturn()), text(value.getAnnualizedReturn()),
                text(value.getMaxDrawdown()), text(value.getSharpeRatio()), text(value.getSortinoRatio()),
                text(value.getWinRate()), text(value.getProfitFactor()), text(value.getTurnover()),
                value.getTradeCount(), text(value.getTotalFee()), text(value.getTotalFunding()),
                value.getLiquidationCount(), value.getErrorCode(), value.getErrorMessage(), value.getStartedAt(),
                value.getFinishedAt(), value.getCreatedAt());
    }

    private static InvestmentBacktestTradeResponse tradeResponse(InvestmentBacktestTradePo value) {
        return new InvestmentBacktestTradeResponse(value.getId(), value.getInstrumentId(), value.getPositionSide(),
                value.getEntryTime(), value.getExitTime(), text(value.getEntryPrice()), text(value.getExitPrice()),
                text(value.getQuantity()), text(value.getLeverage()), text(value.getGrossPnl()),
                text(value.getFeeAmount()), text(value.getFundingAmount()), text(value.getNetPnl()),
                value.getExitReason());
    }

    private static InvestmentBacktestEventResponse eventResponse(InvestmentBacktestEventPo value) {
        return new InvestmentBacktestEventResponse(value.getId(), value.getInstrumentId(), value.getEventType(),
                value.getEventTime(), text(value.getAmount()), text(value.getBalanceAfter()),
                value.getDetailsJson(), value.getSequenceNo());
    }

    private static InvestmentBacktestEquityResponse equityResponse(BacktestEquityPoint value) {
        return new InvestmentBacktestEquityResponse(value.pointTime(), text(value.walletBalance()),
                text(value.usedMargin()), text(value.unrealizedPnl()), text(value.equity()),
                text(value.drawdown()), text(value.grossExposure()));
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
