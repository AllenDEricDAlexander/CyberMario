package top.egon.mario.im.facade;

import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.CancelJoinCommand;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.LeaveCommand;
import top.egon.mario.im.facade.dto.command.RejectJoinCommand;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.service.ImException;

import java.util.List;

@Component("imRoomFacade")
public class RoomFacade {

    public ChannelView createChannel(CreateChannelCommand command) {
        throw notImplemented();
    }

    public GroupView createGroup(CreateGroupCommand command) {
        throw notImplemented();
    }

    public JoinResultView applyJoin(JoinCommand command) {
        throw notImplemented();
    }

    public JoinResultView approveJoin(ApproveCommand command) {
        throw notImplemented();
    }

    public JoinResultView rejectJoin(RejectJoinCommand command) {
        throw notImplemented();
    }

    public JoinResultView cancelJoin(CancelJoinCommand command) {
        throw notImplemented();
    }

    public void leave(LeaveCommand command) {
        throw notImplemented();
    }

    public List<ChannelView> listChannels(ListChannelsQuery query) {
        throw notImplemented();
    }

    public List<GroupView> listGroups(ListGroupsQuery query) {
        throw notImplemented();
    }

    private static ImException notImplemented() {
        return new ImException("IM_FACADE_NOT_IMPLEMENTED",
                "IM facade contract is defined; business implementation is pending");
    }
}
