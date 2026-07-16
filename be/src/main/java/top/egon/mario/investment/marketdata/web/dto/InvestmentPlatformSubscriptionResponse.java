package top.egon.mario.investment.marketdata.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Read-only view of a Java-declared market subscription.
 */
public record InvestmentPlatformSubscriptionResponse(
        String sourceCode,
        String productType,
        String symbol,
        String status,
        List<String> capabilities,
        List<String> priceTypes,
        List<String> intervals,
        Map<String, String> refreshIntervals,
        Map<String, String> backfillWindows,
        Map<String, String> retention
) {

    public InvestmentPlatformSubscriptionResponse {
        capabilities = List.copyOf(capabilities);
        priceTypes = List.copyOf(priceTypes);
        intervals = List.copyOf(intervals);
        refreshIntervals = Map.copyOf(refreshIntervals);
        backfillWindows = Map.copyOf(backfillWindows);
        retention = Map.copyOf(retention);
    }
}
