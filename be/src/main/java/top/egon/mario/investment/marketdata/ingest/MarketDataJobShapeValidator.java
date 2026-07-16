package top.egon.mario.investment.marketdata.ingest;

import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;

/**
 * Central allow-list for durable market job payload shapes.
 */
public final class MarketDataJobShapeValidator {

    public void validate(InvestmentJobType jobType, MarketDataJobInput input) {
        boolean ranged = input.startInclusive() != null;
        switch (jobType) {
            case CONTRACT_SYNC -> requireNonTemporal(input, DataCapability.CONTRACT_METADATA);
            case POSITION_TIER_SYNC -> requireNonTemporal(input, DataCapability.POSITION_TIER);
            case QUOTE_REFRESH -> {
                requireNone(input);
                if (input.capability() != DataCapability.LATEST_TICKER
                        && input.capability() != DataCapability.OPEN_INTEREST) {
                    invalid("QUOTE_REFRESH requires LATEST_TICKER or OPEN_INTEREST");
                }
                require(!ranged, "QUOTE_REFRESH must not carry a time range");
            }
            case BAR_INCREMENTAL, BAR_BACKFILL -> {
                require(ranged, "Candle jobs require a half-open time range");
                require(input.interval() != BarInterval.NONE, "Candle jobs require a concrete interval");
                PriceType expected = switch (input.capability()) {
                    case MARKET_CANDLE -> PriceType.MARKET;
                    case MARK_CANDLE -> PriceType.MARK;
                    case INDEX_CANDLE -> PriceType.INDEX;
                    default -> null;
                };
                require(expected != null && input.priceType() == expected,
                        "Candle capability and price type do not match");
            }
            case FUNDING_RATE_INCREMENTAL, FUNDING_RATE_BACKFILL -> {
                requireNone(input);
                require(input.capability() == DataCapability.FUNDING_RATE,
                        "Funding jobs require FUNDING_RATE");
                require(ranged, "Funding jobs require a half-open time range");
            }
            case DATA_QUALITY_CHECK -> {
                requireNone(input);
                require(!ranged, "DATA_QUALITY_CHECK must not carry a time range");
            }
            default -> invalid("Job type is not a market-data ingestion job: " + jobType);
        }
    }

    private void requireNonTemporal(MarketDataJobInput input, DataCapability capability) {
        requireNone(input);
        require(input.capability() == capability, "Unexpected capability for non-temporal job");
        require(input.startInclusive() == null, "Non-temporal jobs must not carry a time range");
    }

    private void requireNone(MarketDataJobInput input) {
        require(input.priceType() == PriceType.NONE, "PriceType must be NONE");
        require(input.interval() == BarInterval.NONE, "BarInterval must be NONE");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            invalid(message);
        }
    }

    private void invalid(String message) {
        throw new InvestmentJobNonRetryableException("MARKET_JOB_SHAPE_INVALID", message);
    }
}
