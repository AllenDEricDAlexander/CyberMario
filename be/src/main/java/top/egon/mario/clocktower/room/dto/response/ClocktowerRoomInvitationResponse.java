package top.egon.mario.clocktower.room.dto.response;

import top.egon.mario.room.facade.RoomFacade;

import java.time.Instant;

public record ClocktowerRoomInvitationResponse(
        Long invitationId,
        Long roomId,
        Long inviteeUserId,
        String status,
        Integer targetSeatNo,
        Instant expiresAt
) {

    public static ClocktowerRoomInvitationResponse from(RoomFacade.RoomInvitationView invitation) {
        return new ClocktowerRoomInvitationResponse(invitation.invitationId(), invitation.roomId(),
                invitation.inviteeUserId(), invitation.status(), invitation.targetSeatNo(), invitation.expiresAt());
    }
}
