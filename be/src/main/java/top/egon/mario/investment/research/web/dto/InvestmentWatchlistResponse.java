package top.egon.mario.investment.research.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Private watchlist with its owner-scoped instrument entries.
 */
public record InvestmentWatchlistResponse(
        Long id,
        Long workspaceId,
        String name,
        String description,
        int sortNo,
        List<InvestmentWatchlistItemResponse> items,
        Instant createdAt
) {

    public InvestmentWatchlistResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
