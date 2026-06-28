package top.egon.mario.clocktower.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatReadStateResponse;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.dto.ClocktowerGameConversationResponse;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImAccessContext;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMessageRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ClocktowerImAdapter {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MEMBER_TYPE_OWNER = "OWNER";
    private static final String MEMBER_TYPE_MEMBER = "MEMBER";
    private static final String MEMBER_TYPE_SPECTATOR = "SPECTATOR";
    private static final String PHASE_LOBBY = "LOBBY";
    private static final String JOIN_POLICY_OPEN = "OPEN";
    private static final String MESSAGE_TYPE_TEXT = "TEXT";
    private static final String SURFACE_TYPE_GROUP = "GROUP";
    private static final String PRIVATE_GROUP_PREFIX = ClocktowerChatConstants.GROUP_PRIVATE + ":";

    private final ObjectProvider<RoomFacade> roomFacadeProvider;
    private final ObjectProvider<ImFacade> imFacadeProvider;
    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final ImMessageRepository messageRepository;
    private final ImConversationRepository conversationRepository;
    private final ImConversationMemberRepository memberRepository;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerRoomProfileRepository profileRepository;
    private final RoomSpaceRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ClocktowerChatPolicy policy;

    @Transactional
    public Long ensureRoomPublicConversation(Long roomId, Long participantUserId) {
        requireId(roomId, "CLOCKTOWER_ROOM_ID_REQUIRED");
        ImPrincipal principal = imPrincipal(participantUserId);
        ChannelView channel = roomFacade().createChannel(new CreateChannelCommand(principal,
                ClocktowerChatConstants.CONTEXT_TYPE, roomId, ClocktowerChatConstants.CHANNEL_ROOM,
                ClocktowerChatConstants.CHANNEL_ROOM, JOIN_POLICY_OPEN, "{}"));
        GroupView group = roomFacade().createGroup(new CreateGroupCommand(principal, channel.id(),
                ClocktowerChatConstants.CONTEXT_TYPE, roomId, ClocktowerChatConstants.GROUP_PUBLIC,
                ClocktowerChatConstants.GROUP_PUBLIC, JOIN_POLICY_OPEN, "{}"));
        joinGroup(group.id(), participantUserId);
        return group.conversationId();
    }

    @Transactional(readOnly = true)
    public Long roomPublicConversationId(Long roomId) {
        if (roomId == null) {
            return null;
        }
        return channelRepository.findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse(
                        ClocktowerChatConstants.CONTEXT_TYPE, roomId, ClocktowerChatConstants.CHANNEL_ROOM)
                .flatMap(channel -> groupRepository.findByChannelIdAndGroupKeyAndDeletedFalse(
                        channel.getId(), ClocktowerChatConstants.GROUP_PUBLIC))
                .map(ImGroupPo::getConversationId)
                .orElse(null);
    }

    @Transactional
    public List<ClocktowerGameConversationResponse> activateGameConversations(Long gameId, Long ownerUserId,
                                                                              Collection<Long> playerUserIds) {
        requireId(gameId, "CLOCKTOWER_GAME_ID_REQUIRED");
        ImPrincipal owner = imPrincipal(ownerUserId);
        ChannelView channel = roomFacade().createChannel(new CreateChannelCommand(owner,
                ClocktowerChatConstants.CONTEXT_TYPE, gameId, ClocktowerChatConstants.CHANNEL_GAME,
                ClocktowerChatConstants.CHANNEL_GAME, JOIN_POLICY_OPEN, "{}"));
        return List.of(
                ensureGameConversation(channel.id(), gameId, owner, ClocktowerChatConstants.GROUP_PUBLIC,
                        ClocktowerChatConstants.CONVERSATION_PUBLIC, playerUserIds),
                ensureGameConversation(channel.id(), gameId, owner, ClocktowerChatConstants.GROUP_PRIVATE,
                        ClocktowerChatConstants.CONVERSATION_PRIVATE_CONTAINER, List.of()),
                ensureGameConversation(channel.id(), gameId, owner, ClocktowerChatConstants.GROUP_SPECTATOR,
                        ClocktowerChatConstants.CONVERSATION_SPECTATOR, List.of()),
                ensureGameConversation(channel.id(), gameId, owner, ClocktowerChatConstants.GROUP_SYSTEM,
                        ClocktowerChatConstants.CONVERSATION_SYSTEM, List.of())
        );
    }

    @Transactional
    public ClocktowerChatConversationResponse privateConversation(ClocktowerGamePo game, Long requesterUserId,
                                                                  Long targetUserId) {
        if (game == null || game.getId() == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND");
        }
        String privateGroupKey = privateGroupKey(requesterUserId, targetUserId);
        ImPrincipal requester = imPrincipal(requesterUserId);
        ChannelView channel = roomFacade().createChannel(new CreateChannelCommand(requester,
                ClocktowerChatConstants.CONTEXT_TYPE, game.getId(), ClocktowerChatConstants.CHANNEL_GAME,
                ClocktowerChatConstants.CHANNEL_GAME, JOIN_POLICY_OPEN, "{}"));
        GroupView group = roomFacade().createGroup(new CreateGroupCommand(requester, channel.id(),
                ClocktowerChatConstants.CONTEXT_TYPE, game.getId(), privateGroupKey,
                ClocktowerChatConstants.GROUP_PRIVATE, JOIN_POLICY_OPEN, "{}"));
        joinGroup(group.id(), requesterUserId);
        joinGroup(group.id(), targetUserId);
        return resolve(group.conversationId())
                .map(this::toConversationResponse)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_CHAT_CONVERSATION_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> roomConversations(Long roomId, RbacPrincipal principal,
                                                                      ClocktowerChatViewerMode overrideMode) {
        requireId(roomId, "CLOCKTOWER_ROOM_ID_REQUIRED");
        Long userId = requireUserId(principal);
        return channelRepository.findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse(
                        ClocktowerChatConstants.CONTEXT_TYPE, roomId, ClocktowerChatConstants.CHANNEL_ROOM)
                .stream()
                .flatMap(channel -> groupRepository.findActiveByChannelId(channel.getId()).stream())
                .map(ImGroupPo::getConversationId)
                .map(this::resolve)
                .flatMap(Optional::stream)
                .filter(surface -> canRead(surface, userId, overrideMode))
                .sorted(Comparator.comparing(surface -> surface.group().getId()))
                .map(this::toConversationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> gameConversations(Long gameId, RbacPrincipal principal,
                                                                      ClocktowerChatViewerMode overrideMode) {
        requireId(gameId, "CLOCKTOWER_GAME_ID_REQUIRED");
        Long userId = requireUserId(principal);
        return channelRepository.findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse(
                        ClocktowerChatConstants.CONTEXT_TYPE, gameId, ClocktowerChatConstants.CHANNEL_GAME)
                .stream()
                .flatMap(channel -> groupRepository.findActiveByChannelId(channel.getId()).stream())
                .map(ImGroupPo::getConversationId)
                .map(this::resolve)
                .flatMap(Optional::stream)
                .filter(surface -> canRead(surface, userId, overrideMode))
                .sorted(Comparator.comparing(surface -> surface.group().getId()))
                .map(this::toConversationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ClocktowerChatAccessContext> accessContext(ImAccessContext context) {
        if (context == null || !ClocktowerChatConstants.CONTEXT_TYPE.equals(context.contextType())) {
            return Optional.empty();
        }
        return resolve(context.surfaceType(), context.surfaceId(), null)
                .map(surface -> accessContext(surface, context.principal().userId(), context.activeMembership(),
                        null));
    }

    @Transactional(readOnly = true)
    public Page<ClocktowerChatMessageResponse> history(Long conversationId, Pageable pageable,
                                                       RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        return imFacade().history(new HistoryQuery(imPrincipal(checkedPrincipal), conversationId,
                        pageable.getPageNumber(), pageable.getPageSize(), null, null))
                .map(this::toMessageResponse);
    }

    @Transactional
    public ClocktowerChatMessageResponse sendMessage(Long conversationId, String content, String metadataJson,
                                                     RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        ensureSpectatorConversationMemberIfAllowed(conversationId, checkedPrincipal.userId(), true);
        MessageView message = imFacade().send(new SendMessageCommand(imPrincipal(checkedPrincipal), conversationId,
                null, MESSAGE_TYPE_TEXT, content, "{}", metadata(metadataJson)));
        return toMessageResponse(message);
    }

    @Transactional
    public ClocktowerChatReadStateResponse markRead(Long conversationId, Long messageSeq, RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        ensureSpectatorConversationMemberIfAllowed(conversationId, checkedPrincipal.userId(), false);
        UnreadView unread = imFacade().markRead(new MarkReadCommand(
                imPrincipal(checkedPrincipal), conversationId, messageSeq));
        return new ClocktowerChatReadStateResponse(null, unread.conversationId(), unread.userId(),
                unread.lastReadSeq(), null);
    }

    @Transactional(readOnly = true)
    public Page<ClocktowerChatMessageResponse> auditMessages(Long conversationId, Pageable pageable) {
        if (pageable == null) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_PAGE_REQUIRED");
        }
        resolve(conversationId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_CHAT_CONVERSATION_NOT_FOUND"));
        return messageRepository.findByConversationIdAndDeletedFalseOrderByMessageSeqAsc(conversationId, pageable)
                .map(this::toMessageResponse);
    }

    private ClocktowerGameConversationResponse ensureGameConversation(Long channelId, Long gameId,
                                                                      ImPrincipal owner, String groupKey,
                                                                      String conversationType,
                                                                      Collection<Long> participantUserIds) {
        GroupView group = roomFacade().createGroup(new CreateGroupCommand(owner, channelId,
                ClocktowerChatConstants.CONTEXT_TYPE, gameId, groupKey, groupKey, JOIN_POLICY_OPEN, "{}"));
        if (participantUserIds != null) {
            participantUserIds.stream()
                    .filter(userId -> userId != null)
                    .distinct()
                    .forEach(userId -> joinGroup(group.id(), userId));
        }
        return new ClocktowerGameConversationResponse(groupKey, conversationType, group.conversationId());
    }

    private boolean canRead(ConversationSurface surface, Long userId, ClocktowerChatViewerMode overrideMode) {
        return policy.canRead(accessContext(surface, userId, activeConversationMember(surface.conversation().getId(),
                userId), overrideMode));
    }

    private void ensureSpectatorConversationMemberIfAllowed(Long conversationId, Long userId, boolean send) {
        if (conversationId == null || userId == null) {
            return;
        }
        resolve(conversationId).ifPresent(surface -> {
            if (!ClocktowerChatConstants.GROUP_SPECTATOR.equals(surface.semanticGroupKey())
                    || !ClocktowerChatConstants.CONVERSATION_SPECTATOR.equals(surface.semanticConversationType())) {
                return;
            }
            boolean activeMember = activeConversationMember(conversationId, userId);
            ClocktowerChatAccessContext accessContext = accessContext(surface, userId, activeMember, null);
            if (accessContext.viewerMode() != ClocktowerChatViewerMode.SPECTATOR || activeMember) {
                return;
            }
            boolean allowed = send ? policy.canSend(accessContext) : policy.canRead(accessContext);
            if (allowed) {
                joinGroup(surface.group().getId(), userId);
            }
        });
    }

    private Optional<ConversationSurface> resolve(Long conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return conversationRepository.findByIdAndDeletedFalse(conversationId)
                .flatMap(conversation -> resolve(conversation.getOwnerSurfaceType(),
                        conversation.getOwnerSurfaceId(), conversation));
    }

    private Optional<ConversationSurface> resolve(ImSurfaceType surfaceType, Long surfaceId,
                                                  ImConversationPo knownConversation) {
        if (surfaceType != ImSurfaceType.GROUP || surfaceId == null) {
            return Optional.empty();
        }
        Optional<ImGroupPo> group = groupRepository.findByIdAndDeletedFalse(surfaceId)
                .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()));
        if (group.isEmpty() || group.get().getChannelId() == null) {
            return Optional.empty();
        }
        Optional<ImChannelPo> channel = channelRepository.findByIdAndDeletedFalse(group.get().getChannelId())
                .filter(candidate -> ImSurfaceStatus.ACTIVE.equals(candidate.getStatus()));
        if (channel.isEmpty() || !ClocktowerChatConstants.CONTEXT_TYPE.equals(channel.get().getContextType())) {
            return Optional.empty();
        }
        ImConversationPo conversation = knownConversation == null
                ? conversationRepository.findByOwnerSurfaceTypeAndOwnerSurfaceIdAndConversationTypeAndDeletedFalse(
                        ImSurfaceType.GROUP, group.get().getId(), ImConversationType.GROUP).orElse(null)
                : knownConversation;
        if (conversation == null || !ImConversationStatus.ACTIVE.equals(conversation.getStatus())) {
            return Optional.empty();
        }
        if (!ClocktowerChatConstants.CONTEXT_TYPE.equals(conversation.getContextType())) {
            return Optional.empty();
        }
        String semanticGroupKey = semanticGroupKey(group.get().getGroupKey());
        String semanticConversationType = semanticConversationType(channel.get().getChannelKey(),
                group.get().getGroupKey());
        if (semanticConversationType == null) {
            return Optional.empty();
        }
        ClocktowerGamePo game = ClocktowerChatConstants.CHANNEL_GAME.equals(channel.get().getChannelKey())
                ? gameRepository.findByIdAndDeletedFalse(channel.get().getContextId()).orElse(null)
                : null;
        Long roomId = game == null ? channel.get().getContextId() : game.getRoomId();
        String participantKey = participantKey(channel.get(), group.get(), semanticConversationType);
        return Optional.of(new ConversationSurface(conversation, channel.get(), group.get(), semanticGroupKey,
                semanticConversationType, participantKey, roomId, game));
    }

    private ClocktowerChatAccessContext accessContext(ConversationSurface surface, Long userId,
                                                      boolean activeConversationMember,
                                                      ClocktowerChatViewerMode overrideMode) {
        ClocktowerChatViewerMode mode = overrideMode == null ? viewerMode(surface, userId) : overrideMode;
        ClocktowerGamePo game = surface.game();
        return new ClocktowerChatAccessContext(mode, surface.semanticGroupKey(), surface.semanticConversationType(),
                game == null ? PHASE_LOBBY : game.getPhase(), game == null ? 0 : game.getDayNo(),
                activeConversationMember);
    }

    private ClocktowerChatViewerMode viewerMode(ConversationSurface surface, Long userId) {
        if (userId == null || surface == null || surface.roomId() == null) {
            return ClocktowerChatViewerMode.UNKNOWN;
        }
        RoomSpacePo room = roomRepository.findByIdAndDeletedFalse(surface.roomId()).orElse(null);
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomIdAndDeletedFalse(surface.roomId()).orElse(null);
        if (isStoryteller(room, profile, userId)) {
            return ClocktowerChatViewerMode.STORYTELLER;
        }
        if (surface.game() != null && activeGamePlayer(surface.game().getId(), userId)) {
            return ClocktowerChatViewerMode.PLAYER;
        }
        Optional<RoomMemberPo> member = roomMemberRepository.findActiveByRoomIdAndUserId(surface.roomId(), userId);
        if (member.isEmpty()) {
            return ClocktowerChatViewerMode.UNKNOWN;
        }
        String memberType = member.get().getMemberType();
        if (MEMBER_TYPE_SPECTATOR.equals(memberType)) {
            return ClocktowerChatViewerMode.SPECTATOR;
        }
        if (MEMBER_TYPE_MEMBER.equals(memberType) || MEMBER_TYPE_OWNER.equals(memberType)) {
            return ClocktowerChatViewerMode.PLAYER;
        }
        return ClocktowerChatViewerMode.UNKNOWN;
    }

    private boolean isStoryteller(RoomSpacePo room, ClocktowerRoomProfilePo profile, Long userId) {
        return (room != null && userId.equals(room.getOwnerUserId()))
                || (profile != null && userId.equals(profile.getStorytellerUserId()));
    }

    private boolean activeGamePlayer(Long gameId, Long userId) {
        return gameSeatRepository.findByGameIdAndUserIdAndDeletedFalse(gameId, userId)
                .filter(seat -> STATUS_ACTIVE.equals(seat.getStatus()))
                .map(ClocktowerGameSeatPo::getUserId)
                .isPresent();
    }

    private boolean activeConversationMember(Long conversationId, Long userId) {
        return conversationId != null && userId != null
                && memberRepository.existsByConversationIdAndUserIdAndStatusAndDeletedFalse(
                conversationId, userId, ImMembershipStatus.ACTIVE);
    }

    private ClocktowerChatConversationResponse toConversationResponse(ConversationSurface surface) {
        ImConversationPo conversation = surface.conversation();
        return new ClocktowerChatConversationResponse(conversation.getId(), surface.roomId(), surface.gameId(),
                surface.channel().getChannelKey(), surface.semanticGroupKey(), surface.semanticConversationType(),
                surface.participantKey(), conversation.getMessageSeq(), conversation.getLastMessageAt());
    }

    private ClocktowerChatMessageResponse toMessageResponse(MessageView message) {
        return new ClocktowerChatMessageResponse(message.id(), message.conversationId(), message.senderUserId(),
                message.messageSeq(), message.messageType(), message.content(), message.sentAt());
    }

    private ClocktowerChatMessageResponse toMessageResponse(ImMessagePo message) {
        return new ClocktowerChatMessageResponse(message.getId(), message.getConversationId(),
                message.getSenderUserId(), message.getMessageSeq(), message.getMessageType(), message.getContent(),
                message.getSentAt());
    }

    private void joinGroup(Long groupId, Long userId) {
        if (groupId == null || userId == null) {
            return;
        }
        roomFacade().applyJoin(new JoinCommand(imPrincipal(userId), SURFACE_TYPE_GROUP, groupId, null));
    }

    private String semanticGroupKey(String groupKey) {
        return groupKey != null && groupKey.startsWith(PRIVATE_GROUP_PREFIX)
                ? ClocktowerChatConstants.GROUP_PRIVATE
                : groupKey;
    }

    private String semanticConversationType(String channelKey, String groupKey) {
        if (ClocktowerChatConstants.CHANNEL_ROOM.equals(channelKey)
                && ClocktowerChatConstants.GROUP_PUBLIC.equals(groupKey)) {
            return ClocktowerChatConstants.CONVERSATION_ROOM;
        }
        if (groupKey != null && groupKey.startsWith(PRIVATE_GROUP_PREFIX)) {
            return ClocktowerChatConstants.CONVERSATION_PRIVATE;
        }
        if (ClocktowerChatConstants.GROUP_PRIVATE.equals(groupKey)) {
            return ClocktowerChatConstants.CONVERSATION_PRIVATE_CONTAINER;
        }
        if (ClocktowerChatConstants.GROUP_PUBLIC.equals(groupKey)) {
            return ClocktowerChatConstants.CONVERSATION_PUBLIC;
        }
        if (ClocktowerChatConstants.GROUP_SPECTATOR.equals(groupKey)) {
            return ClocktowerChatConstants.CONVERSATION_SPECTATOR;
        }
        if (ClocktowerChatConstants.GROUP_SYSTEM.equals(groupKey)) {
            return ClocktowerChatConstants.CONVERSATION_SYSTEM;
        }
        return null;
    }

    private String participantKey(ImChannelPo channel, ImGroupPo group, String semanticConversationType) {
        if (ClocktowerChatConstants.CONVERSATION_PRIVATE.equals(semanticConversationType)) {
            return group.getGroupKey().substring(PRIVATE_GROUP_PREFIX.length());
        }
        String scopeType = ClocktowerChatConstants.CHANNEL_ROOM.equals(channel.getChannelKey())
                ? ClocktowerChatConstants.SCOPE_ROOM
                : ClocktowerChatConstants.SCOPE_GAME;
        return scopeType + ":" + channel.getContextId();
    }

    private String privateGroupKey(Long firstUserId, Long secondUserId) {
        requireId(firstUserId, "CLOCKTOWER_CHAT_PLAYER_REQUIRED");
        requireId(secondUserId, "CLOCKTOWER_CHAT_PLAYER_REQUIRED");
        long lo = Math.min(firstUserId, secondUserId);
        long hi = Math.max(firstUserId, secondUserId);
        return PRIVATE_GROUP_PREFIX + lo + ":" + hi;
    }

    private String metadata(String metadataJson) {
        return StringUtils.hasText(metadataJson) ? metadataJson : "{}";
    }

    private Long requireUserId(RbacPrincipal principal) {
        return requirePrincipal(principal).userId();
    }

    private RbacPrincipal requirePrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ClocktowerException("CLOCKTOWER_AUTH_REQUIRED");
        }
        return principal;
    }

    private void requireId(Long id, String code) {
        if (id == null) {
            throw new ClocktowerException(code);
        }
    }

    private ImPrincipal imPrincipal(RbacPrincipal principal) {
        return new ImPrincipal(principal.userId(), safeRoles(principal.roleCodes()),
                ClocktowerChatConstants.CONTEXT_TYPE, Map.of());
    }

    private ImPrincipal imPrincipal(Long userId) {
        requireId(userId, "CLOCKTOWER_AUTH_REQUIRED");
        return new ImPrincipal(userId, Set.of(), ClocktowerChatConstants.CONTEXT_TYPE, Map.of());
    }

    private Set<String> safeRoles(Set<String> roleCodes) {
        return roleCodes == null ? Set.of() : roleCodes;
    }

    private RoomFacade roomFacade() {
        return roomFacadeProvider.getObject();
    }

    private ImFacade imFacade() {
        return imFacadeProvider.getObject();
    }

    private record ConversationSurface(ImConversationPo conversation, ImChannelPo channel, ImGroupPo group,
                                       String semanticGroupKey, String semanticConversationType,
                                       String participantKey, Long roomId, ClocktowerGamePo game) {

        Long gameId() {
            return game == null ? null : game.getId();
        }
    }
}
