package top.egon.mario.rag.dto.response;

import java.time.Instant;

/**
 * Document chunk response DTO for preview and retrieval debugging.
 */
public record RagChunkResponse(
        Long id,
        Long documentId,
        Long knowledgeBaseId,
        int chunkIndex,
        String content,
        String contentPreview,
        int tokenCount,
        boolean enabled,
        String metadataJson,
        String contentHash,
        String headingPath,
        Integer startOffset,
        Integer endOffset,
        Instant createdAt
) {
}
