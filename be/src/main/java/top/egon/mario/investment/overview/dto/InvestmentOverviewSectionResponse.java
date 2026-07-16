package top.egon.mario.investment.overview.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Stable cross-domain overview section envelope.
 */
public record InvestmentOverviewSectionResponse(
        String code,
        String status,
        Instant dataAsOf,
        Map<String, Object> data,
        String errorCode
) {

    public InvestmentOverviewSectionResponse {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static InvestmentOverviewSectionResponse unavailable(String code, Instant cutoff) {
        return new InvestmentOverviewSectionResponse(code, "UNAVAILABLE", cutoff, Map.of(), null);
    }

    public static InvestmentOverviewSectionResponse error(String code, Instant cutoff) {
        return new InvestmentOverviewSectionResponse(code, "ERROR", cutoff, Map.of(), "SECTION_QUERY_FAILED");
    }
}
