package top.egon.mario.rbac.dto.response;

import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.dto.enums.RbacStatus;

import java.time.Instant;

/**
 * User payload returned to management and current-user APIs.
 */
@Getter
@Setter
public class UserResponse {

    private Long id;
    private String accountNo;
    private String username;
    private String nickname;
    private String email;
    private String mobile;
    private String avatarUrl;
    private RbacStatus status;
    private boolean locked;
    private boolean passwordExpired;
    private Instant lastLoginAt;
    private String remark;

}
