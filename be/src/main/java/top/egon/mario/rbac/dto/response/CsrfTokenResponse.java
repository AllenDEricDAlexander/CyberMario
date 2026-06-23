package top.egon.mario.rbac.dto.response;

/**
 * CSRF token metadata for browser clients.
 */
public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token
) {
}
