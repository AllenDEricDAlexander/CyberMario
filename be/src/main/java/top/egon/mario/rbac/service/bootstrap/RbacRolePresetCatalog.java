package top.egon.mario.rbac.service.bootstrap;

import java.util.List;

/**
 * Static role presets mapped to permission codes that may be seeded by feature bootstraps.
 */
public class RbacRolePresetCatalog {

    public List<RolePresetSeed> roles() {
        return List.of(
                new RolePresetSeed(
                        "RBAC_ADMIN",
                        "RBAC Administrator",
                        "System role for RBAC management.",
                        10,
                        List.of("api:rbac:admin:*", "api:rbac:auth:self")
                ),
                new RolePresetSeed(
                        "CHAT_USER",
                        "Chat User",
                        "System role for the agent chat console.",
                        50,
                        List.of("api:chat:stream", "api:rbac:auth:self")
                )
        );
    }

    public record RolePresetSeed(
            String roleCode,
            String roleName,
            String description,
            int sortNo,
            List<String> permissionCodes
    ) {
    }

}
