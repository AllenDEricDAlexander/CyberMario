package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record ChannelView(
        Long id,
        String contextType,
        Long contextId,
        String channelKey,
        String joinKey,
        String name,
        Long ownerUserId,
        String visibility,
        String joinPolicy,
        String status,
        String announcement,
        Long mainConversationId,
        Integer memberCount,
        Instant lastActiveAt,
        String membershipStatus,
        String memberRole,
        Boolean canRead,
        Boolean canPost) {

    public ChannelView(Long id,
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
        this(id, contextType, contextId, channelKey, name, ownerUserId, visibility, joinPolicy, status,
                announcement, mainConversationId, memberCount, lastActiveAt, null, null, true, false);
    }

    public ChannelView(Long id,
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
                       Instant lastActiveAt,
                       String membershipStatus,
                       String memberRole,
                       Boolean canRead,
                       Boolean canPost) {
        this(id, contextType, contextId, channelKey, null, name, ownerUserId, visibility, joinPolicy, status,
                announcement, mainConversationId, memberCount, lastActiveAt, membershipStatus, memberRole,
                canRead, canPost);
    }
}
