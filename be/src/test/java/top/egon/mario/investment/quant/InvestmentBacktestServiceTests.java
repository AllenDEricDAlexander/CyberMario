package top.egon.mario.investment.quant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.job.InvestmentJobEnqueueService;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;
import top.egon.mario.investment.quant.backtest.BacktestDatasetLoader;
import top.egon.mario.investment.quant.backtest.BacktestResultPersistenceService;
import top.egon.mario.investment.quant.backtest.BacktestRunStateService;
import top.egon.mario.investment.quant.backtest.BacktestSourceResolver;
import top.egon.mario.investment.quant.backtest.BacktestSubmissionPersistenceService;
import top.egon.mario.investment.quant.backtest.InvestmentBacktestService;
import top.egon.mario.investment.quant.backtest.InvestmentBacktestJobHandler;
import top.egon.mario.investment.quant.backtest.model.BacktestEquityPoint;
import top.egon.mario.investment.quant.backtest.model.BacktestMetrics;
import top.egon.mario.investment.quant.backtest.model.BacktestResult;
import top.egon.mario.investment.quant.engine.BacktestEngine;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetHasher;
import top.egon.mario.investment.quant.dataset.InvestmentDatasetSnapshotService;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestEventRepository;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;
import top.egon.mario.investment.quant.repository.InvestmentBacktestTradeRepository;
import top.egon.mario.investment.quant.repository.InvestmentStrategyReleaseRepository;
import top.egon.mario.investment.quant.repository.jdbc.BacktestEquityJdbcRepository;
import top.egon.mario.investment.quant.strategy.InvestmentStrategy;
import top.egon.mario.investment.quant.strategy.InvestmentStrategyRegistry;
import top.egon.mario.investment.quant.strategy.fixture.TestEmaCrossStrategy;
import top.egon.mario.investment.quant.web.dto.SubmitInvestmentBacktestRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentBacktestServiceTests {

    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(120);

    private InvestmentAccessService accessService;
    private BacktestSourceResolver sourceResolver;
    private InvestmentStrategyRegistry strategyRegistry;
    private InvestmentStrategyReleaseRepository releaseRepository;
    private InvestmentDatasetSnapshotService snapshotService;
    private BacktestSubmissionPersistenceService submissionService;
    private InvestmentBacktestRunRepository runRepository;
    private InvestmentBacktestService service;

    @BeforeEach
    void setUp() {
        accessService = mock(InvestmentAccessService.class);
        sourceResolver = mock(BacktestSourceResolver.class);
        when(sourceResolver.resolve(any())).thenReturn(3L);
        strategyRegistry = mock(InvestmentStrategyRegistry.class);
        releaseRepository = mock(InvestmentStrategyReleaseRepository.class);
        snapshotService = mock(InvestmentDatasetSnapshotService.class);
        submissionService = mock(BacktestSubmissionPersistenceService.class);
        runRepository = mock(InvestmentBacktestRunRepository.class);
        service = new InvestmentBacktestService(accessService, sourceResolver, strategyRegistry, releaseRepository,
                snapshotService, submissionService, runRepository,
                mock(InvestmentBacktestTradeRepository.class), mock(InvestmentBacktestEventRepository.class),
                mock(BacktestEquityJdbcRepository.class),
                new InvestmentDatasetHasher(new ObjectMapper()), Clock.fixed(END.plusSeconds(60), ZoneOffset.UTC));
    }

    @Test
    void submitsOnlyARegisteredCodeStrategyAgainstAnOwnerScopedSnapshot() {
        InvestmentStrategy strategy = new TestEmaCrossStrategy();
        when(strategyRegistry.require("TEST_EMA_CROSS")).thenReturn(strategy);
        InvestmentStrategyReleasePo release = release(31L);
        when(releaseRepository.findByStrategyCodeAndStrategyVersion("TEST_EMA_CROSS", "1.0.0"))
                .thenReturn(Optional.of(release));
        InvestmentDatasetSnapshotPo snapshot = new InvestmentDatasetSnapshotPo();
        snapshot.setId(41L);
        snapshot.setDatasetHash("a".repeat(64));
        when(snapshotService.create(any())).thenReturn(
                new InvestmentDatasetSnapshotService.DatasetSnapshot(snapshot, List.of()));
        InvestmentBacktestRunPo run = run(51L);
        when(submissionService.submit(anyLong(), anyLong(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(run);

        var response = service.submit(5L, 7L, request());

        assertThat(response.runId()).isEqualTo(51L);
        assertThat(response.initialEquity()).isEqualTo("10000");
        ArgumentCaptor<InvestmentDatasetSnapshotService.CreateCommand> command =
                ArgumentCaptor.forClass(InvestmentDatasetSnapshotService.CreateCommand.class);
        verify(snapshotService).create(command.capture());
        assertThat(command.getValue().actorId()).isEqualTo(5L);
        assertThat(command.getValue().workspaceId()).isEqualTo(7L);
        assertThat(command.getValue().sourceId()).isEqualTo(3L);
        assertThat(command.getValue().dataAsOf()).isEqualTo(END.plusSeconds(60));
        verify(accessService).requireWorkspaceOwner(7L, 5L);
    }

    @Test
    void rejectsUnknownStrategyAndInvalidRangeBeforeWriting() {
        when(strategyRegistry.require("TEST_EMA_CROSS")).thenThrow(new InvestmentException(
                InvestmentErrorCode.CAPABILITY_UNAVAILABLE, "not installed"));
        assertThatThrownBy(() -> service.submit(5L, 7L, request()))
                .isInstanceOfSatisfying(InvestmentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.CAPABILITY_UNAVAILABLE));
        verify(snapshotService, never()).create(any());

        SubmitInvestmentBacktestRequest invalid = request();
        invalid.setEndTime(START);
        assertThatThrownBy(() -> service.submit(5L, 7L, invalid))
                .isInstanceOfSatisfying(InvestmentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
    }

    @Test
    void ownershipFailurePreventsRegistryAndRepositoryReads() {
        doThrow(new InvestmentException(InvestmentErrorCode.FORBIDDEN, "denied"))
                .when(accessService).requireWorkspaceOwner(7L, 5L);

        assertThatThrownBy(() -> service.submit(5L, 7L, request()))
                .isInstanceOf(InvestmentException.class);
        verify(strategyRegistry, never()).require(any());
        verify(runRepository, never()).findByIdAndWorkspaceIdAndDeletedFalse(anyLong(), anyLong());
    }

    @Test
    void detailRejectsRunsThatAreNotOwnedByTheAuthenticatedActor() {
        when(runRepository.findOwnedRun(51L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(5L, 51L))
                .isInstanceOfSatisfying(InvestmentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.FORBIDDEN));
    }

    @Test
    void sourceResolutionRequiresEveryInstrumentToShareOneServerManagedSource() {
        InvestmentContractSpecRepository repository = mock(InvestmentContractSpecRepository.class);
        BacktestSourceResolver resolver = new BacktestSourceResolver(repository);
        InvestmentContractSpecPo first = contractSpecification(11L, 3L);
        InvestmentContractSpecPo second = contractSpecification(12L, 3L);
        when(repository.findAllById(Set.of(11L, 12L))).thenReturn(List.of(first, second));

        assertThat(resolver.resolve(Set.of(11L, 12L))).isEqualTo(3L);

        second.setSourceId(4L);
        assertThatThrownBy(() -> resolver.resolve(Set.of(11L, 12L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("share one");
    }

    @Test
    void submissionPersistenceCollapsesJobRetriesToOneRun() {
        InvestmentJobEnqueueService enqueueService = mock(InvestmentJobEnqueueService.class);
        InvestmentBacktestRunRepository repository = mock(InvestmentBacktestRunRepository.class);
        BacktestSubmissionPersistenceService persistence = new BacktestSubmissionPersistenceService(
                enqueueService, repository, Clock.fixed(END, ZoneOffset.UTC));
        when(enqueueService.enqueue(any())).thenReturn(99L);
        InvestmentBacktestRunPo saved = run(77L);
        saved.setJobId(99L);
        when(repository.findByJobIdAndDeletedFalse(99L))
                .thenReturn(Optional.empty(), Optional.of(saved));
        when(repository.saveAndFlush(any())).thenReturn(saved);

        var first = persistence.submit(5L, 7L, 31L, 41L, new java.math.BigDecimal("10000"),
                TestEmaCrossStrategy.DESCRIPTOR, "backtest:key");
        var second = persistence.submit(5L, 7L, 31L, 41L, new java.math.BigDecimal("10000"),
                TestEmaCrossStrategy.DESCRIPTOR, "backtest:key");

        assertThat(first).isSameAs(saved);
        assertThat(second).isSameAs(saved);
        verify(repository).saveAndFlush(any());
    }

    @Test
    void completedJobRetryReturnsTheSameRunWithoutASecondEngineEffect() {
        BacktestRunStateService state = mock(BacktestRunStateService.class);
        BacktestDatasetLoader loader = mock(BacktestDatasetLoader.class);
        BacktestEngine engine = mock(BacktestEngine.class);
        BacktestResultPersistenceService results = mock(BacktestResultPersistenceService.class);
        InvestmentBacktestRunPo running = run(51L);
        running.setStatus("RUNNING");
        InvestmentBacktestRunPo succeeded = run(51L);
        succeeded.setStatus("SUCCEEDED");
        when(state.markRunning(61L)).thenReturn(running, succeeded);
        InvestmentStrategyReleasePo release = release(31L);
        when(releaseRepository.findById(31L)).thenReturn(Optional.of(release));
        InvestmentStrategy strategy = new TestEmaCrossStrategy();
        when(strategyRegistry.require("TEST_EMA_CROSS", "1.0.0")).thenReturn(strategy);
        var terms = new top.egon.mario.investment.trading.matching.model.ContractTerms(
                new java.math.BigDecimal("0.1"), new java.math.BigDecimal("0.001"), java.math.BigDecimal.ONE);
        var bar1 = new top.egon.mario.investment.trading.matching.model.FuturesBar(
                START, START.plusSeconds(60), new java.math.BigDecimal("100"),
                new java.math.BigDecimal("110"), new java.math.BigDecimal("90"),
                new java.math.BigDecimal("105"), true);
        var bar2 = new top.egon.mario.investment.trading.matching.model.FuturesBar(
                START.plusSeconds(60), END, new java.math.BigDecimal("105"),
                new java.math.BigDecimal("115"), new java.math.BigDecimal("95"),
                new java.math.BigDecimal("110"), true);
        var instrument = new top.egon.mario.investment.quant.backtest.model.BacktestInstrumentInput(
                11L, terms, new java.math.BigDecimal("0.0002"), new java.math.BigDecimal("0.0006"),
                new java.math.BigDecimal("3"), List.of(new top.egon.mario.investment.portfolio.margin.PositionTier(
                1, java.math.BigDecimal.ZERO, new java.math.BigDecimal("1000000"),
                new java.math.BigDecimal("50"), new java.math.BigDecimal("0.005"))),
                List.of(bar1, bar2), List.of(bar1, bar2), List.of(bar1, bar2), List.of());
        var input = new top.egon.mario.investment.quant.backtest.model.BacktestInput(
                strategy, new java.math.BigDecimal("10000"), List.of(instrument));
        when(loader.load(41L, strategy, running.getInitialEquity())).thenReturn(input);
        BacktestResult result = new BacktestResult(new BacktestMetrics(
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0L,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0L), List.of(), List.of(),
                List.of(new BacktestEquityPoint(END, new java.math.BigDecimal("10000"),
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        new java.math.BigDecimal("10000"), java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, false)));
        when(engine.run(input)).thenReturn(result);
        InvestmentBacktestJobHandler handler = new InvestmentBacktestJobHandler(
                state, releaseRepository, strategyRegistry, loader, engine, results);
        InvestmentJobClaim claim = new InvestmentJobClaim(61L, 7L, InvestmentJobType.BACKTEST_RUN,
                "{}", 0, 3, "worker", "token", START, END);

        assertThat(handler.execute(claim).resultJson()).isEqualTo("{\"runId\":51}");
        assertThat(handler.execute(claim).resultJson()).isEqualTo("{\"runId\":51}");

        verify(engine).run(input);
        verify(results).persist(51L, result);
        verify(state).markSucceeded(51L, result.metrics());
    }

    private SubmitInvestmentBacktestRequest request() {
        SubmitInvestmentBacktestRequest request = new SubmitInvestmentBacktestRequest();
        request.setStrategyCode("TEST_EMA_CROSS");
        request.setInstrumentIds(Set.of(11L));
        request.setStartTime(START);
        request.setEndTime(END);
        return request;
    }

    private InvestmentStrategyReleasePo release(long id) {
        InvestmentStrategyReleasePo value = new InvestmentStrategyReleasePo();
        value.setId(id);
        value.setStrategyCode("TEST_EMA_CROSS");
        value.setStrategyVersion("1.0.0");
        value.setActive(true);
        return value;
    }

    private InvestmentContractSpecPo contractSpecification(long instrumentId, long sourceId) {
        InvestmentContractSpecPo value = new InvestmentContractSpecPo();
        value.setInstrumentId(instrumentId);
        value.setSourceId(sourceId);
        return value;
    }

    private InvestmentBacktestRunPo run(long id) {
        InvestmentBacktestRunPo value = new InvestmentBacktestRunPo();
        value.setId(id);
        value.setWorkspaceId(7L);
        value.setJobId(61L);
        value.setStrategyReleaseId(31L);
        value.setDatasetSnapshotId(41L);
        value.setStatus("QUEUED");
        value.setInitialEquity(new java.math.BigDecimal("10000"));
        value.setCreatedAt(END);
        return value;
    }
}
