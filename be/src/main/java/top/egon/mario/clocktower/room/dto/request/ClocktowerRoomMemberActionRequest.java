package top.egon.mario.clocktower.room.dto.request;

public record ClocktowerRoomMemberActionRequest(
        boolean ban,
        Long banDurationSeconds,
        String reason
) {
}
