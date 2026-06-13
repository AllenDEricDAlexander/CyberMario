package top.egon.mario.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies public monitoring endpoints used by deployment probes and metrics scrapers.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@AutoConfigureObservability
@AutoConfigureWebTestClient
class ActuatorMonitoringTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void healthEndpointIsAvailableWithoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void infoEndpointIsAvailableWithoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.app.name").isEqualTo("Mario");
    }

    @Test
    void prometheusEndpointIsAvailableWithoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("# HELP"));
    }

}
