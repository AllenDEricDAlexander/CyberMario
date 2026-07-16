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
}
