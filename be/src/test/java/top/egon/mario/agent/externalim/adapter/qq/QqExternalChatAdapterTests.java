package top.egon.mario.agent.externalim.adapter.qq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.adapter.ExternalWebhookRequest;
import top.egon.mario.agent.externalim.model.ExternalChatMessage;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QqExternalChatAdapterTests {

    private QqExternalChatAdapter adapter;

    @BeforeEach
    void setUp() {
        QqExternalChatProperties properties = new QqExternalChatProperties();
        QqExternalChatProperties.Connector connector =
                new QqExternalChatProperties.Connector();
        connector.setAccessToken("access-token");
        connector.setBotUserId(10001L);
        properties.setConnectors(Map.of("main", connector));
        adapter = new QqExternalChatAdapter(properties, new ObjectMapper());
    }

    @Test
    void normalizesGroupTextAndDetectsOnlyTheConfiguredBotMention() {
        ExternalChatMessage message = normalize("""
                {
                  "time": 1784731200,
                  "self_id": 10001,
                  "post_type": "message",
                  "message_type": "group",
                  "message_id": 2002,
                  "user_id": 30003,
                  "group_id": 40004,
                  "message": [
                    {"type":"at","data":{"qq":"10001"}},
                    {"type":"text","data":{"text":" review "}},
                    {"type":"at","data":{"qq":"77777"}},
                    {"type":"text","data":{"text":" please"}}
                  ],
                  "sender": {"user_id":30003,"nickname":"Alice","card":"Architect"}
                }
                """).orElseThrow();

        assertThat(message.eventId()).isEqualTo("2002");
        assertThat(message.messageId()).isEqualTo("2002");
        assertThat(message.conversationId()).isEqualTo("group:40004");
        assertThat(message.conversationType()).isEqualTo(ExternalConversationType.GROUP);
        assertThat(message.audienceKey()).isEqualTo("qq:main:group:40004");
        assertThat(message.sender()).isEqualTo(
                new ExternalSender("30003", "Architect", ExternalSenderType.HUMAN));
        assertThat(message.mentionedAgent()).isTrue();
        assertThat(message.repliedToAgentMessage()).isFalse();
        assertThat(message.messageType()).isEqualTo(ExternalMessageType.TEXT);
        assertThat(message.text()).isEqualTo("review @77777 please");
    }

    @Test
    void normalizesPrivateTextAsADirectConversation() {
        ExternalChatMessage message = normalize(privateJson(10001L)).orElseThrow();

        assertThat(message.conversationId()).isEqualTo("private:30003");
        assertThat(message.conversationType()).isEqualTo(ExternalConversationType.DIRECT);
        assertThat(message.audienceKey()).isEqualTo("qq:main:private:30003");
        assertThat(message.sender()).isEqualTo(
                new ExternalSender("30003", "Alice", ExternalSenderType.HUMAN));
        assertThat(message.text()).isEqualTo("hello");
        assertThat(message.mentionedAgent()).isFalse();
    }

    @Test
    void dropsAuthenticatedNonMessageAndMessageSentEvents() {
        assertThat(normalize("""
                {"time":1784731202,"self_id":10001,"post_type":"meta_event",
                 "meta_event_type":"heartbeat"}
                """)).isEmpty();
        assertThat(normalize("""
                {"time":1784731203,"self_id":10001,"post_type":"message_sent",
                 "message_type":"private","message_id":2004,"user_id":30003,
                 "message":[{"type":"text","data":{"text":"sent"}}]}
                """)).isEmpty();
    }

    @Test
    void rejectsInvalidBearerTokenBeforeParsing() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("wrong");

        assertThatThrownBy(() -> adapter.verifyAndNormalize(
                request(headers, "not-json")))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("EXTERNAL_CHAT_SIGNATURE_INVALID");
    }

    @Test
    void rejectsEventsForAnotherConfiguredBot() {
        assertThatThrownBy(() -> normalize(privateJson(99999L)))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("QQ_BOT_ID_MISMATCH");
    }

    @Test
    void auditsSelfMessagesAsBotAndMixedModalMessagesAsUnsupported() {
        ExternalChatMessage self = normalize(groupJson(10001L, """
                [{"type":"text","data":{"text":"echo"}}]
                """)).orElseThrow();
        assertThat(self.sender().type()).isEqualTo(ExternalSenderType.BOT);

        ExternalChatMessage mixed = normalize(groupJson(30003L, """
                [
                  {"type":"text","data":{"text":"caption"}},
                  {"type":"image","data":{"file":"x"}}
                ]
                """)).orElseThrow();
        assertThat(mixed.messageType()).isEqualTo(ExternalMessageType.UNSUPPORTED);
        assertThat(mixed.text()).isEqualTo("caption");
    }

    @Test
    void rejectsStringMessageFormatSoAddressingMetadataCannotBeLost() {
        String json = """
                {
                  "time":1784731204,"self_id":10001,"post_type":"message",
                  "message_type":"group","message_id":2005,"user_id":30003,
                  "group_id":40004,"message":"[CQ:at,qq=10001]hello"
                }
                """;

        assertThatThrownBy(() -> normalize(json))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("QQ_PAYLOAD_INVALID");
    }

    @Test
    void rejectsNullAndOutOfRangeTimestampsAsInvalidPayloads() {
        assertThatThrownBy(() -> normalize("null"))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("QQ_PAYLOAD_INVALID");

        String invalidTimestamp = privateJson(10001L)
                .replace("1784731201", String.valueOf(Long.MAX_VALUE));
        assertThatThrownBy(() -> normalize(invalidTimestamp))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("QQ_PAYLOAD_INVALID");
    }

    private Optional<ExternalChatMessage> normalize(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("access-token");
        return adapter.verifyAndNormalize(request(headers, json));
    }

    private ExternalWebhookRequest request(HttpHeaders headers, String json) {
        return new ExternalWebhookRequest(
                "main", headers, json.getBytes(StandardCharsets.UTF_8));
    }

    private String privateJson(long selfId) {
        return """
                {
                  "time": 1784731201,
                  "self_id": %d,
                  "post_type": "message",
                  "message_type": "private",
                  "message_id": 2003,
                  "user_id": 30003,
                  "message": [{"type":"text","data":{"text":"hello"}}],
                  "sender": {"user_id":30003,"nickname":"Alice","card":""}
                }
                """.formatted(selfId);
    }

    private String groupJson(long userId, String message) {
        return """
                {
                  "time": 1784731200,
                  "self_id": 10001,
                  "post_type": "message",
                  "message_type": "group",
                  "message_id": 2002,
                  "user_id": %d,
                  "group_id": 40004,
                  "message": %s,
                  "sender": {"user_id":%d,"nickname":"Alice","card":"Architect"}
                }
                """.formatted(userId, message, userId);
    }
}
