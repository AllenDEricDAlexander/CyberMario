package top.egon.mario.clocktower.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService;
import top.egon.mario.clocktower.agent.memory.service.ClocktowerAgentMemoryService.ClocktowerAgentMemoryRefreshResult;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerAgentGameActionService;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentRuntime {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerAgentMemoryService memoryService;
    private final ClocktowerAgentGameActionService agentGameActionService;
    private final ClocktowerGameNightTaskService nightTaskService;
    private final ObjectMapper objectMapper;

    public ClocktowerAgentRuntimeResult handle(ClocktowerAgentTaskPo task) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(
                task.getAgentInstanceId()).orElseThrow(
                        () -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        if (!ClocktowerAgentStatus.ACTIVE.equals(instance.getStatus())) {
            return skipped("AGENT_INSTANCE_NOT_ACTIVE");
        }
        if (ClocktowerAgentAutoMode.PAUSED.equals(instance.getAutoMode())) {
            return skipped("AUTO_MODE_PAUSED");
        }
        if (ClocktowerAgentAutoMode.ST_APPROVAL.equals(instance.getAutoMode())) {
            return skipped("AUTO_MODE_REQUIRES_ST_APPROVAL");
        }

        ClocktowerAgentMemoryRefreshResult memoryRefresh = memoryService.refreshForRuntimeTask(task);
        return switch (task.getTriggerType()) {
            case ClocktowerAgentTriggerType.MIC_TURN_STARTED -> passMicTurn(task, memoryRefresh);
            case ClocktowerAgentTriggerType.VOTE_WINDOW_OPENED -> voteFalse(task, memoryRefresh);
            case ClocktowerAgentTriggerType.NIGHT_TASK_OPENED -> autoChooseNightTask(task, memoryRefresh);
            default -> done(withMemoryRefresh(Map.of("actionType", "NOOP", "triggerType", task.getTriggerType()),
                    memoryRefresh));
        };
    }

    private ClocktowerAgentRuntimeResult passMicTurn(ClocktowerAgentTaskPo task,
                                                     ClocktowerAgentMemoryRefreshResult memoryRefresh) {
        ClocktowerGameActionResponse response = agentGameActionService.submitAgentAction(task.getGameId(),
                task.getAgentInstanceId(), new ClocktowerGameActionRequest(task.getGameSeatId(), "PASS", List.of(),
                        null, null, null, Map.of("passType", "MIC_TURN")));
        return done(withMemoryRefresh(actionResult("PASS", response), memoryRefresh));
    }

    private ClocktowerAgentRuntimeResult voteFalse(ClocktowerAgentTaskPo task,
                                                  ClocktowerAgentMemoryRefreshResult memoryRefresh) {
        Long nominationId = longValue(metadata(task).get("nominationId"));
        ClocktowerGameActionResponse response = agentGameActionService.submitAgentAction(task.getGameId(),
                task.getAgentInstanceId(), new ClocktowerGameActionRequest(task.getGameSeatId(), "VOTE", List.of(),
                        nominationId, false, null, Map.of("defaultVote", true)));
        return done(withMemoryRefresh(actionResult("VOTE", response), memoryRefresh));
    }

    private ClocktowerAgentRuntimeResult autoChooseNightTask(ClocktowerAgentTaskPo task,
                                                            ClocktowerAgentMemoryRefreshResult memoryRefresh) {
        Long nightTaskId = longValue(metadata(task).get("taskId"));
        if (nightTaskId == null) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_NIGHT_TASK_REQUIRED");
        }
        ClocktowerGameActionResponse response = nightTaskService.autoChooseTask(task.getGameId(), nightTaskId,
                task.getAgentInstanceId());
        return done(withMemoryRefresh(actionResult("NIGHT_CHOICE", response), memoryRefresh));
    }

    private ClocktowerAgentRuntimeResult done(Map<String, Object> result) {
        return new ClocktowerAgentRuntimeResult(ClocktowerAgentTaskStatus.DONE, result);
    }

    private ClocktowerAgentRuntimeResult skipped(String reason) {
        return new ClocktowerAgentRuntimeResult(ClocktowerAgentTaskStatus.CANCELLED,
                Map.of("skipped", true, "reason", reason));
    }

    private Map<String, Object> actionResult(String actionType, ClocktowerGameActionResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionType", actionType);
        result.put("accepted", response.accepted());
        result.put("rejectedCode", response.rejectedCode());
        if (response.event() != null) {
            result.put("eventId", response.event().eventId());
            result.put("eventType", response.event().eventType());
        }
        return result;
    }

    private Map<String, Object> withMemoryRefresh(Map<String, Object> result,
                                                  ClocktowerAgentMemoryRefreshResult memoryRefresh) {
        Map<String, Object> enriched = new LinkedHashMap<>(result);
        enriched.put("memoryLastSeenEventSeq", memoryRefresh.lastSeenEventSeq());
        enriched.put("memoryInsertedCount", memoryRefresh.insertedCount());
        return enriched;
    }

    private Map<String, Object> metadata(ClocktowerAgentTaskPo task) {
        if (task.getMetadataJson() == null || task.getMetadataJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(task.getMetadataJson(), MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_TASK_JSON_INVALID");
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    public record ClocktowerAgentRuntimeResult(String status, Map<String, Object> result) {
    }
}
