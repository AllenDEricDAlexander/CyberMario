package top.egon.mario.clocktower.agent.control;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentConsoleView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentMemoryView;
import top.egon.mario.clocktower.agent.control.dto.ClocktowerAgentTaskView;
import top.egon.mario.clocktower.agent.control.service.ClocktowerAgentControlService;
import top.egon.mario.clocktower.agent.memory.po.ClocktowerAgentMemoryPo;
import top.egon.mario.clocktower.agent.memory.repository.ClocktowerAgentMemoryRepository;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.runtime.ClocktowerAgentTaskStatus;
import top.egon.mario.clocktower.agent.runtime.ClocktowerAgentTriggerType;
import top.egon.mario.clocktower.agent.runtime.repository.ClocktowerAgentTaskRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent.worker.runner.enabled=false"
})
@Transactional
class ClocktowerAgentControlServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerAgentControlService controlService;

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Autowired
    private ClocktowerAgentTaskRepository agentTaskRepository;

    @Autowired
    private ClocktowerAgentMemoryRepository agentMemoryRepository;

    @Autowired
    private ClocktowerGameEventRepository gameEventRepository;

    @Test
    void pauseAgent_setsAutoModePausedAndAppendsEvent() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());

        ClocktowerAgentConsoleView paused = controlService.pauseAgent(game.gameId(), instance.getId(), owner());

        assertThat(paused.autoMode()).isEqualTo(ClocktowerAgentAutoMode.PAUSED);
        assertThat(agentInstanceRepository.findByIdAndDeletedFalse(instance.getId()).orElseThrow().getAutoMode())
                .isEqualTo(ClocktowerAgentAutoMode.PAUSED);
        assertThat(eventTypes(game.gameId())).contains("AGENT_PAUSED_BY_ST");
    }

    @Test
    void resumeAgent_allowsFullAutoAgainAndAppendsEvent() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());
        controlService.pauseAgent(game.gameId(), instance.getId(), owner());

        ClocktowerAgentConsoleView resumed = controlService.resumeAgent(game.gameId(), instance.getId(), owner());

        assertThat(resumed.autoMode()).isEqualTo(ClocktowerAgentAutoMode.FULL_AUTO);
        assertThat(eventTypes(game.gameId())).contains("AGENT_RESUMED_BY_ST");
    }

    @Test
    void runNowAgent_createsImmediateTaskAndEvent() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());

        ClocktowerAgentTaskView task = controlService.runNow(game.gameId(), instance.getId(), owner());

        assertThat(task.triggerType()).isEqualTo(ClocktowerAgentTriggerType.ST_RUN_NOW);
        assertThat(task.status()).isEqualTo(ClocktowerAgentTaskStatus.PENDING);
        assertThat(task.availableAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(eventTypes(game.gameId())).contains("AGENT_RUN_NOW_REQUESTED_BY_ST");
    }

    @Test
    void listAgentsIncludesSeatRoleAndRecentTask() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());
        controlService.runNow(game.gameId(), instance.getId(), owner());

        List<ClocktowerAgentConsoleView> agents = controlService.listAgents(game.gameId(), owner());

        assertThat(agents).hasSize(4);
        assertThat(agents.getFirst().seatNo()).isGreaterThan(1);
        assertThat(agents.getFirst().roleCode()).isNotBlank();
        assertThat(agents.getFirst().recentTaskStatus()).isEqualTo(ClocktowerAgentTaskStatus.PENDING);
    }

    @Test
    void memoryAndTasksAreScopedToSelectedAgentAndGame() {
        StartedGame game = startGameWithAgents(4);
        ClocktowerAgentInstancePo instance = firstAgent(game.gameId());
        ClocktowerAgentTaskView task = controlService.runNow(game.gameId(), instance.getId(), owner());
        createMemory(game.gameId(), instance);

        assertThat(controlService.listTasks(game.gameId(), instance.getId(), owner()))
                .extracting(ClocktowerAgentTaskView::taskId)
                .contains(task.taskId());
        assertThat(controlService.listMemory(game.gameId(), instance.getId(), owner()))
                .extracting(ClocktowerAgentMemoryView::memoryType)
                .contains("OBSERVATION");
    }

    private StartedGame startGameWithAgents(int agentSeatCount) {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(agentSeatCount), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        ClocktowerGameResponse started = gameService.startGame(room.roomId(), owner());
        return new StartedGame(room.roomId(), started.gameId(),
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(started.gameId()));
    }

    private void assignReadyRoles(Long roomId) {
        List<ClocktowerRoomSeatPo> seats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId);
        for (int index = 0; index < seats.size(); index++) {
            ClocktowerRoomSeatPo seat = seats.get(index);
            seat.setRoleCode(ROLE_CODES.get(index));
            seat.setMetadataJson(readyMetadata(seat.getMetadataJson()));
        }
        roomSeatRepository.saveAllAndFlush(seats);
    }

    private String readyMetadata(String metadataJson) {
        if (metadataJson != null && metadataJson.contains("\"agentSeat\":true")) {
            return metadataJson;
        }
        return "{\"ready\":true}";
    }

    private ClocktowerAgentInstancePo firstAgent(Long gameId) {
        return agentInstanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(gameId).getFirst();
    }

    private void createMemory(Long gameId, ClocktowerAgentInstancePo instance) {
        ClocktowerAgentMemoryPo memory = new ClocktowerAgentMemoryPo();
        memory.setGameId(gameId);
        memory.setAgentInstanceId(instance.getId());
        memory.setGameSeatId(instance.getGameSeatId());
        memory.setMemoryType("OBSERVATION");
        memory.setContentJson("{\"summary\":\"seat 1 claimed good\"}");
        memory.setDayNo(1);
        memory.setNightNo(1);
        agentMemoryRepository.saveAndFlush(memory);
    }

    private List<String> eventTypes(Long gameId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
    }

    private ClocktowerRoomCreateRequest createRequest(int agentSeatCount) {
        return new ClocktowerRoomCreateRequest(
                "Task 14 Control " + System.nanoTime(),
                ClocktowerScriptCode.TROUBLE_BREWING,
                ROLE_CODES.size(),
                null,
                null,
                ROLE_CODES,
                "HUMAN",
                true,
                true,
                agentSeatCount,
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
