package top.egon.mario.rbac.activation.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.activation.model.IssuedActivationToken;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies trusted-base Mock activation link construction without logging raw links.
 */
class MockActivationLinkDeliveryTests {

    @Test
    void buildsAnAdminOnlyFragmentUrlFromTheTrustedBaseUrl() {
        RbacOttProperties properties = new RbacOttProperties(true, Duration.ofHours(24),
                URI.create("https://console.example.test/"), ActivationDeliveryMode.MOCK, true, true);
        Instant expiresAt = Instant.parse("2026-07-23T00:00:00Z");
        IssuedActivationToken issued = new IssuedActivationToken(7L,
                new DefaultOneTimeToken("raw_token-1", "mario", expiresAt));

        var response = new MockActivationLinkDelivery(properties).deliver(issued);

        assertThat(response.mode()).isEqualTo(ActivationDeliveryMode.MOCK);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.mockActivationUrl())
                .isEqualTo("https://console.example.test/activate#token=raw_token-1");
    }
}
