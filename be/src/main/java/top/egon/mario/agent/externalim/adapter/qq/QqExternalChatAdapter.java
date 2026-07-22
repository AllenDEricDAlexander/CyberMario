package top.egon.mario.agent.externalim.adapter.qq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
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
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "mario.agent.external-im.qq",
        name = "enabled", havingValue = "true")
public class QqExternalChatAdapter implements ExternalChatInboundAdapter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final QqExternalChatProperties properties;
    private final ObjectMapper objectMapper;

    public QqExternalChatAdapter(QqExternalChatProperties properties,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExternalChatPlatform platform() {
        return ExternalChatPlatform.QQ;
    }

    @Override
    public Optional<ExternalChatMessage> verifyAndNormalize(ExternalWebhookRequest request) {
        QqExternalChatProperties.Connector connector =
                properties.requireConnector(request.connectorId());
        verifyBearer(connector.getAccessToken(),
                request.headers().getFirst(HttpHeaders.AUTHORIZATION));
        QqOneBotEvent event = read(request.body());
        if (!"message".equals(event.postType())) {
            return Optional.empty();
        }
        requireMessageIdentity(event, connector);
        ExternalConversationType conversationType = conversationType(event.messageType());
        String targetId = conversationType == ExternalConversationType.GROUP
                ? requireGroupId(event) : String.valueOf(event.userId());
        String kind = conversationType == ExternalConversationType.GROUP ? "group" : "private";
        String conversationId = kind + ":" + targetId;
        ParsedText parsed = parseText(event.message(), event.selfId());
        ExternalMessageType messageType = parsed.supported()
                && StringUtils.hasText(parsed.text())
                ? ExternalMessageType.TEXT : ExternalMessageType.UNSUPPORTED;
        ExternalSenderType senderType = event.selfId().equals(event.userId())
                ? ExternalSenderType.BOT : ExternalSenderType.HUMAN;
        ExternalSender sender = new ExternalSender(String.valueOf(event.userId()),
                displayName(event.sender(), event.userId(), conversationType), senderType);
        Instant occurredAt = occurredAt(event.time());
        String messageId = String.valueOf(event.messageId());
        return Optional.of(new ExternalChatMessage(messageId, messageId,
                ExternalChatPlatform.QQ, request.connectorId(), conversationId,
                conversationType, "qq:" + request.connectorId() + ":" + conversationId,
                sender, messageType, parsed.text(), parsed.mentionedAgent(), false, occurredAt));
    }

    private void verifyBearer(String expected, String authorization) {
        String actual = "";
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
                BEARER_PREFIX.length())) {
            actual = authorization.substring(BEARER_PREFIX.length()).trim();
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new ExternalChatException("EXTERNAL_CHAT_SIGNATURE_INVALID",
                    "QQ webhook signature is invalid");
        }
    }

    private QqOneBotEvent read(byte[] body) {
        try {
            QqOneBotEvent event = objectMapper.readValue(body, QqOneBotEvent.class);
            if (event == null) {
                throw new ExternalChatException("QQ_PAYLOAD_INVALID",
                        "QQ webhook payload is invalid");
            }
            return event;
        } catch (IOException error) {
            throw new ExternalChatException("QQ_PAYLOAD_INVALID",
                    "QQ webhook payload is invalid");
        }
    }

    private Instant occurredAt(Long time) {
        if (time == null) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochSecond(time);
        } catch (DateTimeException error) {
            throw new ExternalChatException("QQ_PAYLOAD_INVALID",
                    "QQ webhook payload is invalid");
        }
    }

    private void requireMessageIdentity(QqOneBotEvent event,
                                        QqExternalChatProperties.Connector connector) {
        if (event.messageId() == null) {
            throw new ExternalChatException("QQ_MESSAGE_ID_REQUIRED",
                    "QQ message_id is required");
        }
        if (event.selfId() == null) {
            throw new ExternalChatException("QQ_SELF_ID_REQUIRED",
                    "QQ self_id is required");
        }
        if (!connector.getBotUserId().equals(event.selfId())) {
            throw new ExternalChatException("QQ_BOT_ID_MISMATCH",
                    "QQ event belongs to another bot account");
        }
        if (event.userId() == null) {
            throw new ExternalChatException("QQ_SENDER_ID_REQUIRED",
                    "QQ user_id is required");
        }
    }

    private ExternalConversationType conversationType(String messageType) {
        if ("group".equals(messageType)) {
            return ExternalConversationType.GROUP;
        }
        if ("private".equals(messageType)) {
            return ExternalConversationType.DIRECT;
        }
        throw new ExternalChatException("QQ_MESSAGE_TYPE_UNSUPPORTED",
                "QQ message_type is unsupported");
    }

    private String requireGroupId(QqOneBotEvent event) {
        if (event.groupId() == null) {
            throw new ExternalChatException("QQ_GROUP_ID_REQUIRED",
                    "QQ group_id is required");
        }
        return String.valueOf(event.groupId());
    }

    private ParsedText parseText(List<QqMessageSegment> segments, Long selfId) {
        if (segments == null) {
            return new ParsedText("", false, false);
        }
        StringBuilder text = new StringBuilder();
        boolean mentionedAgent = false;
        boolean supported = true;
        for (QqMessageSegment segment : segments) {
            if (segment == null || !StringUtils.hasText(segment.type())) {
                supported = false;
                continue;
            }
            Map<String, Object> data = segment.data();
            switch (segment.type().toLowerCase(Locale.ROOT)) {
                case "text" -> text.append(value(data, "text"));
                case "at" -> {
                    String target = value(data, "qq").trim();
                    if (String.valueOf(selfId).equals(target)) {
                        mentionedAgent = true;
                    } else if (StringUtils.hasText(target)) {
                        text.append(' ').append('@').append(target).append(' ');
                    }
                }
                case "reply" -> {
                }
                default -> supported = false;
            }
        }
        String normalized = text.toString().replaceAll("\\s+", " ").trim();
        return new ParsedText(normalized, mentionedAgent, supported);
    }

    private String value(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String displayName(QqSender sender, Long userId,
                               ExternalConversationType conversationType) {
        if (sender != null) {
            if (conversationType == ExternalConversationType.GROUP
                    && StringUtils.hasText(sender.card())) {
                return sender.card();
            }
            if (StringUtils.hasText(sender.nickname())) {
                return sender.nickname();
            }
        }
        return String.valueOf(userId);
    }

    private record ParsedText(String text, boolean mentionedAgent, boolean supported) {
    }
}
