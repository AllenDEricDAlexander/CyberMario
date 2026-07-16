package top.egon.mario.investment.marketdata.repository.jdbc.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One persisted funding-rate revision.
 */
public record FundingRateRow(
        long sourceId,
        long instrumentId,
        Instant fundingTime,
        BigDecimal fundingRate,
        Instant ingestedAt,
        long revision,
        Instant validFrom,
        Instant validTo,
        String checksum
) {
}
