package top.egon.mario.investment.marketdata.repository.jdbc.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Normalized funding observation ready for revision-aware persistence.
 */
public record FundingRateWrite(
        long sourceId,
        long instrumentId,
        Instant fundingTime,
        BigDecimal fundingRate,
        Instant ingestedAt,
        String checksum
) {
    public FundingRateWrite {
        MarketDataModelValidation.ids(sourceId, instrumentId);
        Objects.requireNonNull(fundingTime, "fundingTime");
        Objects.requireNonNull(fundingRate, "fundingRate");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        checksum = MarketDataModelValidation.checksum(checksum);
    }
}
