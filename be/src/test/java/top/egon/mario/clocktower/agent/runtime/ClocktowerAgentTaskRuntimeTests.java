package top.egon.mario.clocktower.agent.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.runtime.po.ClocktowerAgentTaskPo;
import top.egon.mario.clocktower.agent.runtime.repository.ClocktowerAgentTaskRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "clocktower.agent.default-response-delay-ms=0",
        "clocktower.agent.worker.runner.enabled=false"
})
class ClocktowerAgentTaskRuntimeTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Autowired
    private ClocktowerAgentTaskRepository agentTaskRepository;

    @Autowired
    private ClocktowerAgentTaskScheduler taskScheduler;

    @Autowired
    private ClocktowerAgentTaskWorker taskWorker;

    @Autowired
    private ClocktowerPublicMicService micService;

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerGameNightTaskRepository nightTaskRepository;

    @Autowired
    private ClocktowerGameEventAppender eventAppender;

    @Test
    void gameStartedSchedulesAllAgentTasksAfterCommit() {
        StartedGame game = startFirstNightGameWithAgents(4);

        List<ClocktowerAgentTaskPo> tasks = agentTaskRepository
                .findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                        game.gameId(), ClocktowerAgentTriggerType.GAME_STARTED);

        assertThat(tasks).hasSize(4);
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.PENDING);
            assertThat(task.getTriggerKey()).isEqualTo("game:%s:started".formatted(game.gameId()));
            assertThat(task.getMetadataJson()).contains("eventSeq");
        });
    }

    @Test
    void scheduleTaskIsIdempotentForSameTrigger() {
        StartedGame game = startFirstNightGameWithAgents(4);
        ClocktowerGameSeatPo agentSeat = game.agentSeats().getFirst();
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(agentSeat.getId())
                .orElseThrow();

        ClocktowerAgentTaskPo first = taskScheduler.scheduleForAgent(game.gameId(), instance.getId(),
                agentSeat.getId(), ClocktowerAgentTriggerType.PHASE_CHANGED,
                "phase:%s:DAY:manual".formatted(game.gameId()), Map.of("phase", "DAY"));
        ClocktowerAgentTaskPo second = taskScheduler.scheduleForAgent(game.gameId(), instance.getId(),
                agentSeat.getId(), ClocktowerAgentTriggerType.PHASE_CHANGED,
                "phase:%s:DAY:manual".formatted(game.gameId()), Map.of("phase", "DAY"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(agentTaskRepository.findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                game.gameId(), ClocktowerAgentTriggerType.PHASE_CHANGED))
                .extracting(ClocktowerAgentTaskPo::getTriggerKey)
                .containsOnly("phase:%s:DAY:manual".formatted(game.gameId()));
    }

    @Test
    void micTurnStartedSchedulesOnlyCurrentAgent() {
        StartedGame game = startDayGameWithAgents(4);
        ClocktowerGameSeatPo humanSeat = game.humanSeat();
        ClocktowerGameSeatPo firstAgentSeat = game.agentSeats().getFirst();
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(firstAgentSeat.getId())
                .orElseThrow();

        micService.startDayMicSession(game.gameId(), owner());
        assertThat(agentTaskRepository.findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                game.gameId(), ClocktowerAgentTriggerType.MIC_TURN_STARTED)).isEmpty();

        humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PASS", List.of(),
                        null, null, null, Map.of("passType", "MIC_TURN")),
                principal(11L, "player1"));

        List<ClocktowerAgentTaskPo> tasks = agentTaskRepository
                .findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                        game.gameId(), ClocktowerAgentTriggerType.MIC_TURN_STARTED);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getAgentInstanceId()).isEqualTo(instance.getId());
        assertThat(tasks.getFirst().getGameSeatId()).isEqualTo(firstAgentSeat.getId());
        assertThat(tasks.getFirst().getMetadataJson()).contains("turnId");
    }

    @Test
    void workerProcessesMicTurnPassAndMarksTaskDone() {
        StartedGame game = startDayGameWithAgents(4);
        ClocktowerGameSeatPo humanSeat = game.humanSeat();
        ClocktowerGameSeatPo firstAgentSeat = game.agentSeats().getFirst();
        micService.startDayMicSession(game.gameId(), owner());
        humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PASS", List.of(),
                        null, null, null, Map.of("passType", "MIC_TURN")),
                principal(11L, "player1"));
        ClocktowerAgentTaskPo micTask = agentTaskRepository
                .findByGameIdAndTriggerTypeAndDeletedFalseOrderByIdAsc(
                        game.gameId(), ClocktowerAgentTriggerType.MIC_TURN_STARTED)
                .getFirst();
        eventAppender.append(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow(),
                "PUBLIC_SPEECH", humanSeat.getId(), null,
                "PUBLIC", List.of(), Map.of("content", "我是共情者。"), Instant.now());

        int processed = taskWorker.processBatch("test-worker", 20);

        ClocktowerAgentTaskPo reloaded = agentTaskRepository.findByIdAndDeletedFalse(micTask.getId()).orElseThrow();
        ClocktowerAgentInstancePo refreshedInstance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(firstAgentSeat.getId())
                .orElseThrow();
        assertThat(processed).isGreaterThanOrEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.DONE);
        assertThat(reloaded.getResultJson()).contains("PLAYER_PASSED");
        assertThat(refreshedInstance.getMetadataJson()).contains("lastSeenEventSeq");
        assertThat(micService.canSpeak(game.gameId(), firstAgentSeat.getId())).isFalse();
    }

    @Test
    void pausedAndApprovalAgentsSkipRuntimeExecution() {
        StartedGame game = startDayGameWithAgents(4);
        ClocktowerGameSeatPo pausedSeat = game.agentSeats().getFirst();
        ClocktowerGameSeatPo approvalSeat = game.agentSeats().get(1);
        ClocktowerAgentInstancePo paused = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(pausedSeat.getId())
                .orElseThrow();
        ClocktowerAgentInstancePo approval = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(approvalSeat.getId())
                .orElseThrow();
        paused.setAutoMode(ClocktowerAgentAutoMode.PAUSED);
        approval.setAutoMode(ClocktowerAgentAutoMode.ST_APPROVAL);
        agentInstanceRepository.saveAllAndFlush(List.of(paused, approval));

        ClocktowerAgentTaskPo pausedTask = taskScheduler.scheduleForAgent(game.gameId(), paused.getId(),
                pausedSeat.getId(), ClocktowerAgentTriggerType.PHASE_CHANGED,
                "phase:%s:paused".formatted(game.gameId()), Map.of("phase", "DAY"));
        ClocktowerAgentTaskPo approvalTask = taskScheduler.scheduleForAgent(game.gameId(), approval.getId(),
                approvalSeat.getId(), ClocktowerAgentTriggerType.PHASE_CHANGED,
                "phase:%s:approval".formatted(game.gameId()), Map.of("phase", "DAY"));

        taskWorker.processBatch("test-worker", 20);

        ClocktowerAgentTaskPo reloadedPaused = agentTaskRepository.findByIdAndDeletedFalse(pausedTask.getId())
                .orElseThrow();
        ClocktowerAgentTaskPo reloadedApproval = agentTaskRepository.findByIdAndDeletedFalse(approvalTask.getId())
                .orElseThrow();
        assertThat(reloadedPaused.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.CANCELLED);
        assertThat(reloadedPaused.getResultJson()).contains("AUTO_MODE_PAUSED");
        assertThat(reloadedApproval.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.CANCELLED);
        assertThat(reloadedApproval.getResultJson()).contains("AUTO_MODE_REQUIRES_ST_APPROVAL");
    }

    @Test
    void nightTaskOpenedSchedulesOwningAgentAndWorkerAutoChooses() {
        StartedGame game = startFirstNightGameWithAgents(4);
        ClocktowerGameSeatPo firstAgentSeat = game.agentSeats().getFirst();
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(firstAgentSeat.getId())
                .orElseThrow();
        ClocktowerGameNightTaskPo nightTask = nightTaskRepository
                .findByGameIdAndNightNoAndActorGameSeatIdAndDeletedFalseOrderBySortOrderAscIdAsc(
                        game.gameId(), 1, firstAgentSeat.getId())
                .getFirst();

        ClocktowerAgentTaskPo queued = agentTaskRepository
                .findByGameIdAndTriggerTypeAndAgentInstanceIdAndDeletedFalse(
                        game.gameId(), ClocktowerAgentTriggerType.NIGHT_TASK_OPENED, instance.getId())
                .orElseThrow();

        taskWorker.processBatch("test-worker", 20);

        ClocktowerAgentTaskPo processed = agentTaskRepository.findByIdAndDeletedFalse(queued.getId()).orElseThrow();
        ClocktowerGameNightTaskPo chosen = nightTaskRepository.findById(nightTask.getId()).orElseThrow();
        assertThat(queued.getMetadataJson()).contains(nightTask.getId().toString());
        assertThat(processed.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.DONE);
        assertThat(chosen.getStatus()).isEqualTo("CHOSEN");
    }

    @Test
    void failedTaskRetriesUntilMaxAttemptsAndRecordsLastError() {
        StartedGame game = startDayGameWithAgents(4);
        ClocktowerAgentTaskPo task = new ClocktowerAgentTaskPo();
        task.setGameId(game.gameId());
        task.setAgentInstanceId(-991L);
        task.setGameSeatId(game.agentSeats().getFirst().getId());
        task.setTriggerType(ClocktowerAgentTriggerType.PHASE_CHANGED);
        task.setTriggerKey("phase:%s:invalid-agent".formatted(game.gameId()));
        task.setStatus(ClocktowerAgentTaskStatus.PENDING);
        task.setPriority(0);
        task.setAvailableAt(Instant.EPOCH);
        task.setMetadataJson("{}");
        task.setResultJson("{}");
        task = agentTaskRepository.saveAndFlush(task);

        for (int index = 0; index < 3; index++) {
            taskWorker.processBatch("test-worker", 1);
            ClocktowerAgentTaskPo reloaded = agentTaskRepository.findByIdAndDeletedFalse(task.getId()).orElseThrow();
            if (!ClocktowerAgentTaskStatus.FAILED.equals(reloaded.getStatus())) {
                reloaded.setAvailableAt(Instant.EPOCH);
                agentTaskRepository.saveAndFlush(reloaded);
            }
        }

        ClocktowerAgentTaskPo failed = agentTaskRepository.findByIdAndDeletedFalse(task.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ClocktowerAgentTaskStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getLastError()).contains("CLOCKTOWER_AGENT_INSTANCE_INVALID");
    }

    private StartedGame startFirstNightGameWithAgents(int agentSeatCount) {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(agentSeatCount), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        ClocktowerGameResponse started = gameService.startGame(room.roomId(), owner());
        return new StartedGame(room.roomId(), started.gameId(),
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(started.gameId()));
    }

    private StartedGame startDayGameWithAgents(int agentSeatCount) {
        StartedGame started = startFirstNightGameWithAgents(agentSeatCount);
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(started.gameId()).orElseThrow();
        game.setPhase("DAY");
        game.setDayNo(1);
        gameRepository.saveAndFlush(game);
        return started;
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

    private ClocktowerRoomCreateRequest createRequest(int agentSeatCount) {
        return new ClocktowerRoomCreateRequest(
                "Task 10 Runtime " + System.nanoTime(),
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

        private ClocktowerGameSeatPo humanSeat() {
            return seats.getFirst();
        }

        private List<ClocktowerGameSeatPo> agentSeats() {
            return seats.stream()
                    .filter(seat -> "AGENT".equals(seat.getActorType()))
                    .toList();
        }
    }
}
