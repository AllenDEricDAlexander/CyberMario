package top.egon.mario.im.platform;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.FriendFacade;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.mapper.ImFacadeMapper;
import top.egon.mario.im.platform.dto.PlatformBootstrapView;
import top.egon.mario.im.platform.dto.PlatformConversationView;
import top.egon.mario.im.platform.dto.PlatformUserView;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImDmPairPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImDmPairRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.repository.ImMessageRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composes generic IM capabilities into the platform Web contract and enforces friendship-gated DMs.
 */
@Component
public class PlatformImFacade {

    private static final String DISPLAY_CHANNEL = "CHANNEL";
    private static final String DISPLAY_GROUP = "GROUP";
    private static final String DISPLAY_DM = "DM";

    private final ImFacade imFacade;
    private final DmFacade dmFacade;
    private final FriendFacade friendFacade;
    private final PlatformRoomFacade platformRoomFacade;
    private final RbacUserDirectoryFacade userDirectoryFacade;
    private final ImConversationRepository conversationRepository;
    private final ImDmPairRepository dmPairRepository;
    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final ImMembershipRepository membershipRepository;
    private final ImMessageRepository messageRepository;
    private final ImFacadeMapper mapper = new ImFacadeMapper();

    public PlatformImFacade(ImFacade imFacade,
                            DmFacade dmFacade,
                            FriendFacade friendFacade,
                            PlatformRoomFacade platformRoomFacade,
                            RbacUserDirectoryFacade userDirectoryFacade,
                            ImConversationRepository conversationRepository,
                            ImDmPairRepository dmPairRepository,
                            ImChannelRepository channelRepository,
                            ImGroupRepository groupRepository,
                            ImMembershipRepository membershipRepository,
                            ImMessageRepository messageRepository) {
        this.imFacade = imFacade;
        this.dmFacade = dmFacade;
        this.friendFacade = friendFacade;
        this.platformRoomFacade = platformRoomFacade;
        this.userDirectoryFacade = userDirectoryFacade;
        this.conversationRepository = conversationRepository;
        this.dmPairRepository = dmPairRepository;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.messageRepository = messageRepository;
    }

