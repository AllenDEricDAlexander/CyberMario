package top.egon.mario.rag.po.enums;

/**
 * Stage name for retrieval trace candidates.
 */
public enum RagRetrievalStage {

    VECTOR,
    KEYWORD,
    FUSED,
    RERANKED,
    FINAL

}
