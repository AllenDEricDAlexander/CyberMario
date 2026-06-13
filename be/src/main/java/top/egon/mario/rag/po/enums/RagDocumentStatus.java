package top.egon.mario.rag.po.enums;

/**
 * Processing state for a user-linked RAG document.
 */
public enum RagDocumentStatus {

    UPLOADED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    INDEXED,
    FAILED,
    DELETED

}
