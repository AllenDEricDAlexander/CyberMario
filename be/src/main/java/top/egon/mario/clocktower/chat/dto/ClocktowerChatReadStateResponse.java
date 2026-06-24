package top.egon.mario.clocktower.chat.dto;

import java.time.Instant;

public record ClocktowerChatReadStateResponse(
        Long readStateId,
        Long conversationId,
        Long userId,
        Long lastReadMessageSeq,
        Instant lastReadAt
) {
}
