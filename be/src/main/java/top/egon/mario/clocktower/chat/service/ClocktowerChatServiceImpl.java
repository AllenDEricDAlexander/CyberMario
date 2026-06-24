package top.egon.mario.clocktower.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.chat.ClocktowerChatAccessContext;
import top.egon.mario.clocktower.chat.ClocktowerChatConstants;
import top.egon.mario.clocktower.chat.ClocktowerChatConversationContext;
import top.egon.mario.clocktower.chat.ClocktowerChatConversationResolver;
import top.egon.mario.clocktower.chat.ClocktowerChatPolicy;
import top.egon.mario.clocktower.chat.ClocktowerChatViewerMode;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMarkReadRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatPrivateConversationRequest;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatReadStateResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatSendMessageRequest;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameContextService;
import top.egon.mario.im.context.ImPrincipal;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImReadStatePo;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClocktowerChatServiceImpl implements ClocktowerChatService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final ClocktowerGameContextService gameContextService;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ImConversationRepository conversationRepository;
    private final ImFacade imFacade;
    private final ClocktowerChatConversationResolver resolver;
    private final ClocktowerChatPolicy policy;

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> conversations(Long roomId, RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        List<ClocktowerChatConversationResponse> visible = new ArrayList<>();
        appendReadableConversations(visible, roomConversations(roomId), checkedPrincipal.userId(), null);
        gameContextService.currentGameId(roomId)
                .ifPresent(gameId -> appendReadableConversations(visible, gameConversations(gameId),
                        checkedPrincipal.userId(), null));
        return visible;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> conversationsForGame(Long roomId, Long gameId,
                                                                         RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        requireGameId(gameId);
        List<ClocktowerChatConversationResponse> visible = new ArrayList<>();
        appendReadableConversations(visible, roomConversations(roomId), checkedPrincipal.userId(), null);
        appendReadableConversations(visible, gameConversations(gameId), checkedPrincipal.userId(), null);
        return visible;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> auditConversations(Long roomId, RbacPrincipal principal) {
        requireAdminAudit(principal);
        List<ClocktowerChatConversationResponse> visible = new ArrayList<>();
        appendReadableConversations(visible, roomConversations(roomId), principal.userId(),
                ClocktowerChatViewerMode.ADMIN_AUDIT);
        gameContextService.currentGameId(roomId)
                .ifPresent(gameId -> appendReadableConversations(visible, gameConversations(gameId),
                        principal.userId(), ClocktowerChatViewerMode.ADMIN_AUDIT));
        return visible;
    }

    @Override
    public Page<ClocktowerChatMessageResponse> messages(Long conversationId, Pageable pageable,
                                                        RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        return imFacade.history(conversationId, new ImPrincipal(checkedPrincipal.userId()), pageable)
                .map(this::toMessageResponse);
    }

    @Override
    @Transactional
    public ClocktowerChatConversationResponse privateConversation(ClocktowerChatPrivateConversationRequest request,
                                                                  RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        if (request == null || request.roomId() == null || request.targetUserId() == null) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_PRIVATE_REQUEST_INVALID");
        }
        if (checkedPrincipal.userId().equals(request.targetUserId())) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_PRIVATE_SELF_DENIED");
        }
        Long gameId = gameContextService.requireCurrentGameId(request.roomId());
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        requireActiveGamePlayer(gameId, checkedPrincipal.userId());
        requireActiveGamePlayer(gameId, request.targetUserId());
        ClocktowerChatAccessContext accessContext = new ClocktowerChatAccessContext(ClocktowerChatViewerMode.PLAYER,
                ClocktowerChatConstants.GROUP_PRIVATE, ClocktowerChatConstants.CONVERSATION_PRIVATE,
                game.getPhase(), game.getDayNo(), true);
        if (!policy.canSend(accessContext)) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_PRIVATE_DENIED");
        }

        ImChannelPo channel = imFacade.ensureChannel(ClocktowerChatConstants.CONTEXT_TYPE, gameId,
                ClocktowerChatConstants.CHANNEL_GAME);
        ImGroupPo group = imFacade.ensureGroup(channel.getId(), ClocktowerChatConstants.GROUP_PRIVATE);
        ImConversationPo conversation = imFacade.ensureConversation(group.getId(), ClocktowerChatConstants.SCOPE_GAME,
                gameId, ClocktowerChatConstants.CONVERSATION_PRIVATE,
                List.of(checkedPrincipal.userId(), request.targetUserId()));
        ClocktowerChatConversationContext context = new ClocktowerChatConversationContext(conversation,
                channel.getChannelKey(), group.getGroupKey(), game.getRoomId(), game);
        return toConversationResponse(context);
    }

    @Override
    public ClocktowerChatMessageResponse sendMessage(Long conversationId, ClocktowerChatSendMessageRequest request,
                                                     RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_CONTENT_REQUIRED");
        }
        ensureSpectatorConversationMemberIfAllowed(conversationId, checkedPrincipal.userId(), true);
        ImMessagePo message = imFacade.sendMessage(conversationId, new ImPrincipal(checkedPrincipal.userId()),
                request.content(), metadata(request.metadataJson()));
        return toMessageResponse(message);
    }

    @Override
    public ClocktowerChatReadStateResponse markRead(Long conversationId, ClocktowerChatMarkReadRequest request,
                                                    RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        if (request == null || request.messageSeq() == null) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_READ_SEQ_REQUIRED");
        }
        ensureSpectatorConversationMemberIfAllowed(conversationId, checkedPrincipal.userId(), false);
        ImReadStatePo readState = imFacade.markRead(conversationId, new ImPrincipal(checkedPrincipal.userId()),
                request.messageSeq());
        return new ClocktowerChatReadStateResponse(readState.getId(), readState.getConversationId(),
                readState.getUserId(), readState.getLastReadMessageSeq(), readState.getLastReadAt());
    }

    private List<ImConversationPo> roomConversations(Long roomId) {
        if (roomId == null) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_ID_REQUIRED");
        }
        return conversationRepository.findByContextTypeAndScopeTypeAndScopeIdAndDeletedFalseOrderByGroupIdAscIdAsc(
                ClocktowerChatConstants.CONTEXT_TYPE, ClocktowerChatConstants.SCOPE_ROOM, roomId);
    }

    private List<ImConversationPo> gameConversations(Long gameId) {
        return conversationRepository.findByContextTypeAndScopeTypeAndScopeIdAndDeletedFalseOrderByGroupIdAscIdAsc(
                ClocktowerChatConstants.CONTEXT_TYPE, ClocktowerChatConstants.SCOPE_GAME, gameId);
    }

    private void appendReadableConversations(List<ClocktowerChatConversationResponse> visible,
                                             List<ImConversationPo> conversations, Long userId,
                                             ClocktowerChatViewerMode overrideMode) {
        conversations.forEach(conversation ->
                resolver.resolve(imContext(conversation, userId))
                        .filter(context -> canRead(context, userId, overrideMode))
                        .map(this::toConversationResponse)
                        .ifPresent(visible::add));
    }

    private boolean canRead(ClocktowerChatConversationContext context, Long userId,
                            ClocktowerChatViewerMode overrideMode) {
        boolean activeMember = resolver.activeConversationMember(context.conversation().getId(), userId);
        return policy.canRead(resolver.accessContext(context, userId, activeMember, overrideMode));
    }

    private void ensureSpectatorConversationMemberIfAllowed(Long conversationId, Long userId, boolean send) {
        if (conversationId == null || userId == null) {
            return;
        }
        conversationRepository.findByIdAndDeletedFalse(conversationId)
                .flatMap(conversation -> resolver.resolve(imContext(conversation, userId)))
                .ifPresent(context -> ensureSpectatorConversationMemberIfAllowed(context, userId, send));
    }

    private void ensureSpectatorConversationMemberIfAllowed(ClocktowerChatConversationContext context, Long userId,
                                                            boolean send) {
        ImConversationPo conversation = context.conversation();
        if (!ClocktowerChatConstants.SCOPE_GAME.equals(conversation.getScopeType())
                || !ClocktowerChatConstants.GROUP_SPECTATOR.equals(context.groupKey())
                || !ClocktowerChatConstants.CONVERSATION_SPECTATOR.equals(conversation.getConversationType())) {
            return;
        }
        boolean activeMember = resolver.activeConversationMember(conversation.getId(), userId);
        ClocktowerChatAccessContext accessContext = resolver.accessContext(context, userId, activeMember, null);
        if (accessContext.viewerMode() != ClocktowerChatViewerMode.SPECTATOR) {
            return;
        }
        boolean allowed = send ? policy.canSend(accessContext) : policy.canRead(accessContext);
        if (!allowed || activeMember) {
            return;
        }
        imFacade.ensureConversation(conversation.getGroupId(), ClocktowerChatConstants.SCOPE_GAME,
                conversation.getScopeId(), ClocktowerChatConstants.CONVERSATION_SPECTATOR, List.of(userId));
    }

    private void requireActiveGamePlayer(Long gameId, Long userId) {
        gameSeatRepository.findByGameIdAndUserIdAndDeletedFalse(gameId, userId)
                .filter(seat -> STATUS_ACTIVE.equals(seat.getStatus()))
                .map(ClocktowerGameSeatPo::getId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_CHAT_PLAYER_REQUIRED"));
    }

    private RbacPrincipal requirePrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ClocktowerException("CLOCKTOWER_AUTH_REQUIRED");
        }
        return principal;
    }

    private void requireGameId(Long gameId) {
        if (gameId == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ID_REQUIRED");
        }
    }

    private void requireAdminAudit(RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        if (checkedPrincipal.roleCodes() == null || !checkedPrincipal.roleCodes().contains(ROLE_SUPER_ADMIN)) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_AUDIT_FORBIDDEN");
        }
    }

    private ClocktowerChatConversationResponse toConversationResponse(ClocktowerChatConversationContext context) {
        ImConversationPo conversation = context.conversation();
        return new ClocktowerChatConversationResponse(conversation.getId(), context.roomId(), context.gameId(),
                context.channelKey(), context.groupKey(), conversation.getConversationType(),
                conversation.getParticipantKey(), conversation.getMessageSeq(), conversation.getLastMessageAt());
    }

    private ClocktowerChatMessageResponse toMessageResponse(ImMessagePo message) {
        return new ClocktowerChatMessageResponse(message.getId(), message.getConversationId(),
                message.getSenderUserId(), message.getMessageSeq(), message.getMessageType(), message.getContent(),
                message.getSentAt());
    }

    private top.egon.mario.im.context.ImContext imContext(ImConversationPo conversation, Long userId) {
        return new top.egon.mario.im.context.ImContext(conversation.getContextType(), conversation.getContextId(),
                conversation.getChannelId(), conversation.getGroupId(), conversation.getId(),
                conversation.getScopeType(), conversation.getScopeId(), conversation.getConversationType(),
                conversation.getParticipantKey(), new ImPrincipal(userId),
                resolver.activeConversationMember(conversation.getId(), userId));
    }

    private String metadata(String metadataJson) {
        return StringUtils.hasText(metadataJson) ? metadataJson : "{}";
    }
}
