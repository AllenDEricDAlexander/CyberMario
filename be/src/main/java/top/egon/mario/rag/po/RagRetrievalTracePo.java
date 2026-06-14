package top.egon.mario.rag.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rag.dto.response.RagSearchMode;

/**
 * Retrieval trace header used by the RAG debugging console.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_retrieval_trace", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rag_trace_id_deleted", columnNames = {"trace_id", "deleted"})
})
public class RagRetrievalTracePo extends BaseAuditablePo {

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "knowledge_base_ids", length = 1024)
    private String knowledgeBaseIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_mode", nullable = false)
    private RagSearchMode searchMode;

    @Column(name = "rerank_enabled", nullable = false)
    private boolean rerankEnabled;

    @Column(name = "degraded", nullable = false)
    private boolean degraded;

    @Column(name = "degrade_reason", length = 1024)
    private String degradeReason;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "cost_ms", nullable = false)
    private long costMs;

}
