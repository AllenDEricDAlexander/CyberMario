package top.egon.mario.im.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.mapper.ImFacadeMapper;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImChannelVisibility;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImJoinPolicy;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMessageRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ConversationService {

    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final ImConversationRepository conversationRepository;
    private final ImConversationMemberRepository conversationMemberRepository;
    private final ImMessageRepository messageRepository;
    private final MembershipService membershipService;
    private final ImFacadeMapper mapper = new ImFacadeMapper();

    public ConversationService(ImChannelRepository channelRepository,
                               ImGroupRepository groupRepository,
                               ImConversationRepository conversationRepository,
                               ImConversationMemberRepository conversationMemberRepository,
                               ImMessageRepository messageRepository,
                               MembershipService membershipService) {
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.membershipService = membershipService;
    }

    @Transactional
    public ChannelView createChannel(CreateChannelCommand command) {
        if (command == null) {
            throw new ImException("IM_CHANNEL_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        String contextType = requireText(command.contextType(), "IM_CONTEXT_TYPE_REQUIRED");
        String channelKey = requireText(command.channelKey(), "IM_CHANNEL_KEY_REQUIRED");

        Optional<ImChannelPo> existing = channelRepository.findActiveByContextAndChannelKey(
                contextType, command.contextId(), channelKey);
        if (existing.isPresent()) {
            ImChannelPo channel = existing.get();
            ensureChannelMainConversation(channel, Instant.now());
            return channelView(channel, principal);
        }

        String name = requireText(command.name(), "IM_CHANNEL_NAME_REQUIRED");
        ImJoinPolicy joinPolicy = joinPolicy(command.joinPolicy());
        Instant now = Instant.now();
        ImChannelPo channel = new ImChannelPo();
        channel.setContextType(contextType);
        channel.setContextId(command.contextId());
        channel.setChannelKey(channelKey);
        channel.setName(name);
        channel.setOwnerUserId(principal.userId());
        channel.setVisibility(ImChannelVisibility.PUBLIC);
        channel.setJoinPolicy(joinPolicy);
        channel.setStatus(ImSurfaceStatus.ACTIVE);
        channel.setAnnouncement("");
        channel.setMemberCount(1);
        channel.setLastActiveAt(now);
        channel.setMetadataJson(metadata(command.metadataJson()));
        channel = channelRepository.saveAndFlush(channel);

        ImConversationPo conversation = ensureChannelMainConversation(channel, now);
        ImMembershipPo membership = membershipService.ensureOwnerMembership(
                ImSurfaceType.CHANNEL, channel.getId(), principal.userId(), now);
        membershipService.ensureConversationMember(conversation.getId(), principal.userId(), now);
        return mapper.toChannelView(channel, membership);
    }

    @Transactional
    public GroupView createGroup(CreateGroupCommand command) {
        if (command == null) {
            throw new ImException("IM_GROUP_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        String groupKey = requireText(command.groupKey(), "IM_GROUP_KEY_REQUIRED");

        GroupContext context = groupContext(command);
        Optional<ImGroupPo> existing = context.channelId() == null
                ? groupRepository.findActiveStandaloneByContextAndGroupKey(
                context.contextType(), context.contextId(), groupKey)
                : groupRepository.findActiveByChannelIdAndGroupKey(context.channelId(), groupKey);
        if (existing.isPresent()) {
            ImGroupPo group = existing.get();
            ensureGroupConversation(group, Instant.now());
            return groupView(group, principal);
        }

        String name = requireText(command.name(), "IM_GROUP_NAME_REQUIRED");
        ImJoinPolicy joinPolicy = joinPolicy(command.joinPolicy());
        Instant now = Instant.now();
        ImGroupPo group = new ImGroupPo();
        group.setChannelId(context.channelId());
        group.setContextType(context.contextType());
        group.setContextId(context.contextId());
        group.setGroupKey(groupKey);
        group.setName(name);
        group.setOwnerUserId(principal.userId());
        group.setJoinPolicy(joinPolicy);
        group.setStatus(ImSurfaceStatus.ACTIVE);
        group.setAnnouncement("");
        group.setMemberCount(1);
        group.setLastActiveAt(now);
        group.setMetadataJson(metadata(command.metadataJson()));
        group = groupRepository.saveAndFlush(group);

        ImConversationPo conversation = ensureGroupConversation(group, now);
        ImMembershipPo membership = membershipService.ensureOwnerMembership(
                ImSurfaceType.GROUP, group.getId(), principal.userId(), now);
        membershipService.ensureConversationMember(conversation.getId(), principal.userId(), now);
        return mapper.toGroupView(group, membership);
    }

    @Transactional(readOnly = true)
    public List<ChannelView> listChannels(ListChannelsQuery query) {
        if (query == null) {
            throw new ImException("IM_CHANNEL_QUERY_REQUIRED");
        }
        String contextType = requireText(query.contextType(), "IM_CONTEXT_TYPE_REQUIRED");
        List<ImChannelPo> channels = channelRepository.findActivePublicByContext(contextType, query.contextId());
        Map<Long, ImMembershipPo> memberships = membershipMap(
                ImSurfaceType.CHANNEL, channels.stream().map(ImChannelPo::getId).toList(), query.principal());
        return channels.stream()
                .map(channel -> mapper.toChannelView(channel, memberships.get(channel.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupView> listGroups(ListGroupsQuery query) {
        if (query == null) {
            throw new ImException("IM_GROUP_QUERY_REQUIRED");
        }
        List<ImGroupPo> groups;
        if (query.channelId() == null) {
            String contextType = requireText(query.contextType(), "IM_CONTEXT_TYPE_REQUIRED");
            groups = groupRepository.findActiveStandaloneByContext(contextType, query.contextId());
        } else {
            requireActiveChannel(query.channelId());
            groups = groupRepository.findActiveByChannelId(query.channelId());
        }
        Map<Long, ImMembershipPo> memberships = membershipMap(
                ImSurfaceType.GROUP, groups.stream().map(ImGroupPo::getId).toList(), query.principal());
        return groups.stream()
                .map(group -> mapper.toGroupView(group, memberships.get(group.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationView> listConversations(ListConversationsQuery query) {
        if (query == null) {
            throw new ImException("IM_CONVERSATION_QUERY_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(query.principal());
        String contextType = optionalText(query.contextType());
        if ((query.contextType() != null || query.contextId() != null) && contextType == null) {
            throw new ImException("IM_CONTEXT_TYPE_REQUIRED");
        }

        return conversationMemberRepository.findActiveByUserId(principal.userId()).stream()
                .map(member -> conversationListItem(member, contextType, query.contextId()))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparing((ConversationListItem item) -> item.conversation().getLastActiveAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.conversation().getId(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(item -> mapper.toConversationView(item.conversation(), item.unreadCount(), item.lastMessage()))
                .toList();
    }

    private ImConversationPo ensureChannelMainConversation(ImChannelPo channel, Instant now) {
        ImConversationPo conversation = conversationRepository
                .findByOwnerSurfaceTypeAndOwnerSurfaceIdAndConversationTypeAndDeletedFalse(
                        ImSurfaceType.CHANNEL, channel.getId(), ImConversationType.CHANNEL_MAIN)
                .orElseGet(() -> conversationRepository.saveAndFlush(conversation(
                        ImConversationType.CHANNEL_MAIN, ImSurfaceType.CHANNEL, channel.getId(),
                        channel.getContextType(), channel.getContextId(), now)));
        if (!conversation.getId().equals(channel.getMainConversationId())) {
            channel.setMainConversationId(conversation.getId());
            channelRepository.saveAndFlush(channel);
        }
        return conversation;
    }

    private ImConversationPo ensureGroupConversation(ImGroupPo group, Instant now) {
        ImConversationPo conversation = conversationRepository
                .findByOwnerSurfaceTypeAndOwnerSurfaceIdAndConversationTypeAndDeletedFalse(
                        ImSurfaceType.GROUP, group.getId(), ImConversationType.GROUP)
                .orElseGet(() -> conversationRepository.saveAndFlush(conversation(
                        ImConversationType.GROUP, ImSurfaceType.GROUP, group.getId(),
                        group.getContextType(), group.getContextId(), now)));
        if (!conversation.getId().equals(group.getConversationId())) {
            group.setConversationId(conversation.getId());
            groupRepository.saveAndFlush(group);
        }
        return conversation;
    }

    private ImConversationPo conversation(ImConversationType conversationType,
                                          ImSurfaceType ownerSurfaceType,
                                          Long ownerSurfaceId,
                                          String contextType,
                                          Long contextId,
                                          Instant now) {
        ImConversationPo conversation = new ImConversationPo();
        conversation.setConversationType(conversationType);
        conversation.setOwnerSurfaceType(ownerSurfaceType);
        conversation.setOwnerSurfaceId(ownerSurfaceId);
        conversation.setContextType(contextType);
        conversation.setContextId(contextId);
        conversation.setMessageSeq(0L);
        conversation.setLastActiveAt(now);
        conversation.setStatus(ImConversationStatus.ACTIVE);
        conversation.setMetadataJson("{}");
        return conversation;
    }

    private GroupContext groupContext(CreateGroupCommand command) {
        if (command.channelId() == null) {
            return new GroupContext(null, requireText(command.contextType(), "IM_CONTEXT_TYPE_REQUIRED"),
                    command.contextId());
        }
        ImChannelPo channel = requireActiveChannel(command.channelId());
        return new GroupContext(channel.getId(), channel.getContextType(), channel.getContextId());
    }

    private ImChannelPo requireActiveChannel(Long channelId) {
        return channelRepository.findByIdAndDeletedFalse(channelId)
                .filter(channel -> ImSurfaceStatus.ACTIVE.equals(channel.getStatus()))
                .orElseThrow(() -> invalidChannelReference(channelId));
    }

    private ImException invalidChannelReference(Long channelId) {
        if (groupRepository.findByIdAndDeletedFalse(channelId)
                .filter(group -> ImSurfaceStatus.ACTIVE.equals(group.getStatus()))
                .isPresent()) {
            return new ImException("IM_INVALID_SURFACE_NESTING", "Group id cannot be used as a channel id");
        }
        return new ImException("IM_CHANNEL_NOT_FOUND");
    }

    private ChannelView channelView(ImChannelPo channel, ImPrincipal principal) {
        return mapper.toChannelView(channel, membershipService.findCallerMembership(
                ImSurfaceType.CHANNEL, channel.getId(), principal.userId()).orElse(null));
    }

    private GroupView groupView(ImGroupPo group, ImPrincipal principal) {
        return mapper.toGroupView(group, membershipService.findCallerMembership(
                ImSurfaceType.GROUP, group.getId(), principal.userId()).orElse(null));
    }

    private Map<Long, ImMembershipPo> membershipMap(ImSurfaceType surfaceType, List<Long> surfaceIds,
                                                   ImPrincipal principal) {
        return membershipService.findCallerMemberships(
                surfaceType, surfaceIds, principal == null ? null : principal.userId());
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private String requireText(String value, String code) {
        if (!StringUtils.hasText(value)) {
            throw new ImException(code);
        }
        return value.trim();
    }

    private String optionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ImJoinPolicy joinPolicy(String value) {
        String policy = requireText(value, "IM_JOIN_POLICY_REQUIRED");
        try {
            return ImJoinPolicy.valueOf(policy.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ImException("IM_JOIN_POLICY_INVALID", policy);
        }
    }

    private String metadata(String metadataJson) {
        return StringUtils.hasText(metadataJson) ? metadataJson : "{}";
    }

    private Optional<ConversationListItem> conversationListItem(ImConversationMemberPo member,
                                                                String contextType,
                                                                Long contextId) {
        return conversationRepository.findByIdAndDeletedFalse(member.getConversationId())
                .filter(conversation -> ImConversationStatus.ACTIVE.equals(conversation.getStatus()))
                .filter(conversation -> matchesContext(conversation, contextType, contextId))
                .map(conversation -> new ConversationListItem(
                        conversation, lastMessage(conversation), unreadCount(conversation, member)));
    }

    private boolean matchesContext(ImConversationPo conversation, String contextType, Long contextId) {
        if (contextType != null && !contextType.equals(conversation.getContextType())) {
            return false;
        }
        return contextId == null || contextId.equals(conversation.getContextId());
    }

    private long unreadCount(ImConversationPo conversation, ImConversationMemberPo member) {
        long conversationSeq = conversation.getMessageSeq() == null ? 0L : conversation.getMessageSeq();
        long lastReadSeq = member.getLastReadSeq() == null ? 0L : member.getLastReadSeq();
        return Math.max(0L, conversationSeq - lastReadSeq);
    }

    private ImMessagePo lastMessage(ImConversationPo conversation) {
        if (conversation.getLastMessageId() == null) {
            return null;
        }
        return messageRepository.findByIdAndDeletedFalse(conversation.getLastMessageId())
                .filter(message -> conversation.getId().equals(message.getConversationId()))
                .orElse(null);
    }

    private record GroupContext(Long channelId, String contextType, Long contextId) {
    }

    private record ConversationListItem(ImConversationPo conversation, ImMessagePo lastMessage, Long unreadCount) {
    }
}
