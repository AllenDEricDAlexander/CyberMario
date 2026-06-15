package top.egon.mario.agent.mcp.runtime;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.policy.McpToolPolicyService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies runtime MCP tool exposure policy.
 */
class McpToolPolicyServiceTests {

    @Test
    void visibleOnlyWhenServerAndToolAreEnabledAndConfirmationIsNotRequired() {
        McpToolPolicyService service = new McpToolPolicyService();
        McpServerConfigPo server = server(true, McpServerStatus.CONNECTED);
        McpToolConfigPo tool = tool(true, false);

        assertThat(service.isAgentVisible(server, tool)).isTrue();

        assertThat(service.isAgentVisible(null, tool)).isFalse();
        assertThat(service.isAgentVisible(server, null)).isFalse();

        server.setEnabled(false);
        assertThat(service.isAgentVisible(server, tool)).isFalse();

        server.setEnabled(true);
        server.setStatus(McpServerStatus.FAILED);
        assertThat(service.isAgentVisible(server, tool)).isFalse();

        server.setStatus(McpServerStatus.CONNECTED);
        tool.setEnabled(false);
        assertThat(service.isAgentVisible(server, tool)).isFalse();

        tool.setEnabled(true);
        tool.setRequireConfirm(true);
        assertThat(service.isAgentVisible(server, tool)).isFalse();
    }

    private McpServerConfigPo server(boolean enabled, McpServerStatus status) {
        McpServerConfigPo server = new McpServerConfigPo();
        server.setEnabled(enabled);
        server.setStatus(status);
        return server;
    }

    private McpToolConfigPo tool(boolean enabled, boolean requireConfirm) {
        McpToolConfigPo tool = new McpToolConfigPo();
        tool.setEnabled(enabled);
        tool.setRequireConfirm(requireConfirm);
        return tool;
    }

}
