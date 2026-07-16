package top.egon.mario.investment.marketdata.repository.jdbc.model;

import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One persisted intraday bar revision.
 */
public record MarketBarIntradayRow(
        long sourceId,
        long instrumentId,
        PriceType priceType,
        BarInterval interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal baseVolume,
        BigDecimal quoteVolume,
        boolean closed,
        Instant sourceUpdatedAt,
        Instant ingestedAt,
        long revision,
        Instant validFrom,
        Instant validTo,
        String checksum
) {
}
