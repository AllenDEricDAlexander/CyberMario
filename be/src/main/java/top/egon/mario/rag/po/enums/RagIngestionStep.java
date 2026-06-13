package top.egon.mario.rag.po.enums;

/**
 * Current step of a RAG ingestion job.
 */
public enum RagIngestionStep {

    UPLOAD,
    PARSE,
    CHUNK,
    EMBEDDING,
    INDEX,
    DONE

}
