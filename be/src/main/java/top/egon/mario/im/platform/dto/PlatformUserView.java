package top.egon.mario.im.platform.dto;

public record PlatformUserView(
        Long userId,
        String accountNo,
        String displayName,
        String avatarUrl) {
}
