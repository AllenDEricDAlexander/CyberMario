package top.egon.mario.rag.dto.response;

import top.egon.mario.rag.po.enums.RagDocumentSourceType;
import top.egon.mario.rag.po.enums.RagDocumentStatus;
import top.egon.mario.rag.po.enums.RagFileType;

import java.time.Instant;

/**
 * User document response DTO for the RAG console.
 */
public record RagDocumentResponse(
        Long id,
        Long userId,
        Long knowledgeBaseId,
        Long fileObjectId,
        String displayName,
        RagDocumentSourceType sourceType,
        RagFileType fileType,
        String contentHash,
        RagDocumentStatus status,
        int chunkCount,
        int indexedChunkCount,
        String errorMessage,
        String sourceUri,
        String parserType,
        String chunkStrategy,
        String embeddingModel,
        Integer embeddingDimension,
        Instant indexedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
