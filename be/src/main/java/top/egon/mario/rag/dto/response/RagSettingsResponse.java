package top.egon.mario.rag.dto.response;

import java.math.BigDecimal;

/**
 * Read-only RAG system settings shown by the frontend console.
 */
public record RagSettingsResponse(
        String chatModel,
        String embeddingModel,
        int embeddingDimension,
        RagSearchMode defaultSearchMode,
        int defaultTopK,
        int candidateTopK,
        int contextTopK,
        BigDecimal defaultSimilarityThreshold,
        boolean rerankEnabled,
        String rerankModel
) {
}
