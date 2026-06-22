package top.egon.mario.agent.soul.po;

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
import top.egon.mario.agent.soul.po.enums.AgentSoulChangeType;
import top.egon.mario.agent.soul.po.enums.AgentSoulSourceType;

import java.time.Instant;

/**
 * Immutable version snapshot for a user Agent SoulMD document.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_soul_md_version")
public class AgentSoulMdVersionPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "content_markdown", nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "content_chars", nullable = false)
    private int contentChars;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 32)
    private AgentSoulChangeType changeType;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 32)
    private AgentSoulSourceType sourceType;

    @Column(name = "source_session_id", length = 128)
    private String sourceSessionId;

    @Column(name = "source_message_ids", columnDefinition = "TEXT")
    private String sourceMessageIds;

    @Column(name = "model_provider", length = 64)
    private String modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
