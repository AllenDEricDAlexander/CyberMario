package top.egon.mario.investment.research.web.dto;

import java.time.Instant;

/**
 * Immutable evidence attached to one report detail response.
 */
public record InvestmentReportEvidenceResponse(
        Long evidenceId,
        String evidenceType,
        Long sourceId,
        Long instrumentId,
        Instant dataStartTime,
        Instant dataEndTime,
        Instant dataAsOf,
        String sourceReference,
        String payloadHash,
        String metadataJson,
        Instant createdAt
) {
}
