package top.egon.mario.agent.memory.dto.response;

import top.egon.mario.agent.memory.po.AgentMemorySessionPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;

import java.time.Instant;

/**
 * Current-user memory session summary.
 */
public record AgentMemorySessionResponse(
        String sessionId,
        AgentMemoryEntryType entryType,
        String title,
        AgentMemorySessionStatus status,
        boolean memoryEnabled,
        boolean longTermExtractionEnabled,
        int shortTermWindowTurns,
        Instant lastActiveAt,
        Instant releasedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static AgentMemorySessionResponse from(AgentMemorySessionPo session) {
        return new AgentMemorySessionResponse(
                session.getSessionId(),
                session.getEntryType(),
                session.getTitle(),
                session.getStatus(),
                session.isMemoryEnabled(),
                session.isLongTermExtractionEnabled(),
                session.getShortTermWindowTurns(),
                session.getLastActiveAt(),
                session.getReleasedAt(),
                session.getArchivedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
