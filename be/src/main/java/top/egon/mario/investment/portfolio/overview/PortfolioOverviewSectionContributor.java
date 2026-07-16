package top.egon.mario.investment.portfolio.overview;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.overview.InvestmentOverviewSectionContributor;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;
import top.egon.mario.investment.portfolio.query.InvestmentPortfolioQueryService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Portfolio overview projection at the aggregator-owned cutoff. */
@Component
@RequiredArgsConstructor
public class PortfolioOverviewSectionContributor implements InvestmentOverviewSectionContributor {

    private final InvestmentPortfolioQueryService queryService;

    @Override
    public String sectionCode() {
        return "PORTFOLIO";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public InvestmentOverviewSectionResponse contribute(OverviewContext context) {
        var summary = queryService.workspaceSummary(context.workspaceId(), context.dataAsOf());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountCount", summary.accountCount());
        data.put("positionCount", summary.positionCount());
        data.put("walletBalance", text(summary.walletBalance()));
        data.put("equity", text(summary.equity()));
        data.put("availableBalance", text(summary.availableBalance()));
        data.put("unrealizedPnl", text(summary.unrealizedPnl()));
        data.put("grossExposure", text(summary.grossExposure()));
        data.put("maxDrawdown", text(summary.maxDrawdown()));
        data.put("riskWarningCount", summary.riskWarningCount());
        data.put("positions", queryService.workspacePositions(context.workspaceId(), context.dataAsOf()));
        return new InvestmentOverviewSectionResponse(
                sectionCode(), "AVAILABLE", context.dataAsOf(), data, null);
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
