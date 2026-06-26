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
import top.egon.mario.agent.mcp.dto.response.McpToolResponse;
import top.egon.mario.agent.mcp.po.enums.McpToolRiskLevel;
import top.egon.mario.agent.mcp.po.enums.McpToolRuntimeStatus;
import top.egon.mario.agent.mcp.runtime.McpRuntimeRefreshCoordinator;
import top.egon.mario.agent.mcp.service.McpToolConfigService;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies admin MCP tool policy endpoints.
 */
@WebFluxTest(controllers = AdminMcpToolController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AdminMcpToolControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private McpToolConfigService toolConfigService;

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
    void updatePolicyRefreshesRuntimeServerAfterPersistenceSucceeds() {
        useImmediateScheduler();
        given(toolConfigService.updatePolicy(eq(10L), any(), any())).willReturn(response(true,
                McpToolRuntimeStatus.AVAILABLE));

        webTestClient.put()
                .uri("/api/admin/agent/mcp/tools/10/policy")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "riskLevel":"MEDIUM",
                          "readonly":true,
                          "requireConfirm":false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(10)
                .jsonPath("$.data.serverId").isEqualTo(9)
                .jsonPath("$.data.enabled").isEqualTo(true);

        InOrder inOrder = inOrder(toolConfigService, refreshCoordinator);
        inOrder.verify(toolConfigService).updatePolicy(eq(10L), any(), any());
        inOrder.verify(refreshCoordinator).refreshServer(9L, "tool_policy_update");
    }

    @Test
    void enableRefreshesRuntimeServerAfterPersistenceSucceeds() {
        useImmediateScheduler();
        given(toolConfigService.enable(eq(10L), any())).willReturn(response(true,
                McpToolRuntimeStatus.AVAILABLE));

        webTestClient.post()
                .uri("/api/admin/agent/mcp/tools/10/enable")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").doesNotExist();

        InOrder inOrder = inOrder(toolConfigService, refreshCoordinator);
        inOrder.verify(toolConfigService).enable(eq(10L), isNull());
        inOrder.verify(refreshCoordinator).refreshServer(9L, "tool_enable");
    }

    @Test
    void disableRefreshesRuntimeServerAfterPersistenceSucceeds() {
        useImmediateScheduler();
        given(toolConfigService.disable(eq(10L), any())).willReturn(response(false,
                McpToolRuntimeStatus.DISABLED));

        webTestClient.post()
                .uri("/api/admin/agent/mcp/tools/10/disable")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").doesNotExist();

        InOrder inOrder = inOrder(toolConfigService, refreshCoordinator);
        inOrder.verify(toolConfigService).disable(eq(10L), isNull());
        inOrder.verify(refreshCoordinator).refreshServer(9L, "tool_disable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void enableSkipsRuntimeRefreshWhenCoordinatorUnavailable() {
        McpToolConfigService toolConfigService = mock(McpToolConfigService.class);
        ObjectProvider<McpRuntimeRefreshCoordinator> refreshCoordinatorProvider = mock(ObjectProvider.class);
        given(toolConfigService.enable(10L, null)).willReturn(response(true, McpToolRuntimeStatus.AVAILABLE));
        given(refreshCoordinatorProvider.getIfAvailable()).willReturn(null);
        AdminMcpToolController controller = new AdminMcpToolController(toolConfigService, refreshCoordinatorProvider);
        controller.setBlockingScheduler(Schedulers.immediate());

        controller.enable(10L, null).block();

        verify(toolConfigService).enable(10L, null);
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

    private McpToolResponse response(boolean enabled, McpToolRuntimeStatus runtimeStatus) {
        return new McpToolResponse(10L, 9L, "search", "search", "search_docs",
                "search_docs", "Search docs", "{\"type\":\"object\"}", enabled, McpToolRiskLevel.MEDIUM,
                true, false, runtimeStatus, Instant.parse("2026-06-14T01:00:00Z"));
    }

}
