package top.egon.mario.agent.memory.dto.request;

import jakarta.validation.constraints.Size;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;

/**
 * Request body for creating or updating a user-owned memory session.
 */
public record AgentMemorySessionRequest(
        AgentMemoryEntryType entryType,
        @Size(max = 256) String title,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled
) {
}
