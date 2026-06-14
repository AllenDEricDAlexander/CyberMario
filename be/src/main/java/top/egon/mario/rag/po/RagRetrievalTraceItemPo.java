package top.egon.mario.rag.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rag.po.enums.RagRetrievalStage;

/**
 * Ranked source item captured for one retrieval trace stage.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_retrieval_trace_item")
public class RagRetrievalTraceItemPo extends BaseAuditablePo {

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private RagRetrievalStage stage;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "score")
    private Double score;

    @Column(name = "vector_score")
    private Double vectorScore;

    @Column(name = "keyword_score")
    private Double keywordScore;

    @Column(name = "fusion_score")
    private Double fusionScore;

    @Column(name = "rerank_score")
    private Double rerankScore;

    @Column(name = "matched_by", length = 64)
    private String matchedBy;

    @Column(name = "content_preview", length = 512)
    private String contentPreview;

    @Column(name = "metadata_json")
    private String metadataJson;

}
