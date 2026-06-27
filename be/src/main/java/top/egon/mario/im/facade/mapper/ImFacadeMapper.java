package top.egon.mario.im.facade.mapper;

import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImChannelVisibility;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;

import java.util.Objects;

public final class ImFacadeMapper {

    public MessageView toMessageView(Object source) {
        ImMessagePo message = requireType(source, ImMessagePo.class);
        return new MessageView(
                message.getId(),
                message.getConversationId(),
                message.getSenderUserId(),
                message.getMessageSeq(),
                message.getClientMsgId(),
                message.getMessageType(),
                message.getContent(),
                message.getPayloadJson(),
                name(message.getStatus()),
                message.getSentAt(),
                message.getEditedAt(),
                message.getDeletedAt(),
                message.getMetadataJson()
        );
    }

    public ConversationView toConversationView(Object source) {
        ImConversationPo conversation = requireType(source, ImConversationPo.class);
        return new ConversationView(
                conversation.getId(),
                conversation.getConversationType(),
                name(conversation.getOwnerSurfaceType()),
                conversation.getOwnerSurfaceId(),
                conversation.getContextType(),
                conversation.getContextId(),
                conversation.getMessageSeq(),
                conversation.getLastMessageId(),
                conversation.getLastMessageAt(),
                conversation.getLastActiveAt(),
                name(conversation.getStatus())
        );
    }

    public ChannelView toChannelView(Object source) {
        return toChannelView(source, null);
    }

    public ChannelView toChannelView(Object source, ImMembershipPo callerMembership) {
        ImChannelPo channel = requireType(source, ImChannelPo.class);
        boolean canRead = ImSurfaceStatus.ACTIVE.equals(channel.getStatus())
                && ImChannelVisibility.PUBLIC.equals(channel.getVisibility());
        boolean canPost = canRead && active(callerMembership);
        return new ChannelView(
                channel.getId(),
                channel.getContextType(),
                channel.getContextId(),
                channel.getChannelKey(),
                channel.getName(),
                channel.getOwnerUserId(),
                name(channel.getVisibility()),
                name(channel.getJoinPolicy()),
                name(channel.getStatus()),
                channel.getAnnouncement(),
                channel.getMainConversationId(),
                channel.getMemberCount(),
                channel.getLastActiveAt(),
                callerMembership == null ? null : name(callerMembership.getStatus()),
                callerMembership == null ? null : name(callerMembership.getMemberRole()),
                canRead,
                canPost
        );
    }

    public GroupView toGroupView(Object source) {
        return toGroupView(source, null);
    }

    public GroupView toGroupView(Object source, ImMembershipPo callerMembership) {
        ImGroupPo group = requireType(source, ImGroupPo.class);
        boolean canRead = ImSurfaceStatus.ACTIVE.equals(group.getStatus()) && active(callerMembership);
        boolean canPost = canRead;
        return new GroupView(
                group.getId(),
                group.getChannelId(),
                group.getContextType(),
                group.getContextId(),
                group.getGroupKey(),
                group.getName(),
                group.getOwnerUserId(),
                name(group.getJoinPolicy()),
                name(group.getStatus()),
                group.getAnnouncement(),
                group.getConversationId(),
                group.getMemberCount(),
                group.getLastActiveAt(),
                callerMembership == null ? null : name(callerMembership.getStatus()),
                callerMembership == null ? null : name(callerMembership.getMemberRole()),
                canRead,
                canPost
        );
    }

    private static <T> T requireType(Object source, Class<T> type) {
        Objects.requireNonNull(source, "source must not be null");
        if (!type.isInstance(source)) {
            throw new IllegalArgumentException("Expected " + type.getSimpleName());
        }
        return type.cast(source);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static boolean active(ImMembershipPo membership) {
        return membership != null && ImMembershipStatus.ACTIVE.equals(membership.getStatus());
    }
}
