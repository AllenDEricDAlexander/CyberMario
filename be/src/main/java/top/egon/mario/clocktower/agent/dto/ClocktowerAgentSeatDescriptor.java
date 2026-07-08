package top.egon.mario.clocktower.agent.dto;

import java.util.Map;

public record ClocktowerAgentSeatDescriptor(
        Long actorId,
        Long agentInstanceId,
        Long roomId,
        Long roomSeatId,
        int seatNo,
        String displayName,
        String roleCode,
        String profileName,
        String autoMode,
        Map<String, Object> metadata
) {
}
