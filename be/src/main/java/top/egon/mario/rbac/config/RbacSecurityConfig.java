package top.egon.mario.rbac.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import reactor.core.publisher.Mono;
import top.egon.mario.rbac.service.security.DynamicAuthorizationManager;
import top.egon.mario.rbac.service.security.JwtAuthenticationWebFilter;
import top.egon.mario.rbac.service.security.JwtProperties;
import top.egon.mario.rbac.service.security.RbacPublicApiPolicy;

import java.util.Map;

/**
 * WebFlux security configuration for RBAC JWT authentication and dynamic API authorization.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
@Slf4j
public class RbacSecurityConfig {

    private static final String ARGON2ID = "argon2id";
    private static final String BCRYPT = "bcrypt";
    private static final int ARGON2_MEMORY_KIB = 19 * 1024;
    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;
    private final DynamicAuthorizationManager dynamicAuthorizationManager;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.POST, RbacPublicApiPolicy.PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .pathMatchers(HttpMethod.GET, RbacPublicApiPolicy.PUBLIC_ACTUATOR_ENDPOINTS).permitAll()
                        .anyExchange().access(dynamicAuthorizationManager)
                )
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
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
