package top.egon.mario.clocktower.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMarkReadRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatPrivateConversationRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatReadStateResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatSendMessageRequest;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
import top.egon.mario.clocktower.chat.service.ClocktowerChatServiceImpl;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameConversationResponse;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ClocktowerChatServiceTests {

    private static final Long SPECTATOR_USER_ID = 90L;
    private static final List<String> ROLE_CODES = List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP");

    @Autowired
    private ClocktowerChatService chatService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Test
    void chatServiceImplDependsOnClocktowerAdapterInsteadOfLegacyImBoundary() {
        assertThat(fieldTypes(ClocktowerChatServiceImpl.class).stream()
                .map(Class::getName)
                .toList())
                .contains(ClocktowerImAdapter.class.getName())
                .noneMatch(fieldType -> fieldType.startsWith("top.egon.mario.im."));
    }

    @Test
    void gameStartupCreatesStableClocktowerConversationSemanticsThroughAdapter() {
        StartedGame startedGame = startedGameWithSpectator();

        assertThat(startedGame.game().conversations())
                .extracting(ClocktowerGameConversationResponse::groupKey)
                .containsExactly(
                        ClocktowerChatConstants.GROUP_PUBLIC,
                        ClocktowerChatConstants.GROUP_PRIVATE,
                        ClocktowerChatConstants.GROUP_SPECTATOR,
                        ClocktowerChatConstants.GROUP_SYSTEM);
        assertThat(startedGame.game().conversations())
                .extracting(ClocktowerGameConversationResponse::conversationType)
                .containsExactly(
                        ClocktowerChatConstants.CONVERSATION_PUBLIC,
                        ClocktowerChatConstants.CONVERSATION_PRIVATE_CONTAINER,
                        ClocktowerChatConstants.CONVERSATION_SPECTATOR,
                        ClocktowerChatConstants.CONVERSATION_SYSTEM);
    }

    @Test
    void spectatorSendToSpectatorChannelCreatesActiveImMemberBeforePersistingMessage() {
        StartedGame startedGame = startedGameWithSpectator();
        Long spectatorConversationId = spectatorConversationId(startedGame.game());

        ClocktowerChatMessageResponse response = chatService.sendMessage(spectatorConversationId,
                new ClocktowerChatSendMessageRequest("spectator hello", null), spectator());

        assertThat(response.messageId()).isNotNull();
        assertThat(response.senderUserId()).isEqualTo(SPECTATOR_USER_ID);
        assertThat(response.content()).isEqualTo("spectator hello");
        assertThat(response.messageType()).isEqualTo("TEXT");
        assertThat(chatService.messages(spectatorConversationId, PageRequest.of(0, 10), spectator()).getContent())
                .extracting(ClocktowerChatMessageResponse::messageId)
                .contains(response.messageId());
    }

    @Test
    void spectatorMarkReadOnSpectatorChannelCreatesActiveImMemberAndReadState() {
        StartedGame startedGame = startedGameWithSpectator();
        Long spectatorConversationId = spectatorConversationId(startedGame.game());

        ClocktowerChatReadStateResponse response = chatService.markRead(spectatorConversationId,
                new ClocktowerChatMarkReadRequest(1L), spectator());

        assertThat(response.conversationId()).isEqualTo(spectatorConversationId);
        assertThat(response.userId()).isEqualTo(SPECTATOR_USER_ID);
        assertThat(response.lastReadMessageSeq()).isZero();
    }

    @Test
    void privateConversationCreatesGameScopedPrivateSurfaceWithStableClocktowerSemantics() {
        StartedGame startedGame = startedGameWithSpectator();
        ClocktowerGamePo game = gameRepository.findById(startedGame.game().gameId()).orElseThrow();
        game.setPhase("DAY");
        game.setDayNo(1);
        gameRepository.saveAndFlush(game);

        ClocktowerChatPrivateConversationRequest request =
                new ClocktowerChatPrivateConversationRequest(game.getRoomId(), 12L);
        ClocktowerChatMessageResponse publicMessage = chatService.sendMessage(publicConversationId(startedGame.game()),
                new ClocktowerChatSendMessageRequest("public day hello", null), principal(11L, "player1"));
        ClocktowerChatConversationResponse conversation = chatService.privateConversation(request,
                principal(11L, "player1"));

        assertThat(publicMessage.content()).isEqualTo("public day hello");
        assertThat(conversation.roomId()).isEqualTo(game.getRoomId());
        assertThat(conversation.gameId()).isEqualTo(game.getId());
        assertThat(conversation.channelKey()).isEqualTo(ClocktowerChatConstants.CHANNEL_GAME);
        assertThat(conversation.groupKey()).isEqualTo(ClocktowerChatConstants.GROUP_PRIVATE);
        assertThat(conversation.conversationType()).isEqualTo(ClocktowerChatConstants.CONVERSATION_PRIVATE);
        assertThat(conversation.displayPeerKey()).isEqualTo("11:12");
        ClocktowerChatMessageResponse privateMessage = chatService.sendMessage(conversation.conversationId(),
                new ClocktowerChatSendMessageRequest("private day hello", null), principal(11L, "player1"));
        assertThat(chatService.messages(conversation.conversationId(), PageRequest.of(0, 10),
                principal(12L, "player2")).getContent())
                .extracting(ClocktowerChatMessageResponse::messageId)
                .contains(privateMessage.messageId());
        assertThat(chatService.conversationsForGame(game.getRoomId(), game.getId(), principal(11L, "player1")))
                .extracting(ClocktowerChatConversationResponse::conversationType)
                .contains(ClocktowerChatConstants.CONVERSATION_PRIVATE);
    }

    private StartedGame startedGameWithSpectator() {
        Long roomId = readyRoom();
        ClocktowerGameResponse game = gameService.startGame(roomId, owner());
        roomService.enterRoom(roomId, spectator());
        return new StartedGame(game);
    }

    private Long readyRoom() {
        ClocktowerRoomResponse room = roomService.createRoom(createRequest(), owner());
        for (int seatNo = 1; seatNo <= ROLE_CODES.size(); seatNo++) {
            roomService.claimSeat(room.roomId(), seatNo, new ClocktowerSeatClaimRequest("Player " + seatNo),
                    principal(10L + seatNo, "player" + seatNo));
        }
        List<ClocktowerRoomSeatPo> seats = roomSeatRepository.findByRoomIdOrderBySeatNoAsc(room.roomId());
        for (int index = 0; index < seats.size(); index++) {
            ClocktowerRoomSeatPo seat = seats.get(index);
            seat.setRoleCode(ROLE_CODES.get(index));
            seat.setMetadataJson("{\"ready\":true}");
        }
        roomSeatRepository.saveAllAndFlush(seats);
        return room.roomId();
    }

    private ClocktowerRoomCreateRequest createRequest() {
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
                0,
                "PUBLIC",
                "OPEN_SEATING"
        );
    }

    private Long spectatorConversationId(ClocktowerGameResponse game) {
        return game.conversations().stream()
                .filter(conversation -> ClocktowerChatConstants.GROUP_SPECTATOR.equals(conversation.groupKey()))
                .filter(conversation -> ClocktowerChatConstants.CONVERSATION_SPECTATOR.equals(
                        conversation.conversationType()))
                .findFirst()
                .map(ClocktowerGameConversationResponse::conversationId)
                .orElseThrow();
    }

    private Long publicConversationId(ClocktowerGameResponse game) {
        return game.conversations().stream()
                .filter(conversation -> ClocktowerChatConstants.GROUP_PUBLIC.equals(conversation.groupKey()))
                .filter(conversation -> ClocktowerChatConstants.CONVERSATION_PUBLIC.equals(
                        conversation.conversationType()))
                .findFirst()
                .map(ClocktowerGameConversationResponse::conversationId)
                .orElseThrow();
    }

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal spectator() {
        return principal(SPECTATOR_USER_ID, "spectator");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }

    private List<Class<?>> fieldTypes(Class<?> type) {
        return List.of(type.getDeclaredFields()).stream()
                .map(Field::getType)
                .toList();
    }

    private record StartedGame(ClocktowerGameResponse game) {
    }
}
