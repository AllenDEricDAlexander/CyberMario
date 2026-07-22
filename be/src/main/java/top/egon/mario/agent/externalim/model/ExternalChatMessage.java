package top.egon.mario.agent.externalim.model;

import java.time.Instant;

public record ExternalChatMessage(
        String eventId,
        String messageId,
        ExternalChatPlatform platform,
        String connectorId,
        String conversationId,
        ExternalConversationType conversationType,
        String audienceKey,
        ExternalSender sender,
        ExternalMessageType messageType,
        String text,
        boolean mentionedAgent,
        boolean repliedToAgentMessage,
        Instant occurredAt
) {
}
