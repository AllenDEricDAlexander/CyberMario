package top.egon.mario.clocktower.agent.strategy.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.strategy.AgentDecision;
import top.egon.mario.clocktower.agent.strategy.AgentIntent;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public class ClocktowerAgentLlmOutputParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClocktowerAgentDecisionSanitizer sanitizer;

    public AgentDecision parse(String response, ClocktowerAgentPrompt prompt) {
        Map<String, Object> payload = readResponse(response);
        String intentId = stringValue(payload.get("intentId"));
        if (!StringUtils.hasText(intentId)) {
            throw new ClocktowerAgentLlmPolicyException("LLM_INTENT_MISSING");
        }
        AgentLegalIntentView legalIntent = prompt.legalIntentById().get(intentId);
        if (legalIntent == null) {
            throw new ClocktowerAgentLlmPolicyException("LLM_INTENT_UNKNOWN");
        }
        String reasoning = sanitizer.sanitizeReasoning(stringValue(payload.get("reasoningSummary")));
        AgentIntent intent = toIntent(legalIntent, payload, reasoning, prompt.grimoireIncluded());
        return new AgentDecision(intent, reasoning, Map.of("source", "LLM", "intentId", intentId));
    }

    private Map<String, Object> readResponse(String response) {
        if (!StringUtils.hasText(response)) {
            throw new ClocktowerAgentLlmPolicyException("LLM_EMPTY_OUTPUT");
        }
        String trimmed = response.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new ClocktowerAgentLlmPolicyException("LLM_OUTPUT_NOT_JSON_OBJECT");
        }
        try {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerAgentLlmPolicyException("LLM_OUTPUT_JSON_INVALID", ex);
        }
    }

    private AgentIntent toIntent(AgentLegalIntentView legalIntent, Map<String, Object> payload,
                                 String reasoning, boolean grimoireIncluded) {
        return switch (legalIntent.intentType()) {
            case "PUBLIC_SPEECH" -> new AgentIntent.PublicSpeech(sanitizer.sanitizeSpeech(
                    stringValue(payload.get("content")), grimoireIncluded));
            case "GRAB_MIC" -> new AgentIntent.GrabMic(reasoning);
            case "PASS" -> new AgentIntent.Pass(reasoning);
            case "NOMINATE" -> new AgentIntent.Nominate(selectedTarget(payload,
                    longList(legalIntent.payload().get("eligibleTargetGameSeatIds"))), reasoning);
            case "VOTE" -> new AgentIntent.Vote(legalIntent.nominationId(),
                    Boolean.TRUE.equals(legalIntent.voteValue()), reasoning);
            case "NIGHT_CHOICE" -> new AgentIntent.NightChoice(legalIntent.taskId(),
                    selectedTargets(payload, longList(legalIntent.payload().get("legalTargetGameSeatIds"))),
                    Map.of("source", "LLM"));
            default -> throw new ClocktowerAgentLlmPolicyException("LLM_INTENT_UNSUPPORTED");
        };
    }

    private Long selectedTarget(Map<String, Object> payload, List<Long> legalTargets) {
        Long target = longValue(payload.get("targetGameSeatId"));
        if (target == null || !legalTargets.contains(target)) {
            throw new ClocktowerAgentLlmPolicyException("LLM_TARGET_ILLEGAL");
        }
        return target;
    }

    private List<Long> selectedTargets(Map<String, Object> payload, List<Long> legalTargets) {
        List<Long> targets = longList(payload.get("targetGameSeatIds"));
        if (targets.isEmpty() && legalTargets.isEmpty()) {
            return List.of();
        }
        if (!legalTargets.containsAll(targets)) {
            throw new ClocktowerAgentLlmPolicyException("LLM_TARGET_ILLEGAL");
        }
        return targets;
    }

    private List<Long> longList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(this::longValue).toList();
        }
        return List.of();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
