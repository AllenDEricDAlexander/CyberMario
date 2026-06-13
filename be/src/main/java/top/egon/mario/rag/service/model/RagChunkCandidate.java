package top.egon.mario.rag.service.model;

/**
 * Candidate chunk content produced before persistence.
 */
public record RagChunkCandidate(
        int chunkIndex,
        String content,
        int tokenCount
) {
}
