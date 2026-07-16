package top.egon.mario.investment.quant.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.mario.investment.quant.po.InvestmentBacktestRunPo;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;
import top.egon.mario.investment.research.report.FrozenResearchReportInput;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuantReportGeneratorTests {

    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(3600);
    private static final Instant CUTOFF = END.plusSeconds(60);

    @Test
    void strategyAnalysisUsesFrozenDescriptorAndDatasetEvidence() {
        BacktestReportSupport support = mock(BacktestReportSupport.class);
        BacktestReportSupport.ReportFacts facts = facts();
        when(support.latest(7L, CUTOFF)).thenReturn(facts);

        var report = new StrategyAnalysisReportGenerator(support, new ObjectMapper())
                .generate(context(InvestmentReportType.STRATEGY_ANALYSIS));

        assertThat(report.metrics()).containsEntry("strategyCode", "TEST_EMA_CROSS")
                .containsEntry("backtestRunId", 51L);
        assertThat(report.contentMarkdown()).contains("build-1", "a".repeat(64), "3");
        assertThat(report.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.dataAsOf()).isEqualTo(CUTOFF);
            assertThat(evidence.payloadHash()).isEqualTo("a".repeat(64));
        });
    }

    @Test
    void backtestReportMetricsComeOnlyFromPersistedRunAtCutoff() {
        BacktestReportSupport support = mock(BacktestReportSupport.class);
        when(support.latest(7L, CUTOFF)).thenReturn(facts());

        var report = new BacktestReportGenerator(support, new ObjectMapper())
                .generate(context(InvestmentReportType.BACKTEST_REPORT));

        assertThat(report.metrics()).containsEntry("totalReturn", "0.12")
                .containsEntry("maxDrawdown", "0.03")
                .containsEntry("tradeCount", 4L);
        assertThat(report.contentMarkdown()).contains("Bar", "0.12", "0.03");
    }

    private ResearchReportGenerationContext context(InvestmentReportType type) {
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                type, null, null, null, null, null, CUTOFF);
        return new ResearchReportGenerationContext(81L, 7L, 1L, input, "b".repeat(64));
    }

    private BacktestReportSupport.ReportFacts facts() {
        InvestmentBacktestRunPo run = new InvestmentBacktestRunPo();
        run.setId(51L);
        run.setStrategyReleaseId(31L);
        run.setDatasetSnapshotId(41L);
        run.setInitialEquity(new BigDecimal("10000"));
        run.setTotalReturn(new BigDecimal("0.12"));
        run.setMaxDrawdown(new BigDecimal("0.03"));
        run.setWinRate(new BigDecimal("0.5"));
        run.setTradeCount(4L);
        run.setTotalFee(new BigDecimal("12"));
        run.setTotalFunding(new BigDecimal("-2"));
        run.setLiquidationCount(0L);
        InvestmentDatasetSnapshotPo snapshot = new InvestmentDatasetSnapshotPo();
        snapshot.setId(41L);
        snapshot.setSourceId(3L);
        snapshot.setStartTime(START);
        snapshot.setEndTime(END);
        snapshot.setDatasetHash("a".repeat(64));
        InvestmentStrategyReleasePo strategy = new InvestmentStrategyReleasePo();
        strategy.setStrategyCode("TEST_EMA_CROSS");
        strategy.setStrategyVersion("1.0.0");
        strategy.setDisplayName("EMA Cross");
        strategy.setBuildRevision("build-1");
        strategy.setSourceHash("c".repeat(64));
        return new BacktestReportSupport.ReportFacts(run, snapshot, strategy,
                Map.of("defaultLeverage", "3", "matchingModelCode", "NEXT_BAR_V1"));
    }
}
