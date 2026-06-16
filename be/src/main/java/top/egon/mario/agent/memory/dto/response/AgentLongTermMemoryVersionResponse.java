package top.egon.mario.agent.memory.dto.response;

import top.egon.mario.agent.memory.po.AgentLongTermMemoryVersionPo;

import java.time.Instant;

/**
 * Immutable long-term memory version snapshot.
 */
public record AgentLongTermMemoryVersionResponse(
        Long id,
        Long memoryId,
        int versionNo,
        String contentMarkdown,
        int contentChars,
        String changeSummary,
        String sourceSessionIds,
        String sourceMessageIds,
        String requestId,
        String traceId,
        Instant createdAt
) {

    public static AgentLongTermMemoryVersionResponse from(AgentLongTermMemoryVersionPo version) {
        return new AgentLongTermMemoryVersionResponse(
                version.getId(),
                version.getMemoryId(),
                version.getVersionNo(),
                version.getContentMarkdown(),
                version.getContentChars(),
                version.getChangeSummary(),
                version.getSourceSessionIds(),
                version.getSourceMessageIds(),
                version.getRequestId(),
                version.getTraceId(),
                version.getCreatedAt()
        );
    }
}
