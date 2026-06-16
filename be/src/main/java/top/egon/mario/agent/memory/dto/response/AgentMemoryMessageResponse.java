package top.egon.mario.agent.memory.dto.response;

import top.egon.mario.agent.memory.po.AgentMemoryMessagePo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;

import java.time.Instant;

/**
 * Persisted memory message visible to the owning user.
 */
public record AgentMemoryMessageResponse(
        Long id,
        String sessionId,
        AgentMemoryEntryType entryType,
        int seqNo,
        int turnNo,
        AgentMemoryMessageRole role,
        AgentMemoryMessageType messageType,
        String content,
        Integer contentChars,
        String sourceRefsJson,
        String traceId,
        String requestId,
        Instant createdAt
) {

    public static AgentMemoryMessageResponse from(AgentMemoryMessagePo message) {
        return new AgentMemoryMessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getEntryType(),
                message.getSeqNo(),
                message.getTurnNo(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                message.getContentChars(),
                message.getSourceRefsJson(),
                message.getTraceId(),
                message.getRequestId(),
                message.getCreatedAt()
        );
    }
}
