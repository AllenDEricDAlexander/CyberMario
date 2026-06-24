package top.egon.mario.clocktower.room.dto.response;

import top.egon.mario.room.po.RoomMemberPo;

public record ClocktowerRoomMemberResponse(
        Long memberId,
        Long userId,
        String memberType,
        String status,
        Integer seatNo,
        String displayName
) {

    public static ClocktowerRoomMemberResponse from(RoomMemberPo member) {
        return new ClocktowerRoomMemberResponse(member.getId(), member.getUserId(), member.getMemberType(),
                member.getStatus(), member.getSeatNo(), member.getDisplayName());
    }
}
