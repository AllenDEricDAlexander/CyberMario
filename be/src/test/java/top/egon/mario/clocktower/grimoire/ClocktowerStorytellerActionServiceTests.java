package top.egon.mario.clocktower.grimoire;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.grimoire.dto.request.StorytellerActionRequest;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.clocktower.grimoire.dto.response.NightChecklistResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StatusMarkerResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StorytellerActionResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StorytellerTaskResponse;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerStorytellerActionServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerRoomService roomService = context.roomService();
    private final ClocktowerGrimoireService grimoireService = new ClocktowerGrimoireServiceImpl(context.roomRepository(),
            context.seatRepository(), context.grimoireEntryRepository(), context.markerRepository(),
            context.storytellerTaskRepository(), context.nightOrderRepository(), context.roleRepository(),
            context.eventService());

    @Test
    void addMarkerCreatesMarkerAndEvent() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
        Long targetSeat = room.seats().getFirst().seatId();

        StorytellerActionResponse response = grimoireService.storytellerAction(room.roomId(), new StorytellerActionRequest(
                "ADD_MARKER", List.of(targetSeat), null,
                Map.of("markerType", "POISONED", "note", "投毒者选择")), storytellerPrincipal());

        assertThat(response.accepted()).isTrue();
        assertThat(response.grimoire().markers()).extracting(StatusMarkerResponse::markerType).contains("POISONED");
    }

    @Test
    void markDeadUpdatesGrimoireAndAppendsDeathEvent() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
        Long targetSeat = room.seats().getFirst().seatId();

        StorytellerActionResponse response = grimoireService.storytellerAction(room.roomId(), new StorytellerActionRequest(
                "MARK_DEAD", List.of(targetSeat), "夜晚死亡", Map.of("reason", "NIGHT_DEATH")), storytellerPrincipal());

        assertThat(response.grimoire().seats().getFirst().alive()).isFalse();
    }

    @Test
    void resolveTaskCompletesWakeTaskAndChecklistStep() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
        ClocktowerGrimoireResponse grimoire = grimoireService.getGrimoire(room.roomId(), storytellerPrincipal());
        StorytellerTaskResponse task = grimoire.pendingTasks().getFirst();

        StorytellerActionResponse response = grimoireService.storytellerAction(room.roomId(), new StorytellerActionRequest(
                "RESOLVE_TASK", List.of(), "已处理", Map.of("taskId", task.taskId())), storytellerPrincipal());
        NightChecklistResponse checklist = grimoireService.nightChecklist(room.roomId(), storytellerPrincipal());

        assertThat(response.accepted()).isTrue();
        assertThat(response.event().eventType()).isEqualTo(ClocktowerEventType.NIGHT_STEP_UPDATED);
        assertThat(response.grimoire().pendingTasks()).extracting(StorytellerTaskResponse::taskId)
                .doesNotContain(task.taskId());
        assertThat(checklist.steps()).filteredOn(step -> step.roleCode().equals(task.roleCode()))
                .allMatch(step -> step.completed());
    }

    @Test
    void oldAdvancePhaseActionIsRejected() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();

        StorytellerActionResponse response = grimoireService.storytellerAction(room.roomId(),
                new StorytellerActionRequest("ADVANCE_PHASE", List.of(), "推进", Map.of()),
                storytellerPrincipal());

        assertThat(response.accepted()).isFalse();
        assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_ADVANCE_PHASE_REPLACED_BY_FLOW");
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
        return new RbacPrincipal(userId, username, Set.of("CLOCKTOWER_STORYTELLER"), Set.of(), "v1");
    }
}
