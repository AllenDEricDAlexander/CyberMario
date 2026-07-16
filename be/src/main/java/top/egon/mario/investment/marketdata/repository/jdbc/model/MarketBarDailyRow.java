package top.egon.mario.investment.marketdata.repository.jdbc.model;

import top.egon.mario.investment.common.model.PriceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One persisted daily bar revision.
 */
public record MarketBarDailyRow(
        long sourceId,
        long instrumentId,
        PriceType priceType,
        LocalDate barDate,
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
