package top.egon.mario.rag.dto.response;

import top.egon.mario.rag.po.enums.RagIngestionJobStatus;
import top.egon.mario.rag.po.enums.RagIngestionStep;

import java.time.Instant;

/**
 * RAG ingestion job response DTO.
 */
public record RagIngestionJobResponse(
        Long id,
        Long documentId,
        Long knowledgeBaseId,
        RagIngestionJobStatus status,
        RagIngestionStep currentStep,
        int progress,
        int chunkCount,
        int successCount,
        int failedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
