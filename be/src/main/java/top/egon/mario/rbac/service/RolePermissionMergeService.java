package top.egon.mario.rbac.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.service.model.ButtonApiLink;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Applies RBAC authorization policies that derive API permissions from button mappings.
 */
@Component
@Slf4j
public class RolePermissionMergeService {

    public Set<Long> mergeButtonApis(Set<Long> submittedPermissionIds, boolean syncButtonApis,
                                     Collection<ButtonApiLink> buttonApiLinks) {
        Set<Long> mergedPermissionIds = new HashSet<>(submittedPermissionIds);
        if (!syncButtonApis || buttonApiLinks == null || buttonApiLinks.isEmpty()) {
            return mergedPermissionIds;
        }
        Set<Long> submitted = new HashSet<>(submittedPermissionIds);
        for (ButtonApiLink link : buttonApiLinks) {
            if (submitted.contains(link.buttonPermissionId())) {
                mergedPermissionIds.add(link.apiPermissionId());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("rbac role permissions merged, submittedCount={}, mergedCount={}",
                    submittedPermissionIds.size(), mergedPermissionIds.size());
        }
        return mergedPermissionIds;
    }

}
