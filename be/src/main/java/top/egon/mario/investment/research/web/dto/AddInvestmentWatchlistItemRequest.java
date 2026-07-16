package top.egon.mario.investment.research.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request for adding or restoring one instrument in a watchlist.
 */
public record AddInvestmentWatchlistItemRequest(
        @NotNull @Positive Long instrumentId,
        @Size(max = 512) String note
) {
}
