package top.egon.mario.agent.mcp.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies MCP runtime dependency alignment required by Spring AI WebFlux transports.
 */
class McpRuntimeClasspathCompatibilityTests {

    @Test
    void closedMcpTransportSessionIsAvailableForWebfluxTransportClosePath() {
        assertThatCode(() -> Class.forName("io.modelcontextprotocol.spec.ClosedMcpTransportSession"))
                .doesNotThrowAnyException();
    }

}
