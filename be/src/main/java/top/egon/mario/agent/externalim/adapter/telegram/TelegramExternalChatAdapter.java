package top.egon.mario.agent.externalim.adapter.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.adapter.ExternalChatInboundAdapter;
import top.egon.mario.agent.externalim.adapter.ExternalWebhookRequest;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "mario.agent.external-im.telegram",
        name = "enabled", havingValue = "true")
public class TelegramExternalChatAdapter implements ExternalChatInboundAdapter {

    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramExternalChatProperties properties;
    private final ObjectMapper objectMapper;

    public TelegramExternalChatAdapter(TelegramExternalChatProperties properties,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExternalChatPlatform platform() {
        return ExternalChatPlatform.TELEGRAM;
    }

    @Override
    public Optional<ExternalChatMessage> verifyAndNormalize(ExternalWebhookRequest request) {
        TelegramExternalChatProperties.Connector connector =
                properties.requireConnector(request.connectorId());
        verifySecret(connector.getWebhookSecret(),
                request.headers().getFirst(SECRET_HEADER));
        TelegramUpdate update = read(request.body());
        if (update.updateId() == null) {
            throw new ExternalChatException("TELEGRAM_EVENT_ID_REQUIRED",
                    "Telegram update_id is required");
        }
        TelegramUpdate.TelegramMessage message = update.message();
        if (message == null || message.chat() == null) {
            return Optional.of(unsupportedUpdate(update.updateId(), request.connectorId()));
        }
        ExternalConversationType conversationType = "private".equals(message.chat().type())
                ? ExternalConversationType.DIRECT : ExternalConversationType.GROUP;
        ExternalSender sender = sender(message.from());
        boolean mentioned = mentioned(message.text(), connector.getBotUsername());
        boolean replied = repliedToAgent(message.replyToMessage(), connector);
        String text = message.text();
        ExternalMessageType messageType = StringUtils.hasText(text)
                ? ExternalMessageType.TEXT : ExternalMessageType.UNSUPPORTED;
        if (mentioned && text != null) {
            String cleaned = mentionPattern(connector.getBotUsername()).matcher(text)
                    .replaceAll("").trim();
            text = StringUtils.hasText(cleaned) ? cleaned : text.trim();
        }
        String conversationId = String.valueOf(message.chat().id());
        return Optional.of(new ExternalChatMessage(String.valueOf(update.updateId()),
                message.messageId() == null ? null : String.valueOf(message.messageId()),
                ExternalChatPlatform.TELEGRAM, request.connectorId(), conversationId,
                conversationType, "telegram:" + request.connectorId() + ":" + conversationId,
                sender, messageType, text, mentioned, replied,
                message.date() == null ? Instant.now() : Instant.ofEpochSecond(message.date())));
    }

    private void verifySecret(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new ExternalChatException("EXTERNAL_CHAT_SIGNATURE_INVALID",
                    "Telegram webhook signature is invalid");
        }
    }

    private TelegramUpdate read(byte[] body) {
        try {
            return objectMapper.readValue(body, TelegramUpdate.class);
        } catch (IOException error) {
            throw new ExternalChatException("TELEGRAM_PAYLOAD_INVALID",
                    "Telegram webhook payload is invalid");
        }
    }

    private ExternalSender sender(TelegramUpdate.TelegramUser from) {
        if (from == null) {
            return new ExternalSender(null, null, ExternalSenderType.SYSTEM);
        }
        String displayName = StringUtils.hasText(from.displayName())
                ? from.displayName() : from.username();
        return new ExternalSender(from.id() == null ? null : String.valueOf(from.id()),
                displayName, from.bot() ? ExternalSenderType.BOT : ExternalSenderType.HUMAN);
    }

    private boolean mentioned(String text, String botUsername) {
        return StringUtils.hasText(text) && mentionPattern(botUsername).matcher(text).find();
    }

    private boolean repliedToAgent(TelegramUpdate.TelegramMessage reply,
                                   TelegramExternalChatProperties.Connector connector) {
        if (reply == null || reply.from() == null || !reply.from().bot()) {
            return false;
        }
        if (connector.getBotUserId() != null
                && connector.getBotUserId().equals(reply.from().id())) {
            return true;
        }
        return StringUtils.hasText(reply.from().username())
                && withoutAt(connector.getBotUsername())
                .equalsIgnoreCase(reply.from().username());
    }

    private ExternalChatMessage unsupportedUpdate(Long updateId, String connectorId) {
        String eventId = String.valueOf(updateId);
        return new ExternalChatMessage(eventId, null,
                ExternalChatPlatform.TELEGRAM, connectorId,
                "unsupported:" + eventId, ExternalConversationType.GROUP,
                "telegram:" + connectorId + ":unsupported",
                new ExternalSender(null, null, ExternalSenderType.SYSTEM),
                ExternalMessageType.UNSUPPORTED, "", false, false, Instant.now());
    }

    private Pattern mentionPattern(String botUsername) {
        return Pattern.compile("(?i)(?<![A-Za-z0-9_])@"
                + Pattern.quote(withoutAt(botUsername)) + "\\b");
    }

    private String withoutAt(String value) {
        return value.startsWith("@") ? value.substring(1) : value;
    }
}
