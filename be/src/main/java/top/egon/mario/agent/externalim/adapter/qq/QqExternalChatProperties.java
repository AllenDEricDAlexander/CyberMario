package top.egon.mario.agent.externalim.adapter.qq;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.externalim.ExternalChatException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "mario.agent.external-im.qq")
public class QqExternalChatProperties {

    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:3000";
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Map<String, Connector> connectors = new LinkedHashMap<>();

    public Connector requireConnector(String connectorId) {
        Connector connector = StringUtils.hasText(connectorId)
                ? connectors.get(connectorId) : null;
        if (!StringUtils.hasText(baseUrl) || requestTimeout == null
                || requestTimeout.isZero() || requestTimeout.isNegative() || connector == null
                || !StringUtils.hasText(connector.getAccessToken())
                || connector.getBotUserId() == null) {
            throw new ExternalChatException("QQ_CONNECTOR_NOT_CONFIGURED",
                    "QQ connector is not configured");
        }
        return connector;
    }

    @Getter
    @Setter
    public static class Connector {
        private String accessToken;
        private Long botUserId;
        private boolean replyWithQuote = true;
    }
}
