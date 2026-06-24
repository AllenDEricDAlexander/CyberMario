package top.egon.mario.clocktower.room.policy;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.repository.RoomInvitationRepository;

import java.time.Instant;

@Component
public class ClocktowerSeatAssignmentPolicy {

    public static final String OPEN_SEATING = "OPEN_SEATING";
    public static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String INVITE_ONLY = "INVITE_ONLY";

    private static final String STATUS_LOBBY = "LOBBY";

    private final ClocktowerRoomAccessPolicy accessPolicy;
    private final RoomInvitationRepository invitationRepository;

    public ClocktowerSeatAssignmentPolicy(ClocktowerRoomAccessPolicy accessPolicy,
                                          RoomInvitationRepository invitationRepository) {
        this.accessPolicy = accessPolicy;
        this.invitationRepository = invitationRepository;
    }

    public SeatClaimDecision decideClaim(ClocktowerRoomProfilePo profile, ClocktowerRoomSeatPo seat,
                                         RbacPrincipal principal, String seatingPolicy) {
        accessPolicy.requireAuthenticated(principal);
        accessPolicy.requireNotBanned(profile.getRoomId(), principal.userId());
        if (!STATUS_LOBBY.equals(profile.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_ROOM_NOT_LOBBY");
        }
        if (seat.getUserId() != null && !seat.getUserId().equals(principal.userId())) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_OCCUPIED");
        }
        return switch (normalizedPolicy(seatingPolicy)) {
            case OPEN_SEATING -> SeatClaimDecision.ASSIGN;
            case APPROVAL_REQUIRED -> SeatClaimDecision.RESERVE;
            case INVITE_ONLY -> {
                if (activeSeatInvitation(profile.getRoomId(), principal.userId(), seat.getSeatNo()) == null) {
                    throw new ClocktowerException("CLOCKTOWER_SEAT_INVITATION_REQUIRED");
                }
                yield SeatClaimDecision.ACCEPT_INVITATION;
            }
            default -> throw new ClocktowerException("CLOCKTOWER_SEATING_POLICY_INVALID");
        };
    }

    public RoomInvitationPo activeSeatInvitation(Long roomId, Long userId, int seatNo) {
        Instant now = Instant.now();
        return invitationRepository.findActiveByRoomIdAndInviteeUserId(roomId, userId).stream()
                .filter(invitation -> Integer.valueOf(seatNo).equals(invitation.getTargetSeatNo()))
                .filter(invitation -> invitation.getExpiresAt() == null || invitation.getExpiresAt().isAfter(now))
                .findFirst()
                .orElse(null);
    }

    private String normalizedPolicy(String seatingPolicy) {
        if (seatingPolicy == null || seatingPolicy.isBlank()) {
            return APPROVAL_REQUIRED;
        }
        return seatingPolicy;
    }

    public enum SeatClaimDecision {
        ASSIGN,
        RESERVE,
        ACCEPT_INVITATION
    }
}
