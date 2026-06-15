package top.egon.mario.agent.service.model;

import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;

/**
 * Message content appended to an agent conversation audit record.
 */
public record AgentConversationMessageRecord(
        AgentConversationRole role,
        AgentConversationMessageType messageType,
        String content
) {
}
