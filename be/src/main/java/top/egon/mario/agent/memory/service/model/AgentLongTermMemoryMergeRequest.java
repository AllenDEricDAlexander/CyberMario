package top.egon.mario.agent.memory.service.model;

import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;

public record AgentLongTermMemoryMergeRequest(
        Long userId,
        String username,
        AgentLongTermMemoryScopeType scopeType,
        String mergedMarkdown,
        String changeSummary,
        String sourceSessionIds,
        String sourceMessageIds,
        String requestId,
        String traceId
) {
}
