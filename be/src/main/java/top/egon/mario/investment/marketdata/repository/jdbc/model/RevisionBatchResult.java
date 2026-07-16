package top.egon.mario.investment.marketdata.repository.jdbc.model;

/**
 * Outcome counters for one cursor-fenced revision batch.
 */
public record RevisionBatchResult(
        int inserted,
        int revised,
        int unchanged,
        long maxRevision
) {
    public int total() {
        return inserted + revised + unchanged;
    }
}
