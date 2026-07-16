package top.egon.mario.investment.portfolio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.mario.investment.overview.InvestmentOverviewSectionContributor;
import top.egon.mario.investment.portfolio.overview.PortfolioOverviewSectionContributor;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;
import top.egon.mario.investment.portfolio.web.dto.InvestmentPositionResponse;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioOverviewSectionContributorTests {

    @Mock private InvestmentPortfolioQueryService queryService;

    @Test
    void overviewUsesTheAggregatorCutoffAndReturnsDecimalStringsAndRiskWarnings() {
        Instant cutoff = Instant.parse("2026-07-17T00:00:00Z");
        when(queryService.workspaceSummary(11L, cutoff)).thenReturn(PortfolioReportGeneratorTests.summary(cutoff));
        when(queryService.workspacePositions(11L, cutoff))
                .thenReturn(List.of(PortfolioReportGeneratorTests.position(cutoff)));
        PortfolioOverviewSectionContributor contributor = new PortfolioOverviewSectionContributor(queryService);

        var result = contributor.contribute(
                new InvestmentOverviewSectionContributor.OverviewContext(101L, 11L, cutoff));

        assertThat(result.code()).isEqualTo("PORTFOLIO");
        assertThat(result.dataAsOf()).isEqualTo(cutoff);
        assertThat(result.data()).containsEntry("equity", "120")
                .containsEntry("riskWarningCount", 1L).containsEntry("positionCount", 2L);
        assertThat((List<InvestmentPositionResponse>) result.data().get("positions"))
                .singleElement().satisfies(position -> {
                    assertThat(position.instrumentId()).isEqualTo(501L);
                    assertThat(position.unrealizedPnl()).isEqualTo("20");
                });
    }
}
