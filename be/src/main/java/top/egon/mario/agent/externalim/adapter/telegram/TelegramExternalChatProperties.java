package top.egon.mario.agent.externalim.adapter.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "mario.agent.external-im.telegram")
public class TelegramExternalChatProperties {

    private boolean enabled;
    private String baseUrl = "https://api.telegram.org";
    private Map<String, Connector> connectors = new LinkedHashMap<>();

    public Connector requireConnector(String connectorId) {
        Connector connector = StringUtils.hasText(connectorId)
                ? connectors.get(connectorId) : null;
        if (connector == null || !StringUtils.hasText(connector.getWebhookSecret())
                || !StringUtils.hasText(connector.getBotToken())
                || !StringUtils.hasText(connector.getBotUsername())) {
            throw new ExternalChatException("TELEGRAM_CONNECTOR_NOT_CONFIGURED",
                    "Telegram connector is not configured");
        }
        return connector;
    }

    @Getter
    @Setter
    public static class Connector {
        private String webhookSecret;
        private String botToken;
        private String botUsername;
        private Long botUserId;
    }
}
