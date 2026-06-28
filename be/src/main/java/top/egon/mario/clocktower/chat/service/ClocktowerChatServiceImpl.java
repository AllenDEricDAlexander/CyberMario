package top.egon.mario.clocktower.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.chat.ClocktowerChatAccessContext;
import top.egon.mario.clocktower.chat.ClocktowerChatConstants;
import top.egon.mario.clocktower.chat.ClocktowerImAdapter;
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
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClocktowerChatServiceImpl implements ClocktowerChatService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_CLOCKTOWER_ADMIN = "CLOCKTOWER_ADMIN";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final ClocktowerGameContextService gameContextService;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerImAdapter imAdapter;
    private final ClocktowerChatPolicy policy;

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> conversations(Long roomId, RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        List<ClocktowerChatConversationResponse> visible = new ArrayList<>();
        visible.addAll(imAdapter.roomConversations(roomId, checkedPrincipal, null));
        gameContextService.currentGameId(roomId)
                .ifPresent(gameId -> visible.addAll(imAdapter.gameConversations(gameId, checkedPrincipal, null)));
        return visible;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> conversationsForGame(Long roomId, Long gameId,
                                                                         RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        requireGameId(gameId);
        List<ClocktowerChatConversationResponse> visible = new ArrayList<>();
        visible.addAll(imAdapter.roomConversations(roomId, checkedPrincipal, null));
        visible.addAll(imAdapter.gameConversations(gameId, checkedPrincipal, null));
        return visible;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerChatConversationResponse> auditConversations(Long roomId, RbacPrincipal principal) {
        requireAdminAudit(principal);
        List<ClocktowerChatConversationResponse> visible = new ArrayList<>();
        visible.addAll(imAdapter.roomConversations(roomId, principal, ClocktowerChatViewerMode.ADMIN_AUDIT));
        gameContextService.currentGameId(roomId)
                .ifPresent(gameId -> visible.addAll(imAdapter.gameConversations(gameId, principal,
                        ClocktowerChatViewerMode.ADMIN_AUDIT)));
        return visible;
    }

    @Override
    public Page<ClocktowerChatMessageResponse> messages(Long conversationId, Pageable pageable,
                                                        RbacPrincipal principal) {
        return imAdapter.history(conversationId, pageable, requirePrincipal(principal));
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

        return imAdapter.privateConversation(game, checkedPrincipal.userId(), request.targetUserId());
    }

    @Override
    public ClocktowerChatMessageResponse sendMessage(Long conversationId, ClocktowerChatSendMessageRequest request,
                                                     RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_CONTENT_REQUIRED");
        }
        return imAdapter.sendMessage(conversationId, request.content(), request.metadataJson(), checkedPrincipal);
    }

    @Override
    public ClocktowerChatReadStateResponse markRead(Long conversationId, ClocktowerChatMarkReadRequest request,
                                                    RbacPrincipal principal) {
        RbacPrincipal checkedPrincipal = requirePrincipal(principal);
        if (request == null || request.messageSeq() == null) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_READ_SEQ_REQUIRED");
        }
        return imAdapter.markRead(conversationId, request.messageSeq(), checkedPrincipal);
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
        if (checkedPrincipal.roleCodes() == null || (!checkedPrincipal.roleCodes().contains(ROLE_CLOCKTOWER_ADMIN)
                && !checkedPrincipal.roleCodes().contains(ROLE_SUPER_ADMIN))) {
            throw new ClocktowerException("CLOCKTOWER_CHAT_AUDIT_FORBIDDEN");
        }
    }

}
