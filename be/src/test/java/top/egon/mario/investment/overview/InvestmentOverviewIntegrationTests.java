package top.egon.mario.investment.overview;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.overview.AgentOverviewSectionContributor;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentMarketOverviewResponse;
import top.egon.mario.investment.overview.dto.InvestmentOverviewResponse;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;
import top.egon.mario.investment.portfolio.overview.PortfolioOverviewSectionContributor;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.query.PortfolioWorkspaceSummary;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPositionResponse;
import top.egon.mario.investment.quant.overview.QuantOverviewSectionContributor;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Cross-domain projection proof using the production contributor strategies. */
class InvestmentOverviewIntegrationTests {

    private static final Instant CUTOFF = Instant.parse("2035-01-01T00:00:00Z");

    @Test
    void aggregatesMarketPortfolioQuantAndAgentAtOneOwnerValidatedCutoffWithoutNPlusOne() {
        Fixture fixture = fixture();
        InvestmentBacktestRunPo backtest = backtest(71L);
        InvestmentAgentRunPo agentRun = agentRun(81L);
        InvestmentAgentRunPo secondAgentRun = agentRun(82L);
        InvestmentAgentDecisionPo decision = decision(91L, agentRun.getId());
        InvestmentAgentDecisionPo secondDecision = decision(92L, secondAgentRun.getId());
        when(fixture.marketQueryService().overview(CUTOFF)).thenReturn(
                new InvestmentMarketOverviewResponse(3, 2, 1, 4, CUTOFF));
        when(fixture.portfolioQueryService().workspaceSummary(11L, CUTOFF)).thenReturn(
                new PortfolioWorkspaceSummary(
                        11L, CUTOFF, 2, 1, decimal("1000"), decimal("1125"), decimal("900"),
                        decimal("125"), decimal("500"), decimal("0.08"), 1));
        when(fixture.portfolioQueryService().workspacePositions(11L, CUTOFF)).thenReturn(List.of(
                new InvestmentPositionResponse(
                        61L, 501L, "LONG", "0.1", "60000", "3", "65000", "50000",
                        "200", "20", "0", "5", "50", CUTOFF.minusSeconds(60), CUTOFF.minusSeconds(30))));
        when(fixture.backtestRepository()
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        11L, "SUCCEEDED", CUTOFF)).thenReturn(List.of(backtest));
        when(fixture.agentRunRepository()
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        11L, InvestmentRunStatus.SUCCEEDED, CUTOFF))
                .thenReturn(List.of(agentRun, secondAgentRun));
        when(fixture.agentDecisionRepository().findByRunIdInOrderByRunIdAscIdAsc(List.of(81L, 82L)))
                .thenReturn(List.of(decision, secondDecision));

        InvestmentOverviewResponse response = fixture.service().overview(101L, 11L);

        assertThat(response.workspaceId()).isEqualTo(11L);
        assertThat(response.dataAsOf()).isEqualTo(CUTOFF);
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::code)
                .containsExactly("MARKET", "QUANT", "PORTFOLIO", "AGENT");
        assertThat(response.sections()).extracting(InvestmentOverviewSectionResponse::status)
                .containsOnly("AVAILABLE");
        assertThat(section(response, "MARKET").data()).containsEntry("staleOrMissingQuoteCount", 1L);
        assertThat(section(response, "PORTFOLIO").data())
                .containsEntry("accountCount", 2L)
                .containsEntry("equity", "1125")
                .containsEntry("riskWarningCount", 1L);
        assertThat((List<?>) section(response, "QUANT").data().get("recentBacktests"))
                .singleElement().asString().contains("runId=71", "totalReturn=0.12");
        assertThat((List<?>) section(response, "AGENT").data().get("recentRuns"))
                .hasSize(2)
                .first().asString().contains("runId=81", "decisionId=91", "action=OPEN_LONG");

        verify(fixture.accessService()).requireWorkspaceOwner(11L, 101L);
        verify(fixture.marketQueryService()).overview(CUTOFF);
        verify(fixture.portfolioQueryService()).workspaceSummary(11L, CUTOFF);
        verify(fixture.portfolioQueryService()).workspacePositions(11L, CUTOFF);
        verify(fixture.backtestRepository())
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        11L, "SUCCEEDED", CUTOFF);
        verify(fixture.agentRunRepository())
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        11L, InvestmentRunStatus.SUCCEEDED, CUTOFF);
        verify(fixture.agentDecisionRepository()).findByRunIdInOrderByRunIdAscIdAsc(List.of(81L, 82L));
        verify(fixture.agentDecisionRepository(), never()).findFirstByRunIdOrderByIdAsc(81L);
        verify(fixture.agentDecisionRepository(), never()).findFirstByRunIdOrderByIdAsc(82L);
    }

    @Test
    void keepsSafeSectionsWhenOneDomainProjectionFails() {
        Fixture fixture = fixture();
        when(fixture.marketQueryService().overview(CUTOFF)).thenReturn(
                new InvestmentMarketOverviewResponse(0, 0, 0, 0, CUTOFF));
        when(fixture.portfolioQueryService().workspaceSummary(11L, CUTOFF)).thenReturn(
                new PortfolioWorkspaceSummary(
                        11L, CUTOFF, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
        when(fixture.portfolioQueryService().workspacePositions(11L, CUTOFF)).thenReturn(List.of());
        when(fixture.backtestRepository()
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        11L, "SUCCEEDED", CUTOFF)).thenThrow(new IllegalStateException("quant unavailable"));
        when(fixture.agentRunRepository()
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        11L, InvestmentRunStatus.SUCCEEDED, CUTOFF)).thenReturn(List.of());

        InvestmentOverviewResponse response = fixture.service().overview(101L, 11L);

        assertThat(response.sections()).extracting(
                        InvestmentOverviewSectionResponse::code,
                        InvestmentOverviewSectionResponse::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MARKET", "AVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("QUANT", "ERROR"),
                        org.assertj.core.groups.Tuple.tuple("PORTFOLIO", "AVAILABLE"),
                        org.assertj.core.groups.Tuple.tuple("AGENT", "AVAILABLE"));
        assertThat(section(response, "QUANT").errorCode()).isEqualTo("SECTION_QUERY_FAILED");
    }

    @Test
    void rejectsMissingOrCrossOwnerWorkspaceBeforeAnyDomainQuery() {
        Fixture fixture = fixture();
        doThrow(new InvestmentException(InvestmentErrorCode.NOT_FOUND, "workspace missing"))
                .when(fixture.accessService()).requireWorkspaceOwner(99L, 101L);

        assertThatThrownBy(() -> fixture.service().overview(101L, 99L))
                .isInstanceOf(InvestmentException.class)
                .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.NOT_FOUND));

        verifyNoInteractions(
                fixture.marketQueryService(), fixture.portfolioQueryService(), fixture.backtestRepository(),
                fixture.agentRunRepository(), fixture.agentDecisionRepository());
    }

    private Fixture fixture() {
        InvestmentAccessService accessService = mock(InvestmentAccessService.class);
        InvestmentMarketQueryService marketQueryService = mock(InvestmentMarketQueryService.class);
        InvestmentPortfolioQueryService portfolioQueryService = mock(InvestmentPortfolioQueryService.class);
        InvestmentBacktestRunRepository backtestRepository = mock(InvestmentBacktestRunRepository.class);
        InvestmentAgentRunRepository agentRunRepository = mock(InvestmentAgentRunRepository.class);
        InvestmentAgentDecisionRepository agentDecisionRepository = mock(InvestmentAgentDecisionRepository.class);
        InvestmentOverviewQueryService service = new InvestmentOverviewQueryService(
                accessService,
                List.of(
                        new AgentOverviewSectionContributor(agentRunRepository, agentDecisionRepository),
                        new PortfolioOverviewSectionContributor(portfolioQueryService),
                        new QuantOverviewSectionContributor(backtestRepository),
                        new MarketOverviewSectionContributor(marketQueryService)),
                Clock.fixed(CUTOFF, ZoneOffset.UTC));
        return new Fixture(
                service, accessService, marketQueryService, portfolioQueryService, backtestRepository,
                agentRunRepository, agentDecisionRepository);
    }

    private InvestmentBacktestRunPo backtest(Long id) {
        InvestmentBacktestRunPo run = new InvestmentBacktestRunPo();
        run.setId(id);
        run.setStrategyReleaseId(41L);
        run.setDatasetSnapshotId(51L);
        run.setTotalReturn(decimal("0.12"));
        run.setMaxDrawdown(decimal("0.04"));
        run.setWinRate(decimal("0.60"));
        run.setTradeCount(9L);
        run.setFinishedAt(CUTOFF.minusSeconds(2));
        return run;
    }

    private InvestmentAgentRunPo agentRun(Long id) {
        InvestmentAgentRunPo run = new InvestmentAgentRunPo();
        run.setId(id);
        run.setRunType(InvestmentAgentRunType.AUTO_TRADE);
        run.setAccountId(31L);
        run.setReportId(41L);
        run.setDataAsOf(CUTOFF.minusSeconds(60));
        run.setFinishedAt(CUTOFF.minusSeconds(1));
        return run;
    }

    private InvestmentAgentDecisionPo decision(Long id, Long runId) {
        InvestmentAgentDecisionPo decision = new InvestmentAgentDecisionPo();
        decision.setId(id);
        decision.setRunId(runId);
        decision.setInstrumentId(501L);
        decision.setAction(InvestmentAgentAction.OPEN_LONG);
        decision.setConfidence(decimal("0.75"));
        decision.setExecutionStatus(InvestmentAgentExecutionStatus.SUBMITTED);
        decision.setIntentId(101L);
        return decision;
    }

    private InvestmentOverviewSectionResponse section(InvestmentOverviewResponse response, String code) {
        return response.sections().stream().filter(section -> code.equals(section.code())).findFirst().orElseThrow();
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record Fixture(
            InvestmentOverviewQueryService service,
            InvestmentAccessService accessService,
            InvestmentMarketQueryService marketQueryService,
            InvestmentPortfolioQueryService portfolioQueryService,
            InvestmentBacktestRunRepository backtestRepository,
            InvestmentAgentRunRepository agentRunRepository,
            InvestmentAgentDecisionRepository agentDecisionRepository) {
    }
}
