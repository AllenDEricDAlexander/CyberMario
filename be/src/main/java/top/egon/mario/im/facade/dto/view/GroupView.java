package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record GroupView(
        Long id,
        Long channelId,
        String contextType,
        Long contextId,
        String groupKey,
        String name,
        Long ownerUserId,
        String joinPolicy,
        String status,
        String announcement,
        Long conversationId,
        Integer memberCount,
        Instant lastActiveAt,
        String membershipStatus,
        String memberRole,
        Boolean canRead,
        Boolean canPost) {

    public GroupView(Long id,
                     Long channelId,
                     String contextType,
                     Long contextId,
                     String groupKey,
                     String name,
                     Long ownerUserId,
                     String joinPolicy,
                     String status,
                     String announcement,
                     Long conversationId,
                     Integer memberCount,
                     Instant lastActiveAt) {
        this(id, channelId, contextType, contextId, groupKey, name, ownerUserId, joinPolicy, status,
                announcement, conversationId, memberCount, lastActiveAt, null, null, false, false);
    }
}
