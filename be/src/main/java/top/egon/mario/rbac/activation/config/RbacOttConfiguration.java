package top.egon.mario.rbac.activation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;
import top.egon.mario.rbac.activation.delivery.ActivationLinkDelivery;
import top.egon.mario.rbac.activation.delivery.MockActivationLinkDelivery;

import java.security.SecureRandom;
/**
 * Registers OTT infrastructure and rejects unsafe Mock startup.
 */
@Configuration
@EnableConfigurationProperties(RbacOttProperties.class)
public class RbacOttConfiguration {

    @Bean
    SecureRandom rbacOttSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    ActivationLinkDelivery activationLinkDelivery(RbacOttProperties properties, Environment environment) {
        if (!properties.enabled()) {
            throw new IllegalStateException("RBAC OTT must be enabled for pending account creation");
        }
        if (properties.deliveryMode() != ActivationDeliveryMode.MOCK) {
            throw new IllegalStateException("no activation link delivery is available for mode "
                    + properties.deliveryMode());
        }
        if (!properties.mockDeliveryAllowed()) {
            throw new IllegalStateException("mock delivery is not allowed");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("mock delivery is forbidden in prod");
        }
        return new MockActivationLinkDelivery(properties);
    }
}
