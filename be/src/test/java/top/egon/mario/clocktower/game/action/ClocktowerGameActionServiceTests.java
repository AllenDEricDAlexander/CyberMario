package top.egon.mario.clocktower.game.action;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerAgentGameActionService;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
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
class ClocktowerGameActionServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerAgentGameActionService agentActionService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerPublicMicService micService;

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
    private ClocktowerAgentInstanceRepository agentInstanceRepository;

    @Test
    void humanSubmitAction_requiresOwnGameSeat() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);

        ClocktowerGameActionResponse ownSeat = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(agentSeat.getId()),
                        null, null, null, Map.of()),
                principal(11L, "player1"));

        assertThat(ownSeat.accepted()).isFalse();
        assertThat(ownSeat.rejectedCode()).isEqualTo("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
        assertThatThrownBy(() -> humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "NOMINATE", List.of(humanSeat.getId()),
                        null, null, null, Map.of()),
                principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
    }

    @Test
    void humanSubmitAction_cannotActAsAgent() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);

        assertThatThrownBy(() -> humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "agent words", Map.of()),
                principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_ACTION_SEAT_FORBIDDEN");
    }

    @Test
    void agentSubmitAction_requiresAgentInstanceGameSeatMatch() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(agentSeat.getId())
                .orElseThrow();

        ClocktowerGameActionResponse ownSeat = agentActionService.submitAgentAction(game.gameId(), instance.getId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "NOMINATE", List.of(humanSeat.getId()),
                        null, null, null, Map.of()));

        assertThat(ownSeat.accepted()).isFalse();
        assertThat(ownSeat.rejectedCode()).isEqualTo("CLOCKTOWER_ACTION_NOT_IMPLEMENTED");
        assertThatThrownBy(() -> agentActionService.submitAgentAction(game.gameId(), instance.getId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "NOMINATE", List.of(agentSeat.getId()),
                        null, null, null, Map.of())))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_GAME_SEAT_MISMATCH");
    }

    @Test
    void agentSubmitAction_rejectsPausedAutoMode() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo agentSeat = game.seats().get(1);
        ClocktowerAgentInstancePo instance = agentInstanceRepository
                .findByGameSeatIdAndDeletedFalse(agentSeat.getId())
                .orElseThrow();
        instance.setAutoMode(ClocktowerAgentAutoMode.PAUSED);
        agentInstanceRepository.saveAndFlush(instance);

        assertThatThrownBy(() -> agentActionService.submitAgentAction(game.gameId(), instance.getId(),
                new ClocktowerGameActionRequest(agentSeat.getId(), "NOMINATE", List.of(),
                        null, null, null, Map.of())))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AGENT_AUTO_MODE_PAUSED");
    }

    @Test
    void unknownActionTypeIsRejected() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "SING_LOUDLY", List.of(),
                        null, null, null, Map.of()),
                principal(11L, "player1"));

        assertThat(response.accepted()).isFalse();
        assertThat(response.rejectedCode()).isEqualTo("UNKNOWN_ACTION_TYPE");
    }

    @Test
    void actionRequiresRunningGameAndActiveSeat() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        ClocktowerGamePo gamePo = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
        gamePo.setStatus("ENDED");
        gameRepository.saveAndFlush(gamePo);

        assertThatThrownBy(() -> humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "hello", Map.of()),
                principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_NOT_RUNNING");
    }

    @Test
    void publicSpeech_requiresMic() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "I am first", Map.of()),
                principal(11L, "player1"));

        assertThat(response.accepted()).isFalse();
        assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_MIC_SESSION_NOT_FOUND");
    }

    @Test
    void publicSpeech_appendsGameEvent() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        micService.startDayMicSession(game.gameId(), owner());

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "I am the empath.", Map.of("tone", "calm")),
                principal(11L, "player1"));

        assertThat(response.accepted()).isTrue();
        assertThat(response.event()).isNotNull();
        assertThat(response.event().eventType()).isEqualTo("PUBLIC_SPEECH");
        assertThat(response.event().actorGameSeatId()).isEqualTo(humanSeat.getId());
        assertThat(response.event().visibility()).isEqualTo("PUBLIC");
        assertThat(response.event().payload())
                .containsEntry("content", "I am the empath.")
                .containsEntry("actorType", "HUMAN");
        assertThat(gameEventTypes(game.gameId())).contains("PUBLIC_SPEECH");
    }

    @Test
    void publicSpeechRejectsBlankOrLongContent() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        micService.startDayMicSession(game.gameId(), owner());

        ClocktowerGameActionResponse blank = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, " ", Map.of()),
                principal(11L, "player1"));
        ClocktowerGameActionResponse tooLong = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PUBLIC_SPEECH", List.of(),
                        null, null, "x".repeat(1001), Map.of()),
                principal(11L, "player1"));

        assertThat(blank.accepted()).isFalse();
        assertThat(blank.rejectedCode()).isEqualTo("CLOCKTOWER_PUBLIC_SPEECH_CONTENT_REQUIRED");
        assertThat(tooLong.accepted()).isFalse();
        assertThat(tooLong.rejectedCode()).isEqualTo("CLOCKTOWER_PUBLIC_SPEECH_CONTENT_TOO_LONG");
    }

    @Test
    void finishSpeech_finishesCurrentMicTurn() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        micService.startDayMicSession(game.gameId(), owner());

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "FINISH_SPEECH", List.of(),
                        null, null, null, Map.of()),
                principal(11L, "player1"));

        assertThat(response.accepted()).isTrue();
        assertThat(response.event().eventType()).isEqualTo("FINISH_SPEECH");
        assertThat(gameEventTypes(game.gameId())).contains("FINISH_SPEECH", "MIC_TURN_FINISHED");
        assertThat(micService.canSpeak(game.gameId(), humanSeat.getId())).isFalse();
    }

    @Test
    void passMicTurn_finishesCurrentMicTurnAndAppendsPassEvent() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();
        micService.startDayMicSession(game.gameId(), owner());

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PASS", List.of(),
                        null, null, null, Map.of("passType", "MIC_TURN")),
                principal(11L, "player1"));

        assertThat(response.accepted()).isTrue();
        assertThat(response.event().eventType()).isEqualTo("PLAYER_PASSED");
        assertThat(response.event().payload()).containsEntry("passType", "MIC_TURN");
        assertThat(gameEventTypes(game.gameId())).contains("PLAYER_PASSED", "MIC_TURN_FINISHED");
    }

    @Test
    void passRejectsUnsupportedPassType() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo humanSeat = game.seats().getFirst();

        ClocktowerGameActionResponse response = humanActionService.submit(game.gameId(),
                new ClocktowerGameActionRequest(humanSeat.getId(), "PASS", List.of(),
                        null, null, null, Map.of("passType", "NIGHT_TASK")),
                principal(11L, "player1"));

        assertThat(response.accepted()).isFalse();
        assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_PASS_TYPE_UNSUPPORTED");
    }

    private StartedGame startDayGameWithAgents() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(4), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        ClocktowerGameResponse started = gameService.startGame(room.roomId(), owner());
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(started.gameId()).orElseThrow();
        game.setPhase("DAY");
        game.setDayNo(1);
        gameRepository.saveAndFlush(game);
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

    private List<String> gameEventTypes(Long gameId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
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

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
    }

    private record StartedGame(Long roomId, Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
