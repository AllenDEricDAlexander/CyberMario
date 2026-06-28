package top.egon.mario.agent.soul.web;

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
import top.egon.mario.agent.soul.dto.request.AgentSoulMdUpdateRequest;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdResponse;
import top.egon.mario.agent.soul.dto.response.AgentSoulMdVersionResponse;
import top.egon.mario.agent.soul.po.enums.AgentSoulChangeType;
import top.egon.mario.agent.soul.service.AgentSoulService;
import top.egon.mario.agent.web.AgentSoulController;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebFluxTest(controllers = AgentSoulController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AgentSoulControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentSoulService soulService;
    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;
    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;
    @MockitoBean
    private BrowserAuthCookieService browserAuthCookieService;
    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void currentUpdateAndVersionsDelegateToSoulService() {
        useImmediateScheduler();
        given(soulService.currentSoul(any())).willReturn(response());
        given(soulService.updateManual(any(AgentSoulMdUpdateRequest.class), any())).willReturn(response());
        given(soulService.versions(any())).willReturn(List.of(version()));

        webTestClient.get().uri("/api/me/soul-md")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.contentMarkdown").isEqualTo("# SoulMD")
                .jsonPath("$.data.enabled").isEqualTo(true)
                .jsonPath("$.data.contentChars").isEqualTo(8)
                .jsonPath("$.data.maxChars").isEqualTo(50000);

        webTestClient.put().uri("/api/me/soul-md")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"contentMarkdown":"# New Soul","enabled":false}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.contentMarkdown").isEqualTo("# SoulMD");

        webTestClient.get().uri("/api/me/soul-md/versions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].versionNo").isEqualTo(1)
                .jsonPath("$.data[0].changeType").isEqualTo("MANUAL_EDIT")
                .jsonPath("$.data[0].sourceMessageIds").isEqualTo("1,2");

        verify(soulService).currentSoul(any());
        verify(soulService).updateManual(any(AgentSoulMdUpdateRequest.class), any());
        verify(soulService).versions(any());
    }

    @Test
    void updateRejectsMissingFullReplacementFields() {
        webTestClient.put().uri("/api/me/soul-md")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"contentMarkdown":"# New Soul"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.put().uri("/api/me/soul-md")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"enabled":true}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
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

    private AgentSoulMdResponse response() {
        return new AgentSoulMdResponse("# SoulMD", true, 8, 50_000, 1,
                Instant.parse("2026-06-22T00:00:00Z"));
    }

    private AgentSoulMdVersionResponse version() {
        return new AgentSoulMdVersionResponse(10L, 1, "# Old Soul", 10,
                AgentSoulChangeType.MANUAL_EDIT, "Manual SoulMD edit", null, null,
                "1,2", null, null, null, null, Instant.parse("2026-06-22T00:00:00Z"));
    }
}
