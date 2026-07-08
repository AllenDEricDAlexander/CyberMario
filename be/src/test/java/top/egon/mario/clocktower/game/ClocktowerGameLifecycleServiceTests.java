package top.egon.mario.clocktower.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.chat.ClocktowerChatConstants;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameConversationResponse;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerGameLifecycleServiceTests {

    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private RoomSpaceRepository roomSpaceRepository;

    @Autowired
    private RoomInvitationRepository roomInvitationRepository;

    @Autowired
    private ClocktowerRoomProfileRepository profileRepository;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startGameRejectsWhenSeatsNotAcceptedReadyOrRealUsers() {
        Long emptySeatRoomId = readyRoom();
        ClocktowerRoomSeatPo emptySeat = roomSeatRepository.findByRoomIdAndSeatNo(emptySeatRoomId, 1).orElseThrow();
        emptySeat.setUserId(null);
        emptySeat.setStatus("OPEN");
        roomSeatRepository.saveAndFlush(emptySeat);
        assertStartRejected(emptySeatRoomId, "CLOCKTOWER_GAME_SEAT_INVALID");

        Long fakeSeatRoomId = readyRoom();
        ClocktowerRoomSeatPo fakeSeat = roomSeatRepository.findByRoomIdAndSeatNo(fakeSeatRoomId, 1).orElseThrow();
        fakeSeat.setMetadataJson("{\"ready\":true,\"fake\":true}");
        roomSeatRepository.saveAndFlush(fakeSeat);
        assertStartRejected(fakeSeatRoomId, "CLOCKTOWER_GAME_SEAT_INVALID");

        Long unreadySeatRoomId = readyRoom();
        ClocktowerRoomSeatPo unreadySeat = roomSeatRepository.findByRoomIdAndSeatNo(unreadySeatRoomId, 1).orElseThrow();
        unreadySeat.setMetadataJson("{\"ready\":false}");
        roomSeatRepository.saveAndFlush(unreadySeat);
        assertStartRejected(unreadySeatRoomId, "CLOCKTOWER_GAME_SEAT_NOT_READY");

        Long pendingReservationRoomId = readyRoom();
        RoomInvitationPo reservation = new RoomInvitationPo();
        reservation.setRoomId(pendingReservationRoomId);
        reservation.setInviterUserId(1L);
        reservation.setInviteeUserId(99L);
        reservation.setInvitationCode("pending-" + pendingReservationRoomId);
        reservation.setStatus("PENDING");
        reservation.setActiveStatus(true);
        reservation.setTargetSeatNo(1);
        reservation.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        roomInvitationRepository.saveAndFlush(reservation);
        assertStartRejected(pendingReservationRoomId, "CLOCKTOWER_GAME_SEAT_RESERVED");
    }

    @Test
    void startGameRejectsInvalidBoardDraftBeforeCreatingGame() {
        Long roomId = readyRoom();
        List<ClocktowerRoomSeatPo> seats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId);
        seats.get(2).setRoleCode("CHEF");
        roomSeatRepository.saveAllAndFlush(seats);

        assertStartRejected(roomId, "CLOCKTOWER_BOARD_INVALID");

        assertThat(gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(roomId)).isEmpty();
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(roomId).orElseThrow();
        assertThat(profile.getCurrentGameId()).isNull();
        assertThat(profile.getStatus()).isEqualTo("LOBBY");
    }

    @Test
    void startGameRejectsStorytellerSeat() {
        Long roomId = readyRoom();
        ClocktowerRoomSeatPo storytellerSeat = roomSeatRepository.findByRoomIdAndSeatNo(roomId, 1).orElseThrow();
        storytellerSeat.setUserId(1L);
        storytellerSeat.setDisplayName("Mario");
        roomSeatRepository.saveAndFlush(storytellerSeat);

        assertStartRejected(roomId, "CLOCKTOWER_STORYTELLER_CANNOT_PLAY");

        assertThat(gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(roomId)).isEmpty();
    }

    @Test
    void startGameCreatesGameAndImmutableSeatSnapshot() {
        Long roomId = readyRoom();

        ClocktowerGameResponse response = gameService.startGame(roomId, owner());

        ClocktowerGamePo game = gameRepository.findById(response.gameId()).orElseThrow();
        assertThat(game.getRoomId()).isEqualTo(roomId);
        assertThat(game.getGameNo()).isEqualTo(1);
        assertThat(game.getStatus()).isEqualTo("RUNNING");
        assertThat(game.getPhase()).isEqualTo("FIRST_NIGHT");
        assertThat(game.getStartedAt()).isNotNull();
        assertThat(game.getLastActiveAt()).isNotNull();
        assertThat(game.getBoardSnapshotJson()).contains("\"scriptCode\":\"TROUBLE_BREWING\"");

        List<ClocktowerRoomSeatPo> roomSeats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId);
        List<ClocktowerGameSeatPo> gameSeats = gameSeatRepository.findByGameIdOrderBySeatNoAsc(game.getId());
        assertThat(gameSeats).hasSize(5);
        assertThat(gameSeats)
                .extracting(ClocktowerGameSeatPo::getRoomSeatId)
                .containsExactlyElementsOf(roomSeats.stream().map(ClocktowerRoomSeatPo::getId).toList());
        assertThat(gameSeats)
                .extracting(ClocktowerGameSeatPo::getRoleCode)
                .containsExactlyElementsOf(ROLE_CODES);
        assertThat(gameSeats)
                .extracting(ClocktowerGameSeatPo::getRoleType)
                .doesNotContainNull();
        assertThat(gameSeats)
                .extracting(ClocktowerGameSeatPo::getAlignment)
                .doesNotContainNull();

        ClocktowerRoomSeatPo changedRoomSeat = roomSeats.get(0);
        changedRoomSeat.setDisplayName("Changed Later");
        changedRoomSeat.setRoleCode("RAVENKEEPER");
        roomSeatRepository.saveAndFlush(changedRoomSeat);

        ClocktowerGameSeatPo immutableGameSeat = gameSeatRepository
                .findByGameIdAndRoomSeatId(game.getId(), changedRoomSeat.getId())
                .orElseThrow();
        assertThat(immutableGameSeat.getDisplayName()).isEqualTo("Player 1");
        assertThat(immutableGameSeat.getRoleCode()).isEqualTo("EMPATH");
    }

    @Test
    void startGameActivatesGameConversations() {
        Long roomId = readyRoom();

        ClocktowerGameResponse response = gameService.startGame(roomId, owner());

        assertThat(response.conversations()).hasSize(4);
        assertThat(response.conversations())
                .extracting(ClocktowerGameConversationResponse::groupKey,
                        ClocktowerGameConversationResponse::conversationType)
                .containsExactly(
                        tuple(ClocktowerChatConstants.GROUP_PUBLIC, ClocktowerChatConstants.CONVERSATION_PUBLIC),
                        tuple(ClocktowerChatConstants.GROUP_PRIVATE,
                                ClocktowerChatConstants.CONVERSATION_PRIVATE_CONTAINER),
                        tuple(ClocktowerChatConstants.GROUP_SPECTATOR,
                                ClocktowerChatConstants.CONVERSATION_SPECTATOR),
                        tuple(ClocktowerChatConstants.GROUP_SYSTEM, ClocktowerChatConstants.CONVERSATION_SYSTEM));
        assertThat(response.conversations())
                .extracting(ClocktowerGameConversationResponse::conversationId)
                .doesNotContainNull();
    }

    @Test
    void startGameWithAgentSeatsSuccess() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        assertThat(response.status()).isEqualTo("RUNNING");
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(room.roomId()).orElseThrow();
        assertThat(profile.getStatus()).isEqualTo("IN_GAME");
        assertThat(profile.getCurrentGameId()).isEqualTo(response.gameId());
        assertThat(gameSeatRepository.findByGameIdOrderBySeatNoAsc(response.gameId())).hasSize(5);
    }

    @Test
    void startGameCopiesActorFieldsToGameSeat() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        List<ClocktowerGameSeatPo> gameSeats = gameSeatRepository.findByGameIdOrderBySeatNoAsc(response.gameId());
        assertThat(gameSeats.get(0))
                .extracting(ClocktowerGameSeatPo::getActorType, ClocktowerGameSeatPo::getActorId,
                        ClocktowerGameSeatPo::getAgentInstanceId, ClocktowerGameSeatPo::getUserId)
                .containsExactly(ClocktowerActorType.HUMAN, null, null, 11L);
        assertThat(gameSeats.subList(1, 5)).allSatisfy(seat -> {
            assertThat(seat.getActorType()).isEqualTo(ClocktowerActorType.AGENT);
            assertThat(seat.getActorId()).isNotNull();
            assertThat(seat.getAgentInstanceId()).isNotNull();
            assertThat(seat.getUserId()).isNull();
        });
    }

    @Test
    void startGameUpdatesAgentInstances() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        List<ClocktowerGameSeatPo> agentSeats = gameSeatRepository.findByGameIdOrderBySeatNoAsc(response.gameId())
                .stream()
                .filter(seat -> ClocktowerActorType.AGENT.equals(seat.getActorType()))
                .toList();
        assertThat(agentSeats).hasSize(4);
        for (ClocktowerGameSeatPo gameSeat : agentSeats) {
            ClocktowerAgentInstancePo instance = agentInstanceRepository
                    .findByIdAndDeletedFalse(gameSeat.getAgentInstanceId())
                    .orElseThrow();
            assertThat(instance.getGameId()).isEqualTo(response.gameId());
            assertThat(instance.getGameSeatId()).isEqualTo(gameSeat.getId());
            assertThat(instance.getRoomSeatId()).isEqualTo(gameSeat.getRoomSeatId());
            assertThat(instance.getActorId()).isEqualTo(gameSeat.getActorId());
            assertThat(instance.getStatus()).isEqualTo(ClocktowerAgentStatus.ACTIVE);
        }
    }

    @Test
    void startGameManualAgentMetadataRejected() {
        Long roomId = readyRoom();
        ClocktowerRoomSeatPo seat = roomSeatRepository.findByRoomIdAndSeatNo(roomId, 1).orElseThrow();
        seat.setMetadataJson("{\"ready\":true,\"agent\":true,\"agentSeat\":true,"
                + "\"systemManaged\":true,\"createdBy\":\"agentSeatCount\"}");
        roomSeatRepository.saveAndFlush(seat);

        assertStartRejected(roomId, "CLOCKTOWER_GAME_SEAT_INVALID");

        assertThat(gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(roomId)).isEmpty();
    }

    @Test
    void startGameAgentSeatWithUserIdRejected() {
        ClocktowerRoomResponse room = agentRoom();
        ClocktowerRoomSeatPo agentSeat = roomSeatRepository.findByRoomIdAndSeatNo(room.roomId(), 2).orElseThrow();
        agentSeat.setUserId(22L);
        roomSeatRepository.saveAndFlush(agentSeat);

        assertStartRejected(room.roomId(), "CLOCKTOWER_GAME_SEAT_INVALID");

        assertThat(gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(room.roomId())).isEmpty();
    }

    @Test
    void startGameFiltersAgentFromImMembers() {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        ClocktowerGameConversationResponse publicConversation = conversation(response,
                ClocktowerChatConstants.GROUP_PUBLIC, ClocktowerChatConstants.CONVERSATION_PUBLIC);
        assertThat(conversationMemberUserIds(publicConversation.conversationId()))
                .containsExactly(1L, 11L);
    }

    @Test
    void startGameEventPayloadIncludesAgentCounts() throws Exception {
        ClocktowerRoomResponse room = agentRoom();

        ClocktowerGameResponse response = gameService.startGame(room.roomId(), owner());

        List<ClocktowerGameEventPo> events = gameEventRepository
                .findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(response.gameId(), "VISIBLE");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("GAME_STARTED");
        Map<String, Object> payload = objectMapper.readValue(events.get(0).getPayloadJson(), MAP_TYPE);
        assertThat(payload)
                .containsEntry("roomId", room.roomId().intValue())
                .containsEntry("gameNo", 1)
                .containsEntry("humanSeatCount", 1)
                .containsEntry("agentSeatCount", 4)
                .containsEntry("agentMode", "HEURISTIC_V0");
    }

    @Test
    void startGameLocksRoomAgainstDoubleStart() {
        Long roomId = readyRoom();

        ClocktowerGameResponse response = gameService.startGame(roomId, owner());

        assertThatThrownBy(() -> gameService.startGame(roomId, owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_GAME_ALREADY_RUNNING");
        assertThat(gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(roomId))
                .extracting(ClocktowerGamePo::getId)
                .containsExactly(response.gameId());
    }

    @Test
    void endGameReturnsRoomToOpen() {
        Long roomId = readyRoom();
        ClocktowerGameResponse started = gameService.startGame(roomId, owner());

        ClocktowerGameResponse ended = gameService.endGame(started.gameId(), owner());

        assertThat(ended.status()).isEqualTo("ENDED");
        ClocktowerGamePo game = gameRepository.findById(started.gameId()).orElseThrow();
        assertThat(game.getStatus()).isEqualTo("ENDED");
        assertThat(game.getEndedAt()).isNotNull();
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(roomId).orElseThrow();
        assertThat(profile.getStatus()).isEqualTo("LOBBY");
        assertThat(profile.getCurrentGameId()).isNull();
        RoomSpacePo room = roomSpaceRepository.findByIdAndDeletedFalse(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void timeoutAbortDisbandsRoomOnlyWhenRunning() {
        Long runningRoomId = readyRoom();
        ClocktowerGameResponse started = gameService.startGame(runningRoomId, owner());
        ClocktowerGamePo runningGame = gameRepository.findById(started.gameId()).orElseThrow();
        Instant now = Instant.parse("2026-06-24T10:00:00Z");
        Instant staleAt = now.minus(Duration.ofMinutes(10));
        runningGame.setLastActiveAt(staleAt);
        gameRepository.saveAndFlush(runningGame);
        ClocktowerRoomProfilePo runningProfile = profileRepository.findByRoomId(runningRoomId).orElseThrow();
        runningProfile.setLastActiveAt(staleAt);
        profileRepository.saveAndFlush(runningProfile);
        RoomSpacePo runningRoom = roomSpaceRepository.findById(runningRoomId).orElseThrow();
        runningRoom.setLastActiveAt(staleAt);
        roomSpaceRepository.saveAndFlush(runningRoom);

        boolean aborted = gameService.abortTimedOutRoom(runningRoomId, Duration.ofMinutes(5), now, owner());

        assertThat(aborted).isTrue();
        assertThat(gameRepository.findById(started.gameId()).orElseThrow().getStatus()).isEqualTo("ABORTED");
        ClocktowerRoomProfilePo disbandedProfile = profileRepository.findByRoomId(runningRoomId).orElseThrow();
        assertThat(disbandedProfile.getStatus()).isEqualTo("DISBANDED");
        assertThat(disbandedProfile.getCurrentGameId()).isNull();
        assertThat(roomSpaceRepository.findById(runningRoomId).orElseThrow().getStatus()).isEqualTo("DISBANDED");

        Long openRoomId = readyRoom();
        boolean openAborted = gameService.abortTimedOutRoom(openRoomId, Duration.ofMinutes(5), now, owner());

        assertThat(openAborted).isFalse();
        assertThat(profileRepository.findByRoomId(openRoomId).orElseThrow().getStatus()).isEqualTo("LOBBY");
        assertThat(roomSpaceRepository.findById(openRoomId).orElseThrow().getStatus()).isEqualTo("ACTIVE");

        Long endedRoomId = readyRoom();
        ClocktowerGameResponse endedStart = gameService.startGame(endedRoomId, owner());
        gameService.endGame(endedStart.gameId(), owner());
        boolean endedAborted = gameService.abortTimedOutRoom(endedRoomId, Duration.ofMinutes(5), now, owner());

        assertThat(endedAborted).isFalse();
        assertThat(profileRepository.findByRoomId(endedRoomId).orElseThrow().getStatus()).isEqualTo("LOBBY");
        assertThat(roomSpaceRepository.findById(endedRoomId).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void timeoutAbortRequiresRoomOwner() {
        Long roomId = readyRoom();
        ClocktowerGameResponse started = gameService.startGame(roomId, owner());
        Instant now = Instant.parse("2026-06-24T10:00:00Z");
        ClocktowerGamePo game = gameRepository.findById(started.gameId()).orElseThrow();
        game.setLastActiveAt(now.minus(Duration.ofMinutes(10)));
        gameRepository.saveAndFlush(game);

        assertThatThrownBy(() -> gameService.abortTimedOutRoom(
                roomId, Duration.ofMinutes(5), now, principal(2L, "luigi")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_STORYTELLER_FORBIDDEN");

        assertThat(gameRepository.findById(started.gameId()).orElseThrow().getStatus()).isEqualTo("RUNNING");
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(roomId).orElseThrow();
        assertThat(profile.getStatus()).isEqualTo("IN_GAME");
        assertThat(profile.getCurrentGameId()).isEqualTo(started.gameId());
    }

    @Test
    void timeoutAbortKeepsRunningGameWhenRoomOrProfileWasRecentlyActive() {
        Long roomId = readyRoom();
        ClocktowerGameResponse started = gameService.startGame(roomId, owner());
        Instant now = Instant.parse("2026-06-24T10:00:00Z");
        ClocktowerGamePo game = gameRepository.findById(started.gameId()).orElseThrow();
        game.setLastActiveAt(now.minus(Duration.ofMinutes(10)));
        gameRepository.saveAndFlush(game);
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomId(roomId).orElseThrow();
        profile.setLastActiveAt(now.minus(Duration.ofMinutes(1)));
        profileRepository.saveAndFlush(profile);
        RoomSpacePo room = roomSpaceRepository.findById(roomId).orElseThrow();
        room.setLastActiveAt(now.minus(Duration.ofMinutes(1)));
        roomSpaceRepository.saveAndFlush(room);

        boolean aborted = gameService.abortTimedOutRoom(roomId, Duration.ofMinutes(5), now, owner());

        assertThat(aborted).isFalse();
        assertThat(gameRepository.findById(started.gameId()).orElseThrow().getStatus()).isEqualTo("RUNNING");
        ClocktowerRoomProfilePo activeProfile = profileRepository.findByRoomId(roomId).orElseThrow();
        assertThat(activeProfile.getStatus()).isEqualTo("IN_GAME");
        assertThat(activeProfile.getCurrentGameId()).isEqualTo(started.gameId());
    }

    private void assertStartRejected(Long roomId, String code) {
        assertThatThrownBy(() -> gameService.startGame(roomId, owner()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining(code);
    }

    private Long readyRoom() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(), owner());
        claimHumanSeats(room.roomId(), 1, ROLE_CODES.size());
        assignReadyRoles(room.roomId());
        return room.roomId();
    }

    private ClocktowerRoomResponse agentRoom() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(4), owner());
        roomService.claimSeat(room.roomId(), 1, new ClocktowerSeatClaimRequest("Player 1"),
                principal(11L, "player1"));
        assignReadyRoles(room.roomId());
        return room;
    }

    private void claimHumanSeats(Long roomId, int firstSeatNo, int lastSeatNo) {
        for (int seatNo = firstSeatNo; seatNo <= lastSeatNo; seatNo++) {
            roomService.claimSeat(roomId, seatNo, new ClocktowerSeatClaimRequest("Player " + seatNo),
                    principal(10L + seatNo, "player" + seatNo));
        }
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

    private ClocktowerGameConversationResponse conversation(ClocktowerGameResponse response, String groupKey,
                                                           String conversationType) {
        return response.conversations().stream()
                .filter(conversation -> groupKey.equals(conversation.groupKey())
                        && conversationType.equals(conversation.conversationType()))
                .findFirst()
                .orElseThrow();
    }

    private List<Long> conversationMemberUserIds(Long conversationId) {
        return jdbcTemplate.queryForList("""
                select user_id
                from im_conversation_member
                where conversation_id = ?
                  and deleted = false
                order by user_id
                """, Long.class, conversationId);
    }

    private ClocktowerRoomCreateRequest createRequest() {
        return createRequest(0);
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
}
