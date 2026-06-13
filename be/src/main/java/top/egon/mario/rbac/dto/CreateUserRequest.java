package top.egon.mario.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.dto.enums.RbacStatus;

import java.util.Set;

/**
 * Management request for creating a user.
 */
@Getter
@Setter
public class CreateUserRequest {
    @NotBlank
    private String username;
    private String nickname;
    private String email;
    private String mobile;
    private String avatarUrl;
    @NotBlank
    private String initialPassword;
    private RbacStatus status = RbacStatus.ENABLED;
    private String remark;
    private Set<Long> roleIds = Set.of();
}
