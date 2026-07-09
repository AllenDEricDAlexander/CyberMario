package top.egon.mario.clocktower.agent.decision.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.decision.ClocktowerAgentDecisionStatus;
import top.egon.mario.clocktower.agent.decision.po.ClocktowerAgentDecisionPo;
import top.egon.mario.clocktower.agent.decision.repository.ClocktowerAgentDecisionRepository;
import top.egon.mario.clocktower.agent.decision.service.ClocktowerAgentDecisionAuditCommand;
import top.egon.mario.clocktower.agent.decision.service.ClocktowerAgentDecisionAuditService;
import top.egon.mario.clocktower.common.ClocktowerException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentDecisionAuditServiceImpl implements ClocktowerAgentDecisionAuditService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerAgentDecisionRepository decisionRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ClocktowerAgentDecisionPo write(ClocktowerAgentDecisionAuditCommand command) {
        ClocktowerAgentDecisionPo po = new ClocktowerAgentDecisionPo();
        po.setGameId(command.gameId());
        po.setAgentInstanceId(command.agentInstanceId());
        po.setGameSeatId(command.gameSeatId());
        po.setTriggerTaskId(command.triggerTaskId());
        po.setPhase(command.phase());
        po.setDayNo(command.dayNo());
        po.setNightNo(command.nightNo());
        po.setDecisionType(command.decisionType());
        po.setPolicyType(command.policyType());
        po.setLegalIntentsJson(writeJson(command.legalIntents(), "[]"));
        po.setSelectedIntentJson(writeJson(command.selectedIntent(), "{}"));
        po.setReasoningSummary(command.reasoningSummary());
        po.setModelProvider(command.modelProvider());
        po.setModelName(command.modelName());
        po.setPromptHash(command.promptHash());
        po.setStatus(command.status() == null || command.status().isBlank()
                ? ClocktowerAgentDecisionStatus.ACCEPTED : command.status());
        po.setErrorMessage(command.errorMessage());
        po.setMetadataJson(writeJson(sanitizedMetadata(command.metadata()), "{}"));
        return decisionRepository.saveAndFlush(po);
    }

    private Object sanitizedMetadata(Object metadata) {
        Map<String, Object> map = readMap(metadata);
        map.remove("systemPrompt");
        map.remove("userPrompt");
        map.remove("fullPrompt");
        map.remove("prompt");
        return map;
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> target = new LinkedHashMap<>();
            source.forEach((key, mapValue) -> target.put(String.valueOf(key), mapValue));
            return target;
        }
        if (value instanceof String string) {
            try {
                return objectMapper.readValue(string.isBlank() ? "{}" : string, MAP_TYPE);
            } catch (JsonProcessingException ex) {
                throw new ClocktowerException("CLOCKTOWER_AGENT_DECISION_JSON_INVALID");
            }
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private String writeJson(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String string) {
            return string.isBlank() ? fallback : string;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_DECISION_JSON_INVALID");
        }
    }
}
