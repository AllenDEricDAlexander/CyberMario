package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record FriendView(
        Long friendshipId,
        Long friendUserId,
        String accountNo,
        String displayName,
        String avatarUrl,
        String remark,
        boolean available,
        Instant activatedAt) {
}
