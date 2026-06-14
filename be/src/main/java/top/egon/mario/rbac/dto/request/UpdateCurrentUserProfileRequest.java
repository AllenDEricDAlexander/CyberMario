package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Current-user request for updating self-editable profile fields.
 */
public record UpdateCurrentUserProfileRequest(
        @Size(max = 64) String nickname,
        @Size(max = 128) String email,
        @Size(max = 32) String mobile,
        @Size(max = 512) String avatarUrl
) {
}
