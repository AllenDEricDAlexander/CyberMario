package top.egon.mario.clocktower.replay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.clocktower.admin.dto.ClocktowerGameAuditResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerRoomAuditResponse;
import top.egon.mario.clocktower.admin.service.ClocktowerManagementAuditService;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.service.ClocktowerChatService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameHistoryResponse;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameReplayResponse;
import top.egon.mario.clocktower.replay.service.ClocktowerGameReplayService;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameSeatViewResponse;
import top.egon.mario.room.repository.RoomSpaceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ClocktowerGameReplayAccessTests {

    @Autowired
    private ClocktowerGameReplayService gameReplayService;

    @Autowired
    private ClocktowerManagementAuditService auditService;

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
    void gamePlayerSeesOnlyOwnVisibleReplay() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        ClocktowerGameReplayResponse replay = gameReplayService.replay(started.gameId(), started.playerOne());

        assertThat(replay.viewerMode()).isEqualTo(ClocktowerViewerMode.PLAYER);
        assertThat(replay.events())
                .extracting(ClocktowerGameEventResponse::eventType)
                .contains("PUBLIC_TOWN_EVENT", "PLAYER_ONE_PRIVATE_EVENT")
                .doesNotContain("PLAYER_TWO_PRIVATE_EVENT", "STORYTELLER_GRIMOIRE_EVENT", "ADMIN_AUDIT_EVENT");
    }

    @Test
    void storytellerSeesFullGameReplay() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        ClocktowerGameReplayResponse replay = gameReplayService.replay(started.gameId(), started.owner());

        assertThat(replay.viewerMode()).isEqualTo(ClocktowerViewerMode.STORYTELLER);
        assertThat(replay.events())
                .extracting(ClocktowerGameEventResponse::eventType)
                .contains("PUBLIC_TOWN_EVENT", "PLAYER_ONE_PRIVATE_EVENT", "PLAYER_TWO_PRIVATE_EVENT",
                        "STORYTELLER_GRIMOIRE_EVENT")
                .doesNotContain("ADMIN_AUDIT_EVENT");
    }

    @Test
    void spectatorDoesNotSeeGameReplay() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        assertThatThrownBy(() -> gameReplayService.replay(started.gameId(), started.spectator()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_REPLAY_FORBIDDEN");
    }

    @Test
    void adminAuditSeesFullRoomGameChatGovernanceDataOnlyThroughAdminApi() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        assertThatThrownBy(() -> gameReplayService.replay(started.gameId(), started.admin()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_REPLAY_FORBIDDEN");

        ClocktowerRoomAuditResponse roomAudit = auditService.auditRoom(started.roomId(), started.admin());
        assertThat(roomAudit.games())
                .extracting(ClocktowerGameHistoryResponse::gameId)
                .contains(started.gameId());
        assertThat(roomAudit.members())
                .extracting(ClocktowerRoomAuditResponse.Member::userId)
                .contains(started.spectator().userId(), started.adminSpectator().userId());
        assertThat(roomAudit.conversations())
                .extracting(ClocktowerChatConversationResponse::conversationId)
                .contains(started.privateConversationId(), started.spectatorConversationId());

        ClocktowerGameAuditResponse gameAudit = auditService.auditGame(started.gameId(), started.admin());
        assertThat(gameAudit.seats())
                .extracting(ClocktowerGameSeatViewResponse::roleCode)
                .containsExactlyElementsOf(ClocktowerProjectionTestSupport.ROLE_CODES);
        assertThat(gameAudit.events())
                .extracting(ClocktowerGameEventResponse::eventType)
                .contains("PUBLIC_TOWN_EVENT", "PLAYER_ONE_PRIVATE_EVENT", "PLAYER_TWO_PRIVATE_EVENT",
                        "STORYTELLER_GRIMOIRE_EVENT", "ADMIN_AUDIT_EVENT");
        assertThat(gameAudit.conversations())
                .extracting(ClocktowerChatConversationResponse::conversationId)
                .contains(started.privateConversationId(), started.spectatorConversationId());

        Page<ClocktowerChatMessageResponse> messages = auditService.messages(
                started.privateConversationId(), PageRequest.of(0, 20), started.admin());
        assertThat(messages.getContent())
                .extracting(ClocktowerChatMessageResponse::content)
                .contains("private whisper");

        assertThatThrownBy(() -> auditService.auditGame(started.gameId(), started.playerOne()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_AUDIT_FORBIDDEN");
    }

    @Test
    void disbandedRoomIsAbsentFromNormalRoomListButHistoryRemainsEligible() {
        ClocktowerProjectionTestSupport.StartedProjection started = support().runningGame();

        support().disbandRunningRoom(started.roomId(), started.gameId(), started.owner());

        assertThat(roomService.listVisibleRooms(started.playerOne()))
                .extracting(ClocktowerRoomResponse::roomId)
                .doesNotContain(started.roomId());
        assertThat(gameReplayService.history(started.playerOne()))
                .extracting(ClocktowerGameHistoryResponse::gameId)
                .contains(started.gameId());
        assertThat(gameReplayService.history(started.owner()))
                .extracting(ClocktowerGameHistoryResponse::gameId)
                .contains(started.gameId());
    }

    private ClocktowerProjectionTestSupport support() {
        return new ClocktowerProjectionTestSupport(roomService, gameService, chatService, roomSeatRepository,
                profileRepository, gameRepository, gameSeatRepository, gameEventRepository, roomSpaceRepository);
    }
}
