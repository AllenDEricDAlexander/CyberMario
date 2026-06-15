package top.egon.mario.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Verifies agent debug streaming endpoints delegate to the dynamic chat service.
 */
@WebFluxTest(controllers = AgentDebugController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class AgentDebugControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatAgentService chatAgentService;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void debugChatReturnsAgentStream() {
        given(chatAgentService.debugChat(any(), any()))
                .willReturn(Flux.just(new ChatResponse("thread-1", "answer", "message")));

        StepVerifier.create(webTestClient.post()
                        .uri("/api/agent/debug/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_NDJSON)
                        .bodyValue("""
                                {
                                  "message": "hello",
                                  "threadId": "thread-1",
                                  "presetId": 9
                                }
                                """)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(ChatResponse.class)
                        .getResponseBody())
                .expectNext(new ChatResponse("thread-1", "answer", "message"))
                .verifyComplete();
    }

    @Test
    void debugChatRejectsBlankMessage() {
        webTestClient.post()
                .uri("/api/agent/debug/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON)
                .bodyValue("""
                        {
                          "message": ""
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").value(message ->
                        org.assertj.core.api.Assertions.assertThat(message.toString()).contains("message"));
    }

}
