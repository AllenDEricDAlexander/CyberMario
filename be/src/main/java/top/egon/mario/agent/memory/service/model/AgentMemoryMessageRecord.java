package top.egon.mario.agent.memory.service.model;

import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageRole;
import top.egon.mario.agent.memory.po.enums.AgentMemoryMessageStatus;
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
        String requestId,
        AgentMemoryMessageStatus messageStatus,
        String errorCode,
        String errorMessage,
        String metadataJson,
        AgentMemoryMessageSource source
) {

    public AgentMemoryMessageRecord {
        source = source == null ? AgentMemoryMessageSource.webPrivate() : source;
    }

    public AgentMemoryMessageRecord(String sessionId, Long userId, AgentMemoryEntryType entryType,
                                    int turnNo, AgentMemoryMessageRole role,
                                    AgentMemoryMessageType messageType, String content,
                                    String sourceRefsJson, String traceId, String requestId,
                                    AgentMemoryMessageStatus messageStatus, String errorCode,
                                    String errorMessage, String metadataJson) {
        this(sessionId, userId, entryType, turnNo, role, messageType, content, sourceRefsJson,
                traceId, requestId, messageStatus, errorCode, errorMessage, metadataJson,
                AgentMemoryMessageSource.webPrivate());
    }

    public AgentMemoryMessageRecord(String sessionId, Long userId, AgentMemoryEntryType entryType,
                                    int turnNo, AgentMemoryMessageRole role,
                                    AgentMemoryMessageType messageType, String content,
                                    String sourceRefsJson, String traceId, String requestId) {
        this(sessionId, userId, entryType, turnNo, role, messageType, content, sourceRefsJson,
                traceId, requestId, AgentMemoryMessageStatus.SUCCEEDED, null, null, null);
    }

    public static AgentMemoryMessageRecord failed(String sessionId, Long userId, AgentMemoryEntryType entryType,
                                                  int turnNo, AgentMemoryMessageRole role,
                                                  AgentMemoryMessageType messageType, String content,
                                                  String traceId, String requestId,
                                                  String errorCode, String errorMessage) {
        return new AgentMemoryMessageRecord(sessionId, userId, entryType, turnNo, role, messageType,
                content, null, traceId, requestId, AgentMemoryMessageStatus.FAILED,
                errorCode, errorMessage, null);
    }
}
