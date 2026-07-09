package top.egon.mario.clocktower.agent.view.dto;

public record AgentPublicSeatView(
        Long gameSeatId,
        int seatNo,
        String displayName,
        String roleCode,
        String roleType,
        String alignment,
        String lifeStatus,
        String publicLifeStatus,
        boolean hasDeadVote,
        boolean traveler,
        String actorType,
        boolean agent,
        String status
) {
}
