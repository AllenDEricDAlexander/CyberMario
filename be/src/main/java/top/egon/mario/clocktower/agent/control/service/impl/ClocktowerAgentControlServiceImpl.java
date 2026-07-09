package top.egon.mario.clocktower.agent.control.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentConsoleView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentMemoryView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentTaskView;
import top.egon.mario.clocktower.agent.control.service.ClocktowerAgentControlService;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.repository.ClocktowerAgentMemoryRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;
import top.egon.mario.clocktower.agent.runtime.ClocktowerAgentTaskStatus;
import top.egon.mario.clocktower.agent.runtime.ClocktowerAgentTriggerType;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.agent.runtime.repository.ClocktowerAgentTaskRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomAccessPolicy;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentControlServiceImpl implements ClocktowerAgentControlService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerAgentProfileRepository agentProfileRepository;
    private final ClocktowerAgentTaskRepository agentTaskRepository;
    private final ClocktowerAgentMemoryRepository agentMemoryRepository;
    private final ClocktowerGameEventAppender gameEventAppender;
    private final RoomSpaceRepository roomSpaceRepository;
    private final ClocktowerRoomAccessPolicy accessPolicy;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerAgentConsoleView> listAgents(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        requireStoryteller(game, principal);
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(
                game.getId());
        Map<Long, ClocktowerGameSeatPo> seatByAgentInstanceId = seats.stream()
                .filter(seat -> seat.getAgentInstanceId() != null)
                .collect(Collectors.toMap(ClocktowerGameSeatPo::getAgentInstanceId, Function.identity(),
                        (left, right) -> left));
        return agentInstanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(game.getId())
                .stream()
                .map(instance -> toConsoleView(instance, seatByAgentInstanceId.get(instance.getId()), recentTask(
                        game.getId(), instance.getId())))
                .toList();
    }

    @Override
    @Transactional
    public ClocktowerAgentConsoleView pauseAgent(Long gameId, Long agentInstanceId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        ClocktowerAgentInstancePo instance = lockedInstance(gameId, agentInstanceId);
        instance.setAutoMode(ClocktowerAgentAutoMode.PAUSED);
        ClocktowerAgentInstancePo saved = agentInstanceRepository.saveAndFlush(instance);
        ClocktowerGameSeatPo seat = requireAgentSeat(gameId, saved);
        appendAgentEvent(game, "AGENT_PAUSED_BY_ST", saved, seat, principal);
        return toConsoleView(saved, seat, recentTask(gameId, saved.getId()));
    }

    @Override
    @Transactional
    public ClocktowerAgentConsoleView resumeAgent(Long gameId, Long agentInstanceId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        ClocktowerAgentInstancePo instance = lockedInstance(gameId, agentInstanceId);
        instance.setAutoMode(ClocktowerAgentAutoMode.FULL_AUTO);
        ClocktowerAgentInstancePo saved = agentInstanceRepository.saveAndFlush(instance);
        ClocktowerGameSeatPo seat = requireAgentSeat(gameId, saved);
        appendAgentEvent(game, "AGENT_RESUMED_BY_ST", saved, seat, principal);
        return toConsoleView(saved, seat, recentTask(gameId, saved.getId()));
    }

    @Override
    @Transactional
    public ClocktowerAgentTaskView runNow(Long gameId, Long agentInstanceId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        ClocktowerAgentInstancePo instance = lockedInstance(gameId, agentInstanceId);
        if (!ClocktowerAgentStatus.ACTIVE.equals(instance.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_INACTIVE");
        }
        ClocktowerGameSeatPo seat = requireAgentSeat(gameId, instance);
        Instant now = Instant.now();
        ClocktowerAgentTaskPo task = new ClocktowerAgentTaskPo();
        task.setGameId(gameId);
        task.setAgentInstanceId(instance.getId());
        task.setGameSeatId(seat.getId());
        task.setTriggerType(ClocktowerAgentTriggerType.ST_RUN_NOW);
        task.setTriggerKey("st-run-now:%d:%d:%d".formatted(gameId, instance.getId(), now.toEpochMilli()));
        task.setStatus(ClocktowerAgentTaskStatus.PENDING);
        task.setPriority(10);
        task.setAvailableAt(now);
        task.setMetadataJson(writeJson(Map.of("requestedByStorytellerUserId", principal.userId(),
                "reason", "ST_RUN_NOW")));
        task.setResultJson("{}");
        ClocktowerAgentTaskPo saved = agentTaskRepository.saveAndFlush(task);
        appendAgentEvent(game, "AGENT_RUN_NOW_REQUESTED_BY_ST", instance, seat, principal);
        return toTaskView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerAgentMemoryView> listMemory(Long gameId, Long agentInstanceId, RbacPrincipal principal) {
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        requireStoryteller(game, principal);
        requireInstance(gameId, agentInstanceId);
        return agentMemoryRepository
                .findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByCreatedAtDescIdDesc(gameId, agentInstanceId)
                .stream()
                .map(this::toMemoryView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerAgentTaskView> listTasks(Long gameId, Long agentInstanceId, RbacPrincipal principal) {
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        requireStoryteller(game, principal);
        requireInstance(gameId, agentInstanceId);
        return agentTaskRepository.findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByIdDesc(gameId,
                        agentInstanceId)
                .stream()
                .map(this::toTaskView)
                .toList();
    }

    private ClocktowerGamePo lockedGame(Long gameId) {
        return gameRepository.findLockedByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    }

    private ClocktowerAgentInstancePo lockedInstance(Long gameId, Long agentInstanceId) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findLockedByIdAndDeletedFalse(agentInstanceId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        if (!gameId.equals(instance.getGameId())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_MISMATCH");
        }
        return instance;
    }

    private ClocktowerAgentInstancePo requireInstance(Long gameId, Long agentInstanceId) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(agentInstanceId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        if (!gameId.equals(instance.getGameId())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_MISMATCH");
        }
        return instance;
    }

    private ClocktowerGameSeatPo requireAgentSeat(Long gameId, ClocktowerAgentInstancePo instance) {
        if (instance.getGameSeatId() == null) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_REQUIRED");
        }
        return gameSeatRepository.findByIdAndGameIdAndDeletedFalse(instance.getGameSeatId(), gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
    }

    private ClocktowerAgentTaskPo recentTask(Long gameId, Long agentInstanceId) {
        return agentTaskRepository.findByGameIdAndAgentInstanceIdAndDeletedFalseOrderByIdDesc(gameId,
                        agentInstanceId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ClocktowerAgentConsoleView toConsoleView(ClocktowerAgentInstancePo instance, ClocktowerGameSeatPo seat,
                                                     ClocktowerAgentTaskPo recentTask) {
        ClocktowerAgentProfilePo profile = agentProfileRepository.findByIdAndDeletedFalse(instance.getProfileId())
                .orElse(null);
        return new ClocktowerAgentConsoleView(instance.getId(), instance.getActorId(), instance.getGameSeatId(),
                seat == null ? null : seat.getSeatNo(), seat == null ? profileDisplayName(profile) : seat.getDisplayName(),
                profile == null ? null : profile.getName(), instance.getStatus(), instance.getAutoMode(),
                seat == null ? null : seat.getRoleCode(), seat == null ? null : seat.getAlignment(),
                recentTask == null ? null : recentTask.getStatus(),
                recentTask == null ? null : recentTask.getTriggerType(),
                recentTask == null ? Map.of() : readMap(recentTask.getResultJson()),
                recentTask == null ? null : recentTask.getLastError());
    }

    private String profileDisplayName(ClocktowerAgentProfilePo profile) {
        if (profile == null) {
            return null;
        }
        return profile.getDisplayNameTemplate() == null ? profile.getName() : profile.getDisplayNameTemplate();
    }

    private ClocktowerAgentTaskView toTaskView(ClocktowerAgentTaskPo task) {
        return new ClocktowerAgentTaskView(task.getId(), task.getGameId(), task.getAgentInstanceId(),
                task.getGameSeatId(), task.getTriggerType(), task.getTriggerKey(), task.getStatus(),
                task.getPriority(), task.getAvailableAt(), task.getLockedAt(), task.getLockedBy(),
                task.getAttempts(), task.getLastError(), readMap(task.getMetadataJson()), readMap(task.getResultJson()));
    }

    private ClocktowerAgentMemoryView toMemoryView(ClocktowerAgentMemoryPo memory) {
        return new ClocktowerAgentMemoryView(memory.getId(), memory.getGameId(), memory.getAgentInstanceId(),
                memory.getGameSeatId(), memory.getSourceEventId(), memory.getSourceEventSeq(), memory.getMemoryType(),
                memory.getVisibility(), memory.getSubjectGameSeatId(), readMap(memory.getContentJson()),
                memory.getConfidence(), memory.getDayNo(), memory.getNightNo(), readMap(memory.getMetadataJson()));
    }

    private void appendAgentEvent(ClocktowerGamePo game, String eventType, ClocktowerAgentInstancePo instance,
                                  ClocktowerGameSeatPo seat, RbacPrincipal principal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentInstanceId", instance.getId());
        payload.put("gameSeatId", seat.getId());
        payload.put("seatNo", seat.getSeatNo());
        payload.put("autoMode", instance.getAutoMode());
        payload.put("storytellerUserId", principal.userId());
        gameEventAppender.append(game, eventType, null, seat.getId(), "STORYTELLER", List.of(), payload,
                Instant.now());
    }

    private void requireStoryteller(ClocktowerGamePo game, RbacPrincipal principal) {
        RoomSpacePo room = roomSpaceRepository.findByIdAndDeletedFalse(game.getRoomId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        accessPolicy.requireOwner(room, principal);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_CONTROL_JSON_INVALID");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_CONTROL_JSON_INVALID");
        }
    }
}
