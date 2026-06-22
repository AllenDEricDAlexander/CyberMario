package top.egon.mario.agent.soul.dto.response;

import top.egon.mario.agent.soul.po.AgentSoulMdVersionPo;
import top.egon.mario.agent.soul.po.enums.AgentSoulChangeType;
import top.egon.mario.agent.soul.po.enums.AgentSoulSourceType;

import java.time.Instant;

/**
 * Previous SoulMD snapshot for the current user.
 */
public record AgentSoulMdVersionResponse(
        Long id,
        int versionNo,
        String contentMarkdown,
        int contentChars,
        AgentSoulChangeType changeType,
        String changeSummary,
        AgentSoulSourceType sourceType,
        String sourceSessionId,
        String sourceMessageIds,
        String modelProvider,
        String modelName,
        String requestId,
        String traceId,
        Instant createdAt
) {

    public static AgentSoulMdVersionResponse from(AgentSoulMdVersionPo version) {
        return new AgentSoulMdVersionResponse(
                version.getId(),
                version.getVersionNo(),
                version.getContentMarkdown(),
                version.getContentChars(),
                version.getChangeType(),
                version.getChangeSummary(),
                version.getSourceType(),
                version.getSourceSessionId(),
                version.getSourceMessageIds(),
                version.getModelProvider(),
                version.getModelName(),
                version.getRequestId(),
                version.getTraceId(),
                version.getCreatedAt()
        );
    }
}
