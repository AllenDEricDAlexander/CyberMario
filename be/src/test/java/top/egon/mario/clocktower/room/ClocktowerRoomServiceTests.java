package top.egon.mario.clocktower.room;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerRoomServiceTests {

    private final ClocktowerRoomService roomService = ClocktowerRoomTestFactory.service();

    @Test
    void createRoomCreatesLobbySeatsAndRoomCreatedEvent() {
        ClocktowerRoomCreateRequest request = createFivePlayerRequest();

        ClocktowerRoomResponse room = roomService.create(request, principal(1L, "mario"));

        assertThat(room.status()).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(room.phase()).isEqualTo(ClocktowerPhase.LOBBY);
        assertThat(room.seats()).hasSize(5);
        assertThat(room.roomCode()).hasSize(6);
    }

    @Test
    void joinRoomBindsCurrentUserToRequestedSeat() {
        ClocktowerRoomResponse room = roomService.create(createFivePlayerRequest(), principal(1L, "mario"));

        ClocktowerSeatResponse seat = roomService.join(room.roomId(),
                new ClocktowerRoomJoinRequest(2, "Luigi", null), principal(2L, "luigi"));

        assertThat(seat.seatNo()).isEqualTo(2);
        assertThat(seat.userId()).isEqualTo(2L);
        assertThat(seat.displayName()).isEqualTo("Luigi");
    }

    private static ClocktowerRoomCreateRequest createFivePlayerRequest() {
        return new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN", false, true, 0);
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
