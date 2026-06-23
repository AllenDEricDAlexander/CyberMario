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
    void currentUserSelfServiceEndpointsRemainAuthenticated() {
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("PUT", "/api/me/profile")).isFalse();
        assertThat(RbacPublicApiPolicy.isAllowedPublicRule("PUT", "/api/me/password")).isFalse();
    }

}
