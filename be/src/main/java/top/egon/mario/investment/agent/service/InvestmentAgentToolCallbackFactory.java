package top.egon.mario.investment.agent.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.service.model.ScopedAgentToolSet;
import top.egon.mario.investment.agent.tool.InvestmentAgentToolScope;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.service.InvestmentPaperAccountService;
import top.egon.mario.investment.quant.backtest.InvestmentBacktestService;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;
import top.egon.mario.investment.research.indicator.InvestmentIndicatorService;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds eight read-only callbacks whose private scope cannot be supplied or overridden by the model. */
@Service
public class InvestmentAgentToolCallbackFactory {

    private final InvestmentMarketQueryService marketQueryService;
    private final InvestmentIndicatorService indicatorService;
    private final InvestmentPortfolioQueryService portfolioQueryService;
    private final InvestmentPaperAccountService accountService;
    private final InvestmentBacktestService backtestService;
    private final InvestmentBacktestRunRepository backtestRunRepository;

    public InvestmentAgentToolCallbackFactory(
            InvestmentMarketQueryService marketQueryService,
            InvestmentIndicatorService indicatorService,
            InvestmentPortfolioQueryService portfolioQueryService,
            InvestmentPaperAccountService accountService,
            InvestmentBacktestService backtestService,
            InvestmentBacktestRunRepository backtestRunRepository) {
        this.marketQueryService = marketQueryService;
        this.indicatorService = indicatorService;
        this.portfolioQueryService = portfolioQueryService;
        this.accountService = accountService;
        this.backtestService = backtestService;
        this.backtestRunRepository = backtestRunRepository;
    }

    public ScopedAgentToolSet create(InvestmentAgentToolScope scope) {
        List<ToolCallback> callbacks = List.of(
                marketSnapshot(scope), candles(scope), indicators(scope), fundingRates(scope),
                positionTiers(scope), portfolio(scope), riskState(scope), backtest(scope));
        return ScopedAgentToolSet.readOnly(callbacks);
    }

