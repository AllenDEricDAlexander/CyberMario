package top.egon.mario.clocktower.game.night.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.action.dto.ActorContext;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.dto.GameActionCommand;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightSkipRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.night.role.NightTaskContext;
import top.egon.mario.clocktower.game.night.role.NightTaskSpec;
import top.egon.mario.clocktower.game.night.role.RoleSkill;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightOrderService;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerGameNightTaskServiceImpl implements ClocktowerGameNightTaskService {

    private static final String PHASE_FIRST_NIGHT = "FIRST_NIGHT";
    private static final String PHASE_NIGHT = "NIGHT";
    private static final String STATUS_PENDING = "PENDING";

    private final ClocktowerGameSeatRepository gameSeatRepository;
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
    public ClocktowerGameActionResponse submitChoice(ClocktowerGamePo game, GameActionCommand command,
                                                    ActorContext actor) {
        return ClocktowerGameActionResponse.rejected("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
    }

    @Override
    public ClocktowerGameActionResponse autoChooseTask(Long gameId, Long taskId, Long agentInstanceId) {
        return ClocktowerGameActionResponse.rejected("CLOCKTOWER_NIGHT_TASK_SERVICE_NOT_READY");
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_JSON_INVALID");
        }
    }
}
