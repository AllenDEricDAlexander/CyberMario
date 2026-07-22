package top.egon.mario.rbac.service.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies only intended authentication endpoints may be declared public.
 */
class RbacPublicApiPolicyTests {

    @Test
    void registerIsAllowedAsPublicAuthenticationEndpoint() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("POST", "/api/auth/register")).isTrue();
    }

    @Test
    void csrfTokenEndpointIsAllowedAsPublicGetEndpoint() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("GET", "/api/auth/csrf")).isTrue();
    }

    @Test
    void passwordKeyEndpointIsAllowedAsPublicGetEndpoint() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("GET", "/api/auth/password-key")).isTrue();
    }

    @Test
    void accountActivationIsPublicOnlyForPost() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule(
                "POST", "/api/auth/activation/complete")).isTrue();
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule(
                "GET", "/api/auth/activation/complete")).isFalse();
    }

    @Test
    void imWebSocketEndpointIsAllowedAsPublicGetEndpoint() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("GET", "/ws/im")).isTrue();
    }

    @Test
    void currentUserSelfServiceEndpointsRemainAuthenticated() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("PUT", "/api/me/profile")).isFalse();
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("PUT", "/api/me/password")).isFalse();
    }

    @Test
    void onlyPostExternalWebhookPathsMayBePublic() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule(
                "POST", "/api/external-im/webhooks/telegram/main")).isTrue();
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule(
                "GET", "/api/external-im/webhooks/telegram/main")).isFalse();
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule(
                "POST", "/api/external-im/admin/spaces")).isFalse();
    }

}
