package top.egon.mario.agent.externalim.adapter.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import top.egon.mario.agent.externalim.adapter.ExternalChatReplyPort;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalReplyCommand;
import top.egon.mario.agent.externalim.model.ExternalReplyResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "mario.agent.external-im.telegram",
        name = "enabled", havingValue = "true")
public class TelegramExternalChatReplyPort implements ExternalChatReplyPort {

    private static final int MAX_CODE_POINTS = 4096;

    private final TelegramExternalChatProperties properties;
    private final WebClient webClient;

    @Autowired
    public TelegramExternalChatReplyPort(TelegramExternalChatProperties properties,
                                         WebClient.Builder builder) {
        this(properties, builder.build());
    }

    TelegramExternalChatReplyPort(TelegramExternalChatProperties properties,
                                  WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    @Override
    public ExternalChatPlatform platform() {
        return ExternalChatPlatform.TELEGRAM;
    }

    @Override
    public ExternalReplyResult send(ExternalReplyCommand command) {
        TelegramExternalChatProperties.Connector connector =
                properties.requireConnector(command.connectorId());
        List<String> chunks = split(command.text());
        if (chunks.isEmpty()) {
            return ExternalReplyResult.failed(false, "TELEGRAM_REPLY_EMPTY",
                    "Telegram reply text is empty");
        }
        String lastMessageId = null;
        int sentChunks = 0;
        for (String chunk : chunks) {
            ExternalReplyResult result = sendChunk(
                    connector.getBotToken(), command.conversationId(), chunk);
            if (!result.sent()) {
                if (sentChunks > 0) {
                    return ExternalReplyResult.failed(false, "TELEGRAM_PARTIAL_DELIVERY",
                            "Telegram reply stopped after a partial delivery");
                }
                return result;
            }
            sentChunks++;
            lastMessageId = result.platformMessageId();
        }
        return ExternalReplyResult.sent(lastMessageId);
    }

    private ExternalReplyResult sendChunk(String token, String conversationId, String text) {
        try {
            return webClient.post()
                    .uri(properties.getBaseUrl() + "/bot" + token + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("chat_id", conversationId, "text", text))
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(TelegramApiResponse.class)
                                    .map(value -> value.ok() && value.result() != null
                                            ? ExternalReplyResult.sent(
                                            String.valueOf(value.result().messageId()))
                                            : ExternalReplyResult.failed(false,
                                            "TELEGRAM_RESPONSE_INVALID",
                                            "Telegram returned an invalid success response"));
                        }
                        boolean retryable = response.statusCode().value() == 429
                                || response.statusCode().is5xxServerError();
                        return response.releaseBody().thenReturn(ExternalReplyResult.failed(
                                retryable, "TELEGRAM_SEND_FAILED",
                                "Telegram rejected sendMessage"));
                    })
                    .blockOptional()
                    .orElseGet(() -> ExternalReplyResult.failed(false,
                            "TELEGRAM_RESPONSE_EMPTY", "Telegram returned no response"));
        } catch (RuntimeException error) {
            return ExternalReplyResult.failed(false, "TELEGRAM_DELIVERY_AMBIGUOUS",
                    "Telegram delivery outcome is unknown");
        }
    }

    private List<String> split(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int remaining = text.codePointCount(start, text.length());
            int take = Math.min(MAX_CODE_POINTS, remaining);
            int end = text.offsetByCodePoints(start, take);
            chunks.add(text.substring(start, end));
            start = end;
        }
        return List.copyOf(chunks);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramApiResponse(boolean ok, TelegramSentMessage result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramSentMessage(
            @JsonProperty("message_id") Long messageId
    ) {
    }
}
