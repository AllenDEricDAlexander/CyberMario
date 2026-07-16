package top.egon.mario.investment.marketdata.repository.jdbc.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable job and effective-time context shared by one atomic ingestion page.
 */
public record MarketDataWriteContext(
        long jobId,
        Instant effectiveAt,
        Instant nextStartTime
) {
    public MarketDataWriteContext {
        if (jobId <= 0) {
            throw new IllegalArgumentException("jobId must be positive");
        }
        Objects.requireNonNull(effectiveAt, "effectiveAt");
    }
}
