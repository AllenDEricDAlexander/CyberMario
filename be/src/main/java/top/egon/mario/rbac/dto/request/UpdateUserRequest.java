package top.egon.mario.rbac.dto.request;

import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.dto.enums.RbacStatus;

/**
 * Management request for updating a user's profile fields.
 */
@Getter
@Setter
public class UpdateUserRequest {
    private String nickname;
    private String email;
    private String mobile;
    private String avatarUrl;
    private RbacStatus status;
    private Boolean locked;
    private Boolean passwordExpired;
    private String remark;
}
