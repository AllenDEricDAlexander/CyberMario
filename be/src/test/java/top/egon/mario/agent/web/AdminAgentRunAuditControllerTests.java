package top.egon.mario.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventStatus;
import top.egon.mario.agent.observability.po.enums.AgentRunEventType;
import top.egon.mario.agent.observability.po.enums.AgentRunToolType;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Verifies super-admin agent run audit endpoints.
 */
@WebFluxTest(controllers = AdminAgentRunAuditController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AdminAgentRunAuditControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentRunAuditService auditService;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private BrowserAuthCookieService browserAuthCookieService;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void pageAndEventsReturnRunAuditTimelineData() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
        given(auditService.page(any(), any(), any())).willReturn(new PageImpl<>(List.of(listRun()), PageRequest.of(0, 20), 1));
        given(auditService.detail(eq(12L), any())).willReturn(detailRun());
        given(auditService.events(eq(12L), any())).willReturn(List.of(event()));

        webTestClient.get()
                .uri("/api/admin/agent/run-audits?page=1&size=20&username=luigi&status=SUCCESS&toolName=docs_search&mcpServerCode=docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.records[0].id").isEqualTo(12)
                .jsonPath("$.data.records[0].effectiveConfigJson").doesNotExist()
                .jsonPath("$.data.records[0].modelCallCount").isEqualTo(2)
                .jsonPath("$.data.records[0].mcpToolCallCount").isEqualTo(1);

        webTestClient.get()
                .uri("/api/admin/agent/run-audits/12")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.effectiveConfigJson").isEqualTo("{\"systemPrompt\":\"prompt\"}")
                .jsonPath("$.data.userMessage").isEqualTo("raw user")
                .jsonPath("$.data.finalMessage").isEqualTo("raw answer");

        webTestClient.get()
                .uri("/api/admin/agent/run-audits/12/events")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].eventType").isEqualTo("TOOL_RESPONSE")
                .jsonPath("$.data[0].toolArguments").isEqualTo("{\"query\":\"raw\"}")
                .jsonPath("$.data[0].toolResult").isEqualTo("raw tool result");
    }

    private AgentRunAuditResponse listRun() {
        return new AgentRunAuditResponse(12L, "request-1", "trace-1", "thread-1", 8L, "luigi",
                9L, "fingerprint", null, null, null, null,
                AgentRunAuditStatus.SUCCESS, 2, 3, 1, Instant.parse("2026-06-15T01:00:00Z"),
                Instant.parse("2026-06-15T01:00:03Z"), 3000L, null, null,
                Instant.parse("2026-06-15T01:00:00Z"));
    }

    private AgentRunAuditResponse detailRun() {
        return new AgentRunAuditResponse(12L, "request-1", "trace-1", "thread-1", 8L, "luigi",
                9L, "fingerprint", "{\"systemPrompt\":\"prompt\"}", "raw user", "raw answer", "raw think",
                AgentRunAuditStatus.SUCCESS, 2, 3, 1, Instant.parse("2026-06-15T01:00:00Z"),
                Instant.parse("2026-06-15T01:00:03Z"), 3000L, null, null,
                Instant.parse("2026-06-15T01:00:00Z"));
    }

    private AgentRunEventAuditResponse event() {
        return new AgentRunEventAuditResponse(20L, 12L, "request-1", "trace-1", "thread-1", 5,
                AgentRunEventType.TOOL_RESPONSE, 2, "call-1", "docs_search", AgentRunToolType.MCP, "docs",
                AgentRunEventStatus.SUCCESS, Instant.parse("2026-06-15T01:00:02Z"),
                Instant.parse("2026-06-15T01:00:03Z"), 1000L, null, null, null, null, null, null,
                null, "{\"query\":\"raw\"}", "raw tool result", null, null, null,
                Instant.parse("2026-06-15T01:00:03Z"));
    }
}
