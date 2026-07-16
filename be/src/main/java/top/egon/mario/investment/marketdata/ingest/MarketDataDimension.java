package top.egon.mario.investment.marketdata.ingest;

/**
 * Database identifiers resolved from a code subscription immediately before writing.
 */
public record MarketDataDimension(long sourceId, long instrumentId) {

    public MarketDataDimension {
        if (sourceId <= 0 || instrumentId <= 0) {
            throw new IllegalArgumentException("sourceId and instrumentId must be positive");
        }
    }
}
