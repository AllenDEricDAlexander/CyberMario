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
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

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

    private record StartedGame(Long roomId, Long gameId, List<ClocktowerGameSeatPo> seats) {
    }
}
