package top.egon.mario.agent.externalim.adapter.qq;

import com.fasterxml.jackson.databind.JsonNode;
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

class QqExternalChatReplyPortTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicReference<HttpResponseStatus> responseStatus =
            new AtomicReference<>(HttpResponseStatus.OK);
    private final AtomicReference<String> responseBody = new AtomicReference<>("""
            {"status":"ok","retcode":0,"data":{"message_id":9009},
             "message":"","wording":""}
            """);
    private DisposableServer server;

    @BeforeEach
    void startServer() {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asString()
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            requests.add(new CapturedRequest(request.uri(),
                                    request.requestHeaders().get(HttpHeaders.AUTHORIZATION), body));
                            response.status(responseStatus.get());
                            response.header(HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_JSON_VALUE);
                            return response.sendString(Mono.just(responseBody.get())).then();
                        }))
                .bindNow();
    }

    @AfterEach
    void stopServer() {
        server.disposeNow();
    }

    @Test
    void sendsQuotedGroupTextWithBearerAuthentication() throws Exception {
        ExternalReplyResult result = port(properties()).send(command(
                "group:40004", "2002", "answer"));

        assertThat(result.sent()).isTrue();
        assertThat(result.platformMessageId()).isEqualTo("9009");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.uri()).isEqualTo("/send_group_msg");
            assertThat(request.authorization()).isEqualTo("Bearer access-token");
            JsonNode body = readTree(request.body());
            assertThat(body.get("group_id").asText()).isEqualTo("40004");
            assertThat(body.get("message").get(0).get("type").asText())
                    .isEqualTo("reply");
            assertThat(body.get("message").get(0).get("data").get("id").asText())
                    .isEqualTo("2002");
            assertThat(body.get("message").get(1).get("data").get("text").asText())
                    .isEqualTo("answer");
            assertThat(request.uri() + request.body()).doesNotContain("access-token");
        });
    }

    @Test
    void sendsPrivateTextWithoutAReplySegment() throws Exception {
        ExternalReplyResult result = port(properties()).send(command(
                "private:30003", "2003", "answer"));

        assertThat(result.sent()).isTrue();
        JsonNode body = objectMapper.readTree(requests.getFirst().body());
        assertThat(requests.getFirst().uri()).isEqualTo("/send_private_msg");
        assertThat(body.get("user_id").asText()).isEqualTo("30003");
        assertThat(body.get("message").asText()).isEqualTo("answer");
    }

    @Test
    void sendsUnquotedGroupTextWhenTheConnectorDisablesQuoting() throws Exception {
        QqExternalChatProperties properties = properties();
        properties.getConnectors().get("main").setReplyWithQuote(false);

        ExternalReplyResult result = port(properties).send(command(
                "group:40004", "2002", "answer"));

        assertThat(result.sent()).isTrue();
        JsonNode body = objectMapper.readTree(requests.getFirst().body());
        assertThat(body.get("message").asText()).isEqualTo("answer");
    }

    @Test
    void classifiesExplicitAndAmbiguousFailuresWithoutBlindDuplicates() {
        responseStatus.set(HttpResponseStatus.SERVICE_UNAVAILABLE);
        assertThat(port(properties()).send(
                command("group:40004", "2002", "answer")).retryable()).isTrue();

        responseStatus.set(HttpResponseStatus.OK);
        responseBody.set("""
                {"status":"failed","retcode":1200,"data":null,
                 "message":"rejected","wording":"rejected"}
                """);
        ExternalReplyResult rejected = port(properties()).send(
                command("group:40004", "2002", "answer"));
        assertThat(rejected.retryable()).isFalse();
        assertThat(rejected.errorCode()).isEqualTo("QQ_SEND_REJECTED");

        ExternalReplyResult ambiguous = throwingPort().send(
                command("private:30003", "2003", "answer"));
        assertThat(ambiguous.retryable()).isFalse();
        assertThat(ambiguous.errorCode()).isEqualTo("QQ_DELIVERY_AMBIGUOUS");
    }

    @Test
    void rejectsEmptyTextAndMalformedConversationBeforeNetworkUse() {
        assertThat(port(properties()).send(
                command("group:40004", "2002", " ")).errorCode())
                .isEqualTo("QQ_REPLY_EMPTY");
        assertThat(port(properties()).send(
                command("40004", "2002", "answer")).errorCode())
                .isEqualTo("QQ_CONVERSATION_INVALID");
        assertThat(port(properties()).send(
                command("group: ", "2002", "answer")).errorCode())
                .isEqualTo("QQ_CONVERSATION_INVALID");
        assertThat(requests).isEmpty();
    }

    @Test
    void rejectsInvalidSuccessResponsesAndNonRetryableHttpErrors() {
        responseBody.set("{\"status\":\"ok\",\"retcode\":0,\"data\":null}");
        ExternalReplyResult invalid = port(properties()).send(
                command("private:30003", "2003", "answer"));
        assertThat(invalid.errorCode()).isEqualTo("QQ_RESPONSE_INVALID");
        assertThat(invalid.retryable()).isFalse();

        responseStatus.set(HttpResponseStatus.BAD_REQUEST);
        ExternalReplyResult badRequest = port(properties()).send(
                command("private:30003", "2003", "answer"));
        assertThat(badRequest.errorCode()).isEqualTo("QQ_SEND_FAILED");
        assertThat(badRequest.retryable()).isFalse();
    }

    private QqExternalChatReplyPort port(QqExternalChatProperties properties) {
        return new QqExternalChatReplyPort(properties, WebClient.builder().build());
    }

    private QqExternalChatReplyPort throwingPort() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new WebClientRequestException(
                        new SocketTimeoutException("timeout"), HttpMethod.POST,
                        request.url(), request.headers())))
                .build();
        return new QqExternalChatReplyPort(properties(), webClient);
    }

    private QqExternalChatProperties properties() {
        QqExternalChatProperties properties = new QqExternalChatProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.port() + "/");
        QqExternalChatProperties.Connector connector =
                new QqExternalChatProperties.Connector();
        connector.setAccessToken("access-token");
        connector.setBotUserId(10001L);
        properties.setConnectors(Map.of("main", connector));
        return properties;
    }

    private ExternalReplyCommand command(String conversationId, String sourceMessageId,
                                         String text) {
        return new ExternalReplyCommand("main", conversationId, sourceMessageId,
                "qq:main:" + conversationId, 1, text);
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private record CapturedRequest(String uri, String authorization, String body) {
    }
}
