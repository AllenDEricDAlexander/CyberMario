package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record ConversationView(
        Long id,
        String conversationType,
        String ownerSurfaceType,
        Long ownerSurfaceId,
        String contextType,
        Long contextId,
        Long messageSeq,
        Long lastMessageId,
        Instant lastMessageAt,
        Instant lastActiveAt,
        String status) {
}
