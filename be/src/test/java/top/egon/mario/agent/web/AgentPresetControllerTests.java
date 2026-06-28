package top.egon.mario.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import top.egon.mario.agent.dto.response.AgentPresetResponse;
import top.egon.mario.agent.service.AgentPresetService;
import top.egon.mario.agent.service.model.AgentPresetConfig;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Verifies agent preset management endpoints use the preset service contract.
 */
@WebFluxTest(controllers = AgentPresetController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AgentPresetControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentPresetService presetService;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private BrowserAuthCookieService browserAuthCookieService;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void pageReturnsPresetPage() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
        given(presetService.page(any())).willReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1));

        webTestClient.get()
                .uri("/api/agent/presets?page=1&size=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.records[0].id").isEqualTo(9)
                .jsonPath("$.data.records[0].name").isEqualTo("Research");
    }

    @Test
    void createUpdateStatusAndDeleteDelegate() {
        given(blockingScheduler.schedule(any())).willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return (reactor.core.Disposable) () -> {
            };
        });
        given(blockingScheduler.createWorker()).willAnswer(invocation -> Schedulers.immediate().createWorker());
        given(presetService.create(any(), any())).willReturn(response());
        given(presetService.update(eq(9L), any(), any())).willReturn(response());
        given(presetService.updateStatus(eq(9L), any(), any())).willReturn(response());

        webTestClient.post()
                .uri("/api/agent/presets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Research","enabled":true}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(9);

        webTestClient.put()
                .uri("/api/agent/presets/9")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Research v2","enabled":true}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.patch()
                .uri("/api/agent/presets/9/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"enabled":false}
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.delete()
                .uri("/api/agent/presets/9")
                .exchange()
                .expectStatus().isOk();

        verify(presetService).delete(eq(9L), any());
    }

    private AgentPresetResponse response() {
        return new AgentPresetResponse(9L, "Research", "debug", new AgentPresetConfig(null, null, "prompt", null, null),
                true, 8L, 8L, Instant.parse("2026-06-14T01:00:00Z"), Instant.parse("2026-06-14T01:00:00Z"));
    }

}
