package top.egon.mario.clocktower.flow;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ClocktowerExecutionDeathPolicy;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
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
    void firstNightCannotAdvanceBeforeNightTasksAreSyncedByOpeningGrimoire() {
        Long roomId = startedRoom();

        assertThatThrownBy(() -> flowService.advance(roomId, storyteller()))
                .hasMessageContaining("CLOCKTOWER_NIGHT_TASKS_PENDING");
        assertThat(context.storytellerTaskRepository()
                .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING"))
                .isNotEmpty();
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

    @Test
    void normalFlowMovesDayToNominationThenExecution() {
        Long roomId = dayRoom();

        var nominationFlow = flowService.advance(roomId, storyteller());
        assertThat(nominationFlow.phase().phase()).isEqualTo(ClocktowerPhase.NOMINATION);

        var executionFlow = flowService.advance(roomId, storyteller());
        assertThat(executionFlow.phase().phase()).isEqualTo(ClocktowerPhase.EXECUTION);
    }

    @Test
    void executionResolvedMovesToNextNightAndNightCompletionMovesToNextDay() {
        Long roomId = executionResolvedRoom();

        var nightFlow = flowService.advance(roomId, storyteller());
        assertThat(nightFlow.phase().phase()).isEqualTo(ClocktowerPhase.NIGHT);
        assertThat(nightFlow.phase().dayNo()).isEqualTo(1);
        assertThat(nightFlow.phase().nightNo()).isEqualTo(2);
    }

    @Test
    void closeNominationLocksOpenNominationAndAllowsExecutionTransition() {
        Long roomId = dayRoom();
        context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().setPhase(ClocktowerPhase.NOMINATION);
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        context.nominationRepository().save(openNomination(roomId, seats.getFirst().getId(), seats.get(1).getId()));

        var open = context.nominationRepository()
                .findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(roomId, "OPEN")
                .orElseThrow();
        flowService.closeNomination(roomId, open.getId(), new CloseNominationRequest("投票结束"), storyteller());

        assertThat(open.getStatus()).isEqualTo("CLOSED");
        var flow = flowService.advance(roomId, storyteller());
        assertThat(flow.phase().phase()).isEqualTo(ClocktowerPhase.EXECUTION);
    }

    @Test
    void executionCandidateRequiresThresholdAndUniqueTopVote() {
        Long roomId = executionRoomWithClosedNominations(3, 2);

        var flow = flowService.getFlow(roomId, storyteller());

        assertThat(flow.executionCandidate().executable()).isTrue();
        assertThat(flow.executionCandidate().voteCount()).isEqualTo(3);
        assertThat(flow.executionCandidate().threshold()).isEqualTo(3);
    }

    @Test
    void executionCandidateAbsentOnTie() {
        Long roomId = executionRoomWithClosedNominations(3, 3);

        var flow = flowService.getFlow(roomId, storyteller());

        assertThat(flow.executionCandidate().executable()).isFalse();
        assertThat(flow.executionCandidate().reason()).isEqualTo("TIED_TOP_VOTE");
    }

    @Test
    void confirmExecutionMarksNominationExecutedWithoutDeathAndAllowsNight() {
        Long roomId = executionRoomWithClosedNominations(3, 2);
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        Long nomineeSeatId = seats.get(1).getId();

        var confirmed = flowService.confirmExecution(roomId,
                new ExecutionConfirmRequest(true, ClocktowerExecutionDeathPolicy.NO_CHANGE, "处决但未死亡"), storyteller());
        assertThat(confirmed.executionCandidate().resolved()).isTrue();
        assertThat(context.seatRepository().findByIdAndRoomIdAndDeletedFalse(nomineeSeatId, roomId)
                .orElseThrow().getLifeStatus()).isEqualTo("ALIVE");

        var night = flowService.advance(roomId, storyteller());
        assertThat(night.phase().phase()).isEqualTo(ClocktowerPhase.NIGHT);
    }

    @Test
    void confirmExecutionWithDeathPolicyRecordsDeathSeparately() {
        Long roomId = executionRoomWithClosedNominations(3, 2);
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        Long nomineeSeatId = seats.get(1).getId();

        flowService.confirmExecution(roomId,
                new ExecutionConfirmRequest(true, ClocktowerExecutionDeathPolicy.MARK_DEAD, "处决造成死亡"), storyteller());

        var nominee = context.seatRepository().findByIdAndRoomIdAndDeletedFalse(nomineeSeatId, roomId).orElseThrow();
        assertThat(nominee.getLifeStatus()).isEqualTo("DEAD");
        assertThat(context.rulingRepository().findByRoomIdAndDeletedFalseOrderByIdDesc(roomId))
                .extracting(ruling -> ruling.getRulingType().name())
                .containsSubsequence("MARK_DEAD", "EXECUTE_PLAYER");
    }

    @Test
    void confirmNoExecutionRecordsResolutionWithoutSyntheticNomination() {
        Long roomId = executionRoomWithClosedNominations(2, 1);

        flowService.confirmExecution(roomId,
                new ExecutionConfirmRequest(false, ClocktowerExecutionDeathPolicy.NO_CHANGE, "无人达到处决门槛"),
                storyteller());

        assertThat(context.nominationRepository().findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(roomId, 1))
                .allSatisfy(nomination -> {
                    assertThat(nomination.getNominatorSeatId()).isNotZero();
                    assertThat(nomination.getNomineeSeatId()).isNotZero();
                });
        assertThat(flowService.getFlow(roomId, storyteller()).executionCandidate().resolved()).isTrue();
    }

    @Test
    void confirmNoExecutionIsIdempotentAfterResolution() {
        Long roomId = executionRoomWithClosedNominations(2, 1);

        flowService.confirmExecution(roomId,
                new ExecutionConfirmRequest(false, ClocktowerExecutionDeathPolicy.NO_CHANGE, "无人达到处决门槛"),
                storyteller());
        flowService.confirmExecution(roomId,
                new ExecutionConfirmRequest(false, ClocktowerExecutionDeathPolicy.NO_CHANGE, "重复提交"),
                storyteller());

        assertThat(context.rulingRepository().findByRoomIdAndDeletedFalseOrderByIdDesc(roomId))
                .filteredOn(ruling -> "SKIP_EXECUTION".equals(ruling.getRulingType().name()))
                .hasSize(1);
    }

    @Test
    void demonDeathSuggestsGoodVictory() {
        Long roomId = dayRoom();
        context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId).stream()
                .filter(seat -> "DEMON".equals(String.valueOf(seat.getRoleType())))
                .forEach(seat -> seat.setLifeStatus("DEAD"));

        var flow = flowService.getFlow(roomId, storyteller());

        assertThat(flow.victoryCandidate()).isNotNull();
        assertThat(flow.victoryCandidate().winner()).isEqualTo("GOOD");
    }

    @Test
    void twoAliveWithLivingDemonSuggestsEvilVictory() {
        Long roomId = dayRoom();
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        seats.stream()
                .filter(seat -> !"DEMON".equals(String.valueOf(seat.getRoleType())))
                .skip(1)
                .forEach(seat -> seat.setLifeStatus("DEAD"));

        var flow = flowService.getFlow(roomId, storyteller());

        assertThat(flow.victoryCandidate()).isNotNull();
        assertThat(flow.victoryCandidate().winner()).isEqualTo("EVIL");
    }

    private ClocktowerNominationPo openNomination(Long roomId, Long nominatorSeatId, Long nomineeSeatId) {
        ClocktowerNominationPo nomination = new ClocktowerNominationPo();
        nomination.setRoomId(roomId);
        nomination.setDayNo(context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().getCurrentDayNo());
        nomination.setNominatorSeatId(nominatorSeatId);
        nomination.setNomineeSeatId(nomineeSeatId);
        nomination.setStatus("OPEN");
        return nomination;
    }

    private Long executionRoomWithClosedNominations(int firstVotes, int secondVotes) {
        Long roomId = dayRoom();
        var room = context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow();
        room.setPhase(ClocktowerPhase.EXECUTION);
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        ClocktowerNominationPo first = openNomination(roomId, seats.getFirst().getId(), seats.get(1).getId());
        first.setStatus("CLOSED");
        first.setVoteCount(firstVotes);
        context.nominationRepository().save(first);
        ClocktowerNominationPo second = openNomination(roomId, seats.get(2).getId(), seats.get(3).getId());
        second.setStatus("CLOSED");
        second.setVoteCount(secondVotes);
        context.nominationRepository().save(second);
        return roomId;
    }

    private Long dayRoom() {
        Long roomId = startedRoom();
        grimoireService.getGrimoire(roomId, storyteller());
        context.storytellerTaskRepository()
                .findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING")
                .forEach(task -> flowService.skipNightTask(roomId, task.getId(),
                        new top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest("跳过"), storyteller()));
        flowService.advance(roomId, storyteller());
        return roomId;
    }

    private Long executionResolvedRoom() {
        Long roomId = dayRoom();
        flowService.advance(roomId, storyteller());
        flowService.advance(roomId, storyteller());
        flowService.confirmExecution(roomId,
                new top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest(false,
                        top.egon.mario.clocktower.flow.dto.ClocktowerExecutionDeathPolicy.NO_CHANGE,
                        "无人处决"), storyteller());
        return roomId;
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
