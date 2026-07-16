package top.egon.mario.im.platform;

import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.policy.ImPrincipal;

import java.util.List;

/**
 * Restricts browser-driven room operations to the platform context.
 */
@Component
public class PlatformRoomFacade {

    public static final String PLATFORM_CONTEXT_TYPE = "PLATFORM";

    private final RoomFacade roomFacade;

    public PlatformRoomFacade(RoomFacade roomFacade) {
        this.roomFacade = roomFacade;
    }

    public GroupView createGroup(ImPrincipal principal,
                                 String groupKey,
                                 String name,
                                 String joinPolicy,
                                 String metadataJson) {
        return roomFacade.createGroup(new CreateGroupCommand(
                principal, null, PLATFORM_CONTEXT_TYPE, null, groupKey, name, joinPolicy, metadataJson));
    }

    public List<GroupView> listGroups(ImPrincipal principal) {
        return roomFacade.listGroups(new ListGroupsQuery(principal, null, PLATFORM_CONTEXT_TYPE, null));
    }

    public List<ChannelView> listChannels(ImPrincipal principal) {
        return roomFacade.listChannels(new ListChannelsQuery(principal, PLATFORM_CONTEXT_TYPE, null));
    }

    public ChannelView createGeneralChannel(ImPrincipal principal, String channelKey, String channelName) {
        return roomFacade.createChannel(new CreateChannelCommand(
                principal, PLATFORM_CONTEXT_TYPE, null, channelKey, channelName, "OPEN", "{}"));
    }
}
