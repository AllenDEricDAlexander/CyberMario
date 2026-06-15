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
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;

import java.time.Instant;

/**
 * Persisted timeline item for one agent run.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_run_event_audit")
public class AgentRunEventAuditPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AgentRunEventType eventType;

    @Column(name = "react_round")
    private Integer reactRound;

    @Column(name = "tool_call_id", length = 128)
    private String toolCallId;

    @Column(name = "tool_name", length = 192)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", length = 32)
    private AgentRunToolType toolType;

    @Column(name = "mcp_server_code", length = 64)
    private String mcpServerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentRunEventStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_provider", length = 32)
    private ModelProviderType modelProvider;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "request_messages_json", columnDefinition = "TEXT")
    private String requestMessagesJson;

    @Column(name = "request_options_json", columnDefinition = "TEXT")
    private String requestOptionsJson;

    @Column(name = "available_tools_json", columnDefinition = "TEXT")
    private String availableToolsJson;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "tool_arguments", columnDefinition = "TEXT")
    private String toolArguments;

    @Column(name = "tool_result", columnDefinition = "TEXT")
    private String toolResult;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "error_code", length = 256)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
