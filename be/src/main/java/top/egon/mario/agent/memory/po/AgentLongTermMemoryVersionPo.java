package top.egon.mario.agent.memory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable version snapshot for a long-term memory document.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_long_term_memory_version")
public class AgentLongTermMemoryVersionPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_id", nullable = false)
    private Long memoryId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "content_markdown", nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "content_chars", nullable = false)
    private int contentChars;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "source_session_ids", columnDefinition = "TEXT")
    private String sourceSessionIds;

    @Column(name = "source_message_ids", columnDefinition = "TEXT")
    private String sourceMessageIds;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
