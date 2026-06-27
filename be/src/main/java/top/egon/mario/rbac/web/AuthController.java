package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.dto.request.LoginRequest;
import top.egon.mario.rbac.dto.request.RefreshTokenRequest;
import top.egon.mario.rbac.dto.request.RegisterRequest;
import top.egon.mario.rbac.dto.response.CsrfTokenResponse;
import top.egon.mario.rbac.dto.response.LoginResponse;
import top.egon.mario.rbac.dto.response.PasswordEncryptionKeyResponse;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
import top.egon.mario.rbac.service.security.BrowserAuthCookieService;
import top.egon.mario.rbac.service.security.PasswordTransportEncryptionService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Authentication endpoints for RBAC users and JWT dual-token lifecycle.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Slf4j
@Validated
public class AuthController extends ReactiveRbacSupport {

    private final RbacAuthApplication authApplication;
    private final BrowserAuthCookieService browserAuthCookieService;
    private final PasswordTransportEncryptionService passwordTransportEncryptionService;

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:login", name = "RBAC 用户登录", publicFlag = true)
    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        return blocking(() -> authApplication.login(request, clientIp(exchange), userAgent(exchange)))
                .map(response -> browserTokenResponse(response, exchange));
    }

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:register", name = "RBAC 用户注册", publicFlag = true)
    @PostMapping("/register")
    public Mono<ApiResponse<LoginResponse>> register(@Valid @RequestBody RegisterRequest request, ServerWebExchange exchange) {
        return blocking(() -> authApplication.register(request, clientIp(exchange), userAgent(exchange)))
                .map(response -> browserTokenResponse(response, exchange));
    }

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:password-key", name = "RBAC 密码传输公钥", publicFlag = true)
    @GetMapping("/password-key")
    public Mono<ApiResponse<PasswordEncryptionKeyResponse>> passwordKey() {
        return blocking(passwordTransportEncryptionService::currentKey);
    }

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:refresh", name = "RBAC 刷新令牌", publicFlag = true)
    @PostMapping("/refresh")
    public Mono<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody(required = false) RefreshTokenRequest request,
                                                    ServerWebExchange exchange) {
        return blocking(() -> authApplication.refresh(refreshToken(request, exchange), clientIp(exchange), userAgent(exchange)))
                .map(response -> browserTokenResponse(response, exchange));
    }

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:csrf", name = "RBAC CSRF 令牌", publicFlag = true)
    @GetMapping("/csrf")
    public Mono<ApiResponse<CsrfTokenResponse>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> csrfToken = exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());
        return Mono.deferContextual(contextView -> csrfToken
                .map(token -> new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken()))
                .map(response -> ApiResponse.ok(response, TraceContext.traceId(contextView))));
    }

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:self", name = "RBAC 认证自助接口",
            method = "ANY", pattern = "/api/auth/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.MEDIUM)
    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(@Valid @RequestBody(required = false) RefreshTokenRequest request,
                                          ServerWebExchange exchange) {
        boolean browserClient = browserAuthCookieService.isBrowserClient(exchange);
        String refreshToken = refreshToken(request, exchange);
        Mono<ApiResponse<Void>> response = blockingVoid(() -> {
            if (!browserClient || (refreshToken != null && !refreshToken.isBlank())) {
                authApplication.logout(refreshToken);
            }
        });
        if (!browserClient) {
            return response;
        }
        return response
                .doOnSuccess(ignored -> browserAuthCookieService.clearTokenCookies(exchange))
                .doOnError(error -> browserAuthCookieService.clearTokenCookies(exchange));
    }

    @GetMapping("/me")
    public Mono<ApiResponse<LoginResponse>> me(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> authApplication.currentUser(principal.userId()));
    }

    private String userAgent(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }

    private String clientIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() == null ? null : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    private String refreshToken(RefreshTokenRequest request, ServerWebExchange exchange) {
        if (browserAuthCookieService.isBrowserClient(exchange)) {
            return browserAuthCookieService.readRefreshToken(exchange);
        }
        return request == null ? null : request.refreshToken();
    }

    private ApiResponse<LoginResponse> browserTokenResponse(ApiResponse<LoginResponse> response, ServerWebExchange exchange) {
        if (!browserAuthCookieService.isBrowserClient(exchange)) {
            return response;
        }
        browserAuthCookieService.writeTokenCookies(exchange, response.data());
        return new ApiResponse<>(
                response.code(),
                response.message(),
                browserAuthCookieService.withoutBodyTokens(response.data()),
                response.traceId(),
                response.timestamp()
        );
    }

}
