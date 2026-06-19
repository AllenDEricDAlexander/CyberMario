package top.egon.mario.clocktower.flow;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;
import top.egon.mario.clocktower.grimoire.service.impl.ClocktowerGrimoireServiceImpl;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClocktowerFlowServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerFlowService flowService = TestClocktowerFlowServices.flowService(context);
    private final ClocktowerGrimoireServiceImpl grimoireService = TestClocktowerFlowServices.grimoireService(context);

    @Test
    void firstNightFlowReportsPendingTasksAndBlocksAdvance() {
        Long roomId = startedRoom();
        grimoireService.getGrimoire(roomId, storyteller());

        var flow = flowService.getFlow(roomId, storyteller());

        assertThat(flow.phase().phase()).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
        assertThat(flow.nextTransition()).isEqualTo(ClocktowerFlowTransition.COMPLETE_FIRST_NIGHT);
        assertThat(flow.advanceAllowed()).isFalse();
        assertThat(flow.blockingReasons()).containsExactly("CLOCKTOWER_NIGHT_TASKS_PENDING");
        assertThat(flow.nightTaskSummary().pending()).isGreaterThan(0);
    }

    @Test
    void firstNightCannotAdvanceUntilTasksDoneOrSkipped() {
        Long roomId = startedRoom();
        grimoireService.getGrimoire(roomId, storyteller());

        assertThatThrownBy(() -> flowService.advance(roomId, storyteller()))
                .hasMessageContaining("CLOCKTOWER_NIGHT_TASKS_PENDING");
    }

    @Test
    void skippingAllNightTasksAllowsFirstNightToEnterDay() {
        Long roomId = startedRoom();
        grimoireService.getGrimoire(roomId, storyteller());
        var pending = context.storytellerTaskRepository()
                .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING");
        for (var task : pending) {
            flowService.skipNightTask(roomId, task.getId(),
                    new top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest("本轮无需唤醒"), storyteller());
        }

        var flow = flowService.advance(roomId, storyteller());

        assertThat(flow.phase().phase()).isEqualTo(ClocktowerPhase.DAY);
        assertThat(flow.phase().dayNo()).isEqualTo(1);
        assertThat(flow.phase().nightNo()).isEqualTo(1);
    }

    @Test
    void skipNightTaskRequiresReason() {
        Long roomId = startedRoom();
        grimoireService.getGrimoire(roomId, storyteller());
        var task = context.storytellerTaskRepository()
                .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING")
                .getFirst();

        assertThatThrownBy(() -> flowService.skipNightTask(roomId, task.getId(),
                new top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest(" "), storyteller()))
                .hasMessageContaining("CLOCKTOWER_NIGHT_TASK_SKIP_REASON_REQUIRED");
    }

    private Long startedRoom() {
        var room = context.roomService().create(new ClocktowerRoomCreateRequest("流程测试",
                ClocktowerScriptCode.TROUBLE_BREWING, 5,
                null, null, List.of("EMPATH", "CHEF", "MONK", "POISONER", "IMP"),
                "HUMAN", false, true, 0), storyteller());
        for (int i = 0; i < room.seats().size(); i++) {
            context.roomService().join(room.roomId(), new ClocktowerRoomJoinRequest(i + 1, "P" + (i + 1), null),
                    principal((long) i + 2, "p" + (i + 1)));
        }
        var joined = context.roomService().get(room.roomId());
        context.roomService().start(room.roomId(), new ClocktowerRoomStartRequest(List.of(
                new RoleAssignmentRequest(joined.seats().get(0).seatId(), "EMPATH"),
                new RoleAssignmentRequest(joined.seats().get(1).seatId(), "CHEF"),
                new RoleAssignmentRequest(joined.seats().get(2).seatId(), "MONK"),
                new RoleAssignmentRequest(joined.seats().get(3).seatId(), "POISONER"),
                new RoleAssignmentRequest(joined.seats().get(4).seatId(), "IMP")
        ), false), storyteller());
        return room.roomId();
    }

    private static RbacPrincipal storyteller() {
        return principal(1L, "storyteller");
    }

    private static RbacPrincipal principal(Long userId, String username) {
        return new RbacPrincipal(userId, username,
                Set.of("CLOCKTOWER_STORYTELLER", "CLOCKTOWER_PLAYER"), Set.of(), "v1");
    }
}
