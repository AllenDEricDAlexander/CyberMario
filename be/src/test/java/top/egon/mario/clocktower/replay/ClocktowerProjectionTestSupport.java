package top.egon.mario.clocktower.replay;

import top.egon.mario.clocktower.chat.ClocktowerChatConstants;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatPrivateConversationRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatSendMessageRequest;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
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
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ClocktowerProjectionTestSupport {

    public static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    private static final AtomicLong USER_ID_BASE = new AtomicLong(10_000L);

    private final ClocktowerRoomLobbyService roomService;
    private final ClocktowerGameLifecycleService gameService;
    private final ClocktowerChatService chatService;
    private final ClocktowerRoomSeatRepository roomSeatRepository;
    private final ClocktowerRoomProfileRepository profileRepository;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final RoomSpaceRepository roomSpaceRepository;

    public ClocktowerProjectionTestSupport(ClocktowerRoomLobbyService roomService,
                                           ClocktowerGameLifecycleService gameService,
                                           ClocktowerChatService chatService,
                                           ClocktowerRoomSeatRepository roomSeatRepository,
                                           ClocktowerRoomProfileRepository profileRepository,
                                           ClocktowerGameRepository gameRepository,
                                           ClocktowerGameSeatRepository gameSeatRepository,
                                           ClocktowerGameEventRepository gameEventRepository,
                                           RoomSpaceRepository roomSpaceRepository) {
        this.roomService = roomService;
        this.gameService = gameService;
        this.chatService = chatService;
        this.roomSeatRepository = roomSeatRepository;
        this.profileRepository = profileRepository;
        this.gameRepository = gameRepository;
        this.gameSeatRepository = gameSeatRepository;
        this.gameEventRepository = gameEventRepository;
        this.roomSpaceRepository = roomSpaceRepository;
    }

    public StartedProjection runningGame() {
        long base = USER_ID_BASE.getAndAdd(1_000L);
        RbacPrincipal owner = principal(base + 1, "storyteller");
        RbacPrincipal playerOne = principal(base + 11, "player-one");
        RbacPrincipal playerTwo = principal(base + 12, "player-two");
        RbacPrincipal spectator = principal(base + 90, "spectator");
        RbacPrincipal adminSpectator = new RbacPrincipal(base + 91, "admin-spectator",
                Set.of("SUPER_ADMIN", "CLOCKTOWER_PLAYER"), Set.of(), "v1");
        RbacPrincipal admin = new RbacPrincipal(base + 900, "admin", Set.of("SUPER_ADMIN"), Set.of(), "v1");

        ClocktowerRoomResponse room = roomService.createRoom(createRequest(), owner);
        for (int seatNo = 1; seatNo <= ROLE_CODES.size(); seatNo++) {
            roomService.claimSeat(room.roomId(), seatNo, new ClocktowerSeatClaimRequest("Player " + seatNo),
                    principal(base + 10 + seatNo, "player-" + seatNo));
        }
        List<ClocktowerRoomSeatPo> roomSeats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(room.roomId());
        for (int index = 0; index < roomSeats.size(); index++) {
            ClocktowerRoomSeatPo seat = roomSeats.get(index);
            seat.setRoleCode(ROLE_CODES.get(index));
            seat.setMetadataJson("{\"ready\":true}");
        }
        roomSeatRepository.saveAllAndFlush(roomSeats);

        ClocktowerGameResponse game = gameService.startGame(room.roomId(), owner);
        ClocktowerGamePo gamePo = gameRepository.findByIdAndDeletedFalse(game.gameId()).orElseThrow();
        gamePo.setPhase("DAY");
        gamePo.setDayNo(1);
        gameRepository.saveAndFlush(gamePo);
        roomService.enterRoom(room.roomId(), spectator);
        roomService.enterRoom(room.roomId(), adminSpectator);

        List<ClocktowerGameSeatPo> gameSeats = gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(
                game.gameId());
        Long playerOneGameSeatId = gameSeats.getFirst().getId();
        Long playerTwoGameSeatId = gameSeats.get(1).getId();

        Long publicConversationId = conversationId(game.conversations(), ClocktowerChatConstants.GROUP_PUBLIC,
                ClocktowerChatConstants.CONVERSATION_PUBLIC);
        Long spectatorConversationId = conversationId(game.conversations(), ClocktowerChatConstants.GROUP_SPECTATOR,
                ClocktowerChatConstants.CONVERSATION_SPECTATOR);
        chatService.sendMessage(publicConversationId, new ClocktowerChatSendMessageRequest("town square", null),
                playerOne);
        chatService.sendMessage(spectatorConversationId, new ClocktowerChatSendMessageRequest("spectator rail", null),
                spectator);
        Long privateConversationId = chatService.privateConversation(
                new ClocktowerChatPrivateConversationRequest(room.roomId(), playerTwo.userId()), playerOne)
                .conversationId();
        chatService.sendMessage(privateConversationId, new ClocktowerChatSendMessageRequest("private whisper", null),
                playerOne);

        appendGameEvent(game.gameId(), 2L, "PUBLIC_TOWN_EVENT", "PUBLIC", List.of(),
                Map.of("content", "public event"));
        appendGameEvent(game.gameId(), 3L, "PLAYER_ONE_PRIVATE_EVENT", "PRIVATE", List.of(playerOneGameSeatId),
                Map.of("content", "own private event"));
        appendGameEvent(game.gameId(), 4L, "PLAYER_TWO_PRIVATE_EVENT", "PRIVATE", List.of(playerTwoGameSeatId),
                Map.of("content", "other private event"));
        appendGameEvent(game.gameId(), 5L, "STORYTELLER_GRIMOIRE_EVENT", "STORYTELLER", List.of(),
                Map.of("content", "storyteller event"));
        appendGameEvent(game.gameId(), 6L, "ADMIN_AUDIT_EVENT", "AUDIT", List.of(),
                Map.of("content", "audit event"));

        return new StartedProjection(room.roomId(), game.gameId(), publicConversationId, spectatorConversationId,
                privateConversationId, playerOneGameSeatId, playerTwoGameSeatId, owner, playerOne, playerTwo,
                spectator, adminSpectator, admin);
    }

    public void disbandRunningRoom(Long roomId, Long gameId, RbacPrincipal owner) {
        Instant oldLastActiveAt = Instant.now().minusSeconds(3_600);
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId).orElseThrow();
        game.setLastActiveAt(oldLastActiveAt);
        gameRepository.saveAndFlush(game);
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomIdAndDeletedFalse(roomId).orElseThrow();
        profile.setLastActiveAt(oldLastActiveAt);
        profileRepository.saveAndFlush(profile);
        RoomSpacePo room = roomSpaceRepository.findByIdAndDeletedFalse(roomId).orElseThrow();
        room.setLastActiveAt(oldLastActiveAt);
        roomSpaceRepository.saveAndFlush(room);
        gameService.abortTimedOutRoom(roomId, java.time.Duration.ofSeconds(60), Instant.now(), owner);
    }

    private ClocktowerRoomCreateRequest createRequest() {
        return new ClocktowerRoomCreateRequest(
                "Projection Room",
                ClocktowerScriptCode.TROUBLE_BREWING,
                ROLE_CODES.size(),
                null,
                null,
                ROLE_CODES,
                "HUMAN",
                true,
                true,
                0,
                "PUBLIC",
                "OPEN_SEATING"
        );
    }

    private void appendGameEvent(Long gameId, Long eventSeq, String eventType, String visibility,
                                 List<Long> visibleGameSeatIds, Map<String, Object> payload) {
        ClocktowerGameEventPo event = new ClocktowerGameEventPo();
        event.setGameId(gameId);
        event.setEventSeq(eventSeq);
        event.setEventType(eventType);
        event.setPhase("DAY");
        event.setDayNo(1);
        event.setNightNo(1);
        event.setVisibility(visibility);
        event.setVisibleGameSeatIdsJson(jsonArray(visibleGameSeatIds));
        event.setPayloadJson(jsonObject(payload));
        event.setStatus("VISIBLE");
        event.setOccurredAt(Instant.now());
        gameEventRepository.saveAndFlush(event);
    }

    private static Long conversationId(List<ClocktowerGameConversationResponse> conversations, String groupKey,
                                       String conversationType) {
        return conversations.stream()
                .filter(conversation -> groupKey.equals(conversation.groupKey()))
                .filter(conversation -> conversationType.equals(conversation.conversationType()))
                .findFirst()
                .map(ClocktowerGameConversationResponse::conversationId)
                .orElseThrow();
    }

    private static String jsonArray(List<Long> values) {
        return values.isEmpty() ? "[]" : values.toString();
    }

    private static String jsonObject(Map<String, Object> values) {
        if (values.isEmpty()) {
            return "{}";
        }
        return "{\"content\":\"" + values.get("content") + "\"}";
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }

    public record StartedProjection(
            Long roomId,
            Long gameId,
            Long publicConversationId,
            Long spectatorConversationId,
            Long privateConversationId,
            Long playerOneGameSeatId,
            Long playerTwoGameSeatId,
            RbacPrincipal owner,
            RbacPrincipal playerOne,
            RbacPrincipal playerTwo,
            RbacPrincipal spectator,
            RbacPrincipal adminSpectator,
            RbacPrincipal admin
    ) {
    }
}
