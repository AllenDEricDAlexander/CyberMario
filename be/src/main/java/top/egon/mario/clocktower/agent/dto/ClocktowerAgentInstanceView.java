package top.egon.mario.clocktower.agent.dto;

public record ClocktowerAgentInstanceView(
        Long instanceId,
        Long actorId,
        Long roomId,
        Long gameId,
        Long roomSeatId,
        Long gameSeatId,
        String displayName,
        String profileName,
        String status,
        String autoMode
) {
}
