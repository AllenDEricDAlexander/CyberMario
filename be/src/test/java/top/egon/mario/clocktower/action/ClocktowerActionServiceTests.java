package top.egon.mario.clocktower.action;

import org.junit.jupiter.api.Test;
import top.egon.mario.clocktower.action.dto.ClocktowerActionRequest;
import top.egon.mario.clocktower.action.dto.ClocktowerActionResponse;
import top.egon.mario.clocktower.action.service.ClocktowerActionService;
import top.egon.mario.clocktower.action.service.impl.ClocktowerActionServiceImpl;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.room.ClocktowerRoomTestFactory;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.RoleAssignmentRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ClocktowerActionServiceTests {

    private final ClocktowerRoomTestFactory.Context context = ClocktowerRoomTestFactory.context();
    private final ClocktowerRoomService roomService = context.roomService();
    private final ClocktowerActionService actionService = new ClocktowerActionServiceImpl(context.roomRepository(),
            context.seatRepository(), context.nominationRepository(), context.voteRepository(),
            context.eventService());

    @Test
    void publicSpeechCreatesPublicEvent() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long seatId = room.seats().getFirst().seatId();

        ClocktowerActionResponse response = actionService.submit(room.roomId(), new ClocktowerActionRequest(
                seatId, "PUBLIC_SPEECH", List.of(), null, "我今天想听 3 号发言。", Map.of(), "client-1"),
                principal(2L, "mario"));

        assertThat(response.accepted()).isTrue();
        assertThat(context.eventRepository().findByRoomIdAndDeletedFalseOrderByEventSeqAsc(room.roomId()))
                .extracting(ClocktowerEventPo::getEventType)
                .contains(ClocktowerEventType.PUBLIC_MESSAGE_SENT);
    }

    @Test
    void submitLoadsRoomWithWriteLock() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long seatId = room.seats().getFirst().seatId();
        clearInvocations(context.roomRepository());

        actionService.submit(room.roomId(), new ClocktowerActionRequest(
                seatId, "PUBLIC_SPEECH", List.of(), null, "白天发言。", Map.of(), "client-lock"),
                principal(2L, "mario"));

        verify(context.roomRepository()).findLockedByIdAndDeletedFalse(room.roomId());
        verify(context.roomRepository(), never()).findByIdAndDeletedFalse(room.roomId());
    }

    @Test
    void nominationRejectsDeadNominator() {
        ClocktowerRoomResponse room = runningDayRoomWithDeadFirstSeat();
        Long deadSeat = room.seats().getFirst().seatId();
        Long target = room.seats().get(1).seatId();

        ClocktowerActionResponse response = actionService.submit(room.roomId(), new ClocktowerActionRequest(
                deadSeat, "NOMINATE", List.of(target), null, "我提名 2 号。", Map.of(), "client-2"),
                principal(2L, "mario"));

        assertThat(response.accepted()).isFalse();
        assertThat(response.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINATOR_NOT_ALIVE");
    }

    @Test
    void nominationRejectsDeadNominatorAndDeadNominee() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long roomId = room.roomId();
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        seats.getFirst().setLifeStatus("DEAD");

        ClocktowerActionResponse deadNominator = actionService.submit(roomId,
                new ClocktowerActionRequest(seats.getFirst().getId(), "NOMINATE", List.of(seats.get(1).getId()),
                        null, "dead nominates", Map.of(), "dead-nom"),
                principal(seats.getFirst().getUserId(), "p1"));

        assertThat(deadNominator.accepted()).isFalse();
        assertThat(deadNominator.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINATOR_NOT_ALIVE");

        seats.getFirst().setLifeStatus("ALIVE");
        seats.get(1).setLifeStatus("DEAD");

        ClocktowerActionResponse deadNominee = actionService.submit(roomId,
                new ClocktowerActionRequest(seats.getFirst().getId(), "NOMINATE", List.of(seats.get(1).getId()),
                        null, "nominee dead", Map.of(), "dead-target"),
                principal(seats.getFirst().getUserId(), "p1"));

        assertThat(deadNominee.accepted()).isFalse();
        assertThat(deadNominee.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINEE_NOT_ALIVE");
    }

    @Test
    void nominationRejectsDailyRepeatAndOpenNomination() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long roomId = room.roomId();
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);

        ClocktowerActionResponse first = nominate(roomId, seats.getFirst(), seats.get(1));
        assertThat(first.accepted()).isTrue();

        ClocktowerActionResponse whileOpen = nominate(roomId, seats.get(2), seats.get(3));
        assertThat(whileOpen.accepted()).isFalse();
        assertThat(whileOpen.rejectedCode()).isEqualTo("CLOCKTOWER_OPEN_NOMINATION_EXISTS");

        closeOpenNomination(roomId);

        ClocktowerActionResponse sameNominator = nominate(roomId, seats.getFirst(), seats.get(2));
        assertThat(sameNominator.accepted()).isFalse();
        assertThat(sameNominator.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINATOR_ALREADY_NOMINATED_TODAY");

        ClocktowerActionResponse sameNominee = nominate(roomId, seats.get(2), seats.get(1));
        assertThat(sameNominee.accepted()).isFalse();
        assertThat(sameNominee.rejectedCode()).isEqualTo("CLOCKTOWER_NOMINEE_ALREADY_NOMINATED_TODAY");
    }

    @Test
    void publiclyDeadPlayerSpendsOnlyOneDeadVoteAcrossGame() {
        Long roomId = runningNominationRoom();
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        ClocktowerSeatPo voter = seats.getFirst();
        voter.setLifeStatus("ALIVE");
        voter.setPublicLifeStatus("DEAD");
        voter.setHasDeadVote(true);

        ClocktowerActionResponse firstVote = vote(roomId, voter, true);
        assertThat(firstVote.accepted()).isTrue();
        assertThat(voter.isHasDeadVote()).isFalse();

        closeOpenNomination(roomId);
        context.nominationRepository().save(newOpenNomination(roomId, seats.get(2).getId(), seats.get(3).getId()));

        ClocktowerActionResponse secondVote = vote(roomId, voter, true);
        assertThat(secondVote.accepted()).isFalse();
        assertThat(secondVote.rejectedCode()).isEqualTo("CLOCKTOWER_DEAD_VOTE_ALREADY_SPENT");
    }

    @Test
    void alivePubliclyAlivePlayerCanVoteOncePerNomination() {
        Long roomId = runningNominationRoom();
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        ClocktowerSeatPo voter = seats.getFirst();

        ClocktowerActionResponse firstVote = vote(roomId, voter, true);
        ClocktowerActionResponse duplicate = vote(roomId, voter, true);

        assertThat(firstVote.accepted()).isTrue();
        assertThat(duplicate.accepted()).isFalse();
        assertThat(duplicate.rejectedCode()).isEqualTo("CLOCKTOWER_VOTE_ALREADY_CAST");
    }

    @Test
    void rejectsPlayerActionForAnotherUsersSeat() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long otherSeatId = room.seats().get(1).seatId();

        assertThatThrownBy(() -> actionService.submit(room.roomId(), new ClocktowerActionRequest(
                otherSeatId, "PUBLIC_SPEECH", List.of(), null, "代替 2 号发言。", Map.of(), "client-forbidden"),
                principal(2L, "player-1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_ACTION_SEAT_FORBIDDEN");
    }

    @Test
    void rejectsPlayerActionForUnboundSeat() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long seatId = room.seats().getFirst().seatId();
        context.seatRepository().findByIdAndRoomIdAndDeletedFalse(seatId, room.roomId())
                .ifPresent(seat -> seat.setUserId(null));

        assertThatThrownBy(() -> actionService.submit(room.roomId(), new ClocktowerActionRequest(
                seatId, "PUBLIC_SPEECH", List.of(), null, "空座发言。", Map.of(), "client-empty"),
                principal(2L, "player-1")))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_ACTION_SEAT_FORBIDDEN");
    }

    @Test
    void rejectsPlayerActionWithoutPrincipal() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long seatId = room.seats().getFirst().seatId();

        assertThatThrownBy(() -> actionService.submit(room.roomId(), new ClocktowerActionRequest(
                seatId, "PUBLIC_SPEECH", List.of(), null, "匿名发言。", Map.of(), "client-anonymous"), null))
                .isInstanceOf(ClocktowerException.class)
                .hasMessage("CLOCKTOWER_AUTH_REQUIRED");
    }

    private ClocktowerRoomResponse runningDayRoom() {
        ClocktowerRoomResponse room = startedTroubleBrewingRoomWithJoinedUsers();
        context.roomRepository().findByIdAndDeletedFalse(room.roomId()).ifPresent(saved -> {
            saved.setPhase(ClocktowerPhase.DAY);
            saved.setCurrentDayNo(1);
        });
        return roomService.get(room.roomId());
    }

    private ClocktowerRoomResponse runningDayRoomWithDeadFirstSeat() {
        ClocktowerRoomResponse room = runningDayRoom();
        context.seatRepository().findByIdAndRoomIdAndDeletedFalse(room.seats().getFirst().seatId(), room.roomId())
                .ifPresent(seat -> seat.setLifeStatus("DEAD"));
        return roomService.get(room.roomId());
    }

    private Long runningNominationRoom() {
        ClocktowerRoomResponse room = runningDayRoom();
        Long roomId = room.roomId();
        context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().setPhase(ClocktowerPhase.NOMINATION);
        var seats = context.seatRepository().findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        context.nominationRepository().save(newOpenNomination(roomId, seats.get(1).getId(), seats.get(2).getId()));
        return roomId;
    }

    private ClocktowerNominationPo newOpenNomination(Long roomId, Long nominatorSeatId, Long nomineeSeatId) {
        ClocktowerNominationPo nomination = new ClocktowerNominationPo();
        nomination.setRoomId(roomId);
        nomination.setDayNo(context.roomRepository().findByIdAndDeletedFalse(roomId).orElseThrow().getCurrentDayNo());
        nomination.setNominatorSeatId(nominatorSeatId);
        nomination.setNomineeSeatId(nomineeSeatId);
        nomination.setStatus("OPEN");
        return nomination;
    }

    private ClocktowerActionResponse nominate(Long roomId, ClocktowerSeatPo nominator, ClocktowerSeatPo nominee) {
        return actionService.submit(roomId,
                new ClocktowerActionRequest(nominator.getId(), "NOMINATE", List.of(nominee.getId()),
                        null, "nominate", Map.of(), "nom-" + nominator.getId() + "-" + nominee.getId()),
                principal(nominator.getUserId(), "p" + nominator.getSeatNo()));
    }

    private void closeOpenNomination(Long roomId) {
        context.nominationRepository().findTopByRoomIdAndStatusAndDeletedFalseOrderByIdDesc(roomId, "OPEN")
                .orElseThrow()
                .setStatus("CLOSED");
    }

    private ClocktowerActionResponse vote(Long roomId, ClocktowerSeatPo voter, boolean voteValue) {
        return actionService.submit(roomId,
                new ClocktowerActionRequest(voter.getId(), "VOTE", List.of(), null, "vote",
                        Map.of("vote", voteValue), "vote-" + voter.getId() + "-" + System.nanoTime()),
                principal(voter.getUserId(), "p" + voter.getSeatNo()));
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
