package top.egon.mario.investment.research.web.dto;

import java.time.Instant;

/**
 * One instrument entry in a private Investment watchlist.
 */
public record InvestmentWatchlistItemResponse(
        Long id,
        Long instrumentId,
        int sortNo,
        String note,
        Instant createdAt
) {
}
