package top.egon.mario.agent.tools.arxiv.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.tools.arxiv.po.enums.ArxivToolLogStatus;

import java.time.Instant;

/**
 * Persisted arXiv tool search and background import log.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_arxiv_tool_log")
public class ArxivToolLogPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "request_user_id")
    private Long requestUserId;

    @Column(name = "request_username", length = 64)
    private String requestUsername;

    @Column(name = "query", nullable = false, length = 1024)
    private String query;

    @Column(name = "max_results", nullable = false)
    private int maxResults;

    @Column(name = "include_full_text", nullable = false)
    private boolean includeFullText;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    @Column(name = "entry_id", length = 256)
    private String entryId;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ArxivToolLogStatus status;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "rag_ingestion_job_id")
    private Long ragIngestionJobId;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
