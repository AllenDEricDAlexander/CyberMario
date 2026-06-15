package top.egon.mario.agent.mcp.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

/**
 * Remote MCP server configuration managed from the admin console.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_mcp_server_config")
public class McpServerConfigPo extends BaseAuditablePo {

    @Column(name = "server_code", nullable = false, length = 64)
    private String serverCode;

    @Column(name = "server_name", nullable = false, length = 128)
    private String serverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 32)
    private McpTransportType transportType;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "endpoint", nullable = false, length = 256)
    private String endpoint;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "connect_timeout_ms", nullable = false)
    private int connectTimeoutMs = 5000;

    @Column(name = "request_timeout_ms", nullable = false)
    private int requestTimeoutMs = 30000;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private McpServerStatus status = McpServerStatus.DISABLED;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

}
