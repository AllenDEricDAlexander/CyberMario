package top.egon.mario.clocktower.room.dto.request;

public record ClocktowerJoinRoomRequest(String displayName, Integer seatNo) {

    public ClocktowerRoomJoinRequest toJoinRequest() {
        return new ClocktowerRoomJoinRequest(seatNo, displayName, null);
    }
}
