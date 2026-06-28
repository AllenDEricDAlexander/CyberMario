package top.egon.mario.web;

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
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.pojo.response.ChatResponse;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.RbacApiRuleCache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebFluxTest(controllers = ChatController.class,
        excludeAutoConfiguration = {ReactiveSecurityAutoConfiguration.class, ReactiveUserDetailsServiceAutoConfiguration.class})
class ChatControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatAgentService chatAgentService;

    @MockitoBean
    private RbacAuthApplication rbacAuthApplication;

    @MockitoBean
    private RbacApiRuleCache rbacApiRuleCache;

    @MockitoBean
    private BrowserAuthCookieService browserAuthCookieService;

    @MockitoBean
    private Scheduler blockingScheduler;

    @Test
    void chatReturnsAgentResponse() {
        given(chatAgentService.chat(any(ChatRequest.class), any()))
                .willReturn(Flux.just(new ChatResponse("thread-1", "你好，我是 CyberMario。")));

        StepVerifier.create(webTestClient.post()
                        .uri("/demo/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_NDJSON)
                        .bodyValue("""
                                {
                                  "message": "你好",
                                  "threadId": "thread-1",
                                  "sessionId": "session-1",
                                  "memoryEnabled": true
                                }
                                """)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(ChatResponse.class)
                        .getResponseBody())
                .expectNext(new ChatResponse("thread-1", "你好，我是 CyberMario。"))
                .verifyComplete();
        verify(chatAgentService).chat(org.mockito.ArgumentMatchers.argThat(request ->
                "session-1".equals(request.sessionId()) && Boolean.TRUE.equals(request.memoryEnabled())), any());
    }

    @Test
    void chatRejectsBlankMessage() {
        webTestClient.post()
                .uri("/demo/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON)
                .bodyValue("""
                        {
                          "message": "",
                          "threadId": "thread-1"
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
