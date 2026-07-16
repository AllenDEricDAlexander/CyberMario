package top.egon.mario.investment.research.report;

import java.time.Instant;

/**
 * Evidence proposed by a generator; ownership and cutoff are enforced during persistence.
 */
public record ResearchReportEvidenceDraft(
        String evidenceType,
        long sourceId,
        Long instrumentId,
        Instant dataStartTime,
        Instant dataEndTime,
        Instant dataAsOf,
        String sourceReference,
        String payloadHash,
        String metadataJson
) {
}
