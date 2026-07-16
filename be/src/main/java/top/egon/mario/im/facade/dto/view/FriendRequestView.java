package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record FriendRequestView(
        Long id,
        Long requesterUserId,
        Long recipientUserId,
        Long peerUserId,
        String peerAccountNo,
        String peerDisplayName,
        String peerAvatarUrl,
        boolean peerAvailable,
        String status,
        String requestMessage,
        Instant requestedAt,
        Instant decidedAt,
        String decisionReason) {
}
