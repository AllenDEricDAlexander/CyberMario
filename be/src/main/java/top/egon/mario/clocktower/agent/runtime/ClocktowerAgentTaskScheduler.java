package top.egon.mario.clocktower.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.agent.runtime.repository.ClocktowerAgentTaskRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentTaskScheduler {

    private static final String ACTOR_AGENT = "AGENT";
    private static final String SEAT_ACTIVE = "ACTIVE";

    private final ClocktowerAgentTaskRepository taskRepository;
    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerAgentWorkerProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClocktowerAgentTaskPo scheduleForAgent(Long gameId, Long agentInstanceId, Long gameSeatId,
                                                  String triggerType, String triggerKey,
                                                  Map<String, Object> metadata) {
        return taskRepository
                .findByGameIdAndAgentInstanceIdAndTriggerTypeAndTriggerKeyAndDeletedFalse(
                        gameId, agentInstanceId, triggerType, triggerKey)
                .orElseGet(() -> createTask(gameId, agentInstanceId, gameSeatId, triggerType, triggerKey, metadata));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClocktowerAgentTaskPo> scheduleForGameAgents(Long gameId, String triggerType, String triggerKey,
                                                             Map<String, Object> metadata) {
        return activeAgentInstances(gameId).stream()
                .map(instance -> scheduleForAgent(gameId, instance.getId(), instance.getGameSeatId(),
                        triggerType, triggerKey, metadata))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClocktowerAgentTaskPo> scheduleForActiveAgentSeats(Long gameId, String triggerType,
                                                                   String triggerKey,
                                                                   Map<String, Object> metadata) {
        return gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId)
                .stream()
                .filter(this::activeAgentSeat)
                .map(seat -> scheduleForAgent(gameId, seat.getAgentInstanceId(), seat.getId(),
                        triggerType, triggerKey, metadata))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClocktowerAgentTaskPo scheduleForGameSeat(Long gameId, Long gameSeatId, String triggerType,
                                                     String triggerKey, Map<String, Object> metadata) {
        if (gameSeatId == null) {
            return null;
        }
        return gameSeatRepository.findByIdAndGameIdAndDeletedFalse(gameSeatId, gameId)
                .filter(this::activeAgentSeat)
                .map(seat -> scheduleForAgent(gameId, seat.getAgentInstanceId(), seat.getId(),
                        triggerType, triggerKey, metadata))
                .orElse(null);
    }

    private List<ClocktowerAgentInstancePo> activeAgentInstances(Long gameId) {
        return agentInstanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(gameId)
                .stream()
                .filter(instance -> ClocktowerAgentStatus.ACTIVE.equals(instance.getStatus()))
                .filter(instance -> instance.getGameSeatId() != null)
                .toList();
    }

    private boolean activeAgentSeat(ClocktowerGameSeatPo seat) {
        return seat != null
                && ACTOR_AGENT.equals(seat.getActorType())
                && SEAT_ACTIVE.equals(seat.getStatus())
                && seat.getAgentInstanceId() != null;
    }

    private ClocktowerAgentTaskPo createTask(Long gameId, Long agentInstanceId, Long gameSeatId,
                                             String triggerType, String triggerKey,
                                             Map<String, Object> metadata) {
        try {
            ClocktowerAgentTaskPo task = new ClocktowerAgentTaskPo();
            task.setGameId(gameId);
            task.setAgentInstanceId(agentInstanceId);
            task.setGameSeatId(gameSeatId);
            task.setTriggerType(triggerType);
            task.setTriggerKey(triggerKey);
            task.setStatus(ClocktowerAgentTaskStatus.PENDING);
            task.setPriority(100);
            task.setAvailableAt(Instant.now().plus(properties.defaultResponseDelay()));
            task.setMetadataJson(writeJson(metadata == null ? Map.of() : metadata));
            task.setResultJson("{}");
            return taskRepository.saveAndFlush(task);
        } catch (DataIntegrityViolationException ex) {
            return taskRepository
                    .findByGameIdAndAgentInstanceIdAndTriggerTypeAndTriggerKeyAndDeletedFalse(
                            gameId, agentInstanceId, triggerType, triggerKey)
                    .orElseThrow(() -> ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_TASK_JSON_INVALID");
        }
    }
}
