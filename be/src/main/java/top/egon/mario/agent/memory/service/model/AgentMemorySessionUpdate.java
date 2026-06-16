package top.egon.mario.agent.memory.service.model;

public record AgentMemorySessionUpdate(
        String title,
        Boolean memoryEnabled,
        Boolean longTermExtractionEnabled
) {
}
