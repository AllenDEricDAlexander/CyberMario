package top.egon.mario.agent.tools.arxiv.dto;

import top.egon.mario.agent.tools.arxiv.po.enums.ArxivToolLogStatus;

import java.time.Instant;

/**
 * arXiv tool log entry exposed to super administrators.
 */
public record ArxivToolLogResponse(
        Long id,
        String requestId,
        Long requestUserId,
        String requestUsername,
        String query,
        int maxResults,
        boolean includeFullText,
        int resultCount,
        Long knowledgeBaseId,
        String entryId,
        String title,
        String pdfUrl,
        ArxivToolLogStatus status,
        Long documentId,
        Long ragIngestionJobId,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
