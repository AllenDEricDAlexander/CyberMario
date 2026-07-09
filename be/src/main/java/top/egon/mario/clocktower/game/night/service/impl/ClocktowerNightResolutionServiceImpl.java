package top.egon.mario.clocktower.game.night.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightResolveRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightResolution;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.RoleSkill;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerNightResolutionServiceImpl implements ClocktowerNightResolutionService {

    private static final String PHASE_FIRST_NIGHT = "FIRST_NIGHT";
    private static final String PHASE_NIGHT = "NIGHT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CHOSEN = "CHOSEN";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String TASK_CHOOSE_TARGET = "CHOOSE_TARGET";
    private static final String TASK_RECEIVE_INFO = "RECEIVE_INFO";
    private static final String LIFE_DEAD = "DEAD";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameNightTaskRepository nightTaskRepository;
    private final ClocktowerRoleSkillRegistry roleSkillRegistry;
    private final ClocktowerGameEventAppender eventAppender;
    private final RoomSpaceRepository roomSpaceRepository;
    private final ClocktowerRoomAccessPolicy accessPolicy;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClocktowerNightTaskView resolveTask(Long gameId, Long taskId, ClocktowerNightResolveRequest request,
                                               RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        ClocktowerGameNightTaskPo task = nightTaskRepository.findLockedByIdAndGameIdAndDeletedFalse(taskId, gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_FOUND"));
        requireCurrentNight(game, task);
        if (request != null && request.result() != null && !request.result().isEmpty()) {
            return completeWithManualResult(game, task, request.result(), principal, request.note());
        }
        if (!readyToResolve(task)) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_READY");
        }
        return resolveLoadedTask(game, task, principal);
    }

    @Override
    @Transactional
    public List<ClocktowerNightTaskView> resolveReady(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        if (!PHASE_FIRST_NIGHT.equals(game.getPhase()) && !PHASE_NIGHT.equals(game.getPhase())) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_RESOLUTION_PHASE_INVALID");
        }
        List<ClocktowerNightTaskView> resolved = new ArrayList<>();
        List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.getId(), game.getNightNo());
        for (ClocktowerGameNightTaskPo task : tasks) {
            if (!readyToResolve(task)) {
                continue;
            }
            ClocktowerGameNightTaskPo lockedTask = nightTaskRepository.findLockedByIdAndGameIdAndDeletedFalse(
                            task.getId(), game.getId())
                    .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_FOUND"));
            if (readyToResolve(lockedTask)) {
                resolved.add(resolveLoadedTask(game, lockedTask, principal));
            }
        }
        return resolved;
    }

    private ClocktowerNightTaskView resolveLoadedTask(ClocktowerGamePo game, ClocktowerGameNightTaskPo task,
                                                      RbacPrincipal principal) {
        ClocktowerGameSeatPo actorSeat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
                        task.getActorGameSeatId(), game.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(
                game.getId());
        List<ClocktowerGameNightTaskPo> currentTasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.getId(), game.getNightNo());
        RoleSkill skill = roleSkillRegistry.find(task.getRoleCode())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_ROLE_UNSUPPORTED"));
        Map<String, Object> choiceMap = readMap(task.getChoiceJson());
        Map<String, Object> metadata = new LinkedHashMap<>(readMap(task.getMetadataJson()));
        metadata.put("nightMarkers", nightMarkers(currentTasks));
        NightChoice choice = new NightChoice(longListPayload(choiceMap, "targetGameSeatIds"),
                mapPayload(choiceMap.get("payload")));
        NightResolution resolution = skill.resolve(new NightTaskContext(
                game, task, actorSeat, seats, currentTasks, metadata), choice);
        applyResolutionEvents(game, resolution, task, principal);
        Map<String, Object> result = resolution.result() == null ? Map.of() : resolution.result();
        task.setResultJson(writeJson(result));
        task.setStatus(resolution.status() == null ? STATUS_DONE : resolution.status());
        task.setCompletedAt(Instant.now());
        task.setResolvedByActorId(principal.userId());
        ClocktowerGameNightTaskPo saved = nightTaskRepository.saveAndFlush(task);
        return ClocktowerNightTaskView.from(saved, choiceMap, result, metadata);
    }

    private ClocktowerNightTaskView completeWithManualResult(ClocktowerGamePo game, ClocktowerGameNightTaskPo task,
                                                             Map<String, Object> result,
                                                             RbacPrincipal principal,
                                                             String note) {
        task.setResultJson(writeJson(result));
        task.setStatus(STATUS_DONE);
        task.setCompletedAt(Instant.now());
        task.setResolvedByActorId(principal.userId());
        ClocktowerGameNightTaskPo saved = nightTaskRepository.saveAndFlush(task);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", saved.getId());
        payload.put("roleCode", saved.getRoleCode());
        payload.put("taskType", saved.getTaskType());
        payload.put("note", note);
        eventAppender.append(game, "NIGHT_TASK_RESOLVED", null, saved.getActorGameSeatId(),
                "STORYTELLER", List.of(), payload, Instant.now());
        return ClocktowerNightTaskView.from(saved, readMap(saved.getChoiceJson()), result,
                readMap(saved.getMetadataJson()));
    }

    private void applyResolutionEvents(ClocktowerGamePo game, NightResolution resolution,
                                       ClocktowerGameNightTaskPo task, RbacPrincipal principal) {
        Instant now = Instant.now();
        for (Map<String, Object> event : safeEvents(resolution.storytellerEvents())) {
            appendResolutionEvent(game, task, event, "STORYTELLER", List.of(), now);
        }
        for (Map<String, Object> event : safeEvents(resolution.privateInfos())) {
            Long recipientGameSeatId = firstNonNullLong(event.get("recipientGameSeatId"), event.get("targetGameSeatId"));
            appendResolutionEvent(game, task, event, "PRIVATE",
                    recipientGameSeatId == null ? List.of() : List.of(recipientGameSeatId), now);
        }
        for (Map<String, Object> event : safeEvents(resolution.publicEvents())) {
            applyPublicSideEffects(game, event);
            appendResolutionEvent(game, task, event, "PUBLIC", List.of(), now);
        }
    }

    private void applyPublicSideEffects(ClocktowerGamePo game, Map<String, Object> event) {
        if (!"PLAYER_DIED".equals(event.get("eventType"))) {
            return;
        }
        Long targetGameSeatId = longPayload(event, "targetGameSeatId");
        if (targetGameSeatId == null) {
            return;
        }
        ClocktowerGameSeatPo target = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(targetGameSeatId,
                        game.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
        target.setLifeStatus(LIFE_DEAD);
        target.setPublicLifeStatus(LIFE_DEAD);
        gameSeatRepository.saveAndFlush(target);
    }

    private void appendResolutionEvent(ClocktowerGamePo game, ClocktowerGameNightTaskPo task,
                                       Map<String, Object> event, String defaultVisibility,
                                       List<Long> defaultVisibleGameSeatIds, Instant occurredAt) {
        String eventType = stringPayload(event, "eventType");
        if (eventType == null) {
            eventType = "NIGHT_TASK_RESOLVED";
        }
        String visibility = stringPayload(event, "visibility");
        if (visibility == null) {
            visibility = defaultVisibility;
        }
        Long targetGameSeatId = longPayload(event, "targetGameSeatId");
        Map<String, Object> payload = new LinkedHashMap<>(event);
        payload.put("taskId", task.getId());
        payload.put("roleCode", task.getRoleCode());
        payload.put("taskType", task.getTaskType());
        eventAppender.append(game, eventType, task.getActorGameSeatId(), targetGameSeatId, visibility,
                defaultVisibleGameSeatIds, payload, occurredAt);
    }

    private boolean readyToResolve(ClocktowerGameNightTaskPo task) {
        if (STATUS_DONE.equals(task.getStatus()) || STATUS_SKIPPED.equals(task.getStatus())) {
            return false;
        }
        if (TASK_RECEIVE_INFO.equals(task.getTaskType()) && STATUS_PENDING.equals(task.getStatus())) {
            return true;
        }
        return TASK_CHOOSE_TARGET.equals(task.getTaskType()) && STATUS_CHOSEN.equals(task.getStatus());
    }

    private void requireCurrentNight(ClocktowerGamePo game, ClocktowerGameNightTaskPo task) {
        if (!Objects.equals(task.getGameId(), game.getId()) || task.getNightNo() != game.getNightNo()) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_CURRENT");
        }
    }

    private ClocktowerGamePo lockedGame(Long gameId) {
        if (gameId == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ID_REQUIRED");
        }
        return gameRepository.findLockedByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    }

    private void requireStoryteller(ClocktowerGamePo game, RbacPrincipal principal) {
        RoomSpacePo room = roomSpaceRepository.findByIdAndDeletedFalse(game.getRoomId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        accessPolicy.requireOwner(room, principal);
    }

    private List<Map<String, Object>> nightMarkers(List<ClocktowerGameNightTaskPo> currentTasks) {
        return currentTasks.stream()
                .map(ClocktowerGameNightTaskPo::getResultJson)
                .map(this::readMap)
                .filter(result -> result.containsKey("marker"))
                .toList();
    }

    private List<Map<String, Object>> safeEvents(List<Map<String, Object>> events) {
        return events == null ? List.of() : events;
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_JSON_INVALID");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_JSON_INVALID");
        }
    }

    private List<Long> longListPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::longValue)
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapPayload(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Long longPayload(Map<String, Object> payload, String key) {
        return payload == null ? null : longValue(payload.get(key));
    }

    private Long firstNonNullLong(Object first, Object second) {
        Long value = longValue(first);
        return value == null ? longValue(second) : value;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }
}
