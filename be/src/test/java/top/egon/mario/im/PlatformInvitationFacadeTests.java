package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.JoinByKeyCommand;
import top.egon.mario.im.facade.dto.command.LeaveCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.platform.PlatformInvitationFacade;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.platform.dto.PlatformInvitationView;
import top.egon.mario.im.po.enums.ImJoinRequestStatus;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceInvitationStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImJoinRequestRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.repository.ImSurfaceInvitationRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:platform_invitation_facade_tests;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@Transactional
class PlatformInvitationFacadeTests {

    @Autowired
    private PlatformInvitationFacade invitationFacade;

    @Autowired
    private PlatformRoomFacade platformRoomFacade;

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private ImFacade imFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImJoinRequestRepository joinRequestRepository;

    @Autowired
    private ImSurfaceInvitationRepository invitationRepository;

    @Test
    void channelUsesJoinKeyInsteadOfDatabaseIdAndRejoinRestoresHistory() {
        UserPo owner = user("channel-owner", "Channel Owner");
        UserPo member = user("channel-member", "Channel Member");
        ImPrincipal ownerPrincipal = principal(owner.getId());
        ImPrincipal memberPrincipal = principal(member.getId());
        ChannelView channel = platformRoomFacade.createChannel(ownerPrincipal, "产品频道", "{}");

        assertThatThrownBy(() -> roomFacade.applyJoin(new JoinCommand(
                memberPrincipal, "CHANNEL", channel.id(), "search join")))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PLATFORM_JOIN_KEY_REQUIRED");

        JoinResultView pending = roomFacade.applyJoinByKey(new JoinByKeyCommand(
                memberPrincipal, channel.joinKey(), "申请加入"));
        roomFacade.approveJoin(new ApproveCommand(ownerPrincipal, pending.joinRequestId()));

        assertThat(platformRoomFacade.listChannels(memberPrincipal))
                .extracting(ChannelView::id)
                .containsExactly(channel.id());
        imFacade.send(new SendMessageCommand(
                ownerPrincipal, channel.mainConversationId(), "channel-history-1", "TEXT", "历史消息", "{}", "{}"));
        assertThat(imFacade.history(new HistoryQuery(
                memberPrincipal, channel.mainConversationId(), 0, 20, null, null)).getContent())
                .extracting(message -> message.content())
                .containsExactly("历史消息");

        roomFacade.leave(new LeaveCommand(memberPrincipal, "CHANNEL", channel.id()));
        assertThatThrownBy(() -> imFacade.history(new HistoryQuery(
                memberPrincipal, channel.mainConversationId(), 0, 20, null, null)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_HISTORY_FORBIDDEN");

        JoinResultView rejoin = roomFacade.applyJoinByKey(new JoinByKeyCommand(
                memberPrincipal, channel.joinKey(), "再次加入"));
        roomFacade.approveJoin(new ApproveCommand(ownerPrincipal, rejoin.joinRequestId()));
        assertThat(imFacade.history(new HistoryQuery(
                memberPrincipal, channel.mainConversationId(), 0, 20, null, null)).getContent())
                .extracting(message -> message.content())
                .containsExactly("历史消息");
    }

    @Test
    void childGroupRequiresParentMembershipAndChannelDepartureCascadesState() {
        UserPo owner = user("cascade-owner", "Cascade Owner");
        UserPo member = user("cascade-member", "Cascade Member");
        ImPrincipal ownerPrincipal = principal(owner.getId());
        ImPrincipal memberPrincipal = principal(member.getId());
        ChannelView channel = platformRoomFacade.createChannel(ownerPrincipal, "级联频道", "{}");
        GroupView openGroup = platformRoomFacade.createChannelGroup(
                ownerPrincipal, channel.id(), "开放群", "OPEN", "{}");

        assertThatThrownBy(() -> roomFacade.applyJoin(new JoinCommand(
                memberPrincipal, "GROUP", openGroup.id(), null)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED");
        assertThatThrownBy(() -> invitationFacade.invite(
                ownerPrincipal, "GROUP", openGroup.id(), member.getId(), null))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED");

        acceptInvite(ownerPrincipal, memberPrincipal, "CHANNEL", channel.id(), member.getId());
        roomFacade.applyJoin(new JoinCommand(memberPrincipal, "GROUP", openGroup.id(), null));
        GroupView approvalGroup = platformRoomFacade.createChannelGroup(
                ownerPrincipal, channel.id(), "审批群", "APPROVAL", "{}");
        JoinResultView pending = roomFacade.applyJoin(new JoinCommand(
                memberPrincipal, "GROUP", approvalGroup.id(), "申请加入"));
        GroupView invitedGroup = platformRoomFacade.createChannelGroup(
                ownerPrincipal, channel.id(), "邀请群", "OPEN", "{}");
        PlatformInvitationView childInvitation = invitationFacade.invite(
                ownerPrincipal, "GROUP", invitedGroup.id(), member.getId(), "待处理邀请");

        roomFacade.leave(new LeaveCommand(memberPrincipal, "CHANNEL", channel.id()));

        assertMembershipStatus(ImSurfaceType.CHANNEL, channel.id(), member.getId(), ImMembershipStatus.LEFT);
        assertMembershipStatus(ImSurfaceType.GROUP, openGroup.id(), member.getId(), ImMembershipStatus.LEFT);
        assertThat(conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(
                openGroup.conversationId(), member.getId())).get()
                .extracting(memberRow -> memberRow.getStatus())
                .isEqualTo(ImMembershipStatus.LEFT);
        assertThat(joinRequestRepository.findByIdAndDeletedFalse(pending.joinRequestId())).get()
                .extracting(request -> request.getStatus())
                .isEqualTo(ImJoinRequestStatus.CANCELLED);
        assertThat(invitationRepository.findByIdAndDeletedFalse(childInvitation.invitationId())).get()
                .extracting(row -> row.getStatus())
                .isEqualTo(ImSurfaceInvitationStatus.CANCELLED);
        assertThatThrownBy(() -> platformRoomFacade.listChannelGroups(memberPrincipal, channel.id()))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED");
    }

    @Test
    void ownerTransfersChannelAndChildGroupBeforeLeaving() {
        UserPo owner = user("transfer-owner", "Transfer Owner");
        UserPo successor = user("transfer-successor", "Transfer Successor");
        ImPrincipal ownerPrincipal = principal(owner.getId());
        ImPrincipal successorPrincipal = principal(successor.getId());
        ChannelView channel = platformRoomFacade.createChannel(ownerPrincipal, "交接频道", "{}");
        GroupView group = platformRoomFacade.createChannelGroup(
                ownerPrincipal, channel.id(), "交接群", "OPEN", "{}");
        acceptInvite(ownerPrincipal, successorPrincipal, "CHANNEL", channel.id(), successor.getId());
        acceptInvite(ownerPrincipal, successorPrincipal, "GROUP", group.id(), successor.getId());

        invitationFacade.transferOwnership(
                ownerPrincipal, "CHANNEL", channel.id(), successor.getId());
        assertThatThrownBy(() -> roomFacade.leave(new LeaveCommand(ownerPrincipal, "CHANNEL", channel.id())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_CHILD_GROUP_OWNER_TRANSFER_REQUIRED");

        invitationFacade.transferOwnership(
                ownerPrincipal, "GROUP", group.id(), successor.getId());
        roomFacade.leave(new LeaveCommand(ownerPrincipal, "CHANNEL", channel.id()));

        assertMembershipStatus(ImSurfaceType.CHANNEL, channel.id(), owner.getId(), ImMembershipStatus.LEFT);
        assertMembershipStatus(ImSurfaceType.GROUP, group.id(), owner.getId(), ImMembershipStatus.LEFT);
    }

    @Test
    void standaloneGroupInvitationCanBeRejectedWithoutCreatingMembership() {
        UserPo owner = user("standalone-owner", "Standalone Owner");
        UserPo invitee = user("standalone-invitee", "Standalone Invitee");
        ImPrincipal ownerPrincipal = principal(owner.getId());
        ImPrincipal inviteePrincipal = principal(invitee.getId());
        GroupView group = platformRoomFacade.createStandaloneGroup(ownerPrincipal, "独立群", "{}");

        PlatformInvitationView invitation = invitationFacade.invite(
                ownerPrincipal, "GROUP", group.id(), invitee.getId(), null);
        PlatformInvitationView rejected = invitationFacade.reject(inviteePrincipal, invitation.invitationId());

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                ImSurfaceType.GROUP, group.id(), invitee.getId())).isEmpty();
        assertThat(platformRoomFacade.listGroups(inviteePrincipal)).isEmpty();
    }

    private void acceptInvite(ImPrincipal inviter,
                              ImPrincipal invitee,
                              String surfaceType,
                              Long surfaceId,
                              Long inviteeUserId) {
        PlatformInvitationView invitation = invitationFacade.invite(
                inviter, surfaceType, surfaceId, inviteeUserId, null);
        invitationFacade.accept(invitee, invitation.invitationId());
    }

    private void assertMembershipStatus(ImSurfaceType surfaceType,
                                        Long surfaceId,
                                        Long userId,
                                        ImMembershipStatus status) {
        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                surfaceType, surfaceId, userId)).get()
                .extracting(membership -> membership.getStatus())
                .isEqualTo(status);
    }

    private UserPo user(String accountNo, String displayName) {
        UserPo user = new UserPo();
        user.setAccountNo(accountNo);
        user.setUsername(accountNo);
        user.setNickname(displayName);
        user.setPasswordHash("test-password-hash");
        user.setStatus(RbacStatus.ENABLED);
        return userRepository.saveAndFlush(user);
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of("IM_USER"), PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, Map.of());
    }
}
