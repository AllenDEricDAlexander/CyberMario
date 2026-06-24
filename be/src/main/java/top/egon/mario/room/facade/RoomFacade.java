package top.egon.mario.room.facade;

import org.springframework.stereotype.Service;
import top.egon.mario.room.context.RoomPrincipal;
import top.egon.mario.room.po.RoomBanPo;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.service.RoomService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class RoomFacade {

    private final RoomService roomService;

    public RoomFacade(RoomService roomService) {
        this.roomService = roomService;
    }

    public RoomView createRoom(String contextType, Long contextId, Long ownerUserId, String visibility) {
        return toRoomView(roomService.createRoom(contextType, contextId, ownerUserId, visibility));
    }

    public RoomMemberView enterRoom(Long roomId, RoomPrincipal principal) {
        return toMemberView(roomService.enterRoom(roomId, principal));
    }

    public RoomInvitationView invite(Long roomId, Long inviteeUserId, String invitationType,
                                     Integer targetSeatNo, Instant expiresAt) {
        return toInvitationView(roomService.invite(roomId, inviteeUserId, invitationType, targetSeatNo, expiresAt));
    }

    public RoomInvitationView acceptInvitation(Long roomId, Long invitationId, RoomPrincipal principal) {
        return toInvitationView(roomService.acceptInvitation(roomId, invitationId, principal));
    }

    public RoomInvitationView declineInvitation(Long roomId, Long invitationId, RoomPrincipal principal) {
        return toInvitationView(roomService.declineInvitation(roomId, invitationId, principal));
    }

    public void kick(Long roomId, Long targetUserId, String reason) {
        roomService.kick(roomId, targetUserId, reason);
    }

    public RoomBanView ban(Long roomId, Long targetUserId, Duration duration, String reason) {
        return toBanView(roomService.ban(roomId, targetUserId, duration, reason));
    }

    public void heartbeat(Long roomId, RoomPrincipal principal) {
        roomService.heartbeat(roomId, principal);
    }

    public List<RoomReservationView> activeReservations(Long roomId) {
        return roomService.activeReservations(roomId).stream()
                .map(RoomFacade::toReservationView)
                .toList();
    }

    private static RoomView toRoomView(RoomSpacePo room) {
        return new RoomView(room.getId(), room.getContextType(), room.getContextId(), room.getOwnerUserId(),
                room.getVisibility(), room.getStatus(), room.getCurrentMemberCount());
    }

    private static RoomMemberView toMemberView(RoomMemberPo member) {
        return new RoomMemberView(member.getId(), member.getRoomId(), member.getUserId(), member.getMemberType(),
                member.getStatus(), member.getSeatNo());
    }

    private static RoomInvitationView toInvitationView(RoomInvitationPo invitation) {
        return new RoomInvitationView(invitation.getId(), invitation.getRoomId(), invitation.getInviteeUserId(),
                invitation.getStatus(), invitation.getTargetSeatNo(), invitation.getExpiresAt());
    }

    private static RoomReservationView toReservationView(RoomInvitationPo invitation) {
        return new RoomReservationView(invitation.getId(), invitation.getRoomId(), invitation.getInviteeUserId(),
                invitation.getTargetSeatNo(), invitation.getExpiresAt());
    }

    private static RoomBanView toBanView(RoomBanPo ban) {
        return new RoomBanView(ban.getId(), ban.getRoomId(), ban.getUserId(), ban.getStatus(), ban.getExpiresAt());
    }

    public record RoomView(Long roomId, String contextType, Long contextId, Long ownerUserId,
                           String visibility, String status, int currentMemberCount) {
    }

    public record RoomMemberView(Long memberId, Long roomId, Long userId, String memberType,
                                 String status, Integer seatNo) {
    }

    public record RoomInvitationView(Long invitationId, Long roomId, Long inviteeUserId, String status,
                                     Integer targetSeatNo, Instant expiresAt) {
    }

    public record RoomReservationView(Long invitationId, Long roomId, Long inviteeUserId,
                                      Integer targetSeatNo, Instant expiresAt) {
    }

    public record RoomBanView(Long banId, Long roomId, Long userId, String status, Instant expiresAt) {
    }
}
