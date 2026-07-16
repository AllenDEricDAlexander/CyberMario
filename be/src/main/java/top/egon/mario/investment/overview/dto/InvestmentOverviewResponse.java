package top.egon.mario.investment.overview.dto;

import java.time.Instant;
import java.util.List;

/**
 * One owner-scoped overview snapshot selected at a single server cutoff.
 */
public record InvestmentOverviewResponse(
        Long workspaceId,
        Instant dataAsOf,
        List<InvestmentOverviewSectionResponse> sections
) {

    public InvestmentOverviewResponse {
        sections = List.copyOf(sections);
    }
}
