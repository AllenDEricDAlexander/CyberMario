package top.egon.mario.room.context;

public record RoomContext(
        String contextType,
        Long contextId,
        Long roomId,
        Long ownerUserId,
        Long viewerUserId,
        String memberRole,
        String roomStatus,
        String roomVisibility,
        Integer requestedSeatNo
) {
}
