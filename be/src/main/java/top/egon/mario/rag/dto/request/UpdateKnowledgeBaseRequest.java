package top.egon.mario.rag.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;

import java.math.BigDecimal;

/**
 * Request body for updating RAG knowledge base metadata.
 */
public record UpdateKnowledgeBaseRequest(
        @NotBlank @Size(min = 2, max = 128) String name,
        @Size(max = 512) String description,
        @Min(1) @Max(20) Integer defaultTopK,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal defaultSimilarityThreshold,
        RagKnowledgeBaseStatus status
) {
}
