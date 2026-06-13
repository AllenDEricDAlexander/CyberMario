package top.egon.mario.rbac.service.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Settings used to seed the first built-in RBAC administrator.
 */
@ConfigurationProperties(prefix = "mario.rbac.bootstrap.admin")
public record RbacAdminBootstrapProperties(
        boolean enabled,
        String username,
        String password,
        boolean requirePasswordChange
) {

    public RbacAdminBootstrapProperties {
        username = StringUtils.hasText(username) ? username.trim().toLowerCase(Locale.ROOT) : "admin";
        password = password == null ? "" : password;
    }

}
