package top.egon.mario.investment.overview;

import top.egon.mario.investment.overview.dto.InvestmentOverviewSectionResponse;

import java.time.Instant;

/**
 * Strategy extension point for one overview domain section.
 */
public interface InvestmentOverviewSectionContributor {

    String sectionCode();

    int order();

    InvestmentOverviewSectionResponse contribute(OverviewContext context);

    /**
     * Server-bound owner and snapshot boundary shared by every contributor.
     */
    record OverviewContext(Long actorId, Long workspaceId, Instant dataAsOf) {
    }
}
