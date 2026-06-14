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
import top.egon.mario.rag.po.enums.RagKnowledgeBaseStatus;

import java.math.BigDecimal;

/**
 * RAG knowledge base that groups documents and default retrieval options.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_knowledge_base", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rag_kb_code_deleted", columnNames = {"code", "deleted"})
})
public class RagKnowledgeBasePo extends BaseAuditablePo {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "default_top_k", nullable = false)
    private int defaultTopK = 6;

    @Column(name = "default_similarity_threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal defaultSimilarityThreshold = BigDecimal.valueOf(0.55);

    @Enumerated(EnumType.STRING)
    @Column(name = "default_search_mode", nullable = false)
    private RagSearchMode defaultSearchMode = RagSearchMode.HYBRID;

    @Column(name = "rerank_enabled", nullable = false)
    private boolean rerankEnabled;

    @Column(name = "vector_weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal vectorWeight = BigDecimal.valueOf(0.65);

    @Column(name = "keyword_weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal keywordWeight = BigDecimal.valueOf(0.35);

    @Column(name = "candidate_top_k", nullable = false)
    private int candidateTopK = 50;

    @Column(name = "context_top_k", nullable = false)
    private int contextTopK = 6;

    @Column(name = "chunk_size", nullable = false)
    private int chunkSize = 800;

    @Column(name = "chunk_overlap", nullable = false)
    private int chunkOverlap = 120;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RagKnowledgeBaseStatus status = RagKnowledgeBaseStatus.ENABLED;

}
