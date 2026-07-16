package top.egon.mario.rbac.dto.response;

/**
 * Safe user projection for ordinary authenticated user discovery.
 */
public record UserDirectoryItemResponse(
        Long userId,
        String accountNo,
        String displayName,
        String avatarUrl) {
}
