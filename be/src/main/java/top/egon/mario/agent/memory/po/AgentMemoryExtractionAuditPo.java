package top.egon.mario.agent.memory.po;

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
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryExtractionStatus;

import java.time.Instant;

/**
 * Audit trail for automatic long-term memory extraction.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_memory_extraction_audit")
public class AgentMemoryExtractionAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 32)
    private AgentMemoryEntryType entryType;

    @Column(name = "source_message_ids", columnDefinition = "TEXT")
    private String sourceMessageIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentMemoryExtractionStatus status;

    @Column(name = "extracted_markdown", columnDefinition = "TEXT")
    private String extractedMarkdown;

    @Column(name = "merged_version_id")
    private Long mergedVersionId;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
