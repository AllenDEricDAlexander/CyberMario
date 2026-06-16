package top.egon.mario.rag.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import top.egon.mario.rag.dto.response.RagSearchMode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for HTTP-streamed RAG chat.
 */
public record RagChatRequest(
        String sessionId,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled,
        @NotBlank String question,
        List<Long> knowledgeBaseIds,
        @Valid RetrievalOptions retrievalOptions,
        @Valid ModelOptions modelOptions,
        Boolean withSources
) {

    public record RetrievalOptions(
            @Min(1) @Max(20) Integer topK,
            @Min(1) @Max(100) Integer candidateTopK,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal similarityThreshold,
            RagSearchMode searchMode,
            Boolean rerankEnabled
    ) {
    }

    public record ModelOptions(
            String model,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            @Min(1) @Max(8192) Integer maxTokens
    ) {
    }

}
