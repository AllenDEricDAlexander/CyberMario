package top.egon.mario.rag.service;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.rag.dto.response.RagIngestionJobResponse;

/**
 * Runs document ingestion for parsing, chunking and indexing.
 */
public interface RagIngestionService {

    /**
     * Processes a document and updates the related job.
     */
    RagIngestionJobResponse ingest(@NotNull Long jobId);

}
