package top.egon.mario.clocktower.replay;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.grimoire.po.ClocktowerVotePo;
import top.egon.mario.clocktower.replay.dto.ClocktowerReplayResponse;
import top.egon.mario.clocktower.replay.service.ClocktowerReplayService;
import top.egon.mario.clocktower.replay.service.impl.ClocktowerReplayServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerReplayServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerReplayService replayService = new ClocktowerReplayServiceImpl(context.roomRepository(),
            context.eventRepository(), context.voteRepository(), context.objectMapper());

    @Test
    void publicReplayHidesPrivateAndStorytellerEvents() {
        Long roomId = completedRoomWithMixedEvents();

        ClocktowerReplayResponse replay = replayService.replay(roomId, "PUBLIC", null, null, principal(1L, "mario"));

        assertThat(replay.mode()).isEqualTo("PUBLIC");
        assertThat(replay.events()).allSatisfy(event -> assertThat(event.visibility()).isEqualTo(ClocktowerVisibility.PUBLIC));
    }

    @Test
    void fullReplayRequiresRoomStoryteller() {
        Long roomId = completedRoomWithMixedEvents();

        ClocktowerReplayResponse storytellerReplay = replayService.replay(roomId, "FULL", null, null,
                principal(1L, "storyteller"));

        assertThat(storytellerReplay.events())
                .extracting(event -> event.visibility())
                .contains(ClocktowerVisibility.PRIVATE, ClocktowerVisibility.STORYTELLER);
        assertThatThrownBy(() -> replayService.replay(roomId, "FULL", null, null, principal(2L, "luigi")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_REPLAY_FORBIDDEN");
    }

    @Test
    void fullReplayAndVotesRejectNonMemberSuperAdminOnNormalRoute() {
        Long roomId = completedRoomWithMixedEvents();
        ClocktowerVotePo vote = new ClocktowerVotePo();
        vote.setRoomId(roomId);
        vote.setNominationId(100L);
        vote.setVoterSeatId(10L);
        vote.setVoteValue(true);
        context.voteRepository().save(vote);

        assertThatThrownBy(() -> replayService.replay(roomId, "FULL", null, null, superAdminPrincipal()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_REPLAY_FORBIDDEN");
        assertThatThrownBy(() -> replayService.votes(roomId, superAdminPrincipal()))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_REPLAY_FORBIDDEN");
    }

    private Long completedRoomWithMixedEvents() {
        ClocktowerRoomPo room = new ClocktowerRoomPo();
        room.setRoomCode("ROOM01");
        room.setName("回放房间");
        room.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        room.setStatus(ClocktowerRoomStatus.ENDED);
        room.setPhase(ClocktowerPhase.ENDED);
        room.setPlayerCount(5);
        room.setStorytellerUserId(1L);
        room.setStorytellerMode("HUMAN");
        context.roomRepository().save(room);
        Long roomId = room.getId();
        context.eventService().append(new ClocktowerEventAppendRequest(roomId, ClocktowerEventType.PUBLIC_MESSAGE_SENT,
                ClocktowerPhase.DAY, 1, 0, 1L, null, null, ClocktowerVisibility.PUBLIC, List.of(),
                Map.of("content", "public")));
        context.eventService().append(new ClocktowerEventAppendRequest(roomId, ClocktowerEventType.PRIVATE_MESSAGE_SENT,
                ClocktowerPhase.DAY, 1, 0, 1L, null, null, ClocktowerVisibility.PRIVATE, List.of(10L),
                Map.of("content", "private")));
        context.eventService().append(new ClocktowerEventAppendRequest(roomId, ClocktowerEventType.STORYTELLER_RULING,
                ClocktowerPhase.DAY, 1, 0, 1L, null, null, ClocktowerVisibility.STORYTELLER, List.of(),
                Map.of("content", "storyteller")));
        return roomId;
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }

    private static RbacPrincipal superAdminPrincipal() {
        return new RbacPrincipal(900L, "admin", Set.of("SUPER_ADMIN"), Set.of(), "v1");
    }
}
