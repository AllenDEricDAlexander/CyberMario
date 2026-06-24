package top.egon.mario.clocktower.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMarkReadRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatReadStateResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatSendMessageRequest;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameConversationResponse;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImMessageRepository;
import top.egon.mario.im.repository.ImReadStateRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

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
    private ImConversationMemberRepository memberRepository;

    @Autowired
    private ImMessageRepository messageRepository;

    @Autowired
    private ImReadStateRepository readStateRepository;

    @Test
    void spectatorSendToSpectatorChannelCreatesActiveImMemberBeforePersistingMessage() {
        StartedGame startedGame = startedGameWithSpectator();
        Long spectatorConversationId = spectatorConversationId(startedGame.game());

        assertThat(memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                spectatorConversationId, SPECTATOR_USER_ID, "ACTIVE")).isFalse();

        ClocktowerChatMessageResponse response = chatService.sendMessage(spectatorConversationId,
                new ClocktowerChatSendMessageRequest("spectator hello", null), spectator());

        ImMessagePo message = messageRepository
                .findTopByConversationIdAndDeletedFalseOrderByMessageSeqDesc(spectatorConversationId)
                .orElseThrow();
        assertThat(response.messageId()).isEqualTo(message.getId());
        assertThat(message.getSenderUserId()).isEqualTo(SPECTATOR_USER_ID);
        assertThat(message.getSenderMemberId()).isNotNull();
        assertThat(memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                spectatorConversationId, SPECTATOR_USER_ID, "ACTIVE")).isTrue();
    }

    @Test
    void spectatorMarkReadOnSpectatorChannelCreatesActiveImMemberAndReadState() {
        StartedGame startedGame = startedGameWithSpectator();
        Long spectatorConversationId = spectatorConversationId(startedGame.game());

        assertThat(memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                spectatorConversationId, SPECTATOR_USER_ID, "ACTIVE")).isFalse();

        ClocktowerChatReadStateResponse response = chatService.markRead(spectatorConversationId,
                new ClocktowerChatMarkReadRequest(1L), spectator());

        assertThat(response.conversationId()).isEqualTo(spectatorConversationId);
        assertThat(response.userId()).isEqualTo(SPECTATOR_USER_ID);
        assertThat(readStateRepository.findByConversationIdAndUserIdAndDeletedFalse(
                spectatorConversationId, SPECTATOR_USER_ID)).isPresent();
        assertThat(memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                spectatorConversationId, SPECTATOR_USER_ID, "ACTIVE")).isTrue();
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

    private RbacPrincipal owner() {
        return principal(1L, "mario");
    }

    private RbacPrincipal spectator() {
        return principal(SPECTATOR_USER_ID, "spectator");
    }

    private RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }

    private record StartedGame(ClocktowerGameResponse game) {
    }
}
