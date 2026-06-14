package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.dto.request.LoginRequest;
import top.egon.mario.rbac.dto.request.RefreshTokenRequest;
import top.egon.mario.rbac.dto.response.LoginResponse;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.service.resource.annotation.RbacApi;
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

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:login", name = "RBAC 用户登录", publicFlag = true)
    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        return blocking(() -> authApplication.login(request, clientIp(exchange), userAgent(exchange)));
    }

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:refresh", name = "RBAC 刷新令牌", publicFlag = true)
    @PostMapping("/refresh")
    public Mono<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request, ServerWebExchange exchange) {
        return blocking(() -> authApplication.refresh(request.refreshToken(), clientIp(exchange), userAgent(exchange)));
    }

    @RbacApi(appCode = "rbac", code = "api:rbac:auth:self", name = "RBAC 认证自助接口",
            method = "ANY", pattern = "/api/auth/**", matcher = ApiMatcherType.ANT, risk = ApiRiskLevel.MEDIUM)
    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        return blockingVoid(() -> authApplication.logout(request.refreshToken()));
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

}
