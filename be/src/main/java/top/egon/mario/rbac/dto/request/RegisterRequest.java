package top.egon.mario.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public registration request for creating a normal user account.
 */
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 64) String nickname,
        @Size(max = 128) String email,
        @Size(max = 32) String mobile,
        @Size(max = 512) String avatarUrl
) {
}
