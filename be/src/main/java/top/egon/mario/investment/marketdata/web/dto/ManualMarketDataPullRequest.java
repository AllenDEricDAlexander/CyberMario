package top.egon.mario.investment.marketdata.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;

import java.time.Instant;

/**
 * Platform operator request for one bounded Bitget historical import.
 */
public record ManualMarketDataPullRequest(
        @NotBlank String symbol,
        @NotNull DataCapability capability,
        BarInterval interval,
        @NotNull Instant startInclusive,
        @NotNull Instant endExclusive
) {
}
