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
        String traceId,
        String memorySpaceId
) {

    public AgentLongTermMemoryMergeRequest(Long userId, String username,
                                           AgentLongTermMemoryScopeType scopeType,
                                           String mergedMarkdown, String changeSummary,
                                           String sourceSessionIds, String sourceMessageIds,
                                           String requestId, String traceId) {
        this(userId, username, scopeType, mergedMarkdown, changeSummary, sourceSessionIds,
                sourceMessageIds, requestId, traceId, null);
    }
}
