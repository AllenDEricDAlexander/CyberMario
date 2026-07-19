package top.egon.mario.im.facade;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.CancelJoinCommand;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.JoinByKeyCommand;
import top.egon.mario.im.facade.dto.command.LeaveCommand;
import top.egon.mario.im.facade.dto.command.RejectJoinCommand;
import top.egon.mario.im.facade.dto.command.RemoveMemberCommand;
import top.egon.mario.im.facade.dto.query.ListJoinRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.query.ListSurfaceMembersQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.facade.dto.view.JoinRequestView;
import top.egon.mario.im.facade.dto.view.SurfaceMemberView;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.service.MembershipService;

import java.util.List;

@Component("imRoomFacade")
public class RoomFacade {

    private final MembershipService membershipService;
    private final ConversationService conversationService;

    public RoomFacade(MembershipService membershipService, ConversationService conversationService) {
        this.membershipService = membershipService;
        this.conversationService = conversationService;
    }

    public ChannelView createChannel(CreateChannelCommand command) {
        return conversationService.createChannel(command);
    }

    public GroupView createGroup(CreateGroupCommand command) {
        return conversationService.createGroup(command);
    }

    public JoinResultView applyJoin(JoinCommand command) {
        return membershipService.applyJoin(command);
    }

    public JoinResultView applyJoinByKey(JoinByKeyCommand command) {
        return membershipService.applyJoinByKey(command);
    }

    public JoinResultView approveJoin(ApproveCommand command) {
        return membershipService.approveJoin(command);
    }

    public JoinResultView rejectJoin(RejectJoinCommand command) {
        return membershipService.rejectJoin(command);
    }

    public JoinResultView cancelJoin(CancelJoinCommand command) {
        return membershipService.cancelJoin(command);
    }

    public void leave(LeaveCommand command) {
        membershipService.leave(command);
    }

    public Page<SurfaceMemberView> listMembers(ListSurfaceMembersQuery query) {
        return membershipService.listMembers(query);
    }

    public Page<JoinRequestView> listJoinRequests(ListJoinRequestsQuery query) {
        return membershipService.listJoinRequests(query);
    }

    public void removeMember(RemoveMemberCommand command) {
        membershipService.removeMember(command);
    }

    public List<ChannelView> listChannels(ListChannelsQuery query) {
        return conversationService.listChannels(query);
    }

    public List<GroupView> listGroups(ListGroupsQuery query) {
        return conversationService.listGroups(query);
    }
}
