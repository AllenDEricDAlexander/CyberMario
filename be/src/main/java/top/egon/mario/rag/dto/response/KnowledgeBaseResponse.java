package top.egon.mario.rag.dto.response;

import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * RAG knowledge base response DTO.
 */
public record KnowledgeBaseResponse(
        Long id,
        String code,
        String name,
        String description,
        int defaultTopK,
        BigDecimal defaultSimilarityThreshold,
        RagSearchMode defaultSearchMode,
        boolean rerankEnabled,
        BigDecimal vectorWeight,
        BigDecimal keywordWeight,
        int candidateTopK,
        int contextTopK,
        int chunkSize,
        int chunkOverlap,
        RagKnowledgeBaseStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
