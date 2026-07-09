package top.egon.mario.clocktower.agent.strategy.llm;

public record ClocktowerAgentLlmRequest(
        String systemPrompt,
        String userPrompt,
        String promptHash
) {
}
