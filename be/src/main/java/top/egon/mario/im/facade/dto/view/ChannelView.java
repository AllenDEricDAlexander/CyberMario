package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record ChannelView(
        Long id,
        String contextType,
        Long contextId,
        String channelKey,
        String name,
        Long ownerUserId,
        String visibility,
        String joinPolicy,
        String status,
        String announcement,
        Long mainConversationId,
        Integer memberCount,
        Instant lastActiveAt) {
}
