package top.egon.mario.clocktower.agent.strategy.llm;

public record ClocktowerAgentLlmResponse(
        String content,
        String provider,
        String model
) {
}
