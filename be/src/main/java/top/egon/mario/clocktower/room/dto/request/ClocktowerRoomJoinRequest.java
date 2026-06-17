package top.egon.mario.clocktower.room.dto.request;

public record ClocktowerRoomJoinRequest(
        Integer seatNo,
        String displayName,
        String inviteCode
) {
}
