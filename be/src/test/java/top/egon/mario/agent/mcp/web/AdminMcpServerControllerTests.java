package top.egon.mario.agent.mcp.web;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.mcp.dto.response.McpToolDiscoveryResponse;
import top.egon.mario.agent.mcp.dto.response.McpServerResponse;
import top.egon.mario.agent.mcp.po.enums.McpServerStatus;
import top.egon.mario.agent.mcp.po.enums.McpTransportType;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.service.McpServerConfigService;
import top.egon.mario.agent.mcp.service.McpToolDiscoveryService;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
    private McpRuntimeRefreshCoordinator refreshCoordinator;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private BrowserAuthCookieService browserAuthCookieService;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void updateRefreshesRuntimeServerAfterPersistenceSucceeds() {
        useImmediateScheduler();
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

        InOrder inOrder = inOrder(serverConfigService, refreshCoordinator);
        inOrder.verify(serverConfigService).update(eq(9L), any(), any());
        inOrder.verify(refreshCoordinator).refreshServer(9L, "server_update");
    }

    @Test
    void enableRefreshesRuntimeServerAfterPersistenceSucceeds() {
        useImmediateScheduler();

        webTestClient.post()
                .uri("/api/admin/agent/mcp/servers/9/enable")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").doesNotExist();

        InOrder inOrder = inOrder(serverConfigService, refreshCoordinator);
        inOrder.verify(serverConfigService).enable(eq(9L), isNull());
        inOrder.verify(refreshCoordinator).refreshServer(9L, "server_enable");
    }

    @Test
    void disableDisablesRuntimeServerAfterPersistenceSucceeds() {
        useImmediateScheduler();

        webTestClient.post()
                .uri("/api/admin/agent/mcp/servers/9/disable")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").doesNotExist();

        InOrder inOrder = inOrder(serverConfigService, refreshCoordinator);
        inOrder.verify(serverConfigService).disable(eq(9L), isNull());
        inOrder.verify(refreshCoordinator).disableServer(9L, "server_disable");
    }

    @Test
    void deleteDisablesRuntimeServerAfterPersistenceSucceeds() {
        useImmediateScheduler();

        webTestClient.delete()
                .uri("/api/admin/agent/mcp/servers/9")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").doesNotExist();

        InOrder inOrder = inOrder(serverConfigService, refreshCoordinator);
        inOrder.verify(serverConfigService).delete(eq(9L), isNull());
        inOrder.verify(refreshCoordinator).disableServer(9L, "server_delete");
    }

    @Test
    void discoverToolsRefreshesRuntimeServerAfterDiscoverySucceeds() {
        useImmediateScheduler();
        Long actor = null;
        given(toolDiscoveryService.discover(9L, actor)).willReturn(new McpToolDiscoveryResponse(9L, 3, 2, 1));

        webTestClient.post()
                .uri("/api/admin/agent/mcp/servers/9/discover-tools")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.serverId").isEqualTo(9)
                .jsonPath("$.data.discoveredCount").isEqualTo(3)
                .jsonPath("$.data.createdCount").isEqualTo(2)
                .jsonPath("$.data.updatedCount").isEqualTo(1);

        InOrder inOrder = inOrder(toolDiscoveryService, refreshCoordinator);
        inOrder.verify(toolDiscoveryService).discover(9L, actor);
        inOrder.verify(refreshCoordinator).refreshServer(9L, "tool_discover");
    }

    @Test
    @SuppressWarnings("unchecked")
    void enableSkipsRuntimeRefreshWhenCoordinatorUnavailable() {
        McpServerConfigService serverConfigService = mock(McpServerConfigService.class);
        McpToolDiscoveryService toolDiscoveryService = mock(McpToolDiscoveryService.class);
        ObjectProvider<McpRuntimeRefreshCoordinator> refreshCoordinatorProvider = mock(ObjectProvider.class);
        given(refreshCoordinatorProvider.getIfAvailable()).willReturn(null);
        AdminMcpServerController controller = new AdminMcpServerController(serverConfigService, toolDiscoveryService,
                refreshCoordinatorProvider);
        controller.setBlockingScheduler(Schedulers.immediate());

        controller.enable(9L, null).block();

        verify(serverConfigService).enable(9L, null);
        verify(refreshCoordinatorProvider).getIfAvailable();
    }

    private void useImmediateScheduler() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
    }

    private McpServerResponse response() {
        return new McpServerResponse(9L, "search", "Search MCP", McpTransportType.SSE,
                "https://mcp.example.com", "/sse", Map.of(), true, 3000, 10000,
                McpServerStatus.CONNECTED, null, Instant.parse("2026-06-14T01:00:00Z"),
                Instant.parse("2026-06-14T00:00:00Z"), Instant.parse("2026-06-14T01:00:00Z"));
    }

}
