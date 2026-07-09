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
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.night.service.ClocktowerGameNightTaskService;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightResolutionService;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerNightResolutionServiceTests {

    @Autowired
    private ClocktowerNightResolutionService resolutionService;

    @Autowired
    private ClocktowerGameNightTaskService taskService;

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerGameEventRepository gameEventRepository;

    @Autowired
    private ClocktowerGameNightTaskRepository nightTaskRepository;

    @Test
    void poisonerAppliesMarker() {
        StartedGame game = startGameWithRoles(List.of("POISONER", "CHEF", "EMPATH", "FORTUNETELLER", "IMP"));
        ClocktowerGameNightTaskPo poisoner = chooseTask(game, "POISONER", game.seats().get(1).getId());

        resolutionService.resolveReady(game.gameId(), owner());

        ClocktowerGameNightTaskPo reloaded = nightTaskRepository.findById(poisoner.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("DONE");
        assertThat(reloaded.getResultJson()).contains("POISONED");
        assertThat(eventTypes(game.gameId())).contains("MARKER_APPLIED");
    }

    @Test
    void impKillProtectedByMonkNoDeath() {
        StartedGame game = startNightTwoWithRoles(List.of("POISONER", "MONK", "IMP", "EMPATH",
                "BUTLER", "CHEF", "WASHERWOMAN", "RAVENKEEPER"));
        Long targetId = game.seats().getFirst().getId();
        chooseTask(game, "MONK", targetId);
        chooseTask(game, "IMP", targetId);

        resolutionService.resolveReady(game.gameId(), owner());

        assertThat(gameSeatRepository.findByIdAndDeletedFalse(targetId).orElseThrow().getLifeStatus())
                .isEqualTo("ALIVE");
        assertThat(eventTypes(game.gameId())).contains("DEMON_KILL_PROTECTED");
    }

    @Test
    void impKillUnprotectedMarksDead() {
        StartedGame game = startNightTwoWithRoles(List.of("POISONER", "MONK", "IMP", "EMPATH",
                "BUTLER", "CHEF", "WASHERWOMAN", "RAVENKEEPER"));
        Long targetId = game.seats().getFirst().getId();
        chooseTask(game, "MONK", game.seats().get(3).getId());
        chooseTask(game, "IMP", targetId);

        resolutionService.resolveReady(game.gameId(), owner());

        ClocktowerGameSeatPo target = gameSeatRepository.findByIdAndDeletedFalse(targetId).orElseThrow();
        assertThat(target.getLifeStatus()).isEqualTo("DEAD");
        assertThat(target.getPublicLifeStatus()).isEqualTo("DEAD");
        assertThat(eventTypes(game.gameId())).contains("PLAYER_DIED");
    }

    private StartedGame startNightTwoWithRoles(List<String> roleCodes) {
        StartedGame game = startGameWithRoles(roleCodes);
        ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
        entity.setPhase("NIGHT");
        entity.setNightNo(2);
        gameRepository.saveAndFlush(entity);
        taskService.initializeNightTasks(entity);
        return game;
    }

    private ClocktowerGameNightTaskPo chooseTask(StartedGame game, String roleCode, Long targetGameSeatId) {
        ClocktowerGameNightTaskPo task = taskFor(game.gameId(), roleCode);
        ClocktowerGameSeatPo actor = game.seats().stream()
                .filter(seat -> roleCode.equals(seat.getRoleCode()))
                .findFirst()
                .orElseThrow();
        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(actor.getId(), "NIGHT_CHOICE", List.of(targetGameSeatId),
                        null, null, null, Map.of("taskId", task.getId())),
                principal(actor.getUserId(), actor.getDisplayName()));
        assertThat(response.accepted()).isTrue();
        return nightTaskRepository.findById(task.getId()).orElseThrow();
    }

    private ClocktowerGameNightTaskPo taskFor(Long gameId, String roleCode) {
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId).orElseThrow();
        return nightTaskRepository.findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(
                        gameId, game.getNightNo())
                .stream()
                .filter(task -> roleCode.equals(task.getRoleCode()))
                .findFirst()
                .orElseThrow();
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

    private List<String> eventTypes(Long gameId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
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
