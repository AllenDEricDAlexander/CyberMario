package top.egon.mario.rbac.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.application.RbacAuthApplication;
import top.egon.mario.rbac.dto.LoginRequest;
import top.egon.mario.rbac.dto.LoginResponse;
import top.egon.mario.rbac.dto.RefreshTokenRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Authentication endpoints for RBAC users and JWT dual-token lifecycle.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController extends ReactiveRbacSupport {

    private final RbacAuthApplication authApplication;

    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        return blocking(() -> authApplication.login(request, clientIp(exchange), userAgent(exchange)));
    }

    @PostMapping("/refresh")
    public Mono<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request, ServerWebExchange exchange) {
        return blocking(() -> authApplication.refresh(request.refreshToken(), clientIp(exchange), userAgent(exchange)));
    }

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
