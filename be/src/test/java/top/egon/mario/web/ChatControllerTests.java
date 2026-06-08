package top.egon.mario.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import top.egon.mario.agent.service.ChatAgentService;
import top.egon.mario.pojo.response.ChatResponse;

import static org.mockito.BDDMockito.given;

@WebFluxTest(ChatController.class)
class ChatControllerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatAgentService chatAgentService;

    @Test
    void chatReturnsAgentResponse() {
        given(chatAgentService.chat("你好", "thread-1"))
                .willReturn(Flux.just(new ChatResponse("thread-1", "你好，我是 CyberMario。")));

        StepVerifier.create(webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("""
                        {
                          "message": "你好",
                          "threadId": "thread-1"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .returnResult(ChatResponse.class)
                .getResponseBody())
                .expectNext(new ChatResponse("thread-1", "你好，我是 CyberMario。"))
                .verifyComplete();
    }

}
