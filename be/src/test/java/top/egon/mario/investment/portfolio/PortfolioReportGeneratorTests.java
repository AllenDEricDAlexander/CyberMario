package top.egon.mario.investment.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.query.PortfolioWorkspaceSummary;
import top.egon.mario.investment.portfolio.report.PortfolioReportGenerator;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPositionResponse;
import top.egon.mario.investment.research.report.FrozenResearchReportInput;
import top.egon.mario.investment.research.report.InvestmentReportType;
import top.egon.mario.investment.research.report.ResearchEvidenceSource;
import top.egon.mario.investment.research.report.ResearchEvidenceSourceService;
import top.egon.mario.investment.research.report.ResearchReportGenerationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioReportGeneratorTests {

    @Mock private InvestmentPortfolioQueryService queryService;
    @Mock private ResearchEvidenceSourceService evidenceSourceService;

    @Test
    void reportFreezesPortfolioMetricsAndEvidenceAtTheInputCutoff() {
        Instant cutoff = Instant.parse("2026-07-17T00:00:00Z");
        when(queryService.workspaceSummary(11L, cutoff)).thenReturn(summary(cutoff));
        when(queryService.workspacePositions(11L, cutoff)).thenReturn(List.of(position(cutoff)));
        when(evidenceSourceService.requireMarketSources())
                .thenReturn(List.of(new ResearchEvidenceSource(1L, 501L, "BITGET", "BTCUSDT")));
        PortfolioReportGenerator generator = new PortfolioReportGenerator(
                queryService, evidenceSourceService, new ObjectMapper().findAndRegisterModules());
        FrozenResearchReportInput input = new FrozenResearchReportInput(
                InvestmentReportType.PORTFOLIO_REPORT, null, null, null, null, null, cutoff);

        var result = generator.generate(new ResearchReportGenerationContext(31L, 11L, 1L, input, "hash"));

        assertThat(generator.reportType()).isEqualTo(InvestmentReportType.PORTFOLIO_REPORT);
        assertThat(result.metrics()).containsEntry("equity", "120").containsEntry("riskWarningCount", 1L);
        assertThat((List<InvestmentPositionResponse>) result.metrics().get("positions"))
                .singleElement().satisfies(position -> {
                    assertThat(position.instrumentId()).isEqualTo(501L);
                    assertThat(position.markPrice()).isEqualTo("110");
                });
        assertThat(result.contentMarkdown()).contains(cutoff.toString()).contains("仅描述模拟盘事实");
        assertThat(result.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.dataAsOf()).isEqualTo(cutoff);
            assertThat(evidence.evidenceType()).isEqualTo("PORTFOLIO");
        });
    }

    static PortfolioWorkspaceSummary summary(Instant cutoff) {
        return new PortfolioWorkspaceSummary(
                11L, cutoff, 1L, 2L, new BigDecimal("100"), new BigDecimal("120"),
                new BigDecimal("90"), new BigDecimal("20"), new BigDecimal("200"),
                new BigDecimal("0.2"), 1L);
    }

    static InvestmentPositionResponse position(Instant cutoff) {
        return new InvestmentPositionResponse(
                71L, 501L, "LONG", "2", "100", "10", "110", "80", "20", "5",
                "0", "0", "20", cutoff.minusSeconds(60), cutoff);
    }
}
