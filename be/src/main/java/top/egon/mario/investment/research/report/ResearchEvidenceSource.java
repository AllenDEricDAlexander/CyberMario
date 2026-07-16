package top.egon.mario.investment.research.report;

/**
 * Active code-subscribed market source used by persisted report evidence.
 */
public record ResearchEvidenceSource(long sourceId, long instrumentId, String sourceCode, String externalSymbol) {
}
