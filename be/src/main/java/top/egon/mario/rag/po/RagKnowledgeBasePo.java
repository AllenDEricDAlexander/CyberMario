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
    @Column(name = "status", nullable = false)
    private RagKnowledgeBaseStatus status = RagKnowledgeBaseStatus.ENABLED;

}
