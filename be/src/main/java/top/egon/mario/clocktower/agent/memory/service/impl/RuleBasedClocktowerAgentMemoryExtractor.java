package top.egon.mario.clocktower.agent.memory.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.agent.memory.constant.ClocktowerAgentMemoryType;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryExtractor;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuleBasedClocktowerAgentMemoryExtractor implements ClocktowerAgentMemoryExtractor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_SNIPPET_LENGTH = 120;

    private final ClocktowerRoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<ClocktowerAgentMemoryPo> extract(ClocktowerGameEventPo event,
                                                 Long agentInstanceId,
                                                 ClocktowerGameSeatPo agentSeat) {
        Map<String, Object> payload = readMap(event.getPayloadJson());
        return switch (event.getEventType()) {
            case "PUBLIC_SPEECH" -> publicSpeech(event, agentInstanceId, agentSeat, payload);
            case "NOMINATION_OPENED" -> List.of(memory(event, agentInstanceId, agentSeat,
                    ClocktowerAgentMemoryType.NOMINATION_OBSERVATION, event.getTargetGameSeatId(), payload, 60));
            case "VOTE_CAST" -> List.of(memory(event, agentInstanceId, agentSeat,
                    ClocktowerAgentMemoryType.VOTE_OBSERVATION, event.getActorGameSeatId(), payload, 60));
            case "PRIVATE_INFO_RECEIVED" -> List.of(memory(event, agentInstanceId, agentSeat,
                    ClocktowerAgentMemoryType.PRIVATE_INFO, event.getTargetGameSeatId(), payload, 80));
            case "PLAYER_DIED" -> List.of(memory(event, agentInstanceId, agentSeat,
                    ClocktowerAgentMemoryType.DEATH_OBSERVATION, event.getTargetGameSeatId(), payload, 70));
            default -> List.of();
        };
    }

    private List<ClocktowerAgentMemoryPo> publicSpeech(ClocktowerGameEventPo event,
                                                       Long agentInstanceId,
                                                       ClocktowerGameSeatPo agentSeat,
                                                       Map<String, Object> payload) {
        List<ClocktowerAgentMemoryPo> memories = new ArrayList<>();
        String content = stringValue(payload.get("content"));
        memories.add(memory(event, agentInstanceId, agentSeat, ClocktowerAgentMemoryType.PUBLIC_SPEECH_SUMMARY,
                event.getActorGameSeatId(), payloadOf(
                        "actorGameSeatId", event.getActorGameSeatId(),
                        "contentSnippet", snippet(content),
                        "eventSeq", event.getEventSeq()
                ), 60));
        roleClaim(content, event, agentInstanceId, agentSeat)
                .ifPresent(memories::add);
        return memories;
    }

    private Optional<ClocktowerAgentMemoryPo> roleClaim(String content,
                                                       ClocktowerGameEventPo event,
                                                       Long agentInstanceId,
                                                       ClocktowerGameSeatPo agentSeat) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Optional<ClocktowerRolePo> matchedRole = roleRepository
                .findByScriptCodeAndDeletedFalseOrderBySortOrderAsc(ClocktowerScriptCode.TROUBLE_BREWING)
                .stream()
                .filter(role -> content.contains(role.getRoleCode()) || content.contains(role.getName()))
                .findFirst();
        boolean hasClaimPhrase = content.contains("我是") || content.contains("我跳") || content.contains("我声称");
        if (!hasClaimPhrase && matchedRole.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> rolePayload = payloadOf(
                "actorGameSeatId", event.getActorGameSeatId(),
                "claimedText", snippet(content),
                "eventSeq", event.getEventSeq()
        );
        matchedRole.ifPresent(role -> {
            rolePayload.put("claimedRoleCode", role.getRoleCode());
            rolePayload.put("claimedRoleName", role.getName());
        });
        return Optional.of(memory(event, agentInstanceId, agentSeat, ClocktowerAgentMemoryType.ROLE_CLAIM,
                event.getActorGameSeatId(), rolePayload, 70));
    }

    private ClocktowerAgentMemoryPo memory(ClocktowerGameEventPo event,
                                           Long agentInstanceId,
                                           ClocktowerGameSeatPo agentSeat,
                                           String memoryType,
                                           Long subjectGameSeatId,
                                           Map<String, Object> content,
                                           int confidence) {
        ClocktowerAgentMemoryPo memory = new ClocktowerAgentMemoryPo();
        memory.setGameId(event.getGameId());
        memory.setAgentInstanceId(agentInstanceId);
        memory.setGameSeatId(agentSeat.getId());
        memory.setSourceEventId(event.getId());
        memory.setSourceEventSeq(event.getEventSeq());
        memory.setMemoryType(memoryType);
        memory.setVisibility("SELF");
        memory.setSubjectGameSeatId(subjectGameSeatId);
        memory.setContentJson(writeJson(content));
        memory.setConfidence(confidence);
        memory.setDayNo(event.getDayNo());
        memory.setNightNo(event.getNightNo());
        memory.setMetadataJson("{}");
        return memory;
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_MEMORY_EVENT_JSON_INVALID");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_MEMORY_JSON_INVALID");
        }
    }

    private Map<String, Object> payloadOf(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object value = keyValues[index + 1];
            if (value != null) {
                payload.put(keyValues[index].toString(), value);
            }
        }
        return payload;
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_SNIPPET_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_SNIPPET_LENGTH);
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
