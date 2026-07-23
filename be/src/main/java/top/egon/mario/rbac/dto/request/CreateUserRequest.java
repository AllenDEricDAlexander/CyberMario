package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Management request for creating a pending user account.
 */
@Getter
@Setter
public class CreateUserRequest {
    @NotBlank
    private String accountNo;
    @NotBlank
    private String username;
    private String nickname;
    @NotBlank
    @Email
    private String email;
    private String mobile;
    private String avatarUrl;
    private String remark;
    private Set<Long> roleIds = Set.of();
}
