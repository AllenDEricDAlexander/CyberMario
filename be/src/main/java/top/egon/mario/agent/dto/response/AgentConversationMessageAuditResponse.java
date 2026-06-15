package top.egon.mario.agent.dto.response;

import top.egon.mario.agent.po.enums.AgentConversationMessageType;
import top.egon.mario.agent.po.enums.AgentConversationRole;

import java.time.Instant;

/**
 * Message row in a super-admin agent conversation audit detail view.
 */
public record AgentConversationMessageAuditResponse(
        Long id,
        Long conversationAuditId,
        int seqNo,
        AgentConversationRole role,
        AgentConversationMessageType messageType,
        String content,
        Integer contentChars,
        Instant createdAt
) {
}
