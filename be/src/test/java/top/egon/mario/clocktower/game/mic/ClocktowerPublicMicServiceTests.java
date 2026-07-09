package top.egon.mario.clocktower.game.mic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.mic.dto.ClocktowerMicSessionView;
import top.egon.mario.clocktower.game.mic.dto.ClocktowerMicTurnView;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicSessionPo;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicTurnPo;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicSessionRepository;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicTurnRepository;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerPublicMicServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerPublicMicService micService;

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
    private ClocktowerGamePublicMicSessionRepository sessionRepository;

    @Autowired
    private ClocktowerGamePublicMicTurnRepository turnRepository;

    @Test
    void startDayMicSessionCreatesSeatOrderRoundRobinTurns() {
        StartedGame game = startDayGameWithAgents();

        ClocktowerMicSessionView view = micService.startDayMicSession(game.gameId(), owner());

        assertThat(view.gameId()).isEqualTo(game.gameId());
        assertThat(view.dayNo()).isEqualTo(1);
        assertThat(view.status()).isEqualTo("ROUND_ROBIN");
        assertThat(view.currentHolderGameSeatId()).isEqualTo(view.turns().getFirst().gameSeatId());
        assertThat(view.currentTurnId()).isEqualTo(view.turns().getFirst().turnId());
        assertThat(view.roundStartedAt()).isNotNull();
        assertThat(view.closedAt()).isNull();
        assertThat(view.turns()).hasSize(ROLE_CODES.size());
        assertThat(view.turns()).extracting(ClocktowerMicTurnView::seatNo)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(view.turns()).extracting(ClocktowerMicTurnView::actorType)
                .containsExactly("HUMAN", "AGENT", "AGENT", "AGENT", "AGENT");
        assertThat(view.turns()).extracting(ClocktowerMicTurnView::stage)
                .containsOnly("ROUND_ROBIN");
        assertThat(view.turns()).extracting(ClocktowerMicTurnView::acquisitionType)
                .containsOnly("ROUND_ROBIN");
        assertThat(view.turns()).extracting(ClocktowerMicTurnView::status)
                .containsExactly("ACTIVE", "PENDING", "PENDING", "PENDING", "PENDING");

        List<String> eventTypes = gameEventRepository
                .findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(game.gameId(), "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
        assertThat(eventTypes).contains("MIC_SESSION_STARTED", "MIC_TURN_STARTED");
    }

    @Test
    void startDayMicSessionIsIdempotentForSameGameDay() {
        StartedGame game = startDayGameWithAgents();

        ClocktowerMicSessionView first = micService.startDayMicSession(game.gameId(), owner());
        ClocktowerMicSessionView second = micService.startDayMicSession(game.gameId(), owner());

        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.currentTurnId()).isEqualTo(first.currentTurnId());
        assertThat(second.turns()).hasSize(first.turns().size());
        List<String> micSessionEvents = gameEventRepository
                .findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(game.gameId(), "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .filter("MIC_SESSION_STARTED"::equals)
                .toList();
        assertThat(micSessionEvents).hasSize(1);
    }

    @Test
    void startDayMicSessionActivatesFirstEligibleSeatWhenSeatOneMuted() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerGameSeatPo firstSeat = game.seats().getFirst();
        firstSeat.setMetadataJson("{\"muted\":true}");
        gameSeatRepository.saveAndFlush(firstSeat);

        ClocktowerMicSessionView view = micService.startDayMicSession(game.gameId(), owner());

        assertThat(view.turns()).extracting(ClocktowerMicTurnView::seatNo)
                .containsExactly(2, 3, 4, 5);
        assertThat(view.currentHolderGameSeatId()).isEqualTo(view.turns().getFirst().gameSeatId());
        assertThat(view.turns()).extracting(ClocktowerMicTurnView::status)
                .containsExactly("ACTIVE", "PENDING", "PENDING", "PENDING");
        assertThatCode(() -> micService.requireCanSpeak(game.gameId(), view.currentHolderGameSeatId()))
                .doesNotThrowAnyException();
    }

    @Test
    void requireCanSpeakRejectsMissingSession() {
        StartedGame game = startDayGameWithAgents();
        Long firstSeatId = game.seats().getFirst().getId();

        assertThat(micService.canSpeak(game.gameId(), firstSeatId)).isFalse();
        assertThatThrownBy(() -> micService.requireCanSpeak(game.gameId(), firstSeatId))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_SESSION_NOT_FOUND");
    }

    @Test
    void requireCanSpeakAllowsOnlyCurrentHolder() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView view = micService.startDayMicSession(game.gameId(), owner());
        Long holderSeatId = view.currentHolderGameSeatId();
        Long otherSeatId = view.turns().stream()
                .map(ClocktowerMicTurnView::gameSeatId)
                .filter(seatId -> !seatId.equals(holderSeatId))
                .findFirst()
                .orElseThrow();

        assertThat(micService.canSpeak(game.gameId(), holderSeatId)).isTrue();
        assertThatCode(() -> micService.requireCanSpeak(game.gameId(), holderSeatId))
                .doesNotThrowAnyException();
        assertThat(micService.canSpeak(game.gameId(), otherSeatId)).isFalse();
        assertThatThrownBy(() -> micService.requireCanSpeak(game.gameId(), otherSeatId))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_NOT_HOLDER");
    }

    @Test
    void agentSeatIsQueuedButCannotUseHumanPrincipalToGrabDuringRoundRobin() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView view = micService.startDayMicSession(game.gameId(), owner());

        assertThat(view.turns())
                .filteredOn(turn -> "AGENT".equals(turn.actorType()))
                .hasSize(4);
        assertThatThrownBy(() -> micService.grabMic(game.gameId(), principal(99L, "agent-principal")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_GRAB_NOT_OPEN");
    }

    @Test
    void finishCurrentRoundRobinTurnAdvancesToNextSeat() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView started = micService.startDayMicSession(game.gameId(), owner());

        ClocktowerMicSessionView view = micService.finishCurrentTurn(game.gameId(), started.currentTurnId(),
                principal(11L, "player1"));

        assertThat(turnBySeatNo(view, 1).status()).isEqualTo("DONE");
        assertThat(activeTurn(view).seatNo()).isEqualTo(2);
        assertThat(view.currentHolderGameSeatId()).isEqualTo(activeTurn(view).gameSeatId());
        assertThat(view.currentTurnId()).isEqualTo(activeTurn(view).turnId());
    }

    @Test
    void finishCurrentTurnAsActorFinishesOnlyCurrentHolder() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView started = micService.startDayMicSession(game.gameId(), owner());
        Long holderSeatId = started.currentHolderGameSeatId();
        Long otherSeatId = started.turns().stream()
                .map(ClocktowerMicTurnView::gameSeatId)
                .filter(seatId -> !seatId.equals(holderSeatId))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> micService.finishCurrentTurnAsActor(game.gameId(), otherSeatId))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_NOT_HOLDER");

        ClocktowerMicSessionView finished = micService.finishCurrentTurnAsActor(game.gameId(), holderSeatId);

        assertThat(turnBySeatNo(finished, 1).status()).isEqualTo("DONE");
        assertThat(activeTurn(finished).seatNo()).isEqualTo(2);
        assertThat(gameEventTypes(game.gameId())).contains("MIC_TURN_FINISHED");
    }

    @Test
    void finishingLastRoundRobinTurnOpensGrabMicWindow() {
        StartedGame game = startDayGameWithAgents();

        ClocktowerMicSessionView view = finishRoundRobin(game);

        assertThat(view.status()).isEqualTo("GRAB_MIC");
        assertThat(view.currentHolderGameSeatId()).isNull();
        assertThat(view.currentTurnId()).isNull();
        assertThat(view.roundFinishedAt()).isNotNull();
        assertThat(view.grabStartedAt()).isNotNull();
        assertThat(view.grabEndsAt()).isAfter(view.grabStartedAt());
        assertThat(view.turns()).extracting(ClocktowerMicTurnView::status)
                .containsOnly("DONE");
        assertThat(gameEventTypes(game.gameId())).contains("MIC_GRAB_OPENED");
    }

    @Test
    void skipPendingTurnRemovesItFromRoundRobinQueue() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView started = micService.startDayMicSession(game.gameId(), owner());
        Long pendingTurnId = turnBySeatNo(started, 2).turnId();

        ClocktowerMicSessionView skipped = micService.skipTurn(game.gameId(), pendingTurnId, owner());
        ClocktowerMicSessionView advanced = micService.finishCurrentTurn(game.gameId(), skipped.currentTurnId(),
                owner());

        assertThat(turnBySeatNo(advanced, 2).status()).isEqualTo("SKIPPED");
        assertThat(activeTurn(advanced).seatNo()).isEqualTo(3);
        assertThat(gameEventTypes(game.gameId())).contains("MIC_TURN_SKIPPED");
    }

    @Test
    void skipCurrentTurnAdvancesQueue() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView started = micService.startDayMicSession(game.gameId(), owner());

        ClocktowerMicSessionView view = micService.skipTurn(game.gameId(), started.currentTurnId(), owner());

        assertThat(turnBySeatNo(view, 1).status()).isEqualTo("SKIPPED");
        assertThat(activeTurn(view).seatNo()).isEqualTo(2);
        assertThat(view.currentHolderGameSeatId()).isEqualTo(activeTurn(view).gameSeatId());
    }

    @Test
    void grabMicCreatesSingleActiveGrabTurn() {
        StartedGame game = startDayGameWithAgents();
        finishRoundRobin(game);

        ClocktowerMicSessionView view = micService.grabMic(game.gameId(), principal(11L, "player1"));

        ClocktowerMicTurnView active = activeTurn(view);
        assertThat(view.status()).isEqualTo("GRAB_MIC");
        assertThat(active.stage()).isEqualTo("GRAB_MIC");
        assertThat(active.acquisitionType()).isEqualTo("GRAB");
        assertThat(active.actorType()).isEqualTo("HUMAN");
        assertThat(view.currentHolderGameSeatId()).isEqualTo(active.gameSeatId());
        assertThat(micService.canSpeak(game.gameId(), active.gameSeatId())).isTrue();
    }

    @Test
    void grabMicAsActorAllowsAgentSeatDuringGrabWindow() {
        StartedGame game = startDayGameWithAgents();
        finishRoundRobin(game);
        ClocktowerGameSeatPo agentSeat = game.seats().stream()
                .filter(seat -> "AGENT".equals(seat.getActorType()))
                .findFirst()
                .orElseThrow();

        ClocktowerMicSessionView view = micService.grabMicAsActor(game.gameId(), agentSeat.getId());

        ClocktowerMicTurnView active = activeTurn(view);
        assertThat(active.actorType()).isEqualTo("AGENT");
        assertThat(active.gameSeatId()).isEqualTo(agentSeat.getId());
        assertThat(active.acquisitionType()).isEqualTo("GRAB");
        assertThat(micService.canSpeak(game.gameId(), agentSeat.getId())).isTrue();
    }

    @Test
    void grabMicAsActorRejectsOccupiedMic() {
        StartedGame game = startDayGameWithAgents();
        finishRoundRobin(game);
        ClocktowerGameSeatPo agentSeat = game.seats().stream()
                .filter(seat -> "AGENT".equals(seat.getActorType()))
                .findFirst()
                .orElseThrow();
        micService.grabMic(game.gameId(), principal(11L, "player1"));

        assertThatThrownBy(() -> micService.grabMicAsActor(game.gameId(), agentSeat.getId()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_OCCUPIED");
    }

    @Test
    void grabMicRejectsWhenHolderIsActive() {
        StartedGame game = startDayGameWithAgents();
        finishRoundRobin(game);
        micService.grabMic(game.gameId(), principal(11L, "player1"));

        assertThatThrownBy(() -> micService.grabMic(game.gameId(), principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_OCCUPIED");
    }

    @Test
    void expiredGrabWindowClosesSessionAndRejectsGrab() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView grabOpen = finishRoundRobin(game);
        ClocktowerGamePublicMicSessionPo session = sessionRepository.findById(grabOpen.sessionId()).orElseThrow();
        session.setGrabEndsAt(Instant.now().minusSeconds(1));
        sessionRepository.saveAndFlush(session);

        ClocktowerMicSessionView closed = micService.getMicSession(game.gameId(), owner());

        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isNotNull();
        assertThatThrownBy(() -> micService.grabMic(game.gameId(), principal(11L, "player1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_SESSION_CLOSED");
    }

    @Test
    void releaseGrabMicClearsHolder() {
        StartedGame game = startDayGameWithAgents();
        finishRoundRobin(game);
        ClocktowerMicSessionView grabbed = micService.grabMic(game.gameId(), principal(11L, "player1"));

        ClocktowerMicSessionView released = micService.releaseMic(game.gameId(), principal(11L, "player1"));

        assertThat(released.status()).isEqualTo("GRAB_MIC");
        assertThat(released.currentHolderGameSeatId()).isNull();
        assertThat(released.currentTurnId()).isNull();
        assertThat(turnById(released, grabbed.currentTurnId()).status()).isEqualTo("DONE");
    }

    @Test
    void extendGrabMicMovesWindowEndButDoesNotExtendCurrentTurn() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView grabOpen = finishRoundRobin(game);
        ClocktowerMicSessionView grabbed = micService.grabMic(game.gameId(), principal(11L, "player1"));
        Instant activeExpiresAt = activeTurn(grabbed).expiresAt();

        ClocktowerMicSessionView extended = micService.extendGrabMic(game.gameId(), 60, owner());

        assertThat(extended.grabEndsAt()).isEqualTo(grabOpen.grabEndsAt().plus(Duration.ofSeconds(60)));
        assertThat(activeTurn(extended).expiresAt()).isEqualTo(activeExpiresAt);
        assertThat(gameEventTypes(game.gameId())).contains("MIC_SESSION_EXTENDED");
    }

    @Test
    void closeSessionCancelsCurrentTurnAndRejectsCanSpeak() {
        StartedGame game = startDayGameWithAgents();
        finishRoundRobin(game);
        ClocktowerMicSessionView grabbed = micService.grabMic(game.gameId(), principal(11L, "player1"));
        Long holderSeatId = grabbed.currentHolderGameSeatId();
        Long activeTurnId = grabbed.currentTurnId();

        ClocktowerMicSessionView closed = micService.closeSession(game.gameId(), owner());

        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.currentHolderGameSeatId()).isNull();
        assertThat(closed.currentTurnId()).isNull();
        assertThat(turnById(closed, activeTurnId).status()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> micService.requireCanSpeak(game.gameId(), holderSeatId))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_MIC_CLOSED");
    }

    @Test
    void lazyRefreshExpiresCurrentTurnAndAdvances() {
        StartedGame game = startDayGameWithAgents();
        ClocktowerMicSessionView started = micService.startDayMicSession(game.gameId(), owner());
        ClocktowerGamePublicMicTurnPo active = turnRepository.findByIdAndDeletedFalse(started.currentTurnId())
                .orElseThrow();
        active.setExpiresAt(Instant.now().minusSeconds(1));
        turnRepository.saveAndFlush(active);

        ClocktowerMicSessionView refreshed = micService.getMicSession(game.gameId(), owner());

        assertThat(turnBySeatNo(refreshed, 1).status()).isEqualTo("EXPIRED");
        assertThat(activeTurn(refreshed).seatNo()).isEqualTo(2);
        assertThat(gameEventTypes(game.gameId())).contains("MIC_TURN_EXPIRED");
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
        List<ClocktowerGameSeatPo> seats = gameSeatRepository
                .findByGameIdAndDeletedFalseOrderBySeatNoAsc(started.gameId());
        return new StartedGame(room.roomId(), started.gameId(), seats);
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

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of(), Set.of(), "test");
    }

    private ClocktowerMicSessionView finishRoundRobin(StartedGame game) {
        ClocktowerMicSessionView view = micService.startDayMicSession(game.gameId(), owner());
        while ("ROUND_ROBIN".equals(view.status())) {
            view = micService.finishCurrentTurn(game.gameId(), view.currentTurnId(), owner());
        }
        return view;
    }

    private ClocktowerMicTurnView activeTurn(ClocktowerMicSessionView view) {
        return view.turns().stream()
                .filter(turn -> "ACTIVE".equals(turn.status()))
                .findFirst()
                .orElseThrow();
    }

    private ClocktowerMicTurnView turnBySeatNo(ClocktowerMicSessionView view, int seatNo) {
        return view.turns().stream()
                .filter(turn -> turn.seatNo() != null && turn.seatNo() == seatNo)
                .findFirst()
                .orElseThrow();
    }

    private ClocktowerMicTurnView turnById(ClocktowerMicSessionView view, Long turnId) {
        return view.turns().stream()
                .filter(turn -> turn.turnId().equals(turnId))
                .findFirst()
                .orElseThrow();
    }

    private List<String> gameEventTypes(Long gameId) {
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(gameId, "VISIBLE")
                .stream()
                .map(ClocktowerGameEventPo::getEventType)
                .toList();
    }

    private record StartedGame(Long roomId, Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
