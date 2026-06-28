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
import top.egon.mario.agent.dto.response.AgentConversationAuditResponse;
import top.egon.mario.agent.dto.response.AgentConversationMessageAuditResponse;
import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;
import top.egon.mario.agent.po.enums.AgentConversationStatus;
import top.egon.mario.agent.service.AgentConversationAuditService;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Verifies super-admin agent conversation audit endpoints.
 */
@WebFluxTest(controllers = AdminAgentConversationAuditController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AdminAgentConversationAuditControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentConversationAuditService auditService;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private BrowserAuthCookieService browserAuthCookieService;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void pageAndMessagesReturnAuditData() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
        given(auditService.page(any(), any(), any())).willReturn(new PageImpl<>(List.of(audit()), PageRequest.of(0, 20), 1));
        given(auditService.messages(eq(12L), any())).willReturn(List.of(message()));

        webTestClient.get()
                .uri("/api/admin/agent/conversation-audits?page=1&size=20&username=luigi&status=SUCCESS")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.records[0].id").isEqualTo(12)
                .jsonPath("$.data.records[0].effectiveConfigJson").isEqualTo("{\"systemPrompt\":\"prompt\"}");

        webTestClient.get()
                .uri("/api/admin/agent/conversation-audits/12/messages")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].content").isEqualTo("raw answer");
    }

    private AgentConversationAuditResponse audit() {
        return new AgentConversationAuditResponse(12L, "request-1", "trace-1", 8L, "luigi", "thread-1",
                9L, "fingerprint", "{\"systemPrompt\":\"prompt\"}", AgentConversationStatus.SUCCESS,
                Instant.parse("2026-06-14T01:00:00Z"), Instant.parse("2026-06-14T01:00:03Z"),
                3000L, null, null, "127.0.0.1", "JUnit", Instant.parse("2026-06-14T01:00:00Z"));
    }

    private AgentConversationMessageAuditResponse message() {
        return new AgentConversationMessageAuditResponse(20L, 12L, 1, AgentConversationRole.ASSISTANT,
                AgentConversationMessageType.MESSAGE, "raw answer", 10, Instant.parse("2026-06-14T01:00:02Z"));
    }

}
