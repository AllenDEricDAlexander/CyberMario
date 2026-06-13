package top.egon.mario.rbac.service.resource.model;

import java.util.List;

/**
 * Managed role preset declaration whose synchronization is append-only for grants.
 */
public record RbacRolePresetSeed(
        String appCode,
        String roleCode,
        String roleName,
        String description,
        int sortNo,
        List<String> permissionCodes,
        RbacResourceSource source
) {

    public RbacRolePresetSeed {
        permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
    }

}
