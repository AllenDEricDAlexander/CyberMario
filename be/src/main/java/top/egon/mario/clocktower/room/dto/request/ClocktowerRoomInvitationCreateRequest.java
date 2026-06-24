package top.egon.mario.clocktower.room.dto.request;

import java.time.Instant;

public record ClocktowerRoomInvitationCreateRequest(
        Long inviteeUserId,
        String invitationType,
        Integer targetSeatNo,
        Instant expiresAt
) {
}
