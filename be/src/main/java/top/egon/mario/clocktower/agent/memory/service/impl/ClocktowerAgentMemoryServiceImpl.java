package top.egon.mario.clocktower.agent.memory.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.repository.ClocktowerAgentMemoryRepository;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryExtractor;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentMemoryServiceImpl implements ClocktowerAgentMemoryService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final ClocktowerAgentMemoryRepository memoryRepository;
    private final ClocktowerAgentMemoryExtractor extractor;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClocktowerAgentMemoryRefreshResult refresh(Long gameId, Long agentInstanceId) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findLockedByIdAndDeletedFalse(agentInstanceId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        ClocktowerGameSeatPo agentSeat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
                        instance.getGameSeatId(), gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_SEAT_INVALID"));
        Map<String, Object> metadata = readMap(instance.getMetadataJson());
        long lastSeen = longValue(metadata.get("lastSeenEventSeq"));
        List<ClocktowerGameEventPo> events = gameEventRepository
                .findByGameIdAndEventSeqGreaterThanAndStatusAndDeletedFalseOrderByEventSeqAsc(
                        gameId, lastSeen, "VISIBLE");
        int inserted = 0;
        long highestEvaluated = lastSeen;
        for (ClocktowerGameEventPo event : events) {
            highestEvaluated = Math.max(highestEvaluated, event.getEventSeq());
            if (!visibleToAgent(event, agentSeat.getId())) {
                continue;
            }
            for (ClocktowerAgentMemoryPo candidate : extractor.extract(event, instance.getId(), agentSeat)) {
                if (!exists(candidate)) {
                    memoryRepository.save(candidate);
                    inserted++;
                }
            }
        }
        metadata.put("lastSeenEventSeq", highestEvaluated);
        metadata.putIfAbsent("suspicion", Map.of());
        metadata.putIfAbsent("trust", Map.of());
        instance.setMetadataJson(writeJson(metadata));
        agentInstanceRepository.saveAndFlush(instance);
        return new ClocktowerAgentMemoryRefreshResult(highestEvaluated, inserted);
    }

    private boolean visibleToAgent(ClocktowerGameEventPo event, Long agentSeatId) {
        if ("PUBLIC".equals(event.getVisibility())) {
            return true;
        }
        if (!"PRIVATE".equals(event.getVisibility())) {
            return false;
        }
        return readLongList(event.getVisibleGameSeatIdsJson()).contains(agentSeatId);
    }

    private boolean exists(ClocktowerAgentMemoryPo candidate) {
        if (candidate.getSubjectGameSeatId() == null) {
            return !memoryRepository.findNullSubjectEventMemory(candidate.getGameId(),
                    candidate.getAgentInstanceId(), candidate.getSourceEventId(),
                    candidate.getMemoryType()).isEmpty();
        }
        return memoryRepository
                .existsByGameIdAndAgentInstanceIdAndSourceEventIdAndMemoryTypeAndSubjectGameSeatIdAndDeletedFalse(
                        candidate.getGameId(), candidate.getAgentInstanceId(), candidate.getSourceEventId(),
                        candidate.getMemoryType(), candidate.getSubjectGameSeatId());
    }

    private List<Long> readLongList(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, LONG_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_MEMORY_EVENT_JSON_INVALID");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_MEMORY_JSON_INVALID");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_MEMORY_JSON_INVALID");
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
