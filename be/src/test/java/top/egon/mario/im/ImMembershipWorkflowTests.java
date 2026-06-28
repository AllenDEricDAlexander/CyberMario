package top.egon.mario.im;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.CancelJoinCommand;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.LeaveCommand;
import top.egon.mario.im.facade.dto.command.RejectJoinCommand;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.po.ImJoinRequestPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImJoinRequestRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.service.ImException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ImMembershipWorkflowTests {

    private static final String CONTEXT_TYPE = "IM_MEMBERSHIP_WORKFLOW_TEST";

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private RoomFacade roomFacade;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private ImChannelRepository channelRepository;

    @MockitoSpyBean(reset = MockReset.AFTER)
    private ImGroupRepository groupRepository;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImJoinRequestRepository joinRequestRepository;

    @Test
    void surfaceRepositoriesExposePessimisticWriteLookupsForMembershipMutations() throws Exception {
        assertPessimisticWriteLookup(ImChannelRepository.class);
        assertPessimisticWriteLookup(ImGroupRepository.class);
    }

    @Test
    void openChannelJoinActivatesMembershipAndConversationMemberIdempotently() {
        ChannelView channel = channel(3001L, "open-channel", "OPEN");
        clearInvocations(channelRepository);

        JoinResultView first = roomFacade.applyJoin(join(3002L, "CHANNEL", channel.id(), "joining"));
        JoinResultView duplicate = roomFacade.applyJoin(join(3002L, "CHANNEL", channel.id(), "again"));

        assertLockedLookupUsed(channelRepository, channel.id());
        assertThat(first.status()).isEqualTo("ACTIVE");
        assertThat(first.surfaceType()).isEqualTo("CHANNEL");
        assertThat(first.surfaceId()).isEqualTo(channel.id());
        assertThat(first.membershipId()).isNotNull();
        assertThat(first.joinRequestId()).isNull();
        assertThat(duplicate.status()).isEqualTo("ACTIVE");
        assertThat(duplicate.membershipId()).isEqualTo(first.membershipId());
        assertThat(channelRepository.findById(channel.id()).orElseThrow().getMemberCount()).isEqualTo(2);
        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                ImSurfaceType.CHANNEL, channel.id(), 3002L, ImMembershipStatus.ACTIVE)).get()
                .satisfies(membership -> assertThat(membership.getMemberRole()).isEqualTo(ImMembershipRole.MEMBER));
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                channel.mainConversationId(), 3002L)).isPresent();
        assertThat(joinRequestRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                ImSurfaceType.CHANNEL, channel.id(), 3002L, ImJoinRequestStatus.PENDING)).isEmpty();
    }

    @Test
    void bannedMemberCannotJoinOpenSurface() {
        ChannelView channel = channel(3011L, "banned-channel", "OPEN");
        ban(ImSurfaceType.CHANNEL, channel.id(), 3012L);

        assertThatThrownBy(() -> roomFacade.applyJoin(join(3012L, "CHANNEL", channel.id(), "blocked")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_MEMBER_BANNED");
        assertThat(channelRepository.findById(channel.id()).orElseThrow().getMemberCount()).isEqualTo(1);
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                channel.mainConversationId(), 3012L)).isEmpty();
    }

    @Test
    void approvalChannelJoinStaysPendingUntilOwnerApproves() {
        ChannelView channel = channel(3101L, "approval-channel", "APPROVAL");
        clearInvocations(channelRepository);

        JoinResultView pending = roomFacade.applyJoin(join(3102L, "CHANNEL", channel.id(), "please"));
        JoinResultView duplicate = roomFacade.applyJoin(join(3102L, "CHANNEL", channel.id(), "please again"));

        assertLockedLookupUsed(channelRepository, channel.id());
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.membershipId()).isNull();
        assertThat(pending.joinRequestId()).isNotNull();
        assertThat(duplicate.status()).isEqualTo("PENDING");
        assertThat(duplicate.joinRequestId()).isEqualTo(pending.joinRequestId());
        assertThat(channelRepository.findById(channel.id()).orElseThrow().getMemberCount()).isEqualTo(1);
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                channel.mainConversationId(), 3102L)).isEmpty();

        clearInvocations(channelRepository);
        JoinResultView approved = roomFacade.approveJoin(new ApproveCommand(principal(3101L), pending.joinRequestId()));

        assertLockedLookupUsed(channelRepository, channel.id());
        assertThat(approved.status()).isEqualTo("ACTIVE");
        assertThat(approved.membershipId()).isNotNull();
        assertThat(approved.joinRequestId()).isEqualTo(pending.joinRequestId());
        assertThat(channelRepository.findById(channel.id()).orElseThrow().getMemberCount()).isEqualTo(2);
        assertThat(joinRequestRepository.findByIdAndDeletedFalse(pending.joinRequestId())).get()
                .satisfies(request -> {
                    assertThat(request.getStatus()).isEqualTo(ImJoinRequestStatus.APPROVED);
                    assertThat(request.getDecidedBy()).isEqualTo(3101L);
                    assertThat(request.getDecidedAt()).isNotNull();
                });
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                channel.mainConversationId(), 3102L)).isPresent();
    }

    @Test
    void rejectKeepsRequesterInactiveAndAllowsApplyingAgain() {
        GroupView group = standaloneGroup(3201L, "approval-group", "APPROVAL");
        clearInvocations(groupRepository);
        JoinResultView pending = roomFacade.applyJoin(join(3202L, "GROUP", group.id(), "please"));

        assertLockedLookupUsed(groupRepository, group.id());
        clearInvocations(groupRepository);
        JoinResultView rejected = roomFacade.rejectJoin(new RejectJoinCommand(
                principal(3201L), pending.joinRequestId(), "not now"));

        assertLockedLookupUsed(groupRepository, group.id());
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.membershipId()).isNull();
        assertThat(rejected.joinRequestId()).isEqualTo(pending.joinRequestId());
        assertThat(joinRequestRepository.findByIdAndDeletedFalse(pending.joinRequestId())).get()
                .satisfies(request -> {
                    assertThat(request.getStatus()).isEqualTo(ImJoinRequestStatus.REJECTED);
                    assertThat(request.getDecidedBy()).isEqualTo(3201L);
                    assertThat(request.getDecisionReason()).isEqualTo("not now");
                    assertThat(request.getDecidedAt()).isNotNull();
                });
        assertThat(groupRepository.findById(group.id()).orElseThrow().getMemberCount()).isEqualTo(1);
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                group.conversationId(), 3202L)).isEmpty();

        JoinResultView reapplied = roomFacade.applyJoin(join(3202L, "GROUP", group.id(), "second try"));

        assertThat(reapplied.status()).isEqualTo("PENDING");
        assertThat(reapplied.joinRequestId()).isNotNull();
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                group.conversationId(), 3202L)).isEmpty();
    }

    @Test
    void cancelKeepsRequesterInactiveAndCannotBeApprovedLater() {
        ChannelView channel = channel(3301L, "cancel-channel", "APPROVAL");
        JoinResultView pending = roomFacade.applyJoin(join(3302L, "CHANNEL", channel.id(), "please"));
        clearInvocations(channelRepository);

        JoinResultView cancelled = roomFacade.cancelJoin(new CancelJoinCommand(
                principal(3302L), pending.joinRequestId()));

        assertLockedLookupUsed(channelRepository, channel.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.membershipId()).isNull();
        assertThat(cancelled.joinRequestId()).isEqualTo(pending.joinRequestId());
        assertThat(joinRequestRepository.findByIdAndDeletedFalse(pending.joinRequestId())).get()
                .satisfies(request -> {
                    assertThat(request.getStatus()).isEqualTo(ImJoinRequestStatus.CANCELLED);
                    assertThat(request.getDecidedBy()).isEqualTo(3302L);
                    assertThat(request.getDecidedAt()).isNotNull();
                });
        assertThat(conversationMemberRepository.findActiveByConversationIdAndUserId(
                channel.mainConversationId(), 3302L)).isEmpty();
        assertThatThrownBy(() -> roomFacade.approveJoin(new ApproveCommand(principal(3301L), pending.joinRequestId())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_JOIN_REQUEST_NOT_PENDING");
    }

    @Test
    void nonOwnerOrAdminCannotApprovePendingJoinRequest() {
        ChannelView channel = channel(3311L, "approval-auth-channel", "APPROVAL");
        JoinResultView pending = roomFacade.applyJoin(join(3312L, "CHANNEL", channel.id(), "please"));

        assertThatThrownBy(() -> roomFacade.approveJoin(new ApproveCommand(principal(3313L), pending.joinRequestId())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_JOIN_APPROVER_REQUIRED");
        assertThat(joinRequestRepository.findByIdAndDeletedFalse(pending.joinRequestId())).get()
                .extracting(ImJoinRequestPo::getStatus)
                .isEqualTo(ImJoinRequestStatus.PENDING);
    }

    @Test
    void ownerCannotLeaveWithoutAnotherActiveOwner() {
        ChannelView channel = channel(3401L, "owner-alone-channel", "OPEN");
        roomFacade.applyJoin(join(3402L, "CHANNEL", channel.id(), "member"));
        clearInvocations(channelRepository);

        assertThatThrownBy(() -> roomFacade.leave(new LeaveCommand(principal(3401L), "CHANNEL", channel.id())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_OWNER_REPLACEMENT_REQUIRED");
        assertLockedLookupUsed(channelRepository, channel.id());
        assertThat(channelRepository.findById(channel.id()).orElseThrow().getMemberCount()).isEqualTo(2);
    }

    @Test
    void ownerCannotLeaveWhenOnlyActiveAdminRemains() {
        ChannelView channel = channel(3411L, "owner-admin-channel", "OPEN");
        JoinResultView adminJoin = roomFacade.applyJoin(join(3412L, "CHANNEL", channel.id(), "admin"));
        ImMembershipPo adminMembership = membershipRepository.findById(adminJoin.membershipId()).orElseThrow();
        adminMembership.setMemberRole(ImMembershipRole.ADMIN);
        membershipRepository.saveAndFlush(adminMembership);

        assertThatThrownBy(() -> roomFacade.leave(new LeaveCommand(principal(3411L), "CHANNEL", channel.id())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_OWNER_REPLACEMENT_REQUIRED");
        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                ImSurfaceType.CHANNEL, channel.id(), 3411L)).get()
                .extracting(ImMembershipPo::getStatus)
                .isEqualTo(ImMembershipStatus.ACTIVE);
        assertThat(channelRepository.findById(channel.id()).orElseThrow().getMemberCount()).isEqualTo(2);
    }

    @Test
    void ownerCanLeaveWhenAnotherActiveOwnerRemains() {
        ChannelView channel = channel(3421L, "owner-owner-channel", "OPEN");
        JoinResultView ownerJoin = roomFacade.applyJoin(join(3422L, "CHANNEL", channel.id(), "owner"));
        ImMembershipPo replacementOwner = membershipRepository.findById(ownerJoin.membershipId()).orElseThrow();
        replacementOwner.setMemberRole(ImMembershipRole.OWNER);
        membershipRepository.saveAndFlush(replacementOwner);

        roomFacade.leave(new LeaveCommand(principal(3421L), "CHANNEL", channel.id()));

        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                ImSurfaceType.CHANNEL, channel.id(), 3421L)).get()
                .extracting(ImMembershipPo::getStatus)
                .isEqualTo(ImMembershipStatus.LEFT);
        assertThat(conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(
                channel.mainConversationId(), 3421L)).get()
                .extracting(member -> member.getStatus())
                .isEqualTo(ImMembershipStatus.LEFT);
        assertThat(channelRepository.findById(channel.id()).orElseThrow().getMemberCount()).isEqualTo(1);
    }

    private ChannelView channel(Long ownerUserId, String key, String joinPolicy) {
        return conversationService.createChannel(new CreateChannelCommand(
                principal(ownerUserId), CONTEXT_TYPE, null, key, key, joinPolicy, "{}"));
    }

    private GroupView standaloneGroup(Long ownerUserId, String key, String joinPolicy) {
        return conversationService.createGroup(new CreateGroupCommand(
                principal(ownerUserId), null, CONTEXT_TYPE, null, key, key, joinPolicy, "{}"));
    }

    private JoinCommand join(Long userId, String surfaceType, Long surfaceId, String reason) {
        return new JoinCommand(principal(userId), surfaceType, surfaceId, reason);
    }

    private void ban(ImSurfaceType surfaceType, Long surfaceId, Long userId) {
        membershipRepository.saveAndFlush(member(surfaceType, surfaceId, userId,
                ImMembershipRole.MEMBER, ImMembershipStatus.BANNED));
    }

    private ImMembershipPo member(ImSurfaceType surfaceType, Long surfaceId, Long userId,
                                  ImMembershipRole role, ImMembershipStatus status) {
        ImMembershipPo membership = new ImMembershipPo();
        membership.setSurfaceType(surfaceType);
        membership.setSurfaceId(surfaceId);
        membership.setUserId(userId);
        membership.setMemberRole(role);
        membership.setStatus(status);
        membership.setJoinedAt(Instant.now());
        membership.setMetadataJson("{}");
        return membership;
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }

    private void assertLockedLookupUsed(Object repository, Long id) {
        assertThat(mockingDetails(repository).getInvocations())
                .anySatisfy(invocation -> {
                    assertThat(invocation.getMethod().getName()).isEqualTo("findLockedByIdAndDeletedFalse");
                    assertThat(invocation.getArguments()).containsExactly(id);
                });
    }

    private void assertPessimisticWriteLookup(Class<?> repositoryType) throws Exception {
        Method method = repositoryType.getMethod("findLockedByIdAndDeletedFalse", Long.class);
        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
