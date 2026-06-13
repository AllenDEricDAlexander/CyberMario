package top.egon.mario.rbac.service.resource;

import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

/**
 * Supplies RBAC resource declarations without writing RBAC tables directly.
 */
public interface RbacResourceProvider {

    String appCode();

    List<RbacResourceSeed> resources();

    default List<RbacRolePresetSeed> rolePresets() {
        return List.of();
    }

}
