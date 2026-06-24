package top.egon.mario.im.factory;

import org.springframework.stereotype.Component;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImReadStatePo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ImEntityFactory {

    public ImChannelPo channel(String contextType, Long contextId, String channelType, Instant now) {
        ImChannelPo channel = new ImChannelPo();
        channel.setContextType(contextType);
        channel.setContextId(contextId);
        channel.setChannelKey(channelType);
        channel.setName(channelType);
        channel.setStatus("ACTIVE");
        channel.setLastActiveAt(now);
        channel.setMetadataJson("{}");
        return channel;
    }

    public ImGroupPo group(Long channelId, String groupType) {
        ImGroupPo group = new ImGroupPo();
        group.setChannelId(channelId);
        group.setGroupKey(groupType);
        group.setName(groupType);
        group.setStatus("ACTIVE");
        group.setMetadataJson("{}");
        return group;
    }

    public ImConversationPo conversation(ImChannelPo channel, ImGroupPo group, String scopeType, Long scopeId,
                                         String conversationType, String participantKey, Instant now) {
        ImConversationPo conversation = new ImConversationPo();
        conversation.setChannelId(channel.getId());
        conversation.setGroupId(group.getId());
        conversation.setContextType(channel.getContextType());
        conversation.setContextId(channel.getContextId());
        conversation.setScopeType(scopeType);
        conversation.setScopeId(scopeId);
        conversation.setConversationType(conversationType);
        conversation.setParticipantKey(participantKey);
        conversation.setStatus("ACTIVE");
        conversation.setMessageSeq(0L);
        conversation.setLastActiveAt(now);
        conversation.setMetadataJson("{}");
        return conversation;
    }

    public ImConversationMemberPo member(Long conversationId, Long userId, String participantKey, Instant now) {
        ImConversationMemberPo member = new ImConversationMemberPo();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setParticipantKey(participantKey);
        member.setMemberRole("MEMBER");
        member.setStatus("ACTIVE");
        member.setLastReadMessageSeq(0L);
        member.setJoinedAt(now);
        member.setLastActiveAt(now);
        member.setMetadataJson("{}");
        return member;
    }

    public ImMessagePo message(Long conversationId, Long senderMemberId, Long senderUserId, Long messageSeq,
                               String content, String metadata, Instant now) {
        ImMessagePo message = new ImMessagePo();
        message.setConversationId(conversationId);
        message.setSenderMemberId(senderMemberId);
        message.setSenderUserId(senderUserId);
        message.setMessageSeq(messageSeq);
        message.setMessageType("TEXT");
        message.setContent(content == null ? "" : content);
        message.setPayloadJson("{}");
        message.setStatus("VISIBLE");
        message.setMetadataJson(metadata == null ? "{}" : metadata);
        message.setSentAt(now);
        return message;
    }

    public ImReadStatePo readState(Long conversationId, Long conversationMemberId, Long userId, Long messageSeq,
                                   Instant now) {
        ImReadStatePo readState = new ImReadStatePo();
        readState.setConversationId(conversationId);
        readState.setConversationMemberId(conversationMemberId);
        readState.setUserId(userId);
        readState.setLastReadMessageSeq(messageSeq);
        readState.setLastReadAt(now);
        readState.setStatus("ACTIVE");
        return readState;
    }

    public String participantKey(String scopeType, Long scopeId, String conversationType,
                                 Collection<Long> participantUserIds) {
        if (!usesParticipantIdentity(conversationType)) {
            return scopeType + ":" + scopeId;
        }
        List<Long> sortedUserIds = participantUserIds == null ? List.of() : participantUserIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sortedUserIds.isEmpty()) {
            throw new IllegalArgumentException("IM_PARTICIPANTS_REQUIRED");
        }
        return sortedUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(":"));
    }

    public boolean usesParticipantIdentity(String conversationType) {
        return "PRIVATE".equalsIgnoreCase(conversationType)
                || "DIRECT".equalsIgnoreCase(conversationType)
                || "ONE_TO_ONE".equalsIgnoreCase(conversationType);
    }

    public List<Long> participantUserIds(Collection<Long> participantUserIds) {
        if (participantUserIds == null) {
            return List.of();
        }
        return participantUserIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
