package top.egon.mario.rag.dto.response;

/**
 * Search mode requested by RAG clients.
 */
public enum RagSearchMode {

    VECTOR,
    KEYWORD,
    HYBRID,
    HYBRID_RERANK

}
