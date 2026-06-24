package top.egon.mario.clocktower.view.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomProfileRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomMemberRepository;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerViewerResolver {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISBANDED = "DISBANDED";
    private static final String ROLE_CLOCKTOWER_ADMIN = "CLOCKTOWER_ADMIN";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerRoomProfileRepository profileRepository;
    private final RoomSpaceRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomInvitationRepository invitationRepository;

    @Transactional(readOnly = true)
    public ClocktowerViewerContext resolveGameViewer(Long gameId, RbacPrincipal principal) {
        requirePrincipal(principal);
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        RoomSpacePo room = roomRepository.findByIdAndDeletedFalse(game.getRoomId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerRoomProfilePo profile = profileRepository.findByRoomIdAndDeletedFalse(game.getRoomId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_PROFILE_NOT_FOUND"));
        Long userId = principal.userId();
        if (isStoryteller(room, profile, userId)) {
            return new ClocktowerViewerContext(game, room, profile, null, ClocktowerViewerMode.STORYTELLER);
        }
        ClocktowerGameSeatPo activeSeat = gameSeatRepository.findByGameIdAndUserIdAndDeletedFalse(gameId, userId)
                .filter(seat -> STATUS_ACTIVE.equals(seat.getStatus()))
                .orElse(null);
        if (activeSeat != null) {
            return new ClocktowerViewerContext(game, room, profile, activeSeat, ClocktowerViewerMode.PLAYER);
        }
        RoomMemberPo member = memberRepository.findActiveByRoomIdAndUserId(game.getRoomId(), userId).orElse(null);
        if (member != null) {
            return new ClocktowerViewerContext(game, room, profile, null, ClocktowerViewerMode.SPECTATOR);
        }
        if (!STATUS_DISBANDED.equals(profile.getStatus()) && hasActiveInvitation(game.getRoomId(), userId)) {
            return new ClocktowerViewerContext(game, room, profile, null, ClocktowerViewerMode.INVITED);
        }
        throw new ClocktowerException("CLOCKTOWER_VIEW_FORBIDDEN");
    }

    public void requireAdminAudit(RbacPrincipal principal) {
        requirePrincipal(principal);
        if (principal.roleCodes() == null || (!principal.roleCodes().contains(ROLE_CLOCKTOWER_ADMIN)
                && !principal.roleCodes().contains(ROLE_SUPER_ADMIN))) {
            throw new ClocktowerException("CLOCKTOWER_AUDIT_FORBIDDEN");
        }
    }

    private void requirePrincipal(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ClocktowerException("CLOCKTOWER_AUTH_REQUIRED");
        }
    }

    private boolean isStoryteller(RoomSpacePo room, ClocktowerRoomProfilePo profile, Long userId) {
        return Objects.equals(room.getOwnerUserId(), userId)
                || Objects.equals(profile.getStorytellerUserId(), userId);
    }

    private boolean hasActiveInvitation(Long roomId, Long userId) {
        Instant now = Instant.now();
        return invitationRepository.findActiveByRoomIdAndInviteeUserId(roomId, userId).stream()
                .anyMatch(invitation -> invitation.getExpiresAt() == null || invitation.getExpiresAt().isAfter(now));
    }
}
