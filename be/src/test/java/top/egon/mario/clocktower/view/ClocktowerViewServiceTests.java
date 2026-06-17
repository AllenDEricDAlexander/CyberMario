package top.egon.mario.clocktower.view;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.clocktower.view.dto.ClocktowerPlayerViewResponse;
import top.egon.mario.clocktower.view.dto.PublicSeatResponse;
import top.egon.mario.clocktower.view.service.ClocktowerViewService;
import top.egon.mario.clocktower.view.service.impl.ClocktowerViewServiceImpl;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerViewServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerRoomService roomService = context.roomService();
    private final ClocktowerViewService viewService = new ClocktowerViewServiceImpl(context.roomRepository(),
            context.seatRepository(), context.eventRepository(), context.objectMapper());

    @Test
    void playerViewIncludesOwnRoleAndHidesOtherRoles() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
        Long marioSeatId = room.seats().getFirst().seatId();
        context.eventService().append(new ClocktowerEventAppendRequest(room.roomId(),
                ClocktowerEventType.STORYTELLER_RULING, ClocktowerPhase.FIRST_NIGHT, 0, 1, 1L, null,
                null, ClocktowerVisibility.STORYTELLER, List.of(), Map.of("note", "hidden")));

        ClocktowerPlayerViewResponse view = viewService.playerView(room.roomId(), marioSeatId, principal(2L, "mario"));

        assertThat(view.mySeat().roleCode()).isEqualTo("EMPATH");
        assertThat(view.publicSeats()).allSatisfy(seat -> assertThat(seat.roleCode()).isNull());
        assertThat(view.publicSeats()).extracting(PublicSeatResponse::seatId).contains(marioSeatId);
        assertThat(view.recentEvents()).noneMatch(event -> event.visibility() == ClocktowerVisibility.STORYTELLER);
    }

    @Test
    void playerViewRejectsSeatIdOwnedByAnotherUser() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
        Long otherSeatId = room.seats().get(1).seatId();

        assertThatThrownBy(() -> viewService.playerView(room.roomId(), otherSeatId, principal(2L, "mario")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_SEAT_FORBIDDEN");
    }

    private ClocktowerRoomResponse startedTroubleBrewingRoomWithJoinedUsers() {
        ClocktowerRoomResponse room = roomService.create(new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN", false, true, 0), storytellerPrincipal());
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        ClocktowerRoomResponse joined = roomService.get(room.roomId());
        roomService.start(joined.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(joined.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(joined.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(joined.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(joined.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(joined.seats().get(4).seatId(), "IMP")
        ), false), storytellerPrincipal());
        return roomService.get(joined.roomId());
    }

    private static RbacPrincipal storytellerPrincipal() {
        return principal(1L, "storyteller");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }
}
