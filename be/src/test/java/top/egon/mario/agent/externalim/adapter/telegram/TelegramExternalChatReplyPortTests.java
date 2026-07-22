package top.egon.mario.agent.externalim.adapter.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import top.egon.mario.agent.externalim.model.ExternalReplyCommand;
import top.egon.mario.agent.externalim.model.ExternalReplyResult;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramExternalChatReplyPortTests {

    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicReference<HttpResponseStatus> responseStatus =
            new AtomicReference<>(HttpResponseStatus.OK);
    private DisposableServer server;

    @BeforeEach
    void startServer() {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            requests.add(new CapturedRequest(request.uri(), body));
                            response.status(responseStatus.get());
                            response.header(HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_JSON_VALUE);
                            return response.sendString(Mono.just(telegramOk(501L))).then();
                        }))
                .bindNow();
    }

    @AfterEach
    void stopServer() {
        server.disposeNow();
    }

    @Test
    void sendsPlainTextAndNeverPlacesTheTokenInTheBody() {
        TelegramExternalChatReplyPort port = new TelegramExternalChatReplyPort(
                properties(), WebClient.builder().build());

        ExternalReplyResult result = port.send(command("answer"));

        assertThat(result.sent()).isTrue();
        assertThat(result.platformMessageId()).isEqualTo("501");
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().uri()).endsWith("/botbot-token/sendMessage");
        assertThat(requests.getFirst().body())
                .contains("\"chat_id\":\"-1001\"", "\"text\":\"answer\"")
                .doesNotContain("bot-token", "webhook-secret", "parse_mode");
    }

    @Test
    void splitsByUnicodeCodePointAtTheTelegram4096CharacterLimit() {
        TelegramExternalChatReplyPort port = new TelegramExternalChatReplyPort(
                properties(), WebClient.builder().build());

        ExternalReplyResult result = port.send(command("😀".repeat(4097)));

        assertThat(result.sent()).isTrue();
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            String text = new ObjectMapper().readTree(request.body()).get("text").asText();
            assertThat(text.codePointCount(0, text.length())).isLessThanOrEqualTo(4096);
        });
    }

    @Test
    void retriesOnlyAnExplicitFailureBeforeAnyChunkWasSent() {
        responseStatus.set(HttpResponseStatus.TOO_MANY_REQUESTS);
        TelegramExternalChatReplyPort retryable = new TelegramExternalChatReplyPort(
                properties(), WebClient.builder().build());
        WebClient throwingClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new WebClientRequestException(
                        new SocketTimeoutException("timeout"), HttpMethod.POST,
                        request.url(), request.headers())))
                .build();
        TelegramExternalChatReplyPort ambiguous = new TelegramExternalChatReplyPort(
                properties(), throwingClient);

        assertThat(retryable.send(command("answer")).retryable()).isTrue();
        ExternalReplyResult timeout = ambiguous.send(command("answer"));
        assertThat(timeout.retryable()).isFalse();
        assertThat(timeout.errorCode()).isEqualTo("TELEGRAM_DELIVERY_AMBIGUOUS");
    }

    private ExternalReplyCommand command(String text) {
        return new ExternalReplyCommand(
                "main", "-1001", "77", "telegram:main:-1001", 1, text);
    }

    private TelegramExternalChatProperties properties() {
        TelegramExternalChatProperties value = new TelegramExternalChatProperties();
        value.setEnabled(true);
        value.setBaseUrl("http://127.0.0.1:" + server.port());
        TelegramExternalChatProperties.Connector connector =
                new TelegramExternalChatProperties.Connector();
        connector.setWebhookSecret("webhook-secret");
        connector.setBotToken("bot-token");
        connector.setBotUsername("cyber_mario_bot");
        connector.setBotUserId(99L);
        value.setConnectors(Map.of("main", connector));
        return value;
    }

    private String telegramOk(long messageId) {
        return """
                {"ok":true,"result":{"message_id":%d}}
                """.formatted(messageId);
    }

    private record CapturedRequest(String uri, String body) {
    }
}
