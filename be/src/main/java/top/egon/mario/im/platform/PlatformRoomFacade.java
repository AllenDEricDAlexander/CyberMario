package top.egon.mario.im.platform;

import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.service.ImException;

import java.util.List;
import java.util.UUID;

/**
 * Restricts browser-driven room operations to the platform context.
 */
@Component
public class PlatformRoomFacade {

    public static final String PLATFORM_CONTEXT_TYPE = "PLATFORM";

    private final RoomFacade roomFacade;
    private final ImMembershipRepository membershipRepository;

    public PlatformRoomFacade(RoomFacade roomFacade, ImMembershipRepository membershipRepository) {
        this.roomFacade = roomFacade;
        this.membershipRepository = membershipRepository;
    }

    public ChannelView createChannel(ImPrincipal principal, String name, String metadataJson) {
        return roomFacade.createChannel(new CreateChannelCommand(
                principal, PLATFORM_CONTEXT_TYPE, null, generatedKey("channel"), name, "APPROVAL", metadataJson));
    }

    public GroupView createStandaloneGroup(ImPrincipal principal, String name, String metadataJson) {
        return createGroup(principal, generatedKey("group"), name, "APPROVAL", metadataJson);
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
        return roomFacade.listGroups(new ListGroupsQuery(principal, null, PLATFORM_CONTEXT_TYPE, null)).stream()
                .filter(group -> ImMembershipStatus.ACTIVE.name().equals(group.membershipStatus()))
                .toList();
    }

    public List<ChannelView> listChannels(ImPrincipal principal) {
        return roomFacade.listChannels(new ListChannelsQuery(principal, PLATFORM_CONTEXT_TYPE, null)).stream()
                .filter(channel -> ImMembershipStatus.ACTIVE.name().equals(channel.membershipStatus()))
                .toList();
    }

    public GroupView createChannelGroup(ImPrincipal principal,
                                        Long channelId,
                                        String name,
                                        String joinPolicy,
                                        String metadataJson) {
        requireChannelManager(principal, channelId);
        return roomFacade.createGroup(new CreateGroupCommand(
                principal, channelId, null, null, generatedKey("group"), name, joinPolicy, metadataJson));
    }

    public List<GroupView> listChannelGroups(ImPrincipal principal, Long channelId) {
        requireActiveChannelMembership(principal, channelId);
        return roomFacade.listGroups(new ListGroupsQuery(principal, channelId, null, null));
    }

    private ImMembershipPo requireActiveChannelMembership(ImPrincipal principal, Long channelId) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        if (channelId == null) {
            throw new ImException("IM_CHANNEL_ID_REQUIRED");
        }
        return membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndStatusAndDeletedFalse(
                        ImSurfaceType.CHANNEL, channelId, principal.userId(), ImMembershipStatus.ACTIVE)
                .orElseThrow(() -> new ImException("IM_PARENT_CHANNEL_MEMBERSHIP_REQUIRED"));
    }

    private void requireChannelManager(ImPrincipal principal, Long channelId) {
        ImMembershipPo membership = requireActiveChannelMembership(principal, channelId);
        if (!ImMembershipRole.OWNER.equals(membership.getMemberRole())
                && !ImMembershipRole.ADMIN.equals(membership.getMemberRole())) {
            throw new ImException("IM_CHANNEL_MANAGEMENT_REQUIRED");
        }
    }

    private String generatedKey(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
