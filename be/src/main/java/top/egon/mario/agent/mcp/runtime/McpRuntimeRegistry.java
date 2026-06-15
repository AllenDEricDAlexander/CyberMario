package top.egon.mario.agent.mcp.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.policy.McpToolPolicyService;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.mcp.service.McpToolCallLogService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the current MCP tool callbacks visible to agent runtimes.
 */
@Component
@RequiredArgsConstructor
public class McpRuntimeRegistry {

    private final McpToolConfigRepository toolRepository;
    private final McpServerConfigRepository serverRepository;
    private final DynamicMcpClientManager clientManager;
    private final McpToolPolicyService policyService;
    private final McpToolCallLogService logService;

    public ToolCallback[] currentToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        Map<Long, Optional<RuntimeServerTools>> runtimeToolsCache = new LinkedHashMap<>();
        for (McpToolConfigPo tool : toolRepository.findByEnabledTrueAndDeletedFalseOrderByIdAsc()) {
            Optional<McpServerConfigPo> serverOptional = serverRepository.findByIdAndDeletedFalse(tool.getServerId());
            if (serverOptional.isEmpty() || !policyService.isAgentVisible(serverOptional.get(), tool)) {
                continue;
            }

            Optional<RuntimeServerTools> runtimeServerTools = runtimeToolsCache.computeIfAbsent(
                    tool.getServerId(),
                    this::loadRuntimeTools);
            if (runtimeServerTools.isEmpty()) {
                continue;
            }

            Optional<McpSchema.Tool> runtimeTool = findRuntimeTool(runtimeServerTools.get(), tool.getToolName());
            if (runtimeTool.isEmpty()) {
                continue;
            }

            ToolCallback delegate = SyncMcpToolCallback.builder()
                    .mcpClient(runtimeServerTools.get().client())
                    .tool(runtimeTool.get())
                    .prefixedToolName(tool.getToolKey())
                    .build();
            callbacks.add(new LoggingMcpToolCallback(delegate, serverOptional.get(), tool, logService));
        }
        return callbacks.toArray(ToolCallback[]::new);
    }

    private Optional<RuntimeServerTools> loadRuntimeTools(Long serverId) {
        Optional<McpSyncClient> clientOptional = clientManager.client(serverId);
        if (clientOptional.isEmpty()) {
            return Optional.empty();
        }
        McpSyncClient client = clientOptional.get();
        try {
            McpSchema.ListToolsResult result = client.listTools();
            if (result == null || result.tools() == null) {
                return Optional.of(new RuntimeServerTools(client, Map.of()));
            }
            Map<String, McpSchema.Tool> tools = new LinkedHashMap<>();
            for (McpSchema.Tool tool : result.tools()) {
                if (tool != null) {
                    tools.putIfAbsent(tool.name(), tool);
                }
            }
            return Optional.of(new RuntimeServerTools(client, tools));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<McpSchema.Tool> findRuntimeTool(RuntimeServerTools runtimeServerTools, String toolName) {
        return Optional.ofNullable(runtimeServerTools.tools().get(toolName));
    }

    private record RuntimeServerTools(McpSyncClient client, Map<String, McpSchema.Tool> tools) {
    }

}
