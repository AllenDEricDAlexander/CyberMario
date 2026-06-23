package top.egon.mario.rbac.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import top.egon.mario.rbac.service.security.BrowserAuthCookieProperties;
import top.egon.mario.rbac.service.security.DynamicAuthorizationManager;
import top.egon.mario.rbac.service.security.JwtAuthenticationWebFilter;
import top.egon.mario.rbac.service.security.JwtProperties;
import top.egon.mario.rbac.service.security.RbacPublicApiPolicy;
import top.egon.mario.rbac.service.security.RbacSecurityExceptionHandler;

import java.util.Map;
import java.util.Set;

/**
 * WebFlux security configuration for RBAC JWT authentication and dynamic API authorization.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({JwtProperties.class, BrowserAuthCookieProperties.class})
@Slf4j
public class RbacSecurityConfig {

    private static final String ARGON2ID = "argon2id";
    private static final String BCRYPT = "bcrypt";
    private static final int ARGON2_MEMORY_KIB = 19 * 1024;
    private static final Set<HttpMethod> CSRF_SAFE_METHODS = Set.of(
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS,
            HttpMethod.TRACE
    );
    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;
    private final DynamicAuthorizationManager dynamicAuthorizationManager;
    private final RbacSecurityExceptionHandler securityExceptionHandler;
    private final BrowserAuthCookieProperties browserCookieProperties;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .requireCsrfProtectionMatcher(this::requiresCsrfProtection)
                        .accessDeniedHandler(securityExceptionHandler))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.POST, RbacPublicApiPolicy.PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .pathMatchers(HttpMethod.GET, RbacPublicApiPolicy.PUBLIC_AUTH_CSRF_ENDPOINTS).permitAll()
                        .pathMatchers(HttpMethod.GET, RbacPublicApiPolicy.PUBLIC_ACTUATOR_ENDPOINTS).permitAll()
                        .anyExchange().access(dynamicAuthorizationManager)
                )
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private ServerCsrfTokenRepository csrfTokenRepository() {
        CookieServerCsrfTokenRepository repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(browserCookieProperties.isSecure()));
        return repository;
    }

    private Mono<MatchResult> requiresCsrfProtection(ServerWebExchange exchange) {
        if (isSafeMethod(exchange) || hasBearerAuthorization(exchange) || !browserCookieProperties.isEnabled()) {
            return MatchResult.notMatch();
        }
        if (isBrowserClient(exchange) || hasBrowserAuthCookie(exchange)) {
            return MatchResult.match();
        }
        return MatchResult.notMatch();
    }

    private boolean isSafeMethod(ServerWebExchange exchange) {
        return CSRF_SAFE_METHODS.contains(exchange.getRequest().getMethod());
    }

    private boolean hasBearerAuthorization(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith("Bearer ");
    }

    private boolean isBrowserClient(ServerWebExchange exchange) {
        String clientType = exchange.getRequest().getHeaders().getFirst(browserCookieProperties.getBrowserHeaderName());
        return clientType != null && clientType.equalsIgnoreCase(browserCookieProperties.getBrowserHeaderValue());
    }

    private boolean hasBrowserAuthCookie(ServerWebExchange exchange) {
        return hasCookie(exchange, browserCookieProperties.getAccessCookieName())
                || hasCookie(exchange, browserCookieProperties.getRefreshCookieName());
    }

    private boolean hasCookie(ServerWebExchange exchange, String cookieName) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
        return cookie != null;
    }

    /**
     * Disables Spring Boot's generated reactive user while RBAC keeps authentication in the JWT filter.
     */
    @Bean
    public ReactiveUserDetailsService reactiveUserDetailsService() {
        return username -> Mono.empty();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(ARGON2ID, Map.of(
                ARGON2ID, new Argon2PasswordEncoder(16, 32, 1, ARGON2_MEMORY_KIB, 2),
                BCRYPT, new BCryptPasswordEncoder(12)
        ));
        passwordEncoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return passwordEncoder;
    }

}
