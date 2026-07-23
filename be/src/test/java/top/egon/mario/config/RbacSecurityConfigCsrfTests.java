package top.egon.mario.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.application.RbacAccountActivationApplication;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Verifies browser-cookie CSRF behavior for the RBAC security filter chain.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@AutoConfigureWebTestClient
class RbacSecurityConfigCsrfTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private RbacAuthApplication authApplication;

    @MockitoBean
    private RbacAccountActivationApplication accountActivationApplication;

    @Test
    void csrfEndpointReturnsTokenMetadataAndCookie() {
        webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values(HttpHeaders.SET_COOKIE, this::assertXsrfCookie)
                .expectBody()
                .jsonPath("$.data.headerName").isEqualTo("X-XSRF-TOKEN")
                .jsonPath("$.data.parameterName").exists()
                .jsonPath("$.data.token").isNotEmpty();
    }

    @Test
    void csrfEndpointIgnoresStaleBrowserAccessCookie() {
        given(authApplication.authenticateAccessToken("stale-access"))
                .willThrow(new RbacException("AUTH_TOKEN_INVALID", "access token is inactive"));

        webTestClient.get()
                .uri("/api/auth/csrf")
                .header("X-Client-Type", "browser")
                .cookie("CM_ACCESS_TOKEN", "stale-access")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.headerName").isEqualTo("X-XSRF-TOKEN");

        then(authApplication).should(never()).authenticateAccessToken("stale-access");
    }

    @Test
    void unsafeBrowserLoginWithoutCsrfIsForbidden() {
        webTestClient.post()
                .uri("/api/auth/login")
                .header("X-Client-Type", "browser")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidLoginBody())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH_CSRF_INVALID");
    }

    @Test
    void unsafeAuthCookieLoginWithoutBrowserHeaderOrCsrfIsForbidden() {
        webTestClient.post()
                .uri("/api/auth/login")
                .cookie("CM_ACCESS_TOKEN", "cookie-access")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidLoginBody())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH_CSRF_INVALID");
    }

    @Test
    void unsafeNonBrowserLoginWithoutCsrfReachesValidation() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidLoginBody())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void browserActivationWithoutCsrfIsForbidden() {
        webTestClient.post().uri("/api/auth/activation/complete")
                .header("X-Client-Type", "browser")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidActivationBody())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_CSRF_INVALID");
    }

    @Test
    void nonBrowserActivationIsPublicAndReachesValidation() {
        webTestClient.post().uri("/api/auth/activation/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidActivationBody())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void bearerPostWithoutCsrfReachesValidation() {
        given(authApplication.authenticateAccessToken("api-token"))
                .willReturn(new UsernamePasswordAuthenticationToken(
                        new RbacPrincipal(1L, "mario", Set.of("ROLE_ADMIN"), Set.of("api:demo"), "permission-v1"),
                        "api-token",
                        List.of()
                ));

        webTestClient.post()
                .uri("/api/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer api-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidLoginBody())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    private void assertXsrfCookie(List<String> setCookies) {
        assertThat(setCookies)
                .anySatisfy(cookie -> {
                    assertThat(cookie).startsWith("XSRF-TOKEN=");
                    assertThat(cookieAttributes(cookie)).contains("Path=/");
                    assertThat(cookieAttributes(cookie)).doesNotContain("Secure");
                });
    }

    private List<String> cookieAttributes(String cookie) {
        return List.of(cookie.split(";\\s*"));
    }

    private String invalidLoginBody() {
        return """
                {
                  "account": "",
                  "encryptedPassword": "",
                  "passwordKeyId": ""
                }
                """;
    }

    private String invalidActivationBody() {
        return """
                {"token":"","passwordKeyId":"","encryptedPassword":""}
                """;
    }

}
