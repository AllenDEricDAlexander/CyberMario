package top.egon.mario.agent.soul.service.model;

public record AgentSoulEvolutionInput(
        Long userId,
        String username,
        String currentSoulMd,
        String userMessage,
        String assistantMessage,
        String recentContextPrompt,
        String sessionId,
        String requestId,
        String traceId
) {
}
