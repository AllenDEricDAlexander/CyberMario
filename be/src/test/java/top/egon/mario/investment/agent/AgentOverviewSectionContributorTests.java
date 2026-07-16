package top.egon.mario.investment.agent;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.agent.model.InvestmentAgentAction;
import top.egon.mario.investment.agent.model.InvestmentAgentExecutionStatus;
import top.egon.mario.investment.agent.model.InvestmentAgentRunType;
import top.egon.mario.investment.agent.overview.AgentOverviewSectionContributor;
import top.egon.mario.investment.agent.po.InvestmentAgentDecisionPo;
import top.egon.mario.investment.agent.po.InvestmentAgentRunPo;
import top.egon.mario.investment.agent.repository.InvestmentAgentDecisionRepository;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.common.model.InvestmentRunStatus;
import top.egon.mario.investment.overview.InvestmentOverviewSectionContributor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOverviewSectionContributorTests {

    @Test
    void returnsOnlyWorkspaceRunsNoLaterThanTheOwnerValidatedSharedCutoff() {
        InvestmentAgentRunRepository runRepository = mock(InvestmentAgentRunRepository.class);
        InvestmentAgentDecisionRepository decisionRepository = mock(InvestmentAgentDecisionRepository.class);
        Instant cutoff = Instant.parse("2035-01-01T00:00:00Z");
        InvestmentAgentRunPo run = new InvestmentAgentRunPo();
        run.setId(41L);
        run.setRunType(InvestmentAgentRunType.AUTO_TRADE);
        run.setAccountId(31L);
        run.setReportId(91L);
        run.setDataAsOf(cutoff.minusSeconds(60));
        run.setFinishedAt(cutoff.minusSeconds(1));
        InvestmentAgentDecisionPo decision = new InvestmentAgentDecisionPo();
        decision.setId(51L);
        decision.setInstrumentId(11L);
        decision.setAction(InvestmentAgentAction.OPEN_LONG);
        decision.setConfidence(new BigDecimal("0.75"));
        decision.setExecutionStatus(InvestmentAgentExecutionStatus.SUBMITTED);
        decision.setIntentId(61L);
        when(runRepository
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        7L, InvestmentRunStatus.SUCCEEDED, cutoff)).thenReturn(List.of(run));
        when(decisionRepository.findFirstByRunIdOrderByIdAsc(41L)).thenReturn(Optional.of(decision));

        var section = new AgentOverviewSectionContributor(runRepository, decisionRepository).contribute(
                new InvestmentOverviewSectionContributor.OverviewContext(5L, 7L, cutoff));

        assertThat(section.code()).isEqualTo("AGENT");
        assertThat(section.dataAsOf()).isEqualTo(cutoff);
        assertThat((List<?>) section.data().get("recentRuns")).singleElement().satisfies(value ->
                assertThat(value.toString()).contains("runId=41", "decisionId=51", "intentId=61"));
        verify(runRepository)
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        7L, InvestmentRunStatus.SUCCEEDED, cutoff);
    }
}
