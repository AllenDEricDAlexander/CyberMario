package top.egon.mario.clocktower.room.policy;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomBanRepository;
import top.egon.mario.room.repository.RoomInvitationRepository;
import top.egon.mario.room.repository.RoomMemberRepository;

import java.time.Instant;
import java.util.Objects;

@Component
public class ClocktowerRoomAccessPolicy {

    private static final String STATUS_DISBANDED = "DISBANDED";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";

    private final RoomBanRepository banRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomInvitationRepository invitationRepository;

    public ClocktowerRoomAccessPolicy(RoomBanRepository banRepository,
                                      RoomMemberRepository memberRepository,
                                      RoomInvitationRepository invitationRepository) {
        this.banRepository = banRepository;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
    }

    public void requireAuthenticated(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ClocktowerException("CLOCKTOWER_AUTH_REQUIRED");
        }
    }

    public void requireOwner(RoomSpacePo room, RbacPrincipal principal) {
        requireAuthenticated(principal);
        if (!Objects.equals(room.getOwnerUserId(), principal.userId())) {
            throw new ClocktowerException("CLOCKTOWER_STORYTELLER_FORBIDDEN");
        }
    }

    public boolean canView(RoomSpacePo room, ClocktowerRoomProfilePo profile, RbacPrincipal principal) {
        if (isDisbanded(profile)) {
            return false;
        }
        if (principal != null && principal.userId() != null
                && isActivelyBanned(room.getId(), principal.userId(), Instant.now())) {
            return false;
        }
        if (VISIBILITY_PUBLIC.equals(room.getVisibility())) {
            return true;
        }
        if (principal == null || principal.userId() == null) {
            return false;
        }
        return Objects.equals(room.getOwnerUserId(), principal.userId())
                || memberRepository.findActiveByRoomIdAndUserId(room.getId(), principal.userId()).isPresent()
                || hasActiveInvitation(room.getId(), principal.userId(), Instant.now());
    }

    public void requireEnterAllowed(RoomSpacePo room, ClocktowerRoomProfilePo profile, RbacPrincipal principal) {
        requireAuthenticated(principal);
        if (isDisbanded(profile)) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_DISBANDED");
        }
        requireNotBanned(room.getId(), principal.userId());
        if (!VISIBILITY_PUBLIC.equals(room.getVisibility())
                && !Objects.equals(room.getOwnerUserId(), principal.userId())
                && memberRepository.findActiveByRoomIdAndUserId(room.getId(), principal.userId()).isEmpty()
                && !hasActiveInvitation(room.getId(), principal.userId(), Instant.now())) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_PRIVATE");
        }
    }

    public void requireNotBanned(Long roomId, Long userId) {
        if (isActivelyBanned(roomId, userId, Instant.now())) {
            throw new ClocktowerException("CLOCKTOWER_USER_BANNED");
        }
    }

    private boolean isDisbanded(ClocktowerRoomProfilePo profile) {
        return profile != null && STATUS_DISBANDED.equals(profile.getStatus());
    }

    private boolean isActivelyBanned(Long roomId, Long userId, Instant now) {
        return userId != null && banRepository.findActiveByRoomIdAndUserId(roomId, userId, now).isPresent();
    }

    private boolean hasActiveInvitation(Long roomId, Long userId, Instant now) {
        return invitationRepository.findActiveByRoomIdAndInviteeUserId(roomId, userId).stream()
                .anyMatch(invitation -> !isExpired(invitation, now));
    }

    private boolean isExpired(RoomInvitationPo invitation, Instant now) {
        return invitation.getExpiresAt() != null && !invitation.getExpiresAt().isAfter(now);
    }
}
