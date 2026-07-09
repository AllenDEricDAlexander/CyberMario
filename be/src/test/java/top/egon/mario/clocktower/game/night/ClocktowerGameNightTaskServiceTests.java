package top.egon.mario.clocktower.game.night;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameFlowService;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightSkipRequest;
import top.egon.mario.clocktower.game.night.dto.ClocktowerNightTaskView;
import top.egon.mario.clocktower.game.night.web.ClocktowerGameNightTaskController;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.clocktower.game.night.service.ClocktowerRoleSkillRegistry;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameNightTaskServiceTests {

    @Autowired(required = false)
    private ClocktowerGameNightTaskService taskService;

    @Autowired(required = false)
    private ClocktowerNightResolutionService resolutionService;

    @Autowired(required = false)
    private ClocktowerRoleSkillRegistry roleSkillRegistry;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerGameFlowService flowService;

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerGameNightTaskRepository nightTaskRepository;

    @Autowired(required = false)
    private ClocktowerGameNightTaskController nightTaskController;

    @Test
    void nightServicesAreAvailable() {
        assertThat(taskService).isNotNull();
        assertThat(resolutionService).isNotNull();
        assertThat(roleSkillRegistry).isNotNull();
        assertThat(roleSkillRegistry.find("POISONER")).isPresent();
        assertThat(roleSkillRegistry.find("IMP")).isPresent();
        assertThat(nightTaskController).isNotNull();
    }

    @Test
    void createFirstNightTasksTroubleBrewingOrdered() {
        StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "IMP"));

        List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.gameId(), 1);

        assertThat(tasks).extracting(ClocktowerGameNightTaskPo::getRoleCode)
                .containsExactly("POISONER", "CHEF", "EMPATH", "FORTUNETELLER");
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo("PENDING");
            assertThat(task.isMandatory()).isTrue();
            assertThat(task.getTaskKey()).contains(task.getRoleCode()).contains(task.getActorGameSeatId().toString());
        });
        assertThat(tasks).extracting(ClocktowerGameNightTaskPo::getTaskType)
                .containsExactly("CHOOSE_TARGET", "RECEIVE_INFO", "RECEIVE_INFO", "CHOOSE_TARGET");
    }

    @Test
    void initializeNightTasksIsIdempotent() {
        StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "IMP"));
        ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();

        taskService.initializeNightTasks(entity);
        taskService.initializeNightTasks(entity);

        assertThat(nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
                game.gameId(), 1)).hasSize(4);
    }

    @Test
    void createOtherNightTasksTroubleBrewingOrdered() {
        StartedGame game = startGameWithRoles(List.of("POISONER", "MONK", "IMP", "EMPATH",
                "BUTLER", "CHEF", "WASHERWOMAN", "RAVENKEEPER"));
        ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
        entity.setPhase("NIGHT");
        entity.setNightNo(2);
        gameRepository.saveAndFlush(entity);

        taskService.initializeNightTasks(entity);

        List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(game.gameId(), 2);
        assertThat(tasks).extracting(ClocktowerGameNightTaskPo::getRoleCode)
                .containsExactly("POISONER", "MONK", "IMP", "EMPATH", "BUTLER");
    }

    @Test
    void storytellerListsCurrentNightTasks() {
        StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "IMP"));

        List<ClocktowerNightTaskView> tasks = taskService.currentTasks(game.gameId(), owner());

        assertThat(tasks).extracting(ClocktowerNightTaskView::roleCode)
                .containsExactly("POISONER", "CHEF", "EMPATH", "FORTUNETELLER");
    }

    @Test
    void storytellerSkipsNightTasksAndFlowCanEnterDay() {
        StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "IMP"));

        taskService.currentTasks(game.gameId(), owner())
                .forEach(task -> taskService.skipTask(game.gameId(), task.taskId(),
                        new ClocktowerNightSkipRequest("manual skip"), owner()));
        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(),
                new ClocktowerGameAdvanceRequest(null, null, java.util.Map.of()), owner());

        assertThat(result.previousPhase()).isEqualTo("FIRST_NIGHT");
        assertThat(result.phase()).isEqualTo("DAY");
    }

    @Test
    void humanNightChoiceSubmitsChoice() {
        StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "IMP"));
        ClocktowerGameSeatPo poisoner = game.seats().getFirst();
        ClocktowerGameNightTaskPo task = taskFor(game.gameId(), "POISONER");

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(poisoner.getId(), "NIGHT_CHOICE",
                        List.of(game.seats().get(1).getId()), null, null, null,
                        java.util.Map.of("taskId", task.getId())),
                principal(20L, "player1"));

        assertThat(response.accepted()).isTrue();
        assertThat(response.event().eventType()).isEqualTo("NIGHT_CHOICE_SUBMITTED");
        ClocktowerGameNightTaskPo reloaded = nightTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("CHOSEN");
        assertThat(reloaded.getChoiceJson()).contains(game.seats().get(1).getId().toString());
    }

    private StartedGame startGameWithRoles(List<String> roleCodes) {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(roleCodes), owner());
        for (int index = 0; index < roleCodes.size(); index++) {
            roomService.claimSeat(room.roomId(), index + 1, new ClocktowerSeatClaimRequest("Player " + (index + 1)),
                    principal(20L + index, "player" + (index + 1)));
        }
        assignReadyRoles(room.roomId(), roleCodes);
        ClocktowerGameResponse started = gameService.startGame(room.roomId(), owner());
        return new StartedGame(room.roomId(), started.gameId(),
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(started.gameId()));
    }

    private void assignReadyRoles(Long roomId, List<String> roleCodes) {
        List<ClocktowerRoomSeatPo> seats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId);
        for (int index = 0; index < roleCodes.size(); index++) {
            ClocktowerRoomSeatPo seat = seats.get(index);
            seat.setRoleCode(roleCodes.get(index));
            seat.setMetadataJson("{\"ready\":true}");
        }
        roomSeatRepository.saveAllAndFlush(seats);
    }

    private ClocktowerGameNightTaskPo taskFor(Long gameId, String roleCode) {
        return nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(gameId, 1)
                .stream()
                .filter(task -> roleCode.equals(task.getRoleCode()))
                .findFirst()
                .orElseThrow();
    }

    private ClocktowerRoomCreateRequest createRequest(List<String> roleCodes) {
        return new ClocktowerRoomCreateRequest(
                "Friday Clocktower",
                ClocktowerScriptCode.TROUBLE_BREWING,
                roleCodes.size(),
                null,
                null,
                roleCodes,
                "HUMAN",
                true,
                true,
                0,
                "PUBLIC",
                "OPEN_SEATING"
        );
    }

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
    }

    private record StartedGame(Long roomId, Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
