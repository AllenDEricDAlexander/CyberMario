package top.egon.mario.agent.po;

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
import top.egon.mario.agent.po.enums.AgentConversationStatus;

import java.time.Instant;

/**
 * Persisted audit record for one streamed agent conversation turn.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_conversation_audit")
public class AgentConversationAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "thread_id", nullable = false, length = 128)
    private String threadId;

    @Column(name = "preset_id")
    private Long presetId;

    @Column(name = "runtime_fingerprint", length = 128)
    private String runtimeFingerprint;

    @Column(name = "effective_config_json")
    private String effectiveConfigJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentConversationStatus status = AgentConversationStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
