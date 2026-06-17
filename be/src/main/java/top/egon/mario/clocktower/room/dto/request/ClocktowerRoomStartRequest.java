package top.egon.mario.clocktower.room.dto.request;

import java.util.List;

public record ClocktowerRoomStartRequest(
        List<RoleAssignmentRequest> assignments,
        boolean randomize
) {
}
