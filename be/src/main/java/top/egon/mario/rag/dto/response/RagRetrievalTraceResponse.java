package top.egon.mario.rag.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Retrieval trace detail response for the RAG debugging console.
 */
public record RagRetrievalTraceResponse(
        String traceId,
        Long userId,
        String query,
        RagSearchMode searchMode,
        boolean rerankEnabled,
        boolean degraded,
        String degradeReason,
        long costMs,
        List<SourceReferenceResponse> vector,
        List<SourceReferenceResponse> keyword,
        List<SourceReferenceResponse> fused,
        List<SourceReferenceResponse> reranked,
        Instant createdAt
) {
}
