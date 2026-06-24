package top.egon.mario.room;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.room.context.RoomContext;
import top.egon.mario.room.context.RoomPrincipal;
import top.egon.mario.room.facade.RoomFacade;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.policy.RoomMutation;
import top.egon.mario.room.policy.RoomMutationPolicy;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.service.RoomException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class RoomFacadeTests {

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private RoomMemberRepository memberRepository;

    @Autowired
    private RoomInvitationRepository invitationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestRoomMutationPolicy mutationPolicy;

    @BeforeEach
    void resetPolicy() {
        mutationPolicy.reset();
    }

    @Test
    void enterRoomCreatesSpectatorMemberWhenAllowed() {
        RoomFacade.RoomView room = roomFacade.createRoom("ROOM_TEST", 1001L, 1L, "PUBLIC");

        RoomFacade.RoomMemberView member = roomFacade.enterRoom(room.roomId(), principal(2L, "Luigi"));

        assertThat(member.roomId()).isEqualTo(room.roomId());
        assertThat(member.userId()).isEqualTo(2L);
        assertThat(member.memberType()).isEqualTo("SPECTATOR");
        RoomMemberPo saved = memberRepository.findActiveByRoomIdAndUserId(room.roomId(), 2L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getActiveStatus()).isTrue();
        assertThat(saved.getDisplayName()).isEqualTo("Luigi");
    }

    @Test
    void unregisteredContextMutationIsDeniedByDefaultPolicy() {
        assertThatThrownBy(() -> roomFacade.createRoom("UNKNOWN_ROOM_TEST", 2001L, 1L, "PUBLIC"))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("ROOM_MUTATION_FORBIDDEN");
    }

    @Test
    void inviteUsesRoomMutationPolicyContext() {
        RoomFacade.RoomView room = roomFacade.createRoom("ROOM_TEST", 2002L, 1L, "PUBLIC");
        mutationPolicy.reset();

        assertThatThrownBy(() -> roomFacade.invite(
                room.roomId(), 21L, "SEAT", 9, Instant.now().plus(Duration.ofHours(1))))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("ROOM_MUTATION_FORBIDDEN");

        assertThat(mutationPolicy.calls()).anySatisfy(call -> {
            assertThat(call.mutation()).isEqualTo(RoomMutation.INVITE);
            assertThat(call.context().contextType()).isEqualTo("ROOM_TEST");
            assertThat(call.context().contextId()).isEqualTo(2002L);
            assertThat(call.context().roomId()).isEqualTo(room.roomId());
            assertThat(call.context().ownerUserId()).isEqualTo(1L);
            assertThat(call.context().viewerUserId()).isEqualTo(1L);
            assertThat(call.context().memberRole()).isEqualTo("OWNER");
            assertThat(call.context().roomStatus()).isEqualTo("ACTIVE");
            assertThat(call.context().roomVisibility()).isEqualTo("PUBLIC");
            assertThat(call.context().requestedSeatNo()).isEqualTo(9);
        });
        assertThat(invitationRepository.findActiveTargetSeatReservations(room.roomId(), Instant.now())).isEmpty();
    }

    @Test
    void enterRoomRejectsActiveBan() {
        RoomFacade.RoomView room = roomFacade.createRoom("ROOM_TEST", 1002L, 1L, "PUBLIC");
        roomFacade.ban(room.roomId(), 3L, Duration.ofHours(1), "spam");

        assertThatThrownBy(() -> roomFacade.enterRoom(room.roomId(), principal(3L, "Banned")))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("ROOM_USER_BANNED");
        assertThat(memberRepository.findActiveByRoomIdAndUserId(room.roomId(), 3L)).isEmpty();
    }

    @Test
    void acceptInvitationRejectsBannedInviteeAndReleasesSeatReservation() {
        RoomFacade.RoomView room = roomFacade.createRoom("ROOM_TEST", 1007L, 1L, "PUBLIC");
        RoomFacade.RoomInvitationView invitation = roomFacade.invite(
                room.roomId(), 31L, "SEAT", 7, Instant.now().plus(Duration.ofHours(1)));

        roomFacade.ban(room.roomId(), 31L, Duration.ofHours(1), "spam");

        assertThatThrownBy(() -> roomFacade.acceptInvitation(
                room.roomId(), invitation.invitationId(), principal(31L, "Banned Invitee")))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("ROOM_USER_BANNED");
        assertThat(memberRepository.findActiveByRoomIdAndUserId(room.roomId(), 31L)).isEmpty();
        assertThat(roomFacade.activeReservations(room.roomId()))
                .extracting(RoomFacade.RoomReservationView::targetSeatNo)
                .doesNotContain(7);
        RoomInvitationPo terminalInvitation = invitationRepository.findById(invitation.invitationId()).orElseThrow();
        assertThat(terminalInvitation.getStatus()).isEqualTo("CANCELLED");
        assertThat(terminalInvitation.getActiveStatus()).isNull();
    }

    @Test
    void seatInvitationReservesTargetUntilAcceptedDeclinedCancelledOrExpired() {
        RoomFacade.RoomView room = roomFacade.createRoom("ROOM_TEST", 1003L, 1L, "PUBLIC");

        RoomFacade.RoomInvitationView accepted = roomFacade.invite(
                room.roomId(), 11L, "SEAT", 3, Instant.now().plus(Duration.ofHours(1)));
        assertThat(roomFacade.activeReservations(room.roomId()))
                .extracting(RoomFacade.RoomReservationView::targetSeatNo)
                .contains(3);
        roomFacade.acceptInvitation(room.roomId(), accepted.invitationId(), principal(11L, "Accepted"));
        assertThat(roomFacade.activeReservations(room.roomId()))
                .extracting(RoomFacade.RoomReservationView::targetSeatNo)
                .doesNotContain(3);

        RoomFacade.RoomInvitationView declined = roomFacade.invite(
                room.roomId(), 12L, "SEAT", 4, Instant.now().plus(Duration.ofHours(1)));
        roomFacade.declineInvitation(room.roomId(), declined.invitationId(), principal(12L, "Declined"));
        assertThat(roomFacade.activeReservations(room.roomId()))
                .extracting(RoomFacade.RoomReservationView::targetSeatNo)
                .doesNotContain(4);

        RoomInvitationPo cancelled = activeInvitation(room.roomId(), 13L, 5);
        cancelled.setStatus("CANCELLED");
        cancelled.setActiveStatus(null);
        invitationRepository.saveAndFlush(cancelled);
        assertThat(roomFacade.activeReservations(room.roomId()))
                .extracting(RoomFacade.RoomReservationView::targetSeatNo)
                .doesNotContain(5);

        RoomInvitationPo expired = activeInvitation(room.roomId(), 14L, 6);
        expired.setExpiresAt(Instant.now().minus(Duration.ofSeconds(1)));
        invitationRepository.saveAndFlush(expired);
        assertThat(roomFacade.activeReservations(room.roomId()))
                .extracting(RoomFacade.RoomReservationView::targetSeatNo)
                .doesNotContain(6);
        entityManager.flush();
        entityManager.clear();
        RoomInvitationPo reloadedExpired = invitationRepository.findById(expired.getId()).orElseThrow();
        assertThat(reloadedExpired.getStatus()).isEqualTo("EXPIRED");
        assertThat(reloadedExpired.getActiveStatus()).isNull();
    }

    @Test
    void kickRemovesMemberButDoesNotDeleteAuditTrail() {
        RoomFacade.RoomView room = roomFacade.createRoom("ROOM_TEST", 1004L, 1L, "PUBLIC");
        roomFacade.enterRoom(room.roomId(), principal(2L, "Luigi"));

        roomFacade.kick(room.roomId(), 2L, "inactive");

        assertThat(memberRepository.findActiveByRoomIdAndUserId(room.roomId(), 2L)).isEmpty();
        List<RoomMemberPo> history = memberRepository.findByRoomIdAndUserIdAndDeletedFalseOrderByIdAsc(
                room.roomId(), 2L);
        assertThat(history).hasSize(1);
        RoomMemberPo kicked = history.getFirst();
        assertThat(kicked.getStatus()).isEqualTo("KICKED");
        assertThat(kicked.getActiveStatus()).isNull();
        assertThat(kicked.getLeftAt()).isNotNull();
        assertThat(kicked.isDeleted()).isFalse();
    }

    @Test
    void heartbeatUpdatesRoomLocalLastActiveAt() {
        RoomFacade.RoomView firstRoom = roomFacade.createRoom("ROOM_TEST", 1005L, 1L, "PUBLIC");
        RoomFacade.RoomView secondRoom = roomFacade.createRoom("ROOM_TEST", 1006L, 1L, "PUBLIC");
        RoomPrincipal principal = principal(2L, "Luigi");
        roomFacade.enterRoom(firstRoom.roomId(), principal);
        roomFacade.enterRoom(secondRoom.roomId(), principal);
        entityManager.flush();
        entityManager.clear();

        roomFacade.heartbeat(firstRoom.roomId(), principal);
        entityManager.flush();
        entityManager.clear();

        RoomMemberPo firstMember = memberRepository.findActiveByRoomIdAndUserId(firstRoom.roomId(), 2L).orElseThrow();
        RoomMemberPo secondMember = memberRepository.findActiveByRoomIdAndUserId(secondRoom.roomId(), 2L).orElseThrow();
        assertThat(firstMember.getLastActiveAt()).isNotNull();
        assertThat(secondMember.getLastActiveAt()).isNull();
    }

    private RoomInvitationPo activeInvitation(Long roomId, Long inviteeUserId, int targetSeatNo) {
        RoomInvitationPo invitation = new RoomInvitationPo();
        invitation.setRoomId(roomId);
        invitation.setInviterUserId(1L);
        invitation.setInviteeUserId(inviteeUserId);
        invitation.setInvitationCode("INV-" + roomId + "-" + targetSeatNo + "-" + inviteeUserId);
        invitation.setStatus("PENDING");
        invitation.setActiveStatus(true);
        invitation.setTargetSeatNo(targetSeatNo);
        return invitationRepository.saveAndFlush(invitation);
    }

    private static RoomPrincipal principal(Long userId, String displayName) {
        return new RoomPrincipal(userId, displayName);
    }

    @TestConfiguration
    static class RoomPolicyTestConfiguration {

        @Bean
        TestRoomMutationPolicy testRoomMutationPolicy() {
            return new TestRoomMutationPolicy();
        }
    }

    static class TestRoomMutationPolicy implements RoomMutationPolicy {

        private final List<PolicyCall> calls = new ArrayList<>();

        @Override
        public String contextType() {
            return "ROOM_TEST";
        }

        @Override
        public boolean canMutate(RoomContext context, RoomMutation mutation) {
            calls.add(new PolicyCall(context, mutation));
            return mutation != RoomMutation.INVITE || !Integer.valueOf(9).equals(context.requestedSeatNo());
        }

        void reset() {
            calls.clear();
        }

        List<PolicyCall> calls() {
            return List.copyOf(calls);
        }
    }

    record PolicyCall(RoomContext context, RoomMutation mutation) {
    }
}
