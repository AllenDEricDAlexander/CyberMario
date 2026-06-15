package top.egon.mario.agent.mcp.po;

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
import top.egon.mario.agent.mcp.po.enums.McpToolCallStatus;

import java.time.Instant;

/**
 * Persisted MCP tool execution log for admin audit.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_mcp_tool_call_log")
public class McpToolCallLogPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "server_code", nullable = false, length = 64)
    private String serverCode;

    @Column(name = "tool_key", nullable = false, length = 192)
    private String toolKey;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "request_args_summary", columnDefinition = "TEXT")
    private String requestArgsSummary;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private McpToolCallStatus status;

    @Column(name = "error_msg", length = 1024)
    private String errorMsg;

    @Column(name = "cost_ms", nullable = false)
    private long costMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
