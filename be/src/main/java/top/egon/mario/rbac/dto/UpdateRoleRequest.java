package top.egon.mario.rbac.dto;

import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.po.RbacStatus;

/**
 * Management request for updating role metadata.
 */
@Getter
@Setter
public class UpdateRoleRequest {
    private String roleName;
    private RbacStatus status;
    private Integer sortNo;
    private Boolean builtIn;
    private String description;
}
