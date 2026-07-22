package top.egon.mario.agent.externalim.model;

import java.time.Instant;

public record ChatInvocation(
        ChatSource source,
        String message,
        Long ownerUserId,
        String ownerUsername,
        String webSessionId,
        String memorySpaceId,
        ExternalChatPlatform platform,
        String connectorId,
        String conversationId,
        ExternalConversationType conversationType,
        String audienceKey,
        ExternalSender sender,
        ExternalMessageType messageType,
        boolean mentionedAgent,
        boolean repliedToAgentMessage,
        String eventId,
        String messageId,
        Instant occurredAt
) {

    public boolean externalIm() {
        return source == ChatSource.EXTERNAL_IM;
    }

    public static ChatInvocation web(String message, Long ownerUserId, String ownerUsername,
                                     String webSessionId, String memorySpaceId) {
        return new ChatInvocation(ChatSource.WEB, message, ownerUserId, ownerUsername, webSessionId,
                memorySpaceId, null, null, null, null, null, null, ExternalMessageType.TEXT,
                false, false, null, null, Instant.now());
    }
}
