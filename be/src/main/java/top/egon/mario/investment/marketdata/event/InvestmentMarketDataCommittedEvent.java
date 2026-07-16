package top.egon.mario.investment.marketdata.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Sanitized signal emitted only after committed market-data writes and cache refresh.
 */
public record InvestmentMarketDataCommittedEvent(
        long sourceId,
        long instrumentId,
        String dataType,
        int recordCount,
        Instant dataAsOf
) {
    public InvestmentMarketDataCommittedEvent {
        if (sourceId <= 0 || instrumentId <= 0 || recordCount < 0) {
            throw new IllegalArgumentException("Invalid committed market-data event identifiers");
        }
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(dataAsOf, "dataAsOf");
    }
}
