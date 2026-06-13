package top.egon.mario.rbac.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.Set;

/**
 * Login response with dual tokens and current authorization snapshot.
 */
@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds,
        UserResponse user,
        Set<String> roleCodes,
        List<MenuTreeResponse> menus,
        Set<String> buttonCodes,
        Set<String> permissionCodes
) {
}
