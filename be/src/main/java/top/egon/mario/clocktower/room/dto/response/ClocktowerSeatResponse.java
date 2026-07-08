package top.egon.mario.clocktower.room.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;

public record ClocktowerSeatResponse(
        Long seatId,
        int seatNo,
        Long userId,
        Long actorId,
        String actorType,
        Long agentInstanceId,
        @JsonProperty("isAgent")
        boolean isAgent,
        String displayName,
        String roleCode,
        ClocktowerRoleType roleType,
        String lifeStatus,
        String publicLifeStatus,
        boolean connected,
        boolean hasDeadVote,
        String status,
        boolean ready
) {

    public static ClocktowerSeatResponse from(ClocktowerSeatPo seat) {
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), null,
                ClocktowerActorType.HUMAN, null, false, seat.getDisplayName(), seat.getRoleCode(),
                seat.getRoleType(), seat.getLifeStatus(), seat.getPublicLifeStatus(), seat.isConnected(),
                seat.isHasDeadVote(), seat.getUserId() == null ? "OPEN" : "OCCUPIED", false);
    }

    public static ClocktowerSeatResponse publicView(ClocktowerSeatPo seat) {
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), null,
                ClocktowerActorType.HUMAN, null, false, seat.getDisplayName(), null, null,
                seat.getPublicLifeStatus(), seat.getPublicLifeStatus(), seat.isConnected(),
                seat.isHasDeadVote(), seat.getUserId() == null ? "OPEN" : "OCCUPIED", false);
    }

    public static ClocktowerSeatResponse from(ClocktowerRoomSeatPo seat, boolean ready) {
        String actorType = actorType(seat.getActorType());
        boolean agent = ClocktowerActorType.AGENT.equals(actorType) || seat.getAgentInstanceId() != null;
        return new ClocktowerSeatResponse(seat.getId(), seat.getSeatNo(), seat.getUserId(), seat.getActorId(),
                actorType, seat.getAgentInstanceId(), agent, seat.getDisplayName(), seat.getRoleCode(), null,
                null, null, seat.getUserId() != null, false, seat.getStatus(), ready);
    }

    private static String actorType(String actorType) {
        return actorType == null ? ClocktowerActorType.HUMAN : actorType;
    }
}
