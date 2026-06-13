package top.egon.mario.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.dto.enums.RbacStatus;

/**
 * Management request for creating a role.
 */
@Getter
@Setter
public class CreateRoleRequest {
    @NotBlank
    private String roleCode;
    @NotBlank
    private String roleName;
    private RbacStatus status = RbacStatus.ENABLED;
    private int sortNo;
    private boolean builtIn;
    private String description;
}
