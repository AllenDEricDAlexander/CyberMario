package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * One ascending candle revision for charting.
 */
public record InvestmentCandleResponse(
        Instant openTime,
        Instant closeTime,
        String open,
        String high,
        String low,
        String close,
        String baseVolume,
        String quoteVolume,
        boolean isClosed,
        long revision,
        Instant dataAsOf
) {
}
