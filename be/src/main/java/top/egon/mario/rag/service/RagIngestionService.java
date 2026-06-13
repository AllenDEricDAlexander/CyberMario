package top.egon.mario.rag.service;

import top.egon.mario.rag.dto.response.RagIngestionJobResponse;

/**
 * Runs document ingestion for parsing, chunking and indexing.
 */
public interface RagIngestionService {

    /**
     * Processes a document and updates the related job.
     */
    RagIngestionJobResponse ingest(Long jobId);

}
