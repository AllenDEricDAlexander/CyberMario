package top.egon.mario.investment.marketdata.web.dto;

import java.time.Instant;

/**
 * Durable job accepted for asynchronous market-data import.
 */
public record ManualMarketDataPullResponse(
        long jobId,
        String jobType,
        String status,
        Instant createdAt
) {
}
