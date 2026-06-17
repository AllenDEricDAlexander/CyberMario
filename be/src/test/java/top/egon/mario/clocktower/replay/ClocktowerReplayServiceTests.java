package top.egon.mario.clocktower.replay;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
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
    void fullReplayRequiresStorytellerOrAdmin() {
        Long roomId = completedRoomWithMixedEvents();

        assertThatThrownBy(() -> replayService.replay(roomId, "FULL", null, null, principal(2L, "luigi")))
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
}
