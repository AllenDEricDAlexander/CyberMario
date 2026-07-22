package top.egon.mario.agent.externalim.adapter.qq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.externalim.adapter.ExternalChatReplyPort;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalReplyCommand;
import top.egon.mario.agent.externalim.model.ExternalReplyResult;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "mario.agent.external-im.qq",
        name = "enabled", havingValue = "true")
public class QqExternalChatReplyPort implements ExternalChatReplyPort {

    private static final String GROUP_PREFIX = "group:";
    private static final String PRIVATE_PREFIX = "private:";

    private final QqExternalChatProperties properties;
    private final WebClient webClient;

    @Autowired
    public QqExternalChatReplyPort(QqExternalChatProperties properties,
                                   WebClient.Builder builder) {
        this(properties, builder.build());
    }

    QqExternalChatReplyPort(QqExternalChatProperties properties,
                            WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    @Override
    public ExternalChatPlatform platform() {
        return ExternalChatPlatform.QQ;
    }

    @Override
    public ExternalReplyResult send(ExternalReplyCommand command) {
        if (command == null || !StringUtils.hasText(command.text())) {
            return ExternalReplyResult.failed(false, "QQ_REPLY_EMPTY",
                    "QQ reply text is empty");
        }
        QqTarget target = target(command.conversationId());
        if (target == null) {
            return ExternalReplyResult.failed(false, "QQ_CONVERSATION_INVALID",
                    "QQ conversation id is invalid");
        }
        QqExternalChatProperties.Connector connector =
                properties.requireConnector(command.connectorId());
        Map<String, Object> payload = payload(command, target, connector);
        return sendToNapCat(connector, target, payload);
    }

    private QqTarget target(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        if (conversationId.startsWith(GROUP_PREFIX)
                && conversationId.length() > GROUP_PREFIX.length()) {
            String id = conversationId.substring(GROUP_PREFIX.length()).trim();
            return isQqId(id)
                    ? new QqTarget("/send_group_msg", "group_id", id, true) : null;
        }
        if (conversationId.startsWith(PRIVATE_PREFIX)
                && conversationId.length() > PRIVATE_PREFIX.length()) {
            String id = conversationId.substring(PRIVATE_PREFIX.length()).trim();
            return isQqId(id)
                    ? new QqTarget("/send_private_msg", "user_id", id, false) : null;
        }
        return null;
    }

    private boolean isQqId(String value) {
        return StringUtils.hasText(value)
                && value.chars().allMatch(character -> character >= '0' && character <= '9');
    }

    private Map<String, Object> payload(ExternalReplyCommand command, QqTarget target,
                                        QqExternalChatProperties.Connector connector) {
        Object message = command.text();
        if (target.group() && connector.isReplyWithQuote()
                && StringUtils.hasText(command.sourceMessageId())) {
            message = List.of(
                    Map.of("type", "reply", "data",
                            Map.of("id", command.sourceMessageId())),
                    Map.of("type", "text", "data",
                            Map.of("text", command.text())));
        }
        return Map.of(target.idField(), target.id(), "message", message);
    }

    private ExternalReplyResult sendToNapCat(QqExternalChatProperties.Connector connector,
                                             QqTarget target,
                                             Map<String, Object> payload) {
        try {
            return webClient.post()
                    .uri(baseUrl() + target.endpoint())
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + connector.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(QqApiResponse.class)
                                    .map(this::mapResponse)
                                    .switchIfEmpty(Mono.just(ExternalReplyResult.failed(false,
                                            "QQ_RESPONSE_EMPTY",
                                            "QQ returned no response body")));
                        }
                        boolean retryable = response.statusCode().value() == 429
                                || response.statusCode().is5xxServerError();
                        return response.releaseBody().thenReturn(ExternalReplyResult.failed(
                                retryable, "QQ_SEND_FAILED",
                                "NapCat rejected the QQ send request"));
                    })
                    .timeout(properties.getRequestTimeout())
                    .blockOptional()
                    .orElseGet(() -> ExternalReplyResult.failed(false,
                            "QQ_RESPONSE_EMPTY", "QQ returned no response"));
        } catch (RuntimeException error) {
            return ExternalReplyResult.failed(false, "QQ_DELIVERY_AMBIGUOUS",
                    "QQ delivery outcome is unknown");
        }
    }

    private ExternalReplyResult mapResponse(QqApiResponse response) {
        if (response == null || response.status() == null || response.retcode() == null) {
            return ExternalReplyResult.failed(false, "QQ_RESPONSE_INVALID",
                    "NapCat returned an invalid QQ response");
        }
        if (!"ok".equalsIgnoreCase(response.status()) || response.retcode() != 0) {
            return ExternalReplyResult.failed(false, "QQ_SEND_REJECTED",
                    "NapCat rejected the QQ message");
        }
        if (response.data() == null || response.data().messageId() == null) {
            return ExternalReplyResult.failed(false, "QQ_RESPONSE_INVALID",
                    "NapCat returned an invalid QQ success response");
        }
        return ExternalReplyResult.sent(String.valueOf(response.data().messageId()));
    }

    private String baseUrl() {
        String value = properties.getBaseUrl();
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private record QqTarget(String endpoint, String idField, String id, boolean group) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QqApiResponse(String status, Integer retcode, QqSentMessage data,
                                 String message, String wording) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QqSentMessage(
            @JsonProperty("message_id") Long messageId
    ) {
    }
}
