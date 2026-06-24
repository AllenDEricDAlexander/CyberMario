package top.egon.mario.clocktower.chat.dto;

import java.time.Instant;

public record ClocktowerChatConversationResponse(
        Long conversationId,
        Long roomId,
        Long gameId,
        String channelKey,
        String groupKey,
        String conversationType,
        String participantKey,
        Long messageSeq,
        Instant lastMessageAt
) {
}
