package top.egon.mario.rag.dto.response;

import java.util.List;

/**
 * Retrieval debug response with ranked source chunks and cost.
 */
public record RetrievalSearchResponse(
        String query,
        RagSearchMode searchMode,
        String traceId,
        List<SourceReferenceResponse> results,
        RetrievalStages stages,
        long costMs
) {
    public record RetrievalStages(
            List<SourceReferenceResponse> vector,
            List<SourceReferenceResponse> keyword,
            List<SourceReferenceResponse> fused,
            List<SourceReferenceResponse> reranked
    ) {
        public RetrievalStages {
            vector = vector == null ? List.of() : vector;
            keyword = keyword == null ? List.of() : keyword;
            fused = fused == null ? List.of() : fused;
            reranked = reranked == null ? List.of() : reranked;
        }
    }
}
