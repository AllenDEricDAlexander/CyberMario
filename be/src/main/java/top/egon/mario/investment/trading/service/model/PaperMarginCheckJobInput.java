package top.egon.mario.investment.trading.service.model;

import java.time.Instant;

/** Immutable identity of one isolated-position maintenance check. */
public record PaperMarginCheckJobInput(
        long workspaceId,
        long accountId,
        long positionId,
        long instrumentId,
        long sourceId,
        Instant dataAsOf
) {
}
