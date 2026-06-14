package top.egon.mario.rag.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rag.po.enums.RagDocumentSourceType;
import top.egon.mario.rag.po.enums.RagDocumentStatus;
import top.egon.mario.rag.po.enums.RagFileType;

import java.time.Instant;

/**
 * User-visible document reference linked to a knowledge base and a physical file.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_user_document")
public class RagUserDocumentPo extends BaseAuditablePo {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "file_object_id")
    private Long fileObjectId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private RagDocumentSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private RagFileType fileType;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RagDocumentStatus status = RagDocumentStatus.UPLOADED;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "indexed_chunk_count", nullable = false)
    private int indexedChunkCount;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "source_uri", length = 1024)
    private String sourceUri;

    @Column(name = "parser_type", length = 64)
    private String parserType;

    @Column(name = "chunk_strategy", length = 64)
    private String chunkStrategy;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "indexed_at")
    private Instant indexedAt;

}
