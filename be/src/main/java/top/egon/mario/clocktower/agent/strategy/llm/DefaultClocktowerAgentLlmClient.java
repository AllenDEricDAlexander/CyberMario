package top.egon.mario.clocktower.agent.strategy.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.clocktower.agent.strategy.ClocktowerAgentPolicyProperties;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultClocktowerAgentLlmClient implements ClocktowerAgentLlmClient {

    private final MarioModelFactory modelFactory;
    private final ClocktowerAgentPolicyProperties properties;

    @Override
    public ClocktowerAgentLlmResponse decide(ClocktowerAgentLlmRequest request) {
        ModelResolveResult result = modelFactory.resolve(new ModelRequest(
                properties.llm().provider(),
                properties.llm().model(),
                new ModelOptions(BigDecimal.valueOf(0.2), properties.llm().maxOutputChars(),
                        null, null, false, null, false, true, Map.of()),
                new ModelCallContext(null, null, null, "clocktower-agent-" + request.promptHash(),
                        properties.llm().scenario(), request.promptHash(), null, null)
        ));
        ChatResponse response = result.chatModel().call(new Prompt(
                new SystemMessage(request.systemPrompt()),
                new UserMessage(request.userPrompt())
        ));
        return new ClocktowerAgentLlmResponse(responseText(response),
                result.provider() == null ? null : result.provider().name(), result.model());
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
