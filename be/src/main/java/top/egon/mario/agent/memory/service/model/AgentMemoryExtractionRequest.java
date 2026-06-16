package top.egon.mario.agent.memory.service.model;

public record AgentMemoryExtractionRequest(
        String sessionId,
        String requestId,
        String traceId
) {
}
