package top.egon.mario.agent.memory.service.model;

import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryDomain;

public record AgentMemoryMessageSource(
        AgentMemoryDomain memoryDomain,
        String memorySpaceId,
        ExternalChatPlatform platform,
        String connectorId,
        String conversationId,
        ExternalConversationType conversationType,
        String audienceKey,
        String externalEventId,
        String externalMessageId,
        String senderId,
        String senderDisplayName,
        boolean observedOnly
) {

    public AgentMemoryMessageSource {
        memoryDomain = memoryDomain == null ? AgentMemoryDomain.WEB_PRIVATE : memoryDomain;
    }

    public static AgentMemoryMessageSource webPrivate() {
        return new AgentMemoryMessageSource(AgentMemoryDomain.WEB_PRIVATE, null, null, null,
                null, null, null, null, null, null, null, false);
    }
}
