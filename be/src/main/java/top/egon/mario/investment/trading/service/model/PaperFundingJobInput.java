package top.egon.mario.investment.trading.service.model;

import java.time.Instant;

/** Immutable identity of one position funding settlement. */
public record PaperFundingJobInput(
        long workspaceId,
        long accountId,
        long positionId,
        long instrumentId,
        long sourceId,
        Instant fundingTime
) {
}
