package top.egon.mario.agent.memory.dto.request;

import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;

/**
 * Query parameters for the current user's memory sessions.
 */
public record AgentMemorySessionQuery(
        AgentMemoryEntryType entryType,
        AgentMemorySessionStatus status
) {
}
