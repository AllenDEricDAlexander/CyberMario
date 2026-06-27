package top.egon.mario.im.facade.dto.view;

import java.time.Instant;

public record MessageView(
        Long id,
        Long conversationId,
        Long senderUserId,
        Long messageSeq,
        String clientMsgId,
        String messageType,
        String content,
        String payloadJson,
        String status,
        Instant sentAt,
        Instant editedAt,
        Instant deletedAt,
        String metadataJson) {
}
