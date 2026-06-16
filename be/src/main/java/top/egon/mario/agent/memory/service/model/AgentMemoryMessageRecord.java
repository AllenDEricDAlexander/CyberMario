package top.egon.mario.agent.memory.service.model;

import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageType;

public record AgentMemoryMessageRecord(
        String sessionId,
        Long userId,
        AgentMemoryEntryType entryType,
        int turnNo,
        AgentMemoryMessageRole role,
        AgentMemoryMessageType messageType,
        String content,
        String sourceRefsJson,
        String traceId,
        String requestId
) {
}
