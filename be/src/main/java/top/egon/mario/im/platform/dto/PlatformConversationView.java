package top.egon.mario.im.platform.dto;

import top.egon.mario.im.facade.dto.view.MessageView;

import java.time.Instant;

public record PlatformConversationView(
        Long conversationId,
        String conversationType,
        String displayType,
        String title,
        String avatarUrl,
        Long peerUserId,
        String ownerSurfaceType,
        Long surfaceId,
        String surfaceKey,
        String joinKey,
        Long channelId,
        String membershipStatus,
        String memberRole,
        boolean canRead,
        boolean canPost,
        Long messageSeq,
        Long lastMessageId,
        Instant lastMessageAt,
        MessageView lastMessage,
        PlatformUserView lastMessageSender,
        Instant lastActiveAt,
        String status,
        Long unreadCount) {

    public PlatformConversationView(
            Long conversationId,
            String conversationType,
            String displayType,
            String title,
            String avatarUrl,
            Long peerUserId,
            String ownerSurfaceType,
            Long surfaceId,
            String surfaceKey,
            Long channelId,
            String membershipStatus,
            String memberRole,
            boolean canRead,
            boolean canPost,
            Long messageSeq,
            Long lastMessageId,
            Instant lastMessageAt,
            MessageView lastMessage,
            PlatformUserView lastMessageSender,
            Instant lastActiveAt,
            String status,
            Long unreadCount
    ) {
        this(conversationId, conversationType, displayType, title, avatarUrl, peerUserId, ownerSurfaceType,
                surfaceId, surfaceKey, null, channelId, membershipStatus, memberRole, canRead, canPost,
                messageSeq, lastMessageId, lastMessageAt, lastMessage, lastMessageSender, lastActiveAt,
                status, unreadCount);
    }
}