    private ToolCallback marketSnapshot(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_market_snapshot",
                        (InstrumentRequest request) -> {
                            long instrumentId = requireInstrument(scope, request == null ? null : request.instrumentId());
                            Instant from = scope.dataAsOf().minus(Duration.ofHours(2));
                            var candles = marketQueryService.candles(instrumentId, PriceType.MARK, BarInterval.M1,
                                    from, scope.dataAsOf(), scope.dataAsOf(), 120);
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("dataAsOf", scope.dataAsOf());
                            result.put("instrument", marketQueryService.instrument(instrumentId));
                            result.put("latestClosedMarkCandle", candles.isEmpty() ? null : candles.getLast());
                            return result;
                        })
                .description("Read the server-bound instrument and its latest closed mark candle at dataAsOf")
                .inputType(InstrumentRequest.class)
                .build();
    }

    private ToolCallback candles(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_candles",
                        (CandleRequest request) -> {
                            requireCandleRequest(scope, request);
                            return marketQueryService.candles(request.instrumentId(), request.priceType(),
                                    request.interval(), request.fromInclusive(), request.toExclusive(),
                                    scope.dataAsOf(), boundedLimit(request.limit(), 2_000));
                        })
                .description("Read subscribed closed futures candles no later than the server-bound dataAsOf")
                .inputType(CandleRequest.class)
                .build();
    }

    private ToolCallback indicators(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_indicators",
                        (CandleRequest request) -> {
                            requireCandleRequest(scope, request);
                            return indicatorService.calculate(request.instrumentId(), request.priceType(),
                                    request.interval(), request.fromInclusive(), request.toExclusive(),
                                    scope.dataAsOf());
                        })
                .description("Calculate the fixed indicator set from server-bound closed candles")
                .inputType(CandleRequest.class)
                .build();
    }

    private ToolCallback fundingRates(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_funding_rates",
                        (FundingRequest request) -> {
                            requireWindow(scope, request == null ? null : request.instrumentId(),
                                    request == null ? null : request.fromInclusive(),
                                    request == null ? null : request.toExclusive());
                            return marketQueryService.fundingRates(request.instrumentId(), request.fromInclusive(),
                                    request.toExclusive(), scope.dataAsOf(), 0, boundedLimit(request.limit(), 200));
                        })
                .description("Read funding rates at the server-bound revision cutoff")
                .inputType(FundingRequest.class)
                .build();
    }

    private ToolCallback positionTiers(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_position_tiers",
                        (InstrumentRequest request) -> marketQueryService.positionTiers(
                                requireInstrument(scope, request == null ? null : request.instrumentId()),
                                scope.dataAsOf()))
                .description("Read current code-subscribed position tiers at the server-bound cutoff")
                .inputType(InstrumentRequest.class)
                .build();
    }

    private ToolCallback portfolio(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_portfolio", () -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("dataAsOf", scope.dataAsOf());
                    result.put("workspaceSummary",
                            portfolioQueryService.workspaceSummary(scope.workspaceId(), scope.dataAsOf()));
                    if (scope.accountId() == null) {
                        result.put("positions",
                                portfolioQueryService.workspacePositions(scope.workspaceId(), scope.dataAsOf()));
                    } else {
                        result.put("positions", portfolioQueryService.positions(scope.actorId(), scope.accountId()));
                        result.put("equity", portfolioQueryService.equity(
                                scope.actorId(), scope.accountId(), 0, 100).getContent());
                    }
                    return result;
                })
                .description("Read only the server-bound owner's paper portfolio")
                .build();
    }

    private ToolCallback riskState(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_risk_state", () -> {
                    if (scope.accountId() == null) {
                        throw unavailable("This run has no server-bound paper account");
                    }
                    return accountService.get(scope.actorId(), scope.accountId());
                })
                .description("Read switches and risk limits for the server-bound paper account")
                .build();
    }

    private ToolCallback backtest(InvestmentAgentToolScope scope) {
        return FunctionToolCallback.builder("get_investment_backtest", (BacktestRequest request) -> {
                    Long runId = request == null ? null : request.runId();
                    if (runId == null || runId <= 0 || backtestRunRepository
                            .findByIdAndWorkspaceIdAndDeletedFalse(runId, scope.workspaceId()).isEmpty()) {
                        throw unavailable("Backtest is outside the server-bound workspace");
                    }
                    return backtestService.detail(scope.actorId(), runId);
                })
                .description("Read one owner-scoped backtest from the server-bound workspace")
                .inputType(BacktestRequest.class)
                .build();
    }

    private static void requireCandleRequest(InvestmentAgentToolScope scope, CandleRequest request) {
        if (request == null || request.priceType() == null || request.priceType() == PriceType.NONE
                || request.interval() == null || request.interval() == BarInterval.NONE) {
            throw invalid("Concrete candle dimensions are required");
        }
        requireWindow(scope, request.instrumentId(), request.fromInclusive(), request.toExclusive());
    }

    private static void requireWindow(InvestmentAgentToolScope scope, Long instrumentId,
                                      Instant fromInclusive, Instant toExclusive) {
        requireInstrument(scope, instrumentId);
        if (fromInclusive == null || toExclusive == null || !toExclusive.isAfter(fromInclusive)
                || toExclusive.isAfter(scope.dataAsOf())) {
            throw invalid("Tool time window exceeds the server-bound dataAsOf");
        }
    }

    private static long requireInstrument(InvestmentAgentToolScope scope, Long instrumentId) {
        if (instrumentId == null || instrumentId <= 0 || !scope.instrumentIds().contains(instrumentId)) {
            throw new InvestmentException(InvestmentErrorCode.FORBIDDEN,
                    "Instrument is outside the server-bound Agent scope");
        }
        return instrumentId;
    }

    private static int boundedLimit(Integer limit, int maximum) {
        if (limit == null || limit <= 0 || limit > maximum) {
            throw invalid("Tool limit is outside the supported bounds");
        }
        return limit;
    }

    private static InvestmentException invalid(String message) {
        return new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, message);
    }

    private static InvestmentException unavailable(String message) {
        return new InvestmentException(InvestmentErrorCode.CAPABILITY_UNAVAILABLE, message);
    }

    public record InstrumentRequest(Long instrumentId) {
    }

    public record CandleRequest(Long instrumentId, PriceType priceType, BarInterval interval,
                                Instant fromInclusive, Instant toExclusive, Integer limit) {
    }

    public record FundingRequest(Long instrumentId, Instant fromInclusive, Instant toExclusive, Integer limit) {
    }

    public record BacktestRequest(Long runId) {
    }
}
