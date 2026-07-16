package top.egon.mario.investment.overview;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.investment.marketdata.query.InvestmentMarketQueryService;
import top.egon.mario.investment.marketdata.web.dto.InvestmentMarketOverviewResponse;
import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared platform-market and data-warning overview strategy.
 */
@Component
@RequiredArgsConstructor
public class MarketOverviewSectionContributor implements InvestmentOverviewSectionContributor {

    private final InvestmentMarketQueryService marketQueryService;

    @Override
    public String sectionCode() {
        return "MARKET";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public InvestmentOverviewSectionResponse contribute(OverviewContext context) {
        InvestmentMarketOverviewResponse market = marketQueryService.overview(context.dataAsOf());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subscribedInstrumentCount", market.subscribedInstrumentCount());
        data.put("freshQuoteCount", market.freshQuoteCount());
        data.put("staleOrMissingQuoteCount", market.staleOrMissingQuoteCount());
        data.put("openQualityIssueCount", market.openQualityIssueCount());
        return new InvestmentOverviewSectionResponse(
                sectionCode(), "AVAILABLE", context.dataAsOf(), data, null);
    }
}
