package top.egon.mario.rag.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

/**
 * Searchable chunk created from a user document.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_document_chunk", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rag_chunk_doc_index_deleted", columnNames = {"document_id", "chunk_index", "deleted"})
})
public class RagDocumentChunkPo extends BaseAuditablePo {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "file_object_id")
    private Long fileObjectId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "content_preview", nullable = false, length = 512)
    private String contentPreview;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "vector_id", length = 64)
    private String vectorId;

    @Column(name = "metadata_json")
    private String metadataJson;

}
