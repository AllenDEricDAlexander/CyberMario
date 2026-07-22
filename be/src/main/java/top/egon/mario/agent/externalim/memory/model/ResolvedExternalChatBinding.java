package top.egon.mario.agent.externalim.memory.model;

import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;

public record ResolvedExternalChatBinding(
        Long ownerUserId,
        String memorySpaceId,
        ExternalChatPlatform platform,
        String connectorId,
        String conversationId,
        ExternalConversationType conversationType,
        String audienceKey
) {
}
