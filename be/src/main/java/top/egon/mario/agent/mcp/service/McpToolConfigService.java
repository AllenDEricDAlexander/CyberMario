package top.egon.mario.agent.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.agent.mcp.dto.request.UpdateMcpToolPolicyRequest;
import top.egon.mario.agent.mcp.dto.response.McpToolResponse;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpToolRuntimeStatus;
import top.egon.mario.agent.mcp.repository.McpServerConfigRepository;
import top.egon.mario.agent.mcp.repository.McpToolConfigRepository;
import top.egon.mario.agent.service.AgentException;

import java.util.List;

/**
 * Manages discovered MCP tool policy records.
 */
@Service
@RequiredArgsConstructor
@Validated
public class McpToolConfigService {

    private final McpToolConfigRepository toolRepository;
    private final McpServerConfigRepository serverRepository;

    @Transactional(readOnly = true)
    public List<McpToolResponse> list(Long serverId) {
        if (serverId == null) {
            return toolRepository.findByDeletedFalseOrderByIdDesc().stream()
                    .map(tool -> toResponse(tool, requireServer(tool.getServerId())))
                    .toList();
        }
        McpServerConfigPo server = requireServer(serverId);
        return toolRepository.findByServerIdAndDeletedFalseOrderByIdAsc(serverId).stream()
                .map(tool -> toResponse(tool, server))
                .toList();
    }

    @Transactional(readOnly = true)
    public McpToolResponse get(Long id) {
        McpToolConfigPo tool = requireTool(id);
        return toResponse(tool, requireServer(tool.getServerId()));
    }

    @Transactional
    public McpToolResponse updatePolicy(Long id, UpdateMcpToolPolicyRequest request, Long actorId) {
        McpToolConfigPo tool = requireTool(id);
        tool.setRiskLevel(request.riskLevel());
        tool.setReadonly(request.readonly());
        tool.setRequireConfirm(request.requireConfirm());
        if (request.requireConfirm()) {
            tool.setEnabled(false);
        }
        tool.setUpdatedBy(actorId);
        tool = toolRepository.save(tool);
        return toResponse(tool, requireServer(tool.getServerId()));
    }

    @Transactional
    public McpToolResponse enable(Long id, Long actorId) {
        McpToolConfigPo tool = requireTool(id);
        if (tool.isRequireConfirm()) {
            throw new AgentException("AGENT_MCP_TOOL_POLICY_BLOCKED",
                    "tool requires confirmation and cannot be enabled for stage one");
        }
        tool.setEnabled(true);
        tool.setUpdatedBy(actorId);
        tool = toolRepository.save(tool);
        return toResponse(tool, requireServer(tool.getServerId()));
    }

    @Transactional
    public McpToolResponse disable(Long id, Long actorId) {
        McpToolConfigPo tool = requireTool(id);
        tool.setEnabled(false);
        tool.setUpdatedBy(actorId);
        tool = toolRepository.save(tool);
        return toResponse(tool, requireServer(tool.getServerId()));
    }

    private McpToolConfigPo requireTool(Long id) {
        return toolRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AgentException("AGENT_MCP_TOOL_NOT_FOUND", "mcp tool not found"));
    }

    private McpServerConfigPo requireServer(Long id) {
        return serverRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AgentException("AGENT_MCP_SERVER_NOT_FOUND", "mcp server not found"));
    }

    private McpToolResponse toResponse(McpToolConfigPo tool, McpServerConfigPo server) {
        return new McpToolResponse(
                tool.getId(),
                tool.getServerId(),
                server.getServerCode(),
                tool.getToolName(),
                tool.getToolKey(),
                tool.getDisplayName(),
                tool.getDescription(),
                tool.getInputSchemaJson(),
                tool.isEnabled(),
                tool.getRiskLevel(),
                tool.isReadonly(),
                tool.isRequireConfirm(),
                runtimeStatus(tool, server),
                tool.getLastDiscoveredAt());
    }

    private McpToolRuntimeStatus runtimeStatus(McpToolConfigPo tool, McpServerConfigPo server) {
        if (!tool.isEnabled()) {
            return McpToolRuntimeStatus.DISABLED;
        }
        if (!server.isEnabled()) {
            return McpToolRuntimeStatus.SERVER_DISABLED;
        }
        if (server.getStatus() == McpServerStatus.FAILED) {
            return McpToolRuntimeStatus.SERVER_FAILED;
        }
        if (tool.isRequireConfirm()) {
            return McpToolRuntimeStatus.POLICY_BLOCKED;
        }
        return McpToolRuntimeStatus.AVAILABLE;
    }

}
