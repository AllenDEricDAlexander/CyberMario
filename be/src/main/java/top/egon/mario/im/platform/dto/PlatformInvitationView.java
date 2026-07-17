package top.egon.mario.im.platform.dto;

import java.time.Instant;

public record PlatformInvitationView(
        Long invitationId,
        String surfaceType,
        Long surfaceId,
        Long channelId,
        String surfaceName,
        Long inviterUserId,
        String inviterDisplayName,
        String status,
        String message,
        Instant createdAt,
        Instant respondedAt) {
}
