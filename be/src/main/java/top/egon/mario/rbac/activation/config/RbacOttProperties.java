package top.egon.mario.rbac.activation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;

import java.net.URI;
import java.time.Duration;

/**
 * Typed OTT settings with safe defaults.
 */
@ConfigurationProperties(prefix = "mario.security.ott")
public record RbacOttProperties(
        boolean enabled,
        Duration activationTokenTtl,
        URI publicBaseUrl,
        ActivationDeliveryMode deliveryMode,
        boolean mockDeliveryAllowed,
        boolean rateLimitEnabled
) {
    public RbacOttProperties {
        activationTokenTtl = activationTokenTtl == null ? Duration.ofHours(24) : activationTokenTtl;
        publicBaseUrl = publicBaseUrl == null ? URI.create("http://localhost:5173") : publicBaseUrl;
        deliveryMode = deliveryMode == null ? ActivationDeliveryMode.MOCK : deliveryMode;
        if (activationTokenTtl.isZero() || activationTokenTtl.isNegative()) {
            throw new IllegalArgumentException("activation token ttl must be positive");
        }
        if (!publicBaseUrl.isAbsolute()
                || !("http".equalsIgnoreCase(publicBaseUrl.getScheme())
                || "https".equalsIgnoreCase(publicBaseUrl.getScheme()))) {
            throw new IllegalArgumentException("OTT public base URL must be an absolute HTTP(S) URI");
        }
    }
}
