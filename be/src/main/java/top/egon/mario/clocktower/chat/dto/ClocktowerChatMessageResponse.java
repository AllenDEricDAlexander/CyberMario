package top.egon.mario.clocktower.chat.dto;

import java.time.Instant;

public record ClocktowerChatMessageResponse(
        Long messageId,
        Long conversationId,
        Long senderUserId,
        Long messageSeq,
        String messageType,
        String content,
        Instant sentAt
) {
}
