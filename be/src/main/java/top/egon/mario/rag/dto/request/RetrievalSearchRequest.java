package top.egon.mario.rag.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import top.egon.mario.rag.dto.response.RagSearchMode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for retrieval-only RAG debugging.
 */
public record RetrievalSearchRequest(
        @NotBlank String query,
        List<Long> knowledgeBaseIds,
        @Min(1) @Max(20) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal similarityThreshold,
        RagSearchMode searchMode
) {
}
