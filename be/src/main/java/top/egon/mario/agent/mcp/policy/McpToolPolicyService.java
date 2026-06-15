package top.egon.mario.agent.mcp.policy;

import org.springframework.stereotype.Service;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;

/**
 * Central runtime policy for exposing MCP tools to agents.
 */
@Service
public class McpToolPolicyService {

    public boolean isAgentVisible(McpServerConfigPo server, McpToolConfigPo tool) {
        return server != null
                && tool != null
                && server.isEnabled()
                && server.getStatus() == McpServerStatus.CONNECTED
                && tool.isEnabled()
                && !tool.isRequireConfirm();
    }

}
