package top.egon.mario.clocktower.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.clocktower.chat.ClocktowerChatConstants;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.dto.ClocktowerGameResponse;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.replay.ClocktowerProjectionTestSupport;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameSeatViewResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameViewResponse;
import top.egon.mario.clocktower.view.service.ClocktowerGameViewService;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
import top.egon.mario.room.repository.RoomSpaceRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ClocktowerViewerMatrixTests {

    @Autowired
    private ClocktowerGameViewService gameViewService;

    @Autowired
    private ClocktowerRoomLobbyService roomService;

    @Autowired
    private ClocktowerGameLifecycleService gameService;

    @Autowired
    private ClocktowerChatService chatService;

    @Autowired
    private ClocktowerRoomSeatRepository roomSeatRepository;

    @Autowired
    private ClocktowerRoomProfileRepository profileRepository;

    @Autowired
    private ClocktowerGameRepository gameRepository;

    @Autowired
    private ClocktowerGameSeatRepository gameSeatRepository;

    @Autowired
    private ClocktowerGameEventRepository gameEventRepository;

    @Autowired
    private RoomSpaceRepository roomSpaceRepository;

    @Test
    void storytellerViewIncludesGrimoireAndPrivatePlayerChatsButNotSpectatorChannel() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        ClocktowerGameViewResponse view = gameViewService.gameView(started.gameId(), started.owner());

        assertThat(view.viewerMode()).isEqualTo(ClocktowerViewerMode.STORYTELLER);
        assertThat(view.grimoire()).hasSize(ClocktowerProjectionTestSupport.ROLE_CODES.size());
        assertThat(view.grimoire())
                .extracting(ClocktowerGameSeatViewResponse::roleCode)
                .containsExactlyElementsOf(ClocktowerProjectionTestSupport.ROLE_CODES);
        assertThat(view.conversations())
                .extracting(ClocktowerChatConversationResponse::groupKey)
                .contains(ClocktowerChatConstants.GROUP_PRIVATE)
                .doesNotContain(ClocktowerChatConstants.GROUP_SPECTATOR);
    }

    @Test
    void playerViewIncludesOwnRolePublicSeatsAndOwnPrivateChats() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        ClocktowerGameViewResponse view = gameViewService.gameView(started.gameId(), started.playerOne());

        assertThat(view.viewerMode()).isEqualTo(ClocktowerViewerMode.PLAYER);
        assertThat(view.mySeat()).isNotNull();
        assertThat(view.mySeat().roleCode()).isEqualTo("EMPATH");
        assertThat(view.publicSeats()).allSatisfy(seat -> assertThat(seat.roleCode()).isNull());
        assertThat(view.grimoire()).isEmpty();
        assertThat(view.conversations())
                .extracting(ClocktowerChatConversationResponse::conversationId)
                .contains(started.privateConversationId())
                .doesNotContain(started.spectatorConversationId());
        assertThat(view.events())
                .extracting(ClocktowerGameEventResponse::eventType)
                .contains("PUBLIC_TOWN_EVENT", "PLAYER_ONE_PRIVATE_EVENT")
                .doesNotContain("PLAYER_TWO_PRIVATE_EVENT", "STORYTELLER_GRIMOIRE_EVENT", "ADMIN_AUDIT_EVENT");
    }

    @Test
    void spectatorViewIncludesPublicSeatsPublicEventsPublicChatAndSpectatorChannel() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        ClocktowerGameViewResponse view = gameViewService.gameView(started.gameId(), started.spectator());

        assertThat(view.viewerMode()).isEqualTo(ClocktowerViewerMode.SPECTATOR);
        assertThat(view.mySeat()).isNull();
        assertThat(view.grimoire()).isEmpty();
        assertThat(view.availableActions()).isEmpty();
        assertThat(view.publicSeats()).allSatisfy(seat -> assertThat(seat.roleCode()).isNull());
        assertThat(view.events())
                .allSatisfy(event -> assertThat(event.visibility()).isEqualTo("PUBLIC"));
        assertThat(view.events())
                .extracting(ClocktowerGameEventResponse::eventType)
                .contains("PUBLIC_TOWN_EVENT");
        assertThat(view.conversations())
                .extracting(ClocktowerChatConversationResponse::groupKey)
                .contains(ClocktowerChatConstants.GROUP_PUBLIC, ClocktowerChatConstants.GROUP_SPECTATOR)
                .doesNotContain(ClocktowerChatConstants.GROUP_PRIVATE);
    }

    @Test
    void historicalGameViewUsesRequestedGameConversationsWhenRoomCurrentGameChanges() {
        ClocktowerProjectionTestSupport.StartedProjection gameA = support().runningGame();
        gameService.endGame(gameA.gameId(), gameA.owner());
        ClocktowerGameResponse gameB = gameService.startGame(gameA.roomId(), gameA.owner());
        Long gameBPublicConversationId = publicConversationId(gameB);

        ClocktowerGameViewResponse view = gameViewService.gameView(gameA.gameId(), gameA.playerOne());

        assertThat(view.conversations())
                .extracting(ClocktowerChatConversationResponse::conversationId)
                .contains(gameA.publicConversationId())
                .doesNotContain(gameBPublicConversationId);
    }

    @Test
    void superAdminNormalRoomPageDoesNotBecomeOmniscient() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        ClocktowerGameViewResponse view = gameViewService.gameView(started.gameId(), started.adminSpectator());

        assertThat(view.viewerMode()).isEqualTo(ClocktowerViewerMode.SPECTATOR);
        assertThat(view.mySeat()).isNull();
        assertThat(view.grimoire()).isEmpty();
        assertThat(view.publicSeats()).allSatisfy(seat -> assertThat(seat.roleCode()).isNull());
        assertThat(view.conversations())
                .extracting(ClocktowerChatConversationResponse::conversationId)
                .doesNotContain(started.privateConversationId());
        assertThat(view.events())
                .allSatisfy(event -> assertThat(event.visibility()).isEqualTo("PUBLIC"));
    }

    private ClocktowerProjectionTestSupport support() {
        return new ClocktowerProjectionTestSupport(roomService, gameService, chatService, roomSeatRepository,
                profileRepository, gameRepository, gameSeatRepository, gameEventRepository, roomSpaceRepository);
    }

    private Long publicConversationId(ClocktowerGameResponse game) {
        return game.conversations().stream()
                .filter(conversation -> ClocktowerChatConstants.GROUP_PUBLIC.equals(conversation.groupKey()))
                .filter(conversation -> ClocktowerChatConstants.CONVERSATION_PUBLIC.equals(
                        conversation.conversationType()))
                .findFirst()
                .map(conversation -> conversation.conversationId())
                .orElseThrow();
    }
}