    public ConversationView openDm(OpenDmCommand command) {
        if (command == null) {
            throw new ImException("IM_DM_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        if (command.targetUserId() == null) {
            throw new ImException("IM_DM_TARGET_REQUIRED");
        }
        if (principal.userId().equals(command.targetUserId())) {
            throw new ImException("IM_DM_SELF_DENIED");
        }
        if (!friendFacade.areActiveFriends(principal.userId(), command.targetUserId())) {
            throw new ImException("IM_DM_FRIENDSHIP_REQUIRED");
        }
        return dmFacade.openDm(new OpenDmCommand(platformPrincipal(principal), command.targetUserId()));
    }

    public MessageView send(SendMessageCommand command) {
        if (command == null) {
            return imFacade.send(null);
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        if (command.conversationId() == null) {
            return imFacade.send(command);
        }
        ImConversationPo conversation = conversationRepository.findByIdAndDeletedFalse(command.conversationId())
                .orElseThrow(() -> new ImException("IM_CONVERSATION_NOT_FOUND"));
        if (ImConversationType.DM.equals(conversation.getConversationTypeEnum())) {
            ImDmPairPo pair = dmPairRepository.findByIdAndDeletedFalse(conversation.getOwnerSurfaceId())
                    .orElseThrow(() -> new ImException("IM_DM_PAIR_NOT_FOUND"));
            Long peerUserId = peerUserId(pair, principal.userId());
            if (!friendFacade.areActiveFriends(principal.userId(), peerUserId)) {
                throw new ImException("IM_DM_FRIENDSHIP_REQUIRED");
            }
        }
        return imFacade.send(command);
    }

    @Transactional(readOnly = true)
    public PlatformBootstrapView bootstrap(ImPrincipal principal) {
        ImPrincipal caller = requirePrincipal(principal);
        PlatformUserView currentUser = userDirectoryFacade.findEnabledById(caller.userId())
                .map(this::userView)
                .orElseThrow(() -> new ImException("IM_PLATFORM_USER_NOT_FOUND"));
        List<PlatformConversationView> conversations = listConversations(caller);
        long unreadTotal = conversations.stream()
                .map(PlatformConversationView::unreadCount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        return new PlatformBootstrapView(
                currentUser,
                conversations,
                unreadTotal,
                friendFacade.countIncomingRequests(caller)
        );
    }

    @Transactional(readOnly = true)
    public List<PlatformConversationView> listConversations(ImPrincipal principal) {
        ImPrincipal caller = requirePrincipal(principal);
        List<ConversationView> coreViews = imFacade.listConversations(new ListConversationsQuery(
                caller, PlatformRoomFacade.PLATFORM_CONTEXT_TYPE, null));
        List<ChannelView> channels = platformRoomFacade.listChannels(caller);

        Map<Long, ConversationSeed> seeds = new LinkedHashMap<>();
        coreViews.forEach(view -> seeds.put(view.id(), ConversationSeed.from(view)));
        Set<Long> missingChannelConversationIds = channels.stream()
                .map(ChannelView::mainConversationId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !seeds.containsKey(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        conversationRepository.findAllById(missingChannelConversationIds).stream()
                .filter(conversation -> !conversation.isDeleted())
                .filter(conversation -> ImConversationStatus.ACTIVE.equals(conversation.getStatus()))
                .map(ConversationSeed::from)
                .forEach(seed -> seeds.put(seed.conversationId(), seed));

        SurfaceRows surfaces = surfaceRows(seeds.values());
        Map<Long, ImMembershipPo> channelMemberships = memberships(
                ImSurfaceType.CHANNEL, surfaces.channels().keySet(), caller.userId());
        Map<Long, ImMembershipPo> groupMemberships = memberships(
                ImSurfaceType.GROUP, surfaces.groups().keySet(), caller.userId());
        Map<Long, MessageView> lastMessages = lastMessages(seeds.values());

        Set<Long> userIds = new LinkedHashSet<>();
        Map<Long, Long> dmPeers = new LinkedHashMap<>();
        seeds.values().forEach(seed -> {
            if (ImSurfaceType.DM_PAIR.name().equals(seed.ownerSurfaceType())) {
                ImDmPairPo pair = surfaces.dmPairs().get(seed.ownerSurfaceId());
                if (pair != null) {
                    Long peerUserId = peerUserId(pair, caller.userId());
                    dmPeers.put(seed.conversationId(), peerUserId);
                    userIds.add(peerUserId);
                }
            }
            MessageView message = lastMessages.get(seed.lastMessageId());
            if (message != null && message.senderUserId() != null) {
                userIds.add(message.senderUserId());
            }
        });
        Map<Long, UserDirectoryItemResponse> users = userDirectoryFacade.findEnabledByIds(userIds);
        Set<Long> activeFriendUserIds = dmPeers.isEmpty()
                ? Set.of()
                : friendFacade.findActiveFriendUserIds(caller);
        Instant now = Instant.now();

        List<PlatformConversationView> views = new ArrayList<>();
        seeds.values().forEach(seed -> views.add(conversationView(
                seed,
                caller,
                surfaces,
                channelMemberships,
                groupMemberships,
                lastMessages,
                dmPeers,
                users,
                activeFriendUserIds,
                now)));
        views.sort(Comparator
                .comparing(PlatformConversationView::lastActiveAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PlatformConversationView::conversationId, Comparator.reverseOrder()));
        return List.copyOf(views);
    }

    private PlatformConversationView conversationView(
            ConversationSeed seed,
            ImPrincipal caller,
            SurfaceRows surfaces,
            Map<Long, ImMembershipPo> channelMemberships,
            Map<Long, ImMembershipPo> groupMemberships,
            Map<Long, MessageView> lastMessages,
            Map<Long, Long> dmPeers,
            Map<Long, UserDirectoryItemResponse> users,
            Set<Long> activeFriendUserIds,
            Instant now) {
        MessageView lastMessage = lastMessages.get(seed.lastMessageId());
        PlatformUserView lastMessageSender = lastMessage == null
                ? null
                : userView(users.get(lastMessage.senderUserId()));
        if (ImSurfaceType.DM_PAIR.name().equals(seed.ownerSurfaceType())) {
            Long peerUserId = dmPeers.get(seed.conversationId());
            UserDirectoryItemResponse peer = users.get(peerUserId);
            ImDmPairPo pair = surfaces.dmPairs().get(seed.ownerSurfaceId());
            boolean participant = pair != null && participant(pair, caller.userId());
            return platformView(
                    seed,
                    DISPLAY_DM,
                    peer == null ? "用户 " + peerUserId : peer.displayName(),
                    peer == null ? null : peer.avatarUrl(),
                    peerUserId,
                    null,
                    null,
                    "ACTIVE",
                    null,
                    participant,
                    participant && activeFriendUserIds.contains(peerUserId)
                            && !Boolean.TRUE.equals(pair.getFrozen()),
                    lastMessage,
                    lastMessageSender
            );
        }
        if (ImSurfaceType.CHANNEL.name().equals(seed.ownerSurfaceType())) {
            ImChannelPo channel = surfaces.channels().get(seed.ownerSurfaceId());
            ImMembershipPo membership = channelMemberships.get(seed.ownerSurfaceId());
            boolean activeSurface = channel != null && ImSurfaceStatus.ACTIVE.equals(channel.getStatus());
            return platformView(
                    seed,
                    DISPLAY_CHANNEL,
                    surfaceTitle(channel == null ? null : channel.getName(),
                            channel == null ? null : channel.getChannelKey(), "频道 " + seed.ownerSurfaceId()),
                    null,
                    null,
                    channel == null ? null : channel.getChannelKey(),
                    null,
                    status(membership),
                    role(membership),
                    activeSurface,
                    activeSurface && canPost(membership, now),
                    lastMessage,
                    lastMessageSender
            );
        }
        ImGroupPo group = surfaces.groups().get(seed.ownerSurfaceId());
        ImMembershipPo membership = groupMemberships.get(seed.ownerSurfaceId());
        boolean activeSurface = group != null && ImSurfaceStatus.ACTIVE.equals(group.getStatus());
        return platformView(
                seed,
                DISPLAY_GROUP,
                surfaceTitle(group == null ? null : group.getName(),
                        group == null ? null : group.getGroupKey(), "群组 " + seed.ownerSurfaceId()),
                null,
                null,
                group == null ? null : group.getGroupKey(),
                group == null ? null : group.getChannelId(),
                status(membership),
                role(membership),
                activeSurface && activeMembership(membership),
                activeSurface && canPost(membership, now),
                lastMessage,
                lastMessageSender
        );
    }

    private PlatformConversationView platformView(
            ConversationSeed seed,
            String displayType,
            String title,
            String avatarUrl,
            Long peerUserId,
            String surfaceKey,
            Long channelId,
            String membershipStatus,
            String memberRole,
            boolean canRead,
            boolean canPost,
            MessageView lastMessage,
            PlatformUserView lastMessageSender) {
        return new PlatformConversationView(
                seed.conversationId(),
                seed.conversationType(),
                displayType,
                title,
                avatarUrl,
                peerUserId,
                seed.ownerSurfaceType(),
                seed.ownerSurfaceId(),
                surfaceKey,
                channelId,
                membershipStatus,
                memberRole,
                canRead,
                canPost,
                seed.messageSeq(),
                seed.lastMessageId(),
                seed.lastMessageAt(),
                lastMessage,
                lastMessageSender,
                seed.lastActiveAt(),
                seed.status(),
                seed.unreadCount()
        );
    }

    private SurfaceRows surfaceRows(Collection<ConversationSeed> seeds) {
        Set<Long> dmPairIds = surfaceIds(seeds, ImSurfaceType.DM_PAIR);
        Set<Long> channelIds = surfaceIds(seeds, ImSurfaceType.CHANNEL);
        Set<Long> groupIds = surfaceIds(seeds, ImSurfaceType.GROUP);
        return new SurfaceRows(
                index(dmPairRepository.findAllById(dmPairIds), ImDmPairPo::getId),
                index(channelRepository.findAllById(channelIds), ImChannelPo::getId),
                index(groupRepository.findAllById(groupIds), ImGroupPo::getId)
        );
    }

    private Set<Long> surfaceIds(Collection<ConversationSeed> seeds, ImSurfaceType surfaceType) {
        return seeds.stream()
                .filter(seed -> surfaceType.name().equals(seed.ownerSurfaceType()))
                .map(ConversationSeed::ownerSurfaceId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, ImMembershipPo> memberships(ImSurfaceType surfaceType,
                                                  Collection<Long> surfaceIds,
                                                  Long userId) {
        if (surfaceIds.isEmpty()) {
            return Map.of();
        }
        return membershipRepository.findBySurfaceTypeAndSurfaceIdInAndUserIdAndDeletedFalse(
                        surfaceType, surfaceIds, userId).stream()
                .collect(Collectors.toMap(ImMembershipPo::getSurfaceId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private Map<Long, MessageView> lastMessages(Collection<ConversationSeed> seeds) {
        Map<Long, MessageView> messages = new LinkedHashMap<>();
        seeds.stream()
                .map(ConversationSeed::lastMessage)
                .filter(java.util.Objects::nonNull)
                .forEach(message -> messages.put(message.id(), message));
        Set<Long> missingIds = seeds.stream()
                .map(ConversationSeed::lastMessageId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !messages.containsKey(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        messageRepository.findAllById(missingIds).stream()
                .filter(message -> !message.isDeleted())
                .map(mapper::toMessageView)
                .forEach(message -> messages.put(message.id(), message));
        return messages;
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private ImPrincipal platformPrincipal(ImPrincipal principal) {
        return new ImPrincipal(
                principal.userId(),
                principal.roleCodes(),
                PlatformRoomFacade.PLATFORM_CONTEXT_TYPE,
                principal.attributes());
    }

    private Long peerUserId(ImDmPairPo pair, Long userId) {
        if (userId.equals(pair.getUserLoId())) {
            return pair.getUserHiId();
        }
        if (userId.equals(pair.getUserHiId())) {
            return pair.getUserLoId();
        }
        throw new ImException("IM_SEND_DENIED");
    }

    private boolean participant(ImDmPairPo pair, Long userId) {
        return userId.equals(pair.getUserLoId()) || userId.equals(pair.getUserHiId());
    }

    private boolean activeMembership(ImMembershipPo membership) {
        return membership != null
                && ImMembershipStatus.ACTIVE.equals(membership.getStatus());
    }

    private boolean canPost(ImMembershipPo membership, Instant now) {
        return activeMembership(membership)
                && (membership.getMutedUntil() == null || !membership.getMutedUntil().isAfter(now));
    }

    private String status(ImMembershipPo membership) {
        return membership == null || membership.getStatus() == null ? null : membership.getStatus().name();
    }

    private String role(ImMembershipPo membership) {
        return membership == null || membership.getMemberRole() == null ? null : membership.getMemberRole().name();
    }

    private String surfaceTitle(String name, String key, String fallback) {
        if (org.springframework.util.StringUtils.hasText(name)) {
            return name.trim();
        }
        return org.springframework.util.StringUtils.hasText(key) ? key.trim() : fallback;
    }

    private PlatformUserView userView(UserDirectoryItemResponse user) {
        return user == null
                ? null
                : new PlatformUserView(user.userId(), user.accountNo(), user.displayName(), user.avatarUrl());
    }

    private <T> Map<Long, T> index(Iterable<T> values, Function<T, Long> idExtractor) {
        Map<Long, T> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(idExtractor.apply(value), value));
        return result;
    }

    private record SurfaceRows(
            Map<Long, ImDmPairPo> dmPairs,
            Map<Long, ImChannelPo> channels,
            Map<Long, ImGroupPo> groups) {
    }

    private record ConversationSeed(
            Long conversationId,
            String conversationType,
            String ownerSurfaceType,
            Long ownerSurfaceId,
            Long messageSeq,
            Long lastMessageId,
            Instant lastMessageAt,
            MessageView lastMessage,
            Instant lastActiveAt,
            String status,
            Long unreadCount) {

        private static ConversationSeed from(ConversationView view) {
            return new ConversationSeed(
                    view.id(), view.conversationType(), view.ownerSurfaceType(), view.ownerSurfaceId(),
                    view.messageSeq(), view.lastMessageId(), view.lastMessageAt(), view.lastMessage(),
                    view.lastActiveAt(), view.status(), view.unreadCount());
        }

        private static ConversationSeed from(ImConversationPo conversation) {
            return new ConversationSeed(
                    conversation.getId(), conversation.getConversationType(),
                    conversation.getOwnerSurfaceType().name(), conversation.getOwnerSurfaceId(),
                    conversation.getMessageSeq(), conversation.getLastMessageId(), conversation.getLastMessageAt(),
                    null, conversation.getLastActiveAt(), conversation.getStatus().name(), 0L);
        }
    }
}
