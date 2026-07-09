package top.egon.mario.clocktower.game.night.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightSkipRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.night.role.AvailableTargetSpec;
import top.egon.mario.clocktower.game.night.role.NightChoice;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightOrderService;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerGameNightTaskServiceImpl implements ClocktowerGameNightTaskService {

    private static final String PHASE_FIRST_NIGHT = "FIRST_NIGHT";
    private static final String PHASE_NIGHT = "NIGHT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CHOSEN = "CHOSEN";
    private static final String TASK_RECEIVE_INFO = "RECEIVE_INFO";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerAgentInstanceRepository agentInstanceRepository;
    private final ClocktowerGameNightTaskRepository nightTaskRepository;
    private final ClocktowerNightOrderService nightOrderService;
    private final ClocktowerRoleSkillRegistry roleSkillRegistry;
    private final ClocktowerGameEventAppender eventAppender;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void initializeNightTasks(ClocktowerGamePo game) {
        if (game == null || (!PHASE_FIRST_NIGHT.equals(game.getPhase()) && !PHASE_NIGHT.equals(game.getPhase()))) {
            return;
        }
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(
                game.getId());
        Map<String, ClocktowerGameSeatPo> seatByRole = seats.stream()
                .filter(seat -> seat.getRoleCode() != null)
                .collect(Collectors.toMap(ClocktowerGameSeatPo::getRoleCode, Function.identity(),
                        (left, right) -> left));
        List<ClocktowerGameNightTaskPo> existingTasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.getId(), game.getNightNo());
        List<Map<String, Object>> created = new ArrayList<>();
        List<String> skippedRoles = new ArrayList<>();
        for (ClocktowerNightOrderPo order : nightOrderService.currentOrders(game, seats)) {
            ClocktowerGameSeatPo seat = seatByRole.get(order.getRoleCode());
            if (seat == null) {
                continue;
            }
            RoleSkill skill = roleSkillRegistry.find(order.getRoleCode()).orElse(null);
            if (skill == null || !actsThisNight(game, skill)) {
                skippedRoles.add(order.getRoleCode());
                continue;
            }
            NightTaskContext context = new NightTaskContext(game, null, seat, seats, existingTasks, Map.of());
            for (NightTaskSpec spec : skill.createTasks(context)) {
                String taskKey = order.getRoleCode() + ":" + seat.getId() + ":" + spec.taskType();
                if (nightTaskRepository.findByGameIdAndNightNoAndTaskKeyAndDeletedFalse(
                        game.getId(), game.getNightNo(), taskKey).isPresent()) {
                    continue;
                }
                ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
                task.setGameId(game.getId());
                task.setNightNo(game.getNightNo());
                task.setTaskKey(taskKey);
                task.setActorGameSeatId(seat.getId());
                task.setRoleCode(order.getRoleCode());
                task.setTaskType(spec.taskType());
                task.setStatus(STATUS_PENDING);
                task.setMandatory(spec.mandatory());
                task.setSortOrder(order.getSortOrder());
                task.setMetadataJson(writeJson(spec.metadata() == null ? Map.of() : spec.metadata()));
                nightTaskRepository.save(task);
                created.add(createdPayload(taskKey, order.getRoleCode(), spec.taskType()));
            }
        }
        nightTaskRepository.flush();
        if (!created.isEmpty() || !skippedRoles.isEmpty()) {
            eventAppender.append(game, "NIGHT_TASKS_CREATED", null, null, "STORYTELLER", List.of(),
                    Map.of("nightNo", game.getNightNo(), "created", created, "skippedRoles", skippedRoles),
                    Instant.now());
        }
    }

    @Override
    public List<ClocktowerNightTaskView> currentTasks(Long gameId, RbacPrincipal principal) {
        return List.of();
    }

    @Override
    @Transactional
    public ClocktowerGameActionResponse submitChoice(ClocktowerGamePo game, GameActionCommand command,
                                                    ActorContext actor) {
        if (!PHASE_FIRST_NIGHT.equals(game.getPhase()) && !PHASE_NIGHT.equals(game.getPhase())) {
            return reject(game, command.actorGameSeatId(), "NIGHT_CHOICE", "CLOCKTOWER_NIGHT_CHOICE_PHASE_INVALID");
        }
        Long taskId = longPayload(command.payload(), "taskId");
        if (taskId == null) {
            return reject(game, command.actorGameSeatId(), "NIGHT_CHOICE", "CLOCKTOWER_NIGHT_TASK_REQUIRED");
        }
        ClocktowerGameNightTaskPo task = nightTaskRepository.findLockedByIdAndGameIdAndDeletedFalse(
                taskId, game.getId()).orElse(null);
        if (task == null || task.getNightNo() != game.getNightNo()) {
            return reject(game, command.actorGameSeatId(), "NIGHT_CHOICE", "CLOCKTOWER_NIGHT_TASK_NOT_CURRENT");
        }
        if (!Objects.equals(task.getActorGameSeatId(), command.actorGameSeatId())) {
            return reject(game, command.actorGameSeatId(), "NIGHT_CHOICE", "CLOCKTOWER_NIGHT_TASK_NOT_OWNED");
        }
        if (!STATUS_PENDING.equals(task.getStatus())) {
            return reject(game, command.actorGameSeatId(), "NIGHT_CHOICE",
                    "CLOCKTOWER_NIGHT_TASK_ALREADY_RESOLVED");
        }
        ClocktowerGameSeatPo actorSeat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
                command.actorGameSeatId(), game.getId()).orElseThrow(
                        () -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(
                game.getId());
        RoleSkill skill = roleSkillRegistry.find(task.getRoleCode()).orElse(null);
        if (skill == null) {
            return reject(game, command.actorGameSeatId(), "NIGHT_CHOICE", "CLOCKTOWER_NIGHT_ROLE_UNSUPPORTED");
        }
        NightChoice choice = new NightChoice(command.targetGameSeatIds() == null
                ? List.of() : command.targetGameSeatIds(), command.payload() == null ? Map.of() : command.payload());
        ClocktowerGameActionResponse targetValidation = validateTargets(game, task, actorSeat, seats, skill, choice);
        if (targetValidation != null) {
            return targetValidation;
        }
        Map<String, Object> choicePayload = new LinkedHashMap<>();
        choicePayload.put("targetGameSeatIds", choice.targetGameSeatIds());
        choicePayload.put("payload", choice.payload());
        task.setChoiceJson(writeJson(choicePayload));
        task.setStatus(STATUS_CHOSEN);
        ClocktowerGameNightTaskPo saved = nightTaskRepository.saveAndFlush(task);
        ClocktowerGameEventResponse event = eventAppender.append(game, "NIGHT_CHOICE_SUBMITTED",
                actorSeat.getId(), null, "PRIVATE", List.of(actorSeat.getId()),
                Map.of("taskId", saved.getId(), "roleCode", saved.getRoleCode(), "taskType", saved.getTaskType(),
                        "actorType", actor.actorType()),
                Instant.now());
        return ClocktowerGameActionResponse.accepted(event);
    }

    @Override
    @Transactional
    public ClocktowerGameActionResponse autoChooseTask(Long gameId, Long taskId, Long agentInstanceId) {
        ClocktowerAgentInstancePo instance = agentInstanceRepository.findByIdAndDeletedFalse(agentInstanceId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_INSTANCE_INVALID"));
        ClocktowerGameNightTaskPo task = nightTaskRepository.findByIdAndGameIdAndDeletedFalse(taskId, gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_FOUND"));
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        ClocktowerGameSeatPo seat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(
                task.getActorGameSeatId(), gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
        if (!Objects.equals(instance.getGameSeatId(), seat.getId())) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
        }
        RoleSkill skill = roleSkillRegistry.find(task.getRoleCode())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NIGHT_ROLE_UNSUPPORTED"));
        List<ClocktowerGameSeatPo> seats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId);
        NightChoice choice = skill.autoChoose(new NightTaskContext(game, task, seat, seats,
                nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
                        gameId, game.getNightNo()),
                readMap(task.getMetadataJson())));
        return submitChoice(game, new GameActionCommand(gameId, seat.getId(), "NIGHT_CHOICE",
                        choice.targetGameSeatIds(), null, null, null, Map.of("taskId", taskId)),
                ActorContext.agent(instance.getActorId(), instance.getId()));
    }

    @Override
    public ClocktowerNightTaskView skipTask(Long gameId, Long taskId, ClocktowerNightSkipRequest request,
                                           RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }

    private boolean actsThisNight(ClocktowerGamePo game, RoleSkill skill) {
        return game.getNightNo() <= 1 ? skill.actsOnFirstNight() : skill.actsOnOtherNights();
    }

    private Map<String, Object> createdPayload(String taskKey, String roleCode, String taskType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskKey", taskKey);
        payload.put("roleCode", roleCode);
        payload.put("taskType", taskType);
        return payload;
    }

    private ClocktowerGameActionResponse validateTargets(ClocktowerGamePo game,
                                                         ClocktowerGameNightTaskPo task,
                                                         ClocktowerGameSeatPo actorSeat,
                                                         List<ClocktowerGameSeatPo> seats,
                                                         RoleSkill skill,
                                                         NightChoice choice) {
        NightTaskContext context = new NightTaskContext(game, task, actorSeat, seats,
                nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
                        game.getId(), game.getNightNo()),
                readMap(task.getMetadataJson()));
        List<AvailableTargetSpec> legalTargets = skill.legalTargets(context);
        if (TASK_RECEIVE_INFO.equals(task.getTaskType()) && choice.targetGameSeatIds().isEmpty()) {
            return null;
        }
        if (choice.targetGameSeatIds().isEmpty()) {
            return reject(game, actorSeat.getId(), "NIGHT_CHOICE", "CLOCKTOWER_NIGHT_TARGET_COUNT_INVALID");
        }
        Set<Long> legalIds = legalTargets.stream()
                .filter(AvailableTargetSpec::selectable)
                .map(AvailableTargetSpec::gameSeatId)
                .collect(Collectors.toSet());
        if (!legalIds.containsAll(choice.targetGameSeatIds())) {
            return reject(game, actorSeat.getId(), "NIGHT_CHOICE", "CLOCKTOWER_NIGHT_TARGET_INVALID");
        }
        return null;
    }

    private ClocktowerGameActionResponse reject(ClocktowerGamePo game, Long actorGameSeatId,
                                                String actionType, String rejectedCode) {
        eventAppender.append(game, "ACTION_REJECTED", actorGameSeatId, null, "PRIVATE", List.of(actorGameSeatId),
                Map.of("actionType", actionType, "rejectedCode", rejectedCode), Instant.now());
        return ClocktowerGameActionResponse.rejected(rejectedCode);
    }

    private Long longPayload(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
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
}
