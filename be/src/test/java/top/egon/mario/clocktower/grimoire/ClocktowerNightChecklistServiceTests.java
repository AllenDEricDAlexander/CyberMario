package top.egon.mario.clocktower.grimoire;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.grimoire.dto.response.NightChecklistResponse;
import top.egon.mario.clocktower.grimoire.dto.response.NightStepResponse;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class ClocktowerNightChecklistServiceTests {

    private final ClocktowerGrimoireTestFactory.Services services = ClocktowerGrimoireTestFactory.services();
    private final ClocktowerRoomService roomService = services.roomService();
    private final ClocktowerGrimoireService grimoireService = services.grimoireService();

    @Test
    void firstNightChecklistContainsAliveRolesWithFirstNightOrder() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithRoles(
                "EMPATH", "CHEF", "MONK", "POISONER", "IMP");

        NightChecklistResponse checklist = grimoireService.nightChecklist(room.roomId(), storytellerPrincipal());

        assertThat(checklist.nightType()).isEqualTo(ClocktowerNightType.FIRST_NIGHT);
        assertThat(checklist.steps()).extracting(NightStepResponse::roleCode)
                .contains("POISONER", "EMPATH");
        assertThat(checklist.steps()).filteredOn(step -> step.roleCode().equals("POISONER"))
                .extracting(NightStepResponse::roleType)
                .containsExactly(ClocktowerRoleType.MINION);
        assertThat(checklist.completed()).isFalse();
        verify(services.nightOrderRepository())
                .findByScriptCodeAndNightTypeAndRoleCodeInAndDeletedFalseOrderBySortOrderAsc(
                        eq(ClocktowerScriptCode.TROUBLE_BREWING), eq(ClocktowerNightType.FIRST_NIGHT), any());
    }

    @Test
    void dayPhaseDoesNotExposeCurrentNightPendingSteps() {
        ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
        ClocktowerGrimoireServiceImpl service = new ClocktowerGrimoireServiceImpl(context.roomRepository(),
                context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
                context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
                context.eventService());
        Long roomId = startedRoom(context);
        service.getGrimoire(roomId, storytellerPrincipal());
        context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().setPhase(ClocktowerPhase.DAY);

        NightChecklistResponse checklist = service.nightChecklist(roomId, storytellerPrincipal());

        assertThat(checklist.steps()).isEmpty();
        assertThat(checklist.completed()).isFalse();
    }

    @Test
    void skippedNightTaskIsShownAsCompletedWithReason() {
        ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
        ClocktowerGrimoireServiceImpl service = new ClocktowerGrimoireServiceImpl(context.roomRepository(),
                context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
                context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
                context.eventService());
        Long roomId = startedRoom(context);
        service.getGrimoire(roomId, storytellerPrincipal());
        var task = context.storytellerTaskRepository()
                .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING")
                .getFirst();
        task.setStatus("SKIPPED");
        task.setNote("本轮无需唤醒");

        NightChecklistResponse checklist = service.nightChecklist(roomId, storytellerPrincipal());

        assertThat(checklist.steps()).filteredOn(step -> step.roleCode().equals(task.getRoleCode()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.completed()).isTrue();
                    assertThat(step.skipReason()).isEqualTo("本轮无需唤醒");
                });
    }

    private ClocktowerRoomResponse startedTroubleBrewingRoomWithRoles(String... roleCodes) {
        ClocktowerRoomResponse room = roomService.create(new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, roleCodes.length, null, null,
                List.of(roleCodes), "HUMAN", false, true, 0), storytellerPrincipal());
        for (int i = 0; i < room.seats().size(); i++) {
            roomService.join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1), null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        ClocktowerRoomResponse joined = roomService.get(room.roomId());
        roomService.start(joined.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(joined.seats().get(0).seatId(), roleCodes[0]),
                new RoleAssignmentRequest(joined.seats().get(1).seatId(), roleCodes[1]),
                new RoleAssignmentRequest(joined.seats().get(2).seatId(), roleCodes[2]),
                new RoleAssignmentRequest(joined.seats().get(3).seatId(), roleCodes[3]),
                new RoleAssignmentRequest(joined.seats().get(4).seatId(), roleCodes[4])
        ), false), storytellerPrincipal());
        return roomService.get(joined.roomId());
    }

    private static Long startedRoom(ClocktowerRoomTestFactory.Context context) {
        ClocktowerRoomResponse room = context.roomService().create(new ClocktowerRoomCreateRequest(
                "周五暗流", ClocktowerScriptCode.TROUBLE_BREWING, 5, null, null,
                List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"), "HUMAN", false, true, 0),
                storytellerPrincipal());
        for (int i = 0; i < room.seats().size(); i++) {
            context.roomService().join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "Player " + (i + 1),
                            null),
                    principal((long) i + 2, "player-" + (i + 1)));
        }
        ClocktowerRoomResponse joined = context.roomService().get(room.roomId());
        context.roomService().start(joined.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(joined.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(joined.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(joined.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(joined.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(joined.seats().get(4).seatId(), "IMP")
        ), false), storytellerPrincipal());
        return room.roomId();
    }

    private static RbacPrincipal storytellerPrincipal() {
        return principal(1L, "storyteller");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
