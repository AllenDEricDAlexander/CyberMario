package top.egon.mario.rag.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.mario.rag.po.enums.RagFeedbackType;

import java.util.List;

/**
 * Request body for RAG answer feedback.
 */
public record RagFeedbackRequest(
        String traceId,
        String messageId,
        @NotNull RagFeedbackType feedbackType,
        String question,
        String answer,
        List<Long> sourceChunkIds,
        @Size(max = 1024) String comment
) {
}
