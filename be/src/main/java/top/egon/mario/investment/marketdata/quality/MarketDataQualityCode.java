package top.egon.mario.investment.marketdata.quality;

/**
 * Stable persisted market-data quality codes.
 */
public enum MarketDataQualityCode {
    GAP,
    DUPLICATE,
    OHLC_INVALID,
    NEGATIVE_VOLUME,
    UNEXPECTED_REVISION,
    STALE_QUOTE,
    MISSING_MARK_PRICE,
    MISSING_OPEN_INTEREST,
    MISSING_FUNDING_RATE,
    MISSING_POSITION_TIER,
    OUT_OF_SUBSCRIPTION
}
