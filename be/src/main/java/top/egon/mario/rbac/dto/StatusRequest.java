package top.egon.mario.rbac.dto;

import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.po.PermissionStatus;
import top.egon.mario.rbac.po.RbacStatus;

/**
 * Generic status update request for users, roles and permissions.
 */
@Getter
@Setter
public class StatusRequest {
    private RbacStatus rbacStatus;
    private PermissionStatus permissionStatus;
}
