package top.egon.mario.rag.dto.response;

import java.util.List;

/**
 * Upload result containing created document links and ingestion jobs.
 */
public record UploadDocumentResponse(
        List<RagDocumentResponse> documents,
        List<Long> jobIds
) {
}
