package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record JoinRequestView(
        Long joinRequestId,
        Long userId,
        String accountNo,
        String displayName,
        String avatarUrl,
        boolean available,
        String status,
        Instant requestedAt) {
}
