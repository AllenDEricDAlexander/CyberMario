package top.egon.mario.investment.common.job;

import top.egon.mario.investment.common.model.InvestmentJobType;

import java.time.Instant;

/**
 * Fenced, immutable execution lease handed to a job handler.
 */
public record InvestmentJobClaim(
        long id,
        Long workspaceId,
        InvestmentJobType jobType,
        String inputJson,
        int attempts,
        int maxAttempts,
        String workerId,
        String claimToken,
        Instant claimedAt,
        Instant leaseExpiresAt
) {
}
