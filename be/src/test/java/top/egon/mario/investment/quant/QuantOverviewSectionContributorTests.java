package top.egon.mario.investment.quant;

import org.junit.jupiter.api.Test;
import top.egon.mario.investment.overview.InvestmentOverviewSectionContributor;
import top.egon.mario.investment.quant.overview.QuantOverviewSectionContributor;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuantOverviewSectionContributorTests {

    @Test
    void returnsOnlyWorkspaceRunsNoLaterThanSharedCutoff() {
        InvestmentBacktestRunRepository repository = mock(InvestmentBacktestRunRepository.class);
        Instant cutoff = Instant.parse("2035-01-01T00:00:00Z");
        InvestmentBacktestRunPo run = new InvestmentBacktestRunPo();
        run.setId(51L);
        run.setStrategyReleaseId(31L);
        run.setDatasetSnapshotId(41L);
        run.setTotalReturn(new BigDecimal("0.12"));
        run.setMaxDrawdown(new BigDecimal("0.03"));
        run.setWinRate(new BigDecimal("0.5"));
        run.setTradeCount(4L);
        run.setFinishedAt(cutoff.minusSeconds(1));
        when(repository
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        7L, "SUCCEEDED", cutoff)).thenReturn(List.of(run));

        var section = new QuantOverviewSectionContributor(repository).contribute(
                new InvestmentOverviewSectionContributor.OverviewContext(5L, 7L, cutoff));

        assertThat(section.code()).isEqualTo("QUANT");
        assertThat(section.dataAsOf()).isEqualTo(cutoff);
        assertThat((List<?>) section.data().get("recentBacktests")).singleElement().satisfies(value ->
                assertThat(value.toString()).contains("runId=51", "totalReturn=0.12"));
        verify(repository)
                .findTop5ByWorkspaceIdAndStatusAndFinishedAtLessThanEqualAndDeletedFalseOrderByFinishedAtDescIdDesc(
                        7L, "SUCCEEDED", cutoff);
    }
}
