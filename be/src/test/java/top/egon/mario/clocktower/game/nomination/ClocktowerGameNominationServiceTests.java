package top.egon.mario.clocktower.game.nomination;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionRequest;
import top.egon.mario.clocktower.game.action.dto.ClocktowerGameActionResponse;
import top.egon.mario.clocktower.game.action.service.ClocktowerAgentGameActionService;
import top.egon.mario.clocktower.game.action.service.ClocktowerHumanGameActionService;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResolveRequest;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResponse;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameNominationResponse;
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

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameNominationServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerHumanGameActionService humanActionService;

    @Autowired
    private ClocktowerAgentGameActionService agentActionService;

    @Autowired
    private ClocktowerGameExecutionService executionService;

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
    void closeNominationCreatesExecutionCandidate() {
        StartedGame game = startDayGameWithAgents();
        Long nominationId = openNomination(game, game.seats().getFirst(), game.seats().get(1));
        voteYes(game, game.seats().getFirst(), nominationId);
        voteYes(game, game.seats().get(2), nominationId);
        voteYes(game, game.seats().get(3), nominationId);

        ClocktowerGameNominationResponse response = executionService.closeNomination(
                game.gameId(), nominationId, owner());

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.voteCount()).isEqualTo(3);
        assertThat(response.execution().nomineeGameSeatId()).isEqualTo(game.seats().get(1).getId());
        assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())
                .isEqualTo("NOMINATION");
        assertThat(gameEventTypes(game.gameId())).contains("NOMINATION_CLOSED", "EXECUTION_CANDIDATE_UPDATED");
    }

    @Test
    void resolveExecutionKillsCandidateAndAppendsEvents() {
        StartedGame game = startDayGameWithAgents();
        Long nominationId = openAndCloseQualifyingNomination(game);
        ClocktowerGameSeatPo nominee = game.seats().get(1);

        ClocktowerGameExecutionResponse response = executionService.resolveExecution(game.gameId(),
                new ClocktowerGameExecutionResolveRequest(true, nominee.getId(), nominationId, "execute"),
                owner());

        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.executed()).isTrue();
        assertThat(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())
                .isEqualTo("EXECUTION");
        ClocktowerGameSeatPo reloaded = gameSeatRepository.findByIdAndDeletedFalse(nominee.getId()).orElseThrow();
        assertThat(reloaded.getLifeStatus()).isEqualTo("DEAD");
        assertThat(reloaded.getPublicLifeStatus()).isEqualTo("DEAD");
        assertThat(gameEventTypes(game.gameId())).contains("PLAYER_EXECUTED", "PLAYER_DIED");
    }

    @Test
    void tiedQualifyingNominationsResolveNoExecution() {
        StartedGame game = startDayGameWithAgents();
        Long firstNominationId = openCloseWithVotes(game, game.seats().getFirst(), game.seats().get(1),
                List.of(game.seats().getFirst(), game.seats().get(2), game.seats().get(3)));
        Long secondNominationId = openCloseWithVotes(game, game.seats().get(2), game.seats().get(3),
                List.of(game.seats().getFirst(), game.seats().get(2), game.seats().get(4)));

        ClocktowerGameExecutionResponse response = executionService.resolveExecution(game.gameId(),
                new ClocktowerGameExecutionResolveRequest(false, null, null, "tie"),
                owner());

        assertThat(firstNominationId).isNotEqualTo(secondNominationId);
        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.executed()).isFalse();
        assertThat(response.nomineeGameSeatId()).isNull();
        assertThat(gameEventTypes(game.gameId())).contains("NO_EXECUTION");
    }

    private Long openAndCloseQualifyingNomination(StartedGame game) {
        return openCloseWithVotes(game, game.seats().getFirst(), game.seats().get(1),
                List.of(game.seats().getFirst(), game.seats().get(2), game.seats().get(3)));
    }

    private Long openCloseWithVotes(StartedGame game, ClocktowerGameSeatPo nominator, ClocktowerGameSeatPo nominee,
                                    List<ClocktowerGameSeatPo> voters) {
        Long nominationId = openNomination(game, nominator, nominee);
        voters.forEach(voter -> voteYes(game, voter, nominationId));
        ClocktowerGameNominationResponse response = executionService.closeNomination(game.gameId(), nominationId,
                owner());
        assertThat(response.status()).isEqualTo("CLOSED");
        return nominationId;
    }

    private Long openNomination(StartedGame game, ClocktowerGameSeatPo nominator, ClocktowerGameSeatPo nominee) {
        if ("DAY".equals(gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow().getPhase())) {
            micService.startDayMicSession(game.gameId(), owner());
            micService.closeSession(game.gameId(), owner());
        }
        ClocktowerGameActionResponse response = submitAction(game, nominator,
                new ClocktowerGameActionRequest(nominator.getId(), "NOMINATE", List.of(nominee.getId()),
                        null, null, null, Map.of()));
        assertThat(response.accepted()).isTrue();
        return ((Number) response.event().payload().get("nominationId")).longValue();
    }

    private void voteYes(StartedGame game, ClocktowerGameSeatPo voter, Long nominationId) {
        ClocktowerGameActionResponse response = submitAction(game, voter,
                new ClocktowerGameActionRequest(voter.getId(), "VOTE", List.of(),
                        nominationId, true, null, Map.of()));
        assertThat(response.accepted()).isTrue();
    }

    private ClocktowerGameActionResponse submitAction(StartedGame game, ClocktowerGameSeatPo seat,
                                                     ClocktowerGameActionRequest request) {
        if (ClocktowerActorType.AGENT.equals(seat.getActorType())) {
            ClocktowerAgentInstancePo instance = agentInstanceRepository
                    .findByGameSeatIdAndDeletedFalse(seat.getId())
                    .orElseThrow();
            return agentActionService.submitAgentAction(game.gameId(), instance.getId(), request);
        }
        return humanActionService.submit(game.gameId(), request, principal(seat.getUserId(), "player" + seat.getSeatNo()));
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
