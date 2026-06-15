package top.egon.mario.agent.mcp.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

/**
 * Discovered MCP tool plus CyberMario runtime policy.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_mcp_tool_config")
public class McpToolConfigPo extends BaseAuditablePo {

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "tool_key", nullable = false, length = 192)
    private String toolKey;

    @Column(name = "display_name", nullable = false, length = 192)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_schema_json", columnDefinition = "TEXT")
    private String inputSchemaJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 32)
    private McpToolRiskLevel riskLevel = McpToolRiskLevel.MEDIUM;

    @Column(name = "readonly", nullable = false)
    private boolean readonly;

    @Column(name = "require_confirm", nullable = false)
    private boolean requireConfirm = true;

    @Column(name = "last_discovered_at", nullable = false)
    private Instant lastDiscoveredAt;

}
