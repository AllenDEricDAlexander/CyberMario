package top.egon.mario.agent.mcp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.mcp.dto.response.McpServerResponse;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.runtime.DynamicMcpClientManager;
import top.egon.mario.agent.mcp.service.McpServerConfigService;
import top.egon.mario.agent.mcp.service.McpToolDiscoveryService;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Verifies admin MCP server management endpoints.
 */
@WebFluxTest(controllers = AdminMcpServerController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AdminMcpServerControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private McpServerConfigService serverConfigService;

    @MockitoBean
    private McpToolDiscoveryService toolDiscoveryService;

    @MockitoBean
    private DynamicMcpClientManager clientManager;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void updateRefreshesRuntimeServerAfterPersistenceSucceeds() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
        given(serverConfigService.update(eq(9L), any(), any())).willReturn(response());

        webTestClient.put()
                .uri("/api/admin/agent/mcp/servers/9")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "serverName":"Search MCP",
                          "transportType":"SSE",
                          "baseUrl":"https://mcp.example.com",
                          "endpoint":"/sse",
                          "connectTimeoutMs":3000,
                          "requestTimeoutMs":10000
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(9)
                .jsonPath("$.data.serverName").isEqualTo("Search MCP");

        verify(clientManager).refreshServer(9L);
    }

    private McpServerResponse response() {
        return new McpServerResponse(9L, "search", "Search MCP", McpTransportType.SSE,
                "https://mcp.example.com", "/sse", Map.of(), true, 3000, 10000,
                McpServerStatus.CONNECTED, null, Instant.parse("2026-06-14T01:00:00Z"),
                Instant.parse("2026-06-14T00:00:00Z"), Instant.parse("2026-06-14T01:00:00Z"));
    }

}
