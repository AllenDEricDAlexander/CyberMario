package top.egon.mario.clocktower.agent.strategy.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.strategy.AgentDecision;
import top.egon.mario.clocktower.agent.strategy.AgentDecisionContext;
import top.egon.mario.clocktower.agent.strategy.ClocktowerAgentPolicyProperties;

@Service
public class ClocktowerAgentLlmPolicy {

    private final ClocktowerAgentLlmClient llmClient;
    private final ClocktowerAgentPromptBuilder promptBuilder;
    private final ClocktowerAgentLlmOutputParser outputParser;

    @Autowired
    public ClocktowerAgentLlmPolicy(ClocktowerAgentLlmClient llmClient,
                                    ClocktowerAgentPolicyProperties properties) {
        this(llmClient, new ClocktowerAgentPromptBuilder(), new ClocktowerAgentLlmOutputParser(
                new ClocktowerAgentDecisionSanitizer(properties.llm().maxSpeechChars())));
    }

    public ClocktowerAgentLlmPolicy(ClocktowerAgentLlmClient llmClient,
                                    ClocktowerAgentPromptBuilder promptBuilder,
                                    ClocktowerAgentLlmOutputParser outputParser) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.outputParser = outputParser;
    }

    public ClocktowerAgentLlmPolicyResult decide(AgentDecisionContext context) {
        ClocktowerAgentPrompt prompt = promptBuilder.build(context);
        ClocktowerAgentLlmResponse response = llmClient.decide(new ClocktowerAgentLlmRequest(
                prompt.systemPrompt(), prompt.userPrompt(), prompt.promptHash()));
        if (response == null || !StringUtils.hasText(response.content())) {
            throw new ClocktowerAgentLlmPolicyException("LLM_EMPTY_OUTPUT");
        }
        AgentDecision decision = outputParser.parse(response.content(), prompt);
        return new ClocktowerAgentLlmPolicyResult(decision, response.provider(), response.model(), prompt.promptHash());
    }

    public record ClocktowerAgentLlmPolicyResult(
            AgentDecision decision,
            String provider,
            String model,
            String promptHash
    ) {
    }
}
