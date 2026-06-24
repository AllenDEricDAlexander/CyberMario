package top.egon.mario.clocktower.room.dto.response;

import top.egon.mario.room.po.RoomInvitationPo;

import java.time.Instant;

public record ClocktowerRoomReservationResponse(
        Long invitationId,
        Long inviteeUserId,
        Integer targetSeatNo,
        Instant expiresAt
) {

    public static ClocktowerRoomReservationResponse from(RoomInvitationPo invitation) {
        return new ClocktowerRoomReservationResponse(invitation.getId(), invitation.getInviteeUserId(),
                invitation.getTargetSeatNo(), invitation.getExpiresAt());
    }
}
