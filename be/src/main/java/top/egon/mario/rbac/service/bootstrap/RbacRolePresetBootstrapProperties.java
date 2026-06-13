package top.egon.mario.rbac.service.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings used to seed built-in RBAC role presets.
 */
@ConfigurationProperties(prefix = "mario.rbac.role-presets")
public record RbacRolePresetBootstrapProperties(
        boolean enabled,
        SyncMode syncMode
) {

    public RbacRolePresetBootstrapProperties {
        syncMode = syncMode == null ? SyncMode.CREATE_ONLY : syncMode;
    }

    public enum SyncMode {
        CREATE_ONLY,
        FORCE_SYNC
    }

}
