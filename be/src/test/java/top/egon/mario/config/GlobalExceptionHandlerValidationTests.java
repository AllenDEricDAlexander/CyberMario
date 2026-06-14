package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.hamcrest.Matchers.containsString;

/**
 * Verifies validation failures use the standard API response envelope.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@AutoConfigureWebTestClient
class GlobalExceptionHandlerValidationTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void requestBodyValidationUsesStandardApiResponse() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "",
                          "password": ""
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").value(containsString("username"))
                .jsonPath("$.traceId").isNotEmpty();
    }

}
