package top.egon.mario.agent.memory.dto.response;

import top.egon.mario.agent.memory.po.AgentLongTermMemoryPo;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryStatus;

import java.time.Instant;

/**
 * Current long-term Markdown memory for the owning user.
 */
public record AgentLongTermMemoryResponse(
        AgentLongTermMemoryScopeType scopeType,
        String contentMarkdown,
        int contentChars,
        Long activeVersionId,
        AgentLongTermMemoryStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static AgentLongTermMemoryResponse from(AgentLongTermMemoryPo memory) {
        return new AgentLongTermMemoryResponse(
                memory.getScopeType(),
                memory.getContentMarkdown(),
                memory.getContentChars(),
                memory.getActiveVersionId(),
                memory.getStatus(),
                memory.getCreatedAt(),
                memory.getUpdatedAt()
        );
    }
}
