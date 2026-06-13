package top.egon.mario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Optional;

/**
 * Enables JPA auditing and resolves the current RBAC user when available.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof RbacPrincipal principal) {
                return Optional.of(principal.userId());
            }
            return Optional.of(0L);
        };
    }

}
