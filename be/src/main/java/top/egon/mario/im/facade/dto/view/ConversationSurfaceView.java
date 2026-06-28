package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record ConversationSurfaceView(
        Long conversationId,
        String conversationType,
        String ownerSurfaceType,
        Long ownerSurfaceId,
        String contextType,
        Long contextId,
        Long messageSeq,
        Instant lastMessageAt,
        String status,
        Long channelId,
        String channelKey,
        Long groupId,
        String groupKey) {
}
