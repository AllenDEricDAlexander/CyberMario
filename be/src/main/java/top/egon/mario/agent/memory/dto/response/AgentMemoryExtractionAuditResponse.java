package top.egon.mario.agent.memory.dto.response;

import top.egon.mario.agent.memory.po.AgentMemoryExtractionAuditPo;
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemoryExtractionStatus;

import java.time.Instant;

/**
 * Current-user extraction audit record.
 */
public record AgentMemoryExtractionAuditResponse(
        Long id,
        String sessionId,
        AgentMemoryEntryType entryType,
        String sourceMessageIds,
        AgentMemoryExtractionStatus status,
        String extractedMarkdown,
        Long mergedVersionId,
        String errorCode,
        String errorMessage,
        String requestId,
        String traceId,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {

    public static AgentMemoryExtractionAuditResponse from(AgentMemoryExtractionAuditPo audit) {
        return new AgentMemoryExtractionAuditResponse(
                audit.getId(),
                audit.getSessionId(),
                audit.getEntryType(),
                audit.getSourceMessageIds(),
                audit.getStatus(),
                audit.getExtractedMarkdown(),
                audit.getMergedVersionId(),
                audit.getErrorCode(),
                audit.getErrorMessage(),
                audit.getRequestId(),
                audit.getTraceId(),
                audit.getStartedAt(),
                audit.getFinishedAt(),
                audit.getCreatedAt()
        );
    }
}
