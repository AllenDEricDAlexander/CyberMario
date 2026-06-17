package top.egon.mario.clocktower.grimoire;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GrimoireSeatResponse;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerStartGameResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerGrimoireServiceTests {

    private final ClocktowerGrimoireTestFactory.Services services = ClocktowerGrimoireTestFactory.services();
    private final ClocktowerRoomService roomService = services.roomService();
    private final ClocktowerGrimoireService grimoireService = services.grimoireService();

    @Test
    void startRoomAssignsRolesAndCreatesPrivateRoleEvents() {
        ClocktowerRoomResponse room = createJoinedFivePlayerRoom();

        ClocktowerStartGameResponse started = roomService.start(room.roomId(), new ClocktowerRoomStartRequest(
                List.of(
                        new RoleAssignmentRequest(room.seats().get(0).seatId(), "EMPATH"),
                        new RoleAssignmentRequest(room.seats().get(1).seatId(), "CHEF"),
                        new RoleAssignmentRequest(room.seats().get(2).seatId(), "MONK"),
                        new RoleAssignmentRequest(room.seats().get(3).seatId(), "POISONER"),
                        new RoleAssignmentRequest(room.seats().get(4).seatId(), "IMP")
                ),
                false
        ), storytellerPrincipal());

        ClocktowerGrimoireResponse grimoire = grimoireService.getGrimoire(room.roomId(), storytellerPrincipal());

        assertThat(started.phase()).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
        assertThat(grimoire.seats()).extracting(GrimoireSeatResponse::roleCode)
                .containsExactly("EMPATH", "CHEF", "MONK", "POISONER", "IMP");
    }

    @Test
    void nonStorytellerCannotReadGrimoire() {
        ClocktowerRoomResponse room = createJoinedFivePlayerRoom();

        assertThatThrownBy(() -> grimoireService.getGrimoire(room.roomId(), principal(2L, "player-1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessageContaining("CLOCKTOWER_STORYTELLER_FORBIDDEN");
    }

    private ClocktowerRoomResponse createJoinedFivePlayerRoom() {
        ClocktowerRoomResponse room = roomService.create(new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN", false, true, 0), storytellerPrincipal());
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        return roomService.get(room.roomId());
    }

    private static RbacPrincipal storytellerPrincipal() {
        return principal(1L, "storyteller");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
