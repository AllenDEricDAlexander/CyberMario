package top.egon.mario.rag.dto.response;

import top.egon.mario.rag.po.enums.RagFeedbackType;

import java.time.Instant;

/**
 * RAG feedback response DTO.
 */
public record RagFeedbackResponse(
        Long id,
        String traceId,
        String messageId,
        Long userId,
        RagFeedbackType feedbackType,
        Instant createdAt
) {
}
