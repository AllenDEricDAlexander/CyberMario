package top.egon.mario.agent.externalim.adapter.telegram;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramExternalChatAdapterTests {

    private TelegramExternalChatAdapter adapter;

    @BeforeEach
    void setUp() {
        TelegramExternalChatProperties properties = new TelegramExternalChatProperties();
        TelegramExternalChatProperties.Connector connector =
                new TelegramExternalChatProperties.Connector();
        connector.setWebhookSecret("webhook-secret");
        connector.setBotToken("bot-token");
        connector.setBotUsername("cyber_mario_bot");
        connector.setBotUserId(99L);
        properties.setConnectors(Map.of("main", connector));
        adapter = new TelegramExternalChatAdapter(properties, new ObjectMapper());
    }

    @Test
    void verifiesSecretNormalizesGroupTextAndRemovesTheBotMention() {
        ExternalChatMessage message = normalize("""
                {
                  "update_id": 9001,
                  "message": {
                    "message_id": 77,
                    "date": 1784505600,
                    "from": {"id": 42, "is_bot": false, "first_name": "Alice"},
                    "chat": {"id": -1001, "type": "supergroup", "title": "Dev"},
                    "text": "@cyber_mario_bot can you review this?"
                  }
                }
                """);

        assertThat(message.eventId()).isEqualTo("9001");
        assertThat(message.messageId()).isEqualTo("77");
        assertThat(message.conversationId()).isEqualTo("-1001");
        assertThat(message.conversationType()).isEqualTo(ExternalConversationType.GROUP);
        assertThat(message.audienceKey()).isEqualTo("telegram:main:-1001");
        assertThat(message.sender()).isEqualTo(new ExternalSender(
                "42", "Alice", ExternalSenderType.HUMAN));
        assertThat(message.mentionedAgent()).isTrue();
        assertThat(message.text()).isEqualTo("can you review this?");
    }

    @Test
    void rejectsAnInvalidSecretBeforeParsingOrPersistence() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TelegramExternalChatAdapter.SECRET_HEADER, "wrong");

        assertThatThrownBy(() -> adapter.verifyAndNormalize(
                new ExternalWebhookRequest("main", headers,
                        "{}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("EXTERNAL_CHAT_SIGNATURE_INVALID");
    }

    @Test
    void detectsRepliesToTheConfiguredBotAndClassifiesNonTextAsUnsupported() {
        ExternalChatMessage reply = normalize("""
                {
                  "update_id": 9002,
                  "message": {
                    "message_id": 78,
                    "date": 1784505601,
                    "from": {"id": 43, "is_bot": false, "first_name": "Bob"},
                    "chat": {"id": -1001, "type": "group", "title": "Dev"},
                    "reply_to_message": {
                      "message_id": 70,
                      "from": {"id": 99, "is_bot": true, "username": "cyber_mario_bot"}
                    }
                  }
                }
                """);

        assertThat(reply.repliedToAgentMessage()).isTrue();
        assertThat(reply.messageType()).isEqualTo(ExternalMessageType.UNSUPPORTED);
    }

    private ExternalChatMessage normalize(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TelegramExternalChatAdapter.SECRET_HEADER, "webhook-secret");
        return adapter.verifyAndNormalize(new ExternalWebhookRequest(
                "main", headers, json.getBytes(StandardCharsets.UTF_8)));
    }
}
