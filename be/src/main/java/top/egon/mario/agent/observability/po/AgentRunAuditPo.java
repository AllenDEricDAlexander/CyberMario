package top.egon.mario.agent.observability.po;

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
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;

import java.time.Instant;

/**
 * Persisted summary for one agent run.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_run_audit")
public class AgentRunAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "thread_id", nullable = false, length = 128)
    private String threadId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "preset_id")
    private Long presetId;

    @Column(name = "runtime_fingerprint", length = 128)
    private String runtimeFingerprint;

    @Column(name = "effective_config_json", columnDefinition = "TEXT")
    private String effectiveConfigJson;

    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "final_message", columnDefinition = "TEXT")
    private String finalMessage;

    @Column(name = "final_thinking", columnDefinition = "TEXT")
    private String finalThinking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentRunAuditStatus status = AgentRunAuditStatus.RUNNING;

    @Column(name = "model_call_count", nullable = false)
    private int modelCallCount;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount;

    @Column(name = "mcp_tool_call_count", nullable = false)
    private int mcpToolCallCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
