package top.egon.mario.rbac.service;

import jakarta.validation.constraints.NotNull;
import top.egon.mario.rbac.service.model.ButtonApiLink;

import java.util.Collection;
import java.util.Set;

/**
 * Applies RBAC authorization policies that derive API permissions from button mappings.
 */
public interface RolePermissionMergeService {

    Set<Long> mergeButtonApis(@NotNull Set<Long> submittedPermissionIds, boolean syncButtonApis,
                              Collection<ButtonApiLink> buttonApiLinks);

}
