package top.egon.mario.clocktower.agent.strategy.llm;

import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;

import java.util.Map;

public record ClocktowerAgentPrompt(
        String systemPrompt,
        String userPrompt,
        String promptHash,
        Map<String, AgentLegalIntentView> legalIntentById,
        boolean grimoireIncluded
) {
}
