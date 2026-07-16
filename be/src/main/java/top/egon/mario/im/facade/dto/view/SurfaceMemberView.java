package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record SurfaceMemberView(
        Long membershipId,
        Long userId,
        String accountNo,
        String displayName,
        String avatarUrl,
        boolean available,
        String memberRole,
        String status,
        Instant mutedUntil,
        Instant joinedAt) {
}
