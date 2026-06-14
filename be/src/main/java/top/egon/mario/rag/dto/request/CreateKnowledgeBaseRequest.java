package top.egon.mario.rag.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import top.egon.mario.rag.dto.response.RagSearchMode;

import java.math.BigDecimal;

/**
 * Request body for creating a RAG knowledge base.
 */
public record CreateKnowledgeBaseRequest(
        @NotBlank @Size(min = 2, max = 128) String name,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") @Size(max = 64) String code,
        @Size(max = 512) String description,
        @Min(1) @Max(20) Integer defaultTopK,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal defaultSimilarityThreshold,
        RagSearchMode defaultSearchMode,
        Boolean rerankEnabled,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal vectorWeight,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal keywordWeight,
        @Min(1) @Max(100) Integer candidateTopK,
        @Min(1) @Max(20) Integer contextTopK,
        @Min(100) @Max(4000) Integer chunkSize,
        @Min(0) @Max(1000) Integer chunkOverlap
) {
}
