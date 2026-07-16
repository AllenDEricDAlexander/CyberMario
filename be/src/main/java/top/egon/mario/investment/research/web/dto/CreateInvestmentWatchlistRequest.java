package top.egon.mario.investment.research.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for creating or restoring a workspace watchlist.
 */
public record CreateInvestmentWatchlistRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description
) {
}
