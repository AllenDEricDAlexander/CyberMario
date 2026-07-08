package top.egon.mario.clocktower.game.mic.dto;

import java.time.Instant;

public record ClocktowerMicTurnView(
        Long turnId,
        Long gameSeatId,
        Integer seatNo,
        String displayName,
        String actorType,
        Long agentInstanceId,
        Integer turnOrder,
        String stage,
        String acquisitionType,
        String status,
        Instant startedAt,
        Instant endedAt,
        Instant expiresAt
) {
}
