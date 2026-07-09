package top.egon.mario.clocktower.game.flow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameFlowView;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameFlowService;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.night.po.ClocktowerGameNightTaskPo;
import top.egon.mario.clocktower.game.night.repository.ClocktowerGameNightTaskRepository;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResolveRequest;
import top.egon.mario.clocktower.game.nomination.service.ClocktowerGameExecutionService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameFlowServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerGameFlowService flowService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerPublicMicService micService;

    @Autowired
    private ClocktowerGameExecutionService executionService;

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

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
    void advanceNominationOpenNominationRejected() {
        StartedGame game = startNominationGameWithOpenNomination();

        ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

        assertThat(flow.advanceAllowed()).isFalse();
        assertThat(flow.blockingReasons()).contains("OPEN_NOMINATION_EXISTS");
    }

    @Test
    void advanceNominationExecutionUnresolvedRejected() {
        StartedGame game = startNominationGameWithClosedNomination();

        ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

        assertThat(flow.advanceAllowed()).isFalse();
        assertThat(flow.blockingReasons()).contains("EXECUTION_NOT_RESOLVED");
    }

    @Test
    void advanceExecutionExecutionResolvedEntersNight() {
        StartedGame game = startNominationGameWithClosedNomination();
        executionService.resolveExecution(game.gameId(),
                new ClocktowerGameExecutionResolveRequest(false, null, null, "tie"),
                owner());

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.previousPhase()).isEqualTo("EXECUTION");
        assertThat(result.phase()).isEqualTo("NIGHT");
        ClocktowerGamePo reloaded = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
        assertThat(reloaded.getPhase()).isEqualTo("NIGHT");
        assertThat(reloaded.getNightNo()).isEqualTo(2);
        assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED");
    }

    @Test
    void forceAdvanceRequiresReason() {
        StartedGame game = startGame();

        assertThatThrownBy(() -> flowService.forceAdvance(game.gameId(),
                new ClocktowerGameAdvanceRequest("DAY", null, Map.of()), owner()))
                .hasMessageContaining("CLOCKTOWER_FORCE_ADVANCE_REASON_REQUIRED");
    }

    @Test
    void forceAdvanceWritesForcedPhaseEvent() {
        StartedGame game = startGame();

        ClocktowerGameAdvanceResult result = flowService.forceAdvance(game.gameId(),
                new ClocktowerGameAdvanceRequest("DAY", "manual test recovery", Map.of("source", "test")),
                owner());

        assertThat(result.forced()).isTrue();
        assertThat(result.phase()).isEqualTo("DAY");
        assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED");
    }

    @Test
    void advanceFirstNightPendingTasksRejected() {
        StartedGame game = startGame();
        ClocktowerGameNightTaskPo task = nightTask(game.gameId(), 1, "IMP:FIRST_NIGHT", "PENDING", true);
        nightTaskRepository.saveAndFlush(task);

        ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

        assertThat(flow.advanceAllowed()).isFalse();
        assertThat(flow.blockingReasons()).containsExactly("PENDING_NIGHT_TASKS");
    }

    @Test
    void advanceFirstNightTasksCompleteEntersDayAndStartsMic() {
        StartedGame game = startGame();
        completeNightTasks(game.gameId(), 1);

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.previousPhase()).isEqualTo("FIRST_NIGHT");
        assertThat(result.phase()).isEqualTo("DAY");
        ClocktowerGamePo reloaded = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
        assertThat(reloaded.getPhase()).isEqualTo("DAY");
        assertThat(reloaded.getDayNo()).isEqualTo(1);
        assertThat(micService.getMicSession(game.gameId(), owner()).status()).isEqualTo("ROUND_ROBIN");
        assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED", "MIC_SESSION_STARTED");
    }

    @Test
    void advanceDayMicOpenRejected() {
        StartedGame game = startDayGame();
        micService.startDayMicSession(game.gameId(), owner());

        ClocktowerGameFlowView flow = flowService.getFlow(game.gameId(), owner());

        assertThat(flow.advanceAllowed()).isFalse();
        assertThat(flow.blockingReasons()).contains("MIC_ROUND_ROBIN_NOT_FINISHED");
    }

    @Test
    void advanceDayMicClosedEntersNomination() {
        StartedGame game = startDayGame();
        micService.startDayMicSession(game.gameId(), owner());
        micService.closeSession(game.gameId(), owner());

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.previousPhase()).isEqualTo("DAY");
        assertThat(result.phase()).isEqualTo("NOMINATION");
        assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())
                .isEqualTo("NOMINATION");
        assertThat(eventTypes(game.gameId())).contains("PHASE_CHANGED");
    }

    @Test
    void victoryGoodWinWhenAllDemonsDead() {
        StartedGame game = startGame();
        ClocktowerGameSeatPo demon = game.seats().stream()
                .filter(seat -> "DEMON".equals(seat.getRoleType()))
                .findFirst()
                .orElseThrow();
        demon.setLifeStatus("DEAD");
        demon.setPublicLifeStatus("DEAD");
        gameSeatRepository.saveAndFlush(demon);

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.phase()).isEqualTo("ENDED");
        assertThat(result.flow().status()).isEqualTo("ENDED");
        assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getStatus()).isEqualTo("ENDED");
        assertThat(eventTypes(game.gameId())).contains("GAME_ENDED", "PHASE_CHANGED");
    }

    @Test
    void victoryEvilWinWhenTwoAliveAndDemonAlive() {
        StartedGame game = startGame();
        List<ClocktowerGameSeatPo> seats = game.seats();
        seats.stream()
                .filter(seat -> !"DEMON".equals(seat.getRoleType()))
                .limit(3)
                .forEach(seat -> {
                    seat.setLifeStatus("DEAD");
                    seat.setPublicLifeStatus("DEAD");
                });
        gameSeatRepository.saveAllAndFlush(seats);

        ClocktowerGameAdvanceResult result = flowService.advance(game.gameId(), emptyRequest(), owner());

        assertThat(result.phase()).isEqualTo("ENDED");
        assertThat(result.flow().status()).isEqualTo("ENDED");
        assertThat(eventTypes(game.gameId())).contains("GAME_ENDED", "PHASE_CHANGED");
    }

    private StartedGame startGame() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(4), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());
        return new StartedGame(response.gameId(),
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(response.gameId()));
    }

    private StartedGame startDayGame() {
        StartedGame game = startGame();
        ClocktowerGamePo entity = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
        entity.setPhase("DAY");
        entity.setDayNo(1);
        gameRepository.saveAndFlush(entity);
        return game;
    }

    private StartedGame startNominationGameWithOpenNomination() {
        StartedGame game = startDayGame();
        micService.startDayMicSession(game.gameId(), owner());
        micService.closeSession(game.gameId(), owner());
        var response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(
                        game.seats().getFirst().getId(), "NOMINATE", List.of(game.seats().get(1).getId()),
                        null, null, null, Map.of()),
                principal(11L, "player1"));
        assertThat(response.accepted()).isTrue();
        return game;
    }

    private StartedGame startNominationGameWithClosedNomination() {
        StartedGame game = startNominationGameWithOpenNomination();
        Long nominationId = gameEventRepository
                .findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(game.gameId(), "VISIBLE")
                .stream()
                .filter(event -> "NOMINATION_OPENED".equals(event.getEventType()))
                .map(event -> extractNominationId(event.getPayloadJson()))
                .findFirst()
                .orElseThrow();
        executionService.closeNomination(game.gameId(), nominationId, owner());
        return game;
    }

    private Long extractNominationId(String payloadJson) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(payloadJson)
                    .get("nominationId")
                    .asLong();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ClocktowerGameNightTaskPo nightTask(Long gameId, int nightNo, String taskKey, String status,
                                                boolean mandatory) {
        ClocktowerGameNightTaskPo task = new ClocktowerGameNightTaskPo();
        task.setGameId(gameId);
        task.setNightNo(nightNo);
        task.setTaskKey(taskKey);
        task.setStatus(status);
        task.setMandatory(mandatory);
        task.setSortOrder(10);
        task.setMetadataJson("{}");
        return task;
    }

    private void completeNightTasks(Long gameId, int nightNo) {
        List<ClocktowerGameNightTaskPo> tasks = nightTaskRepository
                .findByGameIdAndNightNoAndDeletedFalseOrderBySortOrderAscIdAsc(gameId, nightNo);
        tasks.forEach(task -> task.setStatus("DONE"));
        nightTaskRepository.saveAllAndFlush(tasks);
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
                "Friday Clocktower",
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

    private List<String> eventTypes(Long gameId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
    }

    private ClocktowerGameAdvanceRequest emptyRequest() {
        return new ClocktowerGameAdvanceRequest(null, null, Map.of());
    }

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
    }

    private record StartedGame(Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
