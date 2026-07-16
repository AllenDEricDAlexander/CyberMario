package top.egon.mario.im.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls assignment of the platform IM user role to existing accounts.
 */
@ConfigurationProperties(prefix = "mario.im.platform.user-role-backfill")
public record ImUserRoleBackfillProperties(Boolean enabled) {

    public ImUserRoleBackfillProperties {
        enabled = enabled == null || enabled;
    }
}
