package top.egon.mario.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import top.egon.mario.rbac.service.security.DynamicAuthorizationManager;
import top.egon.mario.rbac.service.security.JwtAuthenticationWebFilter;
import top.egon.mario.rbac.service.security.JwtProperties;

/**
 * WebFlux security configuration for RBAC JWT authentication and dynamic API authorization.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class RbacSecurityConfig {

    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;
    private final DynamicAuthorizationManager dynamicAuthorizationManager;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/login", "/api/auth/refresh", "/actuator/health", "/demo/chat/**").permitAll()
                        .anyExchange().access(dynamicAuthorizationManager)
                )
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

}
