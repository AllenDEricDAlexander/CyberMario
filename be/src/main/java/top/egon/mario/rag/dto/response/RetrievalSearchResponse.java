package top.egon.mario.rag.dto.response;

import java.util.List;

/**
 * Retrieval debug response with ranked source chunks and cost.
 */
public record RetrievalSearchResponse(
        String query,
        List<SourceReferenceResponse> results,
        long costMs
) {
}
