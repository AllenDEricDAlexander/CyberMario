package top.egon.mario.rag.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;
import top.egon.mario.rag.po.enums.RagIngestionJobStatus;
import top.egon.mario.rag.po.enums.RagIngestionStep;

import java.time.Instant;

/**
 * Processing job that parses, chunks, embeds and indexes a document.
 */
@Getter
@Setter
@Entity
@Table(name = "rag_ingestion_job")
public class RagIngestionJobPo extends BaseAuditablePo {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "file_object_id")
    private Long fileObjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RagIngestionJobStatus status = RagIngestionJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private RagIngestionStep currentStep = RagIngestionStep.UPLOAD;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

}
