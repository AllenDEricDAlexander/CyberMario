package top.egon.mario.agent.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.mcp.dto.response.McpConnectionTestResponse;
import top.egon.mario.agent.mcp.dto.response.McpToolDiscoveryResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.mcp.runtime.McpClientFactory;
import top.egon.mario.agent.service.AgentException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Discovers remote MCP tools and persists local runtime policy records.
 */
@Service
@RequiredArgsConstructor
public class McpToolDiscoveryService {

    private final McpServerConfigService serverConfigService;
    private final McpToolConfigRepository toolRepository;
    private final McpClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    @Transactional
    public McpToolDiscoveryResponse discover(Long serverId, Long actorId) {
        McpServerConfigPo server = serverConfigService.requireServer(serverId);
        McpSyncClient client = null;
        int created = 0;
        int updated = 0;
        try {
            client = clientFactory.create(server);
            McpSchema.ListToolsResult result = client.listTools();
            List<McpSchema.Tool> remoteTools = result == null || result.tools() == null ? List.of() : result.tools();
            for (McpSchema.Tool remoteTool : remoteTools) {
                boolean wasCreated = upsertTool(server, remoteTool, actorId);
                if (wasCreated) {
                    created++;
                } else {
                    updated++;
                }
            }
            return new McpToolDiscoveryResponse(serverId, remoteTools.size(), created, updated);
        } finally {
            if (client != null) {
                closeClient(client);
            }
        }
    }

    @Transactional(readOnly = true)
    public McpConnectionTestResponse testConnection(Long serverId) {
        McpServerConfigPo server = serverConfigService.requireServer(serverId);
        McpSyncClient client = null;
        try {
            client = clientFactory.create(server);
            McpSchema.ServerCapabilities serverCapabilities = client.getServerCapabilities();
            McpSchema.Implementation serverInfo = client.getServerInfo();
            McpSchema.ListToolsResult result = client.listTools();
            List<McpSchema.Tool> tools = result == null || result.tools() == null ? List.of() : result.tools();
            String serverName = serverInfo == null ? server.getServerName() : serverInfo.name();
            String serverVersion = serverInfo == null ? null : serverInfo.version();
            if (serverCapabilities == null) {
                return new McpConnectionTestResponse(true, serverName, serverVersion, tools.size(), null);
            }
            return new McpConnectionTestResponse(true, serverName, serverVersion, tools.size(), null);
        } catch (RuntimeException e) {
            return new McpConnectionTestResponse(false, null, null, 0, e.getMessage());
        } finally {
            if (client != null) {
                closeClient(client);
            }
        }
    }

    private boolean upsertTool(McpServerConfigPo server, McpSchema.Tool remoteTool, Long actorId) {
        Instant now = Instant.now();
        String toolKey = normalize(server.getServerCode()) + "_" + normalize(remoteTool.name());
        McpToolConfigPo tool = toolRepository.findByServerIdAndToolNameAndDeletedFalse(server.getId(), remoteTool.name())
                .orElseGet(McpToolConfigPo::new);
        boolean created = tool.getId() == null;

        if (created) {
            tool.setServerId(server.getId());
            tool.setToolName(remoteTool.name());
            tool.setToolKey(toolKey);
            tool.setDisplayName(toolKey);
            tool.setEnabled(false);
            tool.setRiskLevel(McpToolRiskLevel.MEDIUM);
            tool.setReadonly(false);
            tool.setRequireConfirm(true);
            tool.setCreatedBy(actorId);
        } else {
            tool.setToolKey(toolKey);
            if (!StringUtils.hasText(tool.getDisplayName())) {
                tool.setDisplayName(toolKey);
            }
        }
        tool.setDescription(remoteTool.description());
        tool.setInputSchemaJson(writeSchema(remoteTool.inputSchema()));
        tool.setLastDiscoveredAt(now);
        tool.setUpdatedBy(actorId);
        toolRepository.save(tool);
        return created;
    }

    private String writeSchema(McpSchema.JsonSchema schema) {
        if (schema == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new AgentException("AGENT_MCP_TOOL_SCHEMA_INVALID", "mcp tool schema cannot be serialized");
        }
    }

    private String normalize(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return "tool";
    }

    private void closeClient(McpSyncClient client) {
        try {
            if (!client.closeGracefully()) {
                client.close();
            }
        } catch (RuntimeException e) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }

}
