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
        RagKnowledgeBaseStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
