package top.egon.mario.agent.soul.service.model;

import top.egon.mario.agent.soul.po.enums.AgentSoulSourceType;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public record AgentSoulEvolutionRequest(
        RbacPrincipal principal,
        String sessionId,
        String userMessage,
        String assistantMessage,
        String recentContextPrompt,
        AgentSoulSourceType sourceType,
        String requestId,
        String traceId
) {
}
