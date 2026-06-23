package top.egon.mario.agent.soul.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelOptions;
import top.egon.mario.agent.model.dto.request.ModelRequest;
import top.egon.mario.agent.model.dto.response.ModelResolveResult;
import top.egon.mario.agent.model.service.MarioModelFactory;
import top.egon.mario.agent.model.service.model.ModelCallContext;
import top.egon.mario.agent.soul.config.AgentSoulProperties;
import top.egon.mario.agent.soul.service.AgentSoulEvolutionModel;
import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionDecision;
import top.egon.mario.agent.soul.service.model.AgentSoulEvolutionInput;

import java.util.Map;

/**
 * Uses a small configured chat model to propose safe, incremental SoulMD rewrites.
 */
@Service
public class DefaultAgentSoulEvolutionModel implements AgentSoulEvolutionModel {

    private final MarioModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final AgentSoulProperties properties;

    public DefaultAgentSoulEvolutionModel(MarioModelFactory modelFactory, ObjectMapper objectMapper,
                                          AgentSoulProperties properties) {
        this.modelFactory = modelFactory;
        this.objectMapper = objectMapper;
        this.properties = properties == null ? new AgentSoulProperties() : properties;
    }

    @Override
    public AgentSoulEvolutionDecision evaluateAndRewrite(AgentSoulEvolutionInput input) {
        if (!properties.evolutionEnabled()) {
            return AgentSoulEvolutionDecision.noUpdate("SoulMD evolution is disabled");
        }
        if (input == null) {
            return AgentSoulEvolutionDecision.noUpdateFailure("SoulMD evolution input is required");
        }
        ModelResolveResult result = modelFactory.resolve(new ModelRequest(
                properties.evolutionProvider(),
                properties.evolutionModel(),
                new ModelOptions(properties.evolutionTemperature(), properties.evolutionMaxTokens(),
                        null, null, false, null, false, false, Map.of()),
                new ModelCallContext(input.userId(), input.traceId(), input.sessionId(), input.sessionId(),
                        ModelScenario.AGENT_SOUL_EVOLUTION, input.requestId(), null, null)
        ));
        String responseText;
        try {
            responseText = responseText(result.chatModel().call(new Prompt(
                    new SystemMessage(systemPrompt()),
                    new UserMessage(userPrompt(input))
            )));
        } catch (RuntimeException error) {
            return AgentSoulEvolutionDecision.noUpdateFailure(modelCallFailureReason(error));
        }
        if (!StringUtils.hasText(responseText)) {
            return AgentSoulEvolutionDecision.noUpdateFailure("SoulMD evolution model returned no response");
        }
        RawDecision rawDecision;
        try {
            rawDecision = objectMapper.readValue(responseText.trim(), RawDecision.class);
        } catch (JsonProcessingException e) {
            return AgentSoulEvolutionDecision.noUpdateFailure("Invalid SoulMD evolution JSON");
        }
        if (rawDecision == null) {
            return AgentSoulEvolutionDecision.noUpdateFailure("SoulMD evolution model returned null decision");
        }
        if (rawDecision.shouldUpdate() == null) {
            return AgentSoulEvolutionDecision.noUpdateFailure("SoulMD evolution decision omitted shouldUpdate");
        }
        if (!Boolean.TRUE.equals(rawDecision.shouldUpdate())) {
            return AgentSoulEvolutionDecision.noUpdate(safeReason(rawDecision.reason(), "SoulMD evolution found no durable update"));
        }
        if (!StringUtils.hasText(rawDecision.updatedSoulMd())) {
            return AgentSoulEvolutionDecision.noUpdateFailure("SoulMD evolution update omitted updatedSoulMd");
        }
        return new AgentSoulEvolutionDecision(true,
                safeReason(rawDecision.reason(), "SoulMD evolution requested an update"),
                rawDecision.changeSummary(),
                rawDecision.updatedSoulMd(),
                result.provider() == null ? null : result.provider().name(),
                result.model());
    }

    private String systemPrompt() {
        return """
                You evolve the user's SoulMD for the CyberMario main agent after one successful chat turn.
                Return only one strict JSON object and no markdown, code fences, commentary, or extra text.
                JSON shape: {"shouldUpdate":boolean,"reason":string,"changeSummary":string,"updatedSoulMd":string}
                Update only when the turn reveals durable user preferences, stable constraints, or important personalization.
                Preserve the current SoulMD structure unless a small rewrite is necessary.
                Never weaken safety, authorization, permission, tool-use, RAG source, privacy, data-boundary, or system-rule constraints.
                If an update would weaken those constraints, return shouldUpdate=false and leave updatedSoulMd empty.
                """;
    }

    private String userPrompt(AgentSoulEvolutionInput input) {
        return """
                User id: %s
                Username: %s
                Session id: %s

                Current SoulMD:
                <current_soul_md>
                %s
                </current_soul_md>

                Recent session context:
                <recent_context>
                %s
                </recent_context>

                User message:
                <user_message>
                %s
                </user_message>

                Assistant reply:
                <assistant_message>
                %s
                </assistant_message>
                """.formatted(input.userId(), safe(input.username()), safe(input.sessionId()),
                safe(input.currentSoulMd()), safe(input.recentContextPrompt()), safe(input.userMessage()),
                safe(input.assistantMessage()));
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String safeReason(String reason, String fallback) {
        return StringUtils.hasText(reason) ? reason.trim() : fallback;
    }

    private String modelCallFailureReason(RuntimeException error) {
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            String type = error == null ? "unknown" : error.getClass().getSimpleName();
            return "SoulMD evolution model call failed: " + type;
        }
        return "SoulMD evolution model call failed: " + error.getMessage().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawDecision(
            Boolean shouldUpdate,
            String reason,
            String changeSummary,
            String updatedSoulMd
    ) {
    }
}
