package top.egon.mario.agent.model.po;

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
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;
import top.egon.mario.agent.model.po.enums.TokenUsageSource;

import java.time.Instant;

/**
 * Persisted audit record for AI model calls and token usage.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_model_call_audit")
public class ModelAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario", nullable = false, length = 32)
    private ModelScenario scenario = ModelScenario.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private ModelProviderType provider;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "options_json")
    private String optionsJson;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_usage_source", nullable = false, length = 32)
    private TokenUsageSource tokenUsageSource = TokenUsageSource.UNAVAILABLE;

    @Column(name = "streaming", nullable = false)
    private boolean streaming;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ModelAuditStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "prompt_chars")
    private Integer promptChars;

    @Column(name = "completion_chars")
    private Integer completionChars;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
