package top.egon.mario.rbac.dto.response;

import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.dto.enums.RbacStatus;

/**
 * Role payload returned by role management APIs.
 */
@Getter
@Setter
public class RoleResponse {

    private Long id;
    private String roleCode;
    private String roleName;
    private RbacStatus status;
    private int sortNo;
    private boolean builtIn;
    private String description;

}
