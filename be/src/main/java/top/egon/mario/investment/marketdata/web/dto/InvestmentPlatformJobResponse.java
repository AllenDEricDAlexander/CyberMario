package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * Sanitized durable-job projection for platform operations.
 */
public record InvestmentPlatformJobResponse(
        Long id,
        String jobType,
        String status,
        int priority,
        int attempts,
        int maxAttempts,
        Instant availableAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt,
        String triggerSource,
        String sourceCode,
        String symbol,
        String capability,
        String priceType,
        String interval,
        Instant startInclusive,
        Instant endExclusive,
        Instant startedAt,
        Instant finishedAt,
        Integer fetchedCount,
        Integer writtenCount
) {
}
