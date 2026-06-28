package top.egon.mario.clocktower.admin.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.admin.dto.ClocktowerGameAuditResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerRoomAuditResponse;
import top.egon.mario.clocktower.admin.service.ClocktowerManagementAuditService;
import top.egon.mario.clocktower.chat.ClocktowerChatViewerMode;
import top.egon.mario.clocktower.chat.ClocktowerImAdapter;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatConversationResponse;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.replay.dto.ClocktowerGameHistoryResponse;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerGameSeatViewResponse;
import top.egon.mario.clocktower.view.service.ClocktowerGameProjectionMapper;
import top.egon.mario.clocktower.view.service.ClocktowerViewerContext;
import top.egon.mario.clocktower.view.service.ClocktowerViewerResolver;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomBanRepository;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClocktowerManagementAuditServiceImpl implements ClocktowerManagementAuditService {

    private final ClocktowerViewerResolver viewerResolver;
    private final RoomSpaceRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomInvitationRepository invitationRepository;
    private final RoomBanRepository banRepository;
    private final ClocktowerRoomProfileRepository profileRepository;
    private final ClocktowerRoomSeatRepository roomSeatRepository;
    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final ClocktowerImAdapter clocktowerImAdapter;
    private final ClocktowerGameProjectionMapper projectionMapper;

    @Override
    @Transactional(readOnly = true)
    public ClocktowerRoomAuditResponse auditRoom(Long roomId, RbacPrincipal principal) {
        viewerResolver.requireAdminAudit(principal);
        RoomSpacePo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        var profile = profileRepository.findByRoomIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_PROFILE_NOT_FOUND"));
        List<ClocktowerGamePo> games = gameRepository.findByRoomIdAndDeletedFalseOrderByGameNoAsc(roomId);
        List<ClocktowerChatConversationResponse> conversations = new ArrayList<>(
                clocktowerImAdapter.roomConversations(roomId, principal, ClocktowerChatViewerMode.ADMIN_AUDIT));
        games.forEach(game -> conversations.addAll(clocktowerImAdapter.gameConversations(game.getId(), principal,
                ClocktowerChatViewerMode.ADMIN_AUDIT)));
        return ClocktowerRoomAuditResponse.from(room, profile,
                roomSeatRepository.findByRoomIdOrderBySeatNoAsc(roomId).stream()
                        .map(ClocktowerRoomAuditResponse.Seat::from)
                        .toList(),
                memberRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(roomId).stream()
                        .map(ClocktowerRoomAuditResponse.Member::from)
                        .toList(),
                invitationRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(roomId).stream()
                        .map(ClocktowerRoomAuditResponse.Invitation::from)
                        .toList(),
                banRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(roomId).stream()
                        .map(ClocktowerRoomAuditResponse.Ban::from)
                        .toList(),
                games.stream().map(ClocktowerGameHistoryResponse::from).toList(),
                conversations);
    }

    @Override
    @Transactional(readOnly = true)
    public ClocktowerGameAuditResponse auditGame(Long gameId, RbacPrincipal principal) {
        viewerResolver.requireAdminAudit(principal);
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        return ClocktowerGameAuditResponse.from(game,
                gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId).stream()
                        .map(ClocktowerGameSeatViewResponse::fullView)
                        .toList(),
                gameEvents(game),
                clocktowerImAdapter.gameConversations(gameId, principal, ClocktowerChatViewerMode.ADMIN_AUDIT));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClocktowerChatMessageResponse> messages(Long conversationId, Pageable pageable,
                                                        RbacPrincipal principal) {
        viewerResolver.requireAdminAudit(principal);
        return clocktowerImAdapter.auditMessages(conversationId, pageable, principal);
    }

    private List<ClocktowerGameEventResponse> gameEvents(ClocktowerGamePo game) {
        ClocktowerViewerContext auditViewer = new ClocktowerViewerContext(game, null, null, null,
                ClocktowerViewerMode.ADMIN_AUDIT);
        return gameEventRepository.findByGameIdAndStatusAndDeletedFalseOrderByEventSeqAsc(game.getId(), "VISIBLE")
                .stream()
                .map(projectionMapper::toEventResponse)
                .filter(event -> projectionMapper.visibleTo(event, auditViewer, true))
                .toList();
    }

}
